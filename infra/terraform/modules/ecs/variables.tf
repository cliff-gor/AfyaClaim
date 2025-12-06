variable "name" {
  description = "Name prefix and cluster name, e.g. afyaclaim-staging."
  type        = string
}

variable "environment" {
  description = "staging | production. Also the SSM path segment: /afyaclaim/<environment>/..."
  type        = string
}

variable "vpc_id" {
  type = string
}

variable "public_subnet_ids" {
  type = list(string)
}

variable "private_subnet_ids" {
  type = list(string)
}

variable "certificate_arn" {
  description = "ACM certificate (same region) for the HTTPS listener. Null serves plain HTTP on :80 (bootstrap only)."
  type        = string
  default     = null
}

variable "alb_deletion_protection" {
  type    = bool
  default = true
}

variable "container_port" {
  type    = number
  default = 3000
}

variable "ecr_repository_url" {
  type = string
}

variable "image_tag" {
  description = "Initial image tag. After the first deploy, the deploy workflow manages the image."
  type        = string
  default     = "bootstrap"
}

variable "cpu_architecture" {
  description = "X86_64 or ARM64. Must match the image built in CI."
  type        = string
  default     = "X86_64"
}

variable "web_command" {
  type    = list(string)
  default = ["./bin/rails", "server", "-b", "0.0.0.0", "-p", "3000"]
}

variable "web_cpu" {
  type    = number
  default = 512
}

variable "web_memory" {
  type    = number
  default = 1024
}

variable "web_desired_count" {
  type    = number
  default = 2
}

variable "web_min_count" {
  type    = number
  default = 2
}

variable "web_max_count" {
  type    = number
  default = 6
}

variable "web_concurrency" {
  description = "Puma worker processes per task."
  type        = number
  default     = 2
}

variable "rails_max_threads" {
  type    = number
  default = 5
}

variable "worker_cpu" {
  type    = number
  default = 512
}

variable "worker_memory" {
  type    = number
  default = 1024
}

variable "worker_desired_count" {
  type    = number
  default = 1
}

variable "web_log_group_name" {
  type = string
}

variable "worker_log_group_name" {
  type = string
}

variable "receipts_bucket_name" {
  type = string
}

variable "receipts_bucket_arn" {
  type = string
}

variable "receipts_kms_key_arn" {
  type    = string
  default = null
}

variable "database_url" {
  description = "Written to SSM as DATABASE_URL."
  type        = string
  sensitive   = true
}

variable "mpesa_environment" {
  description = "Daraja environment: sandbox | production."
  type        = string
  default     = "sandbox"

  validation {
    condition     = contains(["sandbox", "production"], var.mpesa_environment)
    error_message = "mpesa_environment must be sandbox or production."
  }
}

variable "dashboard_origin" {
  description = "Allowed CORS origin for the API, e.g. https://dashboard.afyaclaim.org."
  type        = string
}

variable "extra_environment" {
  description = "Additional plain environment variables for web and worker."
  type        = map(string)
  default     = {}
}

variable "extra_secret_names" {
  description = "Additional SSM SecureString names (placeholders created, values set by an operator)."
  type        = list(string)
  default     = []
}
