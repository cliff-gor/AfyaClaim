variable "name" {
  description = "Name prefix, e.g. afyaclaim-staging."
  type        = string
}

variable "environment" {
  type = string
}

variable "log_retention_days" {
  type    = number
  default = 30
}

variable "alert_emails" {
  description = "On-call addresses subscribed to the alerts SNS topic. Each must confirm the subscription email."
  type        = list(string)
  default     = []
}

variable "alb_arn_suffix" {
  type = string
}

variable "target_group_arn_suffix" {
  type = string
}

variable "rds_identifier" {
  type = string
}

variable "ecs_cluster_name" {
  type = string
}

variable "web_service_name" {
  type    = string
  default = "web"
}

variable "worker_service_name" {
  type    = string
  default = "worker"
}

variable "alb_5xx_rate_threshold_percent" {
  type    = number
  default = 2
}

variable "alb_5xx_min_requests" {
  description = "Ignore the 5xx rate in minutes with fewer requests than this (avoids paging on 1-of-3 at 03:00)."
  type        = number
  default     = 20
}

variable "p95_latency_threshold_seconds" {
  type    = number
  default = 0.3
}

variable "rds_free_storage_threshold_gb" {
  type    = number
  default = 5
}

variable "ecs_memory_threshold_percent" {
  type    = number
  default = 85
}

variable "mpesa_failure_threshold" {
  type    = number
  default = 3
}

variable "job_queue_latency_threshold_seconds" {
  type    = number
  default = 300
}
