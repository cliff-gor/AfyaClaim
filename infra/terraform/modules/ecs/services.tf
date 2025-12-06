# ---------------------------------------------------------------------------
# Task definitions
#
# Terraform owns the *shape* of the task definitions (CPU, memory, env, secrets,
# logging). The deploy workflow owns the *image*: it reads the latest revision
# of each family, swaps the image for the commit SHA and registers a new
# revision. Services therefore ignore task_definition drift.
# ---------------------------------------------------------------------------

locals {
  image = "${var.ecr_repository_url}:${var.image_tag}"

  common_environment = [
    for k, v in merge({
      RAILS_ENV                = "production"
      RAILS_LOG_TO_STDOUT      = "1"
      RAILS_SERVE_STATIC_FILES = "0"
      APP_ENV                  = var.environment
      AWS_REGION               = data.aws_region.current.name
      RECEIPTS_BUCKET          = var.receipts_bucket_name
      MPESA_ENVIRONMENT        = var.mpesa_environment
      DASHBOARD_ORIGIN         = var.dashboard_origin
      WEB_CONCURRENCY          = tostring(var.web_concurrency)
      RAILS_MAX_THREADS        = tostring(var.rails_max_threads)
    }, var.extra_environment) : { name = k, value = v }
  ]

  container_defaults = {
    image                  = local.image
    essential              = true
    environment            = local.common_environment
    secrets                = local.container_secrets
    readonlyRootFilesystem = false
    linuxParameters        = { initProcessEnabled = true } # required for ECS Exec
  }
}

resource "aws_ecs_task_definition" "web" {
  family                   = "${var.name}-web"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.web_cpu
  memory                   = var.web_memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = var.cpu_architecture
  }

  container_definitions = jsonencode([
    merge(local.container_defaults, {
      name = "web"
      # Puma directly on the container port. bin/docker-entrypoint only runs
      # db:prepare for the bare `./bin/rails server` form, so migrations stay
      # in the dedicated one-off task run by the deploy workflow.
      command      = var.web_command
      portMappings = [{ containerPort = var.container_port, protocol = "tcp" }]
      healthCheck = {
        command     = ["CMD-SHELL", "curl -fsS http://localhost:${var.container_port}/up || exit 1"]
        interval    = 15
        timeout     = 5
        retries     = 3
        startPeriod = 30
      }
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = var.web_log_group_name
          awslogs-region        = data.aws_region.current.name
          awslogs-stream-prefix = "web"
        }
      }
    })
  ])
}

resource "aws_ecs_task_definition" "worker" {
  family                   = "${var.name}-worker"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.worker_cpu
  memory                   = var.worker_memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = var.cpu_architecture
  }

  container_definitions = jsonencode([
    merge(local.container_defaults, {
      name    = "worker"
      command = ["./bin/jobs"] # Solid Queue supervisor (dispatcher + workers + scheduler)
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = var.worker_log_group_name
          awslogs-region        = data.aws_region.current.name
          awslogs-stream-prefix = "worker"
        }
      }
    })
  ])
}

# ---------------------------------------------------------------------------
# Services
# ---------------------------------------------------------------------------

resource "aws_ecs_service" "web" {
  name                              = "web"
  cluster                           = aws_ecs_cluster.this.id
  task_definition                   = aws_ecs_task_definition.web.arn
  desired_count                     = var.web_desired_count
  launch_type                       = "FARGATE"
  enable_execute_command            = true
  health_check_grace_period_seconds = 60
  propagate_tags                    = "SERVICE"
  wait_for_steady_state             = false

  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [aws_security_group.app.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.web.arn
    container_name   = "web"
    container_port   = var.container_port
  }

  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  lifecycle {
    ignore_changes = [task_definition, desired_count]
  }

  depends_on = [aws_lb_listener.http]
}

resource "aws_ecs_service" "worker" {
  name                   = "worker"
  cluster                = aws_ecs_cluster.this.id
  task_definition        = aws_ecs_task_definition.worker.arn
  desired_count          = var.worker_desired_count
  launch_type            = "FARGATE"
  enable_execute_command = true
  propagate_tags         = "SERVICE"

  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [aws_security_group.app.id]
    assign_public_ip = false
  }

  # Solid Queue tolerates two supervisors briefly (jobs are claimed with
  # row locks), so a rolling replacement is safe.
  deployment_minimum_healthy_percent = 0
  deployment_maximum_percent         = 200

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  lifecycle {
    ignore_changes = [task_definition]
  }
}

# ---------------------------------------------------------------------------
# Web autoscaling (CPU target tracking)
# ---------------------------------------------------------------------------

resource "aws_appautoscaling_target" "web" {
  service_namespace  = "ecs"
  resource_id        = "service/${aws_ecs_cluster.this.name}/${aws_ecs_service.web.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  min_capacity       = var.web_min_count
  max_capacity       = var.web_max_count
}

resource "aws_appautoscaling_policy" "web_cpu" {
  name               = "${var.name}-web-cpu"
  policy_type        = "TargetTrackingScaling"
  service_namespace  = aws_appautoscaling_target.web.service_namespace
  resource_id        = aws_appautoscaling_target.web.resource_id
  scalable_dimension = aws_appautoscaling_target.web.scalable_dimension

  target_tracking_scaling_policy_configuration {
    target_value       = 60
    scale_in_cooldown  = 300
    scale_out_cooldown = 60

    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
  }
}
