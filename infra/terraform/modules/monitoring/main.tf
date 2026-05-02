terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

data "aws_region" "current" {}

locals {
  namespace     = "AfyaClaim/${var.environment}"
  alarm_actions = [aws_sns_topic.alerts.arn]
}

# ===========================================================================
# Log groups (structured JSON from Lograge / Rails.logger)
# ===========================================================================

resource "aws_cloudwatch_log_group" "web" {
  name              = "/afyaclaim/${var.environment}/web"
  retention_in_days = var.log_retention_days
}

resource "aws_cloudwatch_log_group" "worker" {
  name              = "/afyaclaim/${var.environment}/worker"
  retention_in_days = var.log_retention_days
}

# ===========================================================================
# Alerting
# ===========================================================================

resource "aws_sns_topic" "alerts" {
  name = "${var.name}-alerts"
}

resource "aws_sns_topic_subscription" "email" {
  for_each = toset(var.alert_emails)

  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = each.value
}

# ===========================================================================
# Log metric filters (emitted by the app as JSON log lines)
#   {"event":"reconciliation_mismatch", "kind":"ledger_imbalance", ...}
#   {"event":"mpesa_payout_failed", "reimbursement_id":123, ...}
#   {"event":"job_queue_latency", "queue":"default", "seconds":12.4}
# The bare-term pattern for reconciliation_mismatch also matches non-JSON lines.
# ===========================================================================

resource "aws_cloudwatch_log_metric_filter" "reconciliation_mismatch" {
  name           = "${var.name}-reconciliation-mismatch"
  log_group_name = aws_cloudwatch_log_group.worker.name
  pattern        = "\"reconciliation_mismatch\""

  metric_transformation {
    name          = "ReconciliationMismatch"
    namespace     = local.namespace
    value         = "1"
    default_value = "0"
    unit          = "Count"
  }
}

resource "aws_cloudwatch_log_metric_filter" "mpesa_payout_failed" {
  name           = "${var.name}-mpesa-payout-failed"
  log_group_name = aws_cloudwatch_log_group.worker.name
  pattern        = "{ $.event = \"mpesa_payout_failed\" }"

  metric_transformation {
    name          = "MpesaPayoutFailed"
    namespace     = local.namespace
    value         = "1"
    default_value = "0"
    unit          = "Count"
  }
}

resource "aws_cloudwatch_log_metric_filter" "job_queue_latency" {
  name           = "${var.name}-job-queue-latency"
  log_group_name = aws_cloudwatch_log_group.worker.name
  pattern        = "{ $.event = \"job_queue_latency\" }"

  metric_transformation {
    name      = "JobQueueLatencySeconds"
    namespace = local.namespace
    value     = "$.seconds"
    unit      = "Seconds"
  }
}

# ===========================================================================
# Alarms: API (ALB)
# ===========================================================================

resource "aws_cloudwatch_metric_alarm" "alb_5xx_rate" {
  alarm_name          = "${var.name}-api-5xx-rate"
  alarm_description   = "API 5xx responses above ${var.alb_5xx_rate_threshold_percent}% of requests. Runbook: docs/runbook.md#5xx-spike"
  comparison_operator = "GreaterThanThreshold"
  threshold           = var.alb_5xx_rate_threshold_percent
  evaluation_periods  = 5
  datapoints_to_alarm = 3
  treat_missing_data  = "notBreaching"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions

  metric_query {
    id          = "rate"
    expression  = "IF(requests >= ${var.alb_5xx_min_requests}, 100 * (FILL(target5xx, 0) + FILL(elb5xx, 0)) / requests, 0)"
    label       = "5xx rate (%)"
    return_data = true
  }

  metric_query {
    id = "target5xx"
    metric {
      namespace   = "AWS/ApplicationELB"
      metric_name = "HTTPCode_Target_5XX_Count"
      period      = 60
      stat        = "Sum"
      dimensions  = { LoadBalancer = var.alb_arn_suffix }
    }
  }

  metric_query {
    id = "elb5xx"
    metric {
      namespace   = "AWS/ApplicationELB"
      metric_name = "HTTPCode_ELB_5XX_Count"
      period      = 60
      stat        = "Sum"
      dimensions  = { LoadBalancer = var.alb_arn_suffix }
    }
  }

  metric_query {
    id = "requests"
    metric {
      namespace   = "AWS/ApplicationELB"
      metric_name = "RequestCount"
      period      = 60
      stat        = "Sum"
      dimensions  = { LoadBalancer = var.alb_arn_suffix }
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "target_p95_latency" {
  alarm_name          = "${var.name}-api-p95-latency"
  alarm_description   = "Target p95 response time above ${var.p95_latency_threshold_seconds * 1000}ms (NFR: p95 < 300ms). Runbook: docs/runbook.md#high-latency"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "TargetResponseTime"
  extended_statistic  = "p95"
  period              = 60
  evaluation_periods  = 5
  datapoints_to_alarm = 4
  threshold           = var.p95_latency_threshold_seconds
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions

  dimensions = {
    LoadBalancer = var.alb_arn_suffix
    TargetGroup  = var.target_group_arn_suffix
  }
}

resource "aws_cloudwatch_metric_alarm" "unhealthy_hosts" {
  alarm_name          = "${var.name}-api-unhealthy-hosts"
  alarm_description   = "One or more web tasks failing GET /up. Runbook: docs/runbook.md#5xx-spike"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "UnHealthyHostCount"
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 3
  datapoints_to_alarm = 2
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "breaching"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions

  dimensions = {
    LoadBalancer = var.alb_arn_suffix
    TargetGroup  = var.target_group_arn_suffix
  }
}

# ===========================================================================
# Alarms: RDS
# ===========================================================================

resource "aws_cloudwatch_metric_alarm" "rds_cpu" {
  alarm_name          = "${var.name}-rds-cpu-high"
  alarm_description   = "RDS CPU above 80% for 15 minutes. Runbook: docs/runbook.md#high-latency"
  namespace           = "AWS/RDS"
  metric_name         = "CPUUtilization"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 3
  threshold           = 80
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "missing"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions

  dimensions = { DBInstanceIdentifier = var.rds_identifier }
}

resource "aws_cloudwatch_metric_alarm" "rds_free_storage" {
  alarm_name          = "${var.name}-rds-free-storage-low"
  alarm_description   = "RDS free storage below ${var.rds_free_storage_threshold_gb}GB. Runbook: docs/runbook.md#rds-storage"
  namespace           = "AWS/RDS"
  metric_name         = "FreeStorageSpace"
  statistic           = "Minimum"
  period              = 300
  evaluation_periods  = 2
  threshold           = var.rds_free_storage_threshold_gb * 1024 * 1024 * 1024
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "missing"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions

  dimensions = { DBInstanceIdentifier = var.rds_identifier }
}

# ===========================================================================
# Alarms: ECS
# ===========================================================================

resource "aws_cloudwatch_metric_alarm" "ecs_memory" {
  for_each = toset([var.web_service_name, var.worker_service_name])

  alarm_name          = "${var.name}-ecs-${each.key}-memory-high"
  alarm_description   = "ECS service ${each.key} memory above ${var.ecs_memory_threshold_percent}%. Runbook: docs/runbook.md#high-latency"
  namespace           = "AWS/ECS"
  metric_name         = "MemoryUtilization"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 3
  threshold           = var.ecs_memory_threshold_percent
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "missing"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions

  dimensions = {
    ClusterName = var.ecs_cluster_name
    ServiceName = each.key
  }
}

resource "aws_cloudwatch_metric_alarm" "worker_not_running" {
  alarm_name          = "${var.name}-ecs-worker-not-running"
  alarm_description   = "No Solid Queue worker task running: jobs, payouts and reconciliation are stalled. Runbook: docs/runbook.md#stuck-solid-queue-jobs"
  namespace           = "ECS/ContainerInsights"
  metric_name         = "RunningTaskCount"
  statistic           = "Minimum"
  period              = 60
  evaluation_periods  = 5
  datapoints_to_alarm = 5
  threshold           = 1
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "breaching"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions

  dimensions = {
    ClusterName = var.ecs_cluster_name
    ServiceName = var.worker_service_name
  }
}

# ===========================================================================
# Alarms: business / jobs
# ===========================================================================

resource "aws_cloudwatch_metric_alarm" "reconciliation_mismatch" {
  alarm_name          = "${var.name}-reconciliation-mismatch"
  alarm_description   = "NightlyReconciliationJob found a ledger or M-Pesa mismatch. Freeze payouts. Runbook: docs/runbook.md#m-pesa-payout-failures--reconciliation-mismatch"
  namespace           = local.namespace
  metric_name         = aws_cloudwatch_log_metric_filter.reconciliation_mismatch.metric_transformation[0].name
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = local.alarm_actions
}

resource "aws_cloudwatch_metric_alarm" "mpesa_payout_failures" {
  alarm_name          = "${var.name}-mpesa-payout-failures"
  alarm_description   = "${var.mpesa_failure_threshold}+ M-Pesa B2C payout failures in 15 minutes. Runbook: docs/runbook.md#m-pesa-payout-failures--reconciliation-mismatch"
  namespace           = local.namespace
  metric_name         = aws_cloudwatch_log_metric_filter.mpesa_payout_failed.metric_transformation[0].name
  statistic           = "Sum"
  period              = 900
  evaluation_periods  = 1
  threshold           = var.mpesa_failure_threshold
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = local.alarm_actions
}

resource "aws_cloudwatch_metric_alarm" "job_queue_latency" {
  alarm_name          = "${var.name}-job-queue-latency"
  alarm_description   = "Oldest ready Solid Queue job waiting more than ${var.job_queue_latency_threshold_seconds}s. Runbook: docs/runbook.md#stuck-solid-queue-jobs"
  namespace           = local.namespace
  metric_name         = aws_cloudwatch_log_metric_filter.job_queue_latency.metric_transformation[0].name
  statistic           = "Maximum"
  period              = 300
  evaluation_periods  = 2
  threshold           = var.job_queue_latency_threshold_seconds
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = local.alarm_actions
  ok_actions          = local.alarm_actions
}

# ===========================================================================
# Dashboard
# ===========================================================================

resource "aws_cloudwatch_dashboard" "this" {
  dashboard_name = "${var.name}-overview"

  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "text"
        x      = 0
        y      = 0
        width  = 24
        height = 1
        properties = {
          markdown = "## AfyaClaim ${var.environment} - API, jobs, database. Runbook: docs/runbook.md"
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 1
        width  = 8
        height = 6
        properties = {
          title  = "Requests and 5xx"
          region = data.aws_region.current.name
          stat   = "Sum"
          period = 60
          view   = "timeSeries"
          metrics = [
            ["AWS/ApplicationELB", "RequestCount", "LoadBalancer", var.alb_arn_suffix],
            [".", "HTTPCode_Target_5XX_Count", ".", ".", { yAxis = "right" }],
            [".", "HTTPCode_ELB_5XX_Count", ".", ".", { yAxis = "right" }],
            [".", "HTTPCode_Target_4XX_Count", ".", ".", { yAxis = "right" }],
          ]
        }
      },
      {
        type   = "metric"
        x      = 8
        y      = 1
        width  = 8
        height = 6
        properties = {
          title  = "Target response time (s)"
          region = data.aws_region.current.name
          period = 60
          view   = "timeSeries"
          metrics = [
            ["AWS/ApplicationELB", "TargetResponseTime", "LoadBalancer", var.alb_arn_suffix, "TargetGroup", var.target_group_arn_suffix, { stat = "p50", label = "p50" }],
            ["...", { stat = "p95", label = "p95" }],
            ["...", { stat = "p99", label = "p99" }],
          ]
          annotations = {
            horizontal = [{ label = "p95 SLO", value = var.p95_latency_threshold_seconds }]
          }
        }
      },
      {
        type   = "metric"
        x      = 16
        y      = 1
        width  = 8
        height = 6
        properties = {
          title  = "Healthy / unhealthy web targets"
          region = data.aws_region.current.name
          stat   = "Maximum"
          period = 60
          metrics = [
            ["AWS/ApplicationELB", "HealthyHostCount", "LoadBalancer", var.alb_arn_suffix, "TargetGroup", var.target_group_arn_suffix],
            [".", "UnHealthyHostCount", ".", ".", ".", "."],
          ]
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 7
        width  = 8
        height = 6
        properties = {
          title  = "ECS CPU / memory (%)"
          region = data.aws_region.current.name
          stat   = "Average"
          period = 60
          metrics = [
            ["AWS/ECS", "CPUUtilization", "ClusterName", var.ecs_cluster_name, "ServiceName", var.web_service_name, { label = "web cpu" }],
            [".", "MemoryUtilization", ".", ".", ".", ".", { label = "web mem" }],
            [".", "CPUUtilization", ".", ".", ".", var.worker_service_name, { label = "worker cpu" }],
            [".", "MemoryUtilization", ".", ".", ".", ".", { label = "worker mem" }],
          ]
        }
      },
      {
        type   = "metric"
        x      = 8
        y      = 7
        width  = 8
        height = 6
        properties = {
          title  = "RDS CPU (%) and connections"
          region = data.aws_region.current.name
          stat   = "Average"
          period = 60
          metrics = [
            ["AWS/RDS", "CPUUtilization", "DBInstanceIdentifier", var.rds_identifier],
            [".", "DatabaseConnections", ".", ".", { yAxis = "right" }],
          ]
        }
      },
      {
        type   = "metric"
        x      = 16
        y      = 7
        width  = 8
        height = 6
        properties = {
          title  = "RDS free storage (bytes) and read/write latency"
          region = data.aws_region.current.name
          stat   = "Average"
          period = 300
          metrics = [
            ["AWS/RDS", "FreeStorageSpace", "DBInstanceIdentifier", var.rds_identifier],
            [".", "ReadLatency", ".", ".", { yAxis = "right" }],
            [".", "WriteLatency", ".", ".", { yAxis = "right" }],
          ]
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 13
        width  = 8
        height = 6
        properties = {
          title  = "Job queue latency (s)"
          region = data.aws_region.current.name
          stat   = "Maximum"
          period = 300
          metrics = [
            [local.namespace, "JobQueueLatencySeconds"],
          ]
        }
      },
      {
        type   = "metric"
        x      = 8
        y      = 13
        width  = 8
        height = 6
        properties = {
          title  = "Payout failures and reconciliation mismatches"
          region = data.aws_region.current.name
          stat   = "Sum"
          period = 300
          metrics = [
            [local.namespace, "MpesaPayoutFailed"],
            [".", "ReconciliationMismatch"],
          ]
        }
      },
      {
        type   = "log"
        x      = 16
        y      = 13
        width  = 8
        height = 6
        properties = {
          title  = "Recent errors (web + worker)"
          region = data.aws_region.current.name
          view   = "table"
          query  = "SOURCE '${aws_cloudwatch_log_group.web.name}' | SOURCE '${aws_cloudwatch_log_group.worker.name}' | fields @timestamp, @logStream, @message | filter @message like /(?i)(error|exception|reconciliation_mismatch|mpesa_payout_failed)/ | sort @timestamp desc | limit 50"
        }
      },
    ]
  })
}
