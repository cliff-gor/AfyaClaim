variable "name" {
  description = "Name prefix, e.g. afyaclaim-staging."
  type        = string
}

variable "create_oidc_provider" {
  description = "Create the account-wide GitHub OIDC provider. Set true in exactly one root module per AWS account."
  type        = bool
  default     = false
}

variable "github_repository" {
  description = "owner/repo allowed to assume the roles, e.g. afyaclaim/afyaclaim."
  type        = string
}

variable "github_environment" {
  description = "GitHub environment name that deploy jobs must declare (staging | production)."
  type        = string
}

variable "ecr_repository_arn" {
  type = string
}

variable "ecs_cluster_name" {
  type = string
}

variable "web_task_family" {
  description = "Task definition family used for one-off migration tasks."
  type        = string
}

variable "ecs_pass_role_arns" {
  description = "Execution and task role ARNs the deploy role may pass to ECS."
  type        = list(string)
}

variable "log_group_arns" {
  description = "Log groups the deploy role may read (migration output)."
  type        = list(string)
}

variable "dashboard_bucket_arn" {
  type = string
}

variable "cloudfront_distribution_arn" {
  type = string
}

variable "create_plan_role" {
  description = "Create the read-only role used by terraform plan on pull requests."
  type        = bool
  default     = true
}

variable "state_bucket_name" {
  description = "Terraform state bucket (for the plan role)."
  type        = string
}

variable "lock_table_name" {
  description = "Terraform DynamoDB lock table (for the plan role)."
  type        = string
}
