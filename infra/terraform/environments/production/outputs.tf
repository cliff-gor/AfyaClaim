output "api_url" {
  description = "Point the API DNS record (CNAME/alias) at alb_dns_name."
  value       = var.api_certificate_arn == null ? "http://${module.ecs.alb_dns_name}" : "https://${module.ecs.alb_dns_name}"
}

output "alb_dns_name" {
  value = module.ecs.alb_dns_name
}

output "dashboard_url" {
  value = local.dashboard_origin
}

output "nat_public_ips" {
  value = module.network.nat_public_ips
}

output "rds_identifier" {
  value = module.rds.identifier
}

output "alerts_topic_arn" {
  value = module.monitoring.alerts_topic_arn
}

output "cloudwatch_dashboard" {
  value = module.monitoring.dashboard_name
}

output "ssm_prefix" {
  value = module.ecs.ssm_prefix
}

output "plan_role_arn" {
  value = module.iam.plan_role_arn
}

# Copy these into the GitHub environment's variables (Settings > Environments).
output "github_environment_variables" {
  value = {
    AWS_REGION                 = var.region
    AWS_DEPLOY_ROLE_ARN        = module.iam.deploy_role_arn
    ECR_REPOSITORY             = module.ecr.repository_name
    ECS_CLUSTER                = module.ecs.cluster_name
    ECS_WEB_SERVICE            = module.ecs.web_service_name
    ECS_WORKER_SERVICE         = module.ecs.worker_service_name
    ECS_WEB_TASK_FAMILY        = module.ecs.web_task_family
    ECS_WORKER_TASK_FAMILY     = module.ecs.worker_task_family
    ECS_PRIVATE_SUBNETS        = join(",", module.network.private_subnet_ids)
    ECS_SECURITY_GROUP         = module.ecs.app_security_group_id
    DASHBOARD_BUCKET           = module.s3.dashboard_bucket_name
    CLOUDFRONT_DISTRIBUTION_ID = module.s3.cloudfront_distribution_id
  }
}
