locals {
  name = "afyaclaim-${var.environment}"

  dashboard_origin = var.dashboard_domain != "" ? "https://${var.dashboard_domain}" : "https://${module.s3.cloudfront_domain_name}"
}

module "network" {
  source = "../../modules/network"

  name               = local.name
  region             = var.region
  vpc_cidr           = var.vpc_cidr
  single_nat_gateway = var.single_nat_gateway
  log_retention_days = var.log_retention_days
}

module "ecr" {
  source = "../../modules/ecr"

  repository_name = "${local.name}-api"
}

module "s3" {
  source = "../../modules/s3"

  name                          = local.name
  receipts_cors_allowed_origins = concat([local.dashboard_origin], var.extra_cors_origins)
  dashboard_aliases             = var.dashboard_domain != "" ? [var.dashboard_domain] : []
  dashboard_certificate_arn     = var.dashboard_certificate_arn
}

module "rds" {
  source = "../../modules/rds"

  name                       = local.name
  vpc_id                     = module.network.vpc_id
  private_subnet_ids         = module.network.private_subnet_ids
  allowed_security_group_ids = [module.ecs.app_security_group_id]
  instance_class             = var.db_instance_class
  allocated_storage_gb       = var.db_allocated_storage_gb
  max_allocated_storage_gb   = var.db_max_allocated_storage_gb
  multi_az                   = var.db_multi_az
  backup_retention_days      = 7
  deletion_protection        = var.db_deletion_protection
}

module "monitoring" {
  source = "../../modules/monitoring"

  name                    = local.name
  environment             = var.environment
  log_retention_days      = var.log_retention_days
  alert_emails            = var.alert_emails
  alb_arn_suffix          = module.ecs.alb_arn_suffix
  target_group_arn_suffix = module.ecs.target_group_arn_suffix
  rds_identifier          = module.rds.identifier
  ecs_cluster_name        = module.ecs.cluster_name
  web_service_name        = module.ecs.web_service_name
  worker_service_name     = module.ecs.worker_service_name
}

module "ecs" {
  source = "../../modules/ecs"

  name                    = local.name
  environment             = var.environment
  vpc_id                  = module.network.vpc_id
  public_subnet_ids       = module.network.public_subnet_ids
  private_subnet_ids      = module.network.private_subnet_ids
  certificate_arn         = var.api_certificate_arn
  alb_deletion_protection = var.alb_deletion_protection
  ecr_repository_url      = module.ecr.repository_url
  image_tag               = var.bootstrap_image_tag
  cpu_architecture        = var.cpu_architecture

  web_cpu              = var.web_cpu
  web_memory           = var.web_memory
  web_desired_count    = var.web_min_count
  web_min_count        = var.web_min_count
  web_max_count        = var.web_max_count
  worker_cpu           = var.worker_cpu
  worker_memory        = var.worker_memory
  worker_desired_count = 1

  web_log_group_name    = module.monitoring.web_log_group_name
  worker_log_group_name = module.monitoring.worker_log_group_name

  receipts_bucket_name = module.s3.receipts_bucket_name
  receipts_bucket_arn  = module.s3.receipts_bucket_arn
  database_url         = module.rds.database_url
  mpesa_environment    = var.mpesa_environment
  dashboard_origin     = local.dashboard_origin
  extra_environment    = var.extra_environment
}

module "iam" {
  source = "../../modules/iam"

  name                        = local.name
  create_oidc_provider        = var.create_github_oidc_provider
  github_repository           = var.github_repository
  github_environment          = var.environment
  ecr_repository_arn          = module.ecr.repository_arn
  ecs_cluster_name            = module.ecs.cluster_name
  web_task_family             = module.ecs.web_task_family
  ecs_pass_role_arns          = [module.ecs.execution_role_arn, module.ecs.task_role_arn]
  log_group_arns              = [module.monitoring.web_log_group_arn, module.monitoring.worker_log_group_arn]
  dashboard_bucket_arn        = module.s3.dashboard_bucket_arn
  cloudfront_distribution_arn = module.s3.cloudfront_distribution_arn
  create_plan_role            = var.create_plan_role
  state_bucket_name           = var.state_bucket_name
  lock_table_name             = var.lock_table_name
}
