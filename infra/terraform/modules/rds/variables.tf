variable "name" {
  description = "Name prefix, e.g. afyaclaim-staging."
  type        = string
}

variable "vpc_id" {
  type = string
}

variable "private_subnet_ids" {
  description = "Private subnets for the DB subnet group (two AZs)."
  type        = list(string)
}

variable "allowed_security_group_ids" {
  description = "Security groups allowed to connect on 5432 (the ECS app tasks)."
  type        = list(string)
  default     = []
}

variable "engine_version" {
  type    = string
  default = "16"
}

variable "instance_class" {
  type    = string
  default = "db.t4g.medium"
}

variable "database_name" {
  type    = string
  default = "afyaclaim"
}

variable "master_username" {
  type    = string
  default = "afyaclaim"
}

variable "allocated_storage_gb" {
  type    = number
  default = 20
}

variable "max_allocated_storage_gb" {
  description = "Upper bound for storage autoscaling."
  type        = number
  default     = 100
}

variable "multi_az" {
  description = "Run a synchronous standby in a second AZ."
  type        = bool
  default     = false
}

variable "backup_retention_days" {
  type    = number
  default = 7
}

variable "enhanced_monitoring_interval" {
  description = "Enhanced monitoring granularity in seconds (0 disables)."
  type        = number
  default     = 60
}

variable "slow_query_ms" {
  description = "Log statements slower than this (ms)."
  type        = number
  default     = 500
}

variable "kms_key_arn" {
  description = "Customer-managed KMS key for storage encryption. Null uses the AWS-managed aws/rds key."
  type        = string
  default     = null
}

variable "deletion_protection" {
  description = "Protect the instance from deletion and take a final snapshot on destroy."
  type        = bool
  default     = true
}

variable "apply_immediately" {
  type    = bool
  default = false
}
