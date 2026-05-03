variable "region" {
  description = "AWS region. af-south-1 (Cape Town) is the closest region to Kenyan users."
  type        = string
  default     = "af-south-1"
}

variable "environment" {
  type    = string
  default = "production"
}

variable "github_repository" {
  description = "owner/repo, used for OIDC trust and tagging."
  type        = string
}

variable "create_github_oidc_provider" {
  description = "Create the account-wide GitHub OIDC provider here (true in exactly one root per AWS account)."
  type        = bool
  default     = false
}

variable "create_plan_role" {
  type    = bool
  default = true
}

variable "state_bucket_name" {
  description = "Must match bucket in backend.hcl (used to grant the plan role read access)."
  type        = string
}

variable "lock_table_name" {
  description = "Must match dynamodb_table in backend.hcl."
  type        = string
  default     = "afyaclaim-terraform-locks"
}

# --- Network ---------------------------------------------------------------

variable "vpc_cidr" {
  type    = string
  default = "10.30.0.0/16"
}

variable "single_nat_gateway" {
  type    = bool
  default = false
}

# --- Domains & TLS ---------------------------------------------------------

variable "api_certificate_arn" {
  description = "ACM certificate in var.region for the API hostname. Null = HTTP only (bootstrap)."
  type        = string
  default     = null
}

variable "dashboard_domain" {
  description = "Custom dashboard hostname. Empty uses the CloudFront domain."
  type        = string
  default     = ""
}

variable "dashboard_certificate_arn" {
  description = "ACM certificate in us-east-1 for dashboard_domain."
  type        = string
  default     = null
}

variable "extra_cors_origins" {
  description = "Extra origins allowed to use presigned receipt URLs (e.g. local dev)."
  type        = list(string)
  default     = []
}

# --- Compute ---------------------------------------------------------------

variable "bootstrap_image_tag" {
  description = "Image tag for the very first apply. The deploy workflow owns the image afterwards."
  type        = string
  default     = "bootstrap"
}

variable "cpu_architecture" {
  type    = string
  default = "X86_64"
}

variable "web_cpu" {
  type    = number
  default = 512
}

variable "web_memory" {
  type    = number
  default = 1024
}

variable "web_min_count" {
  type    = number
  default = 2
}

variable "web_max_count" {
  type    = number
  default = 6
}

variable "worker_cpu" {
  type    = number
  default = 512
}

variable "worker_memory" {
  type    = number
  default = 1024
}

variable "alb_deletion_protection" {
  type    = bool
  default = true
}

variable "mpesa_environment" {
  type    = string
  default = "production"
}

variable "extra_environment" {
  type    = map(string)
  default = {}
}

# --- Database --------------------------------------------------------------

variable "db_instance_class" {
  type    = string
  default = "db.m7g.large"
}

variable "db_allocated_storage_gb" {
  type    = number
  default = 50
}

variable "db_max_allocated_storage_gb" {
  type    = number
  default = 200
}

variable "db_multi_az" {
  type    = bool
  default = true
}

variable "db_deletion_protection" {
  type    = bool
  default = true
}

# --- Monitoring ------------------------------------------------------------

variable "alert_emails" {
  type    = list(string)
  default = []
}

variable "log_retention_days" {
  type    = number
  default = 30
}
