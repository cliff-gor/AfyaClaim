variable "name" {
  description = "Name prefix for all resources, e.g. afyaclaim-staging."
  type        = string
}

variable "region" {
  description = "AWS region (used for VPC endpoint service names)."
  type        = string
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC. Subnets are carved as /24s."
  type        = string
  default     = "10.20.0.0/16"
}

variable "single_nat_gateway" {
  description = "Use one NAT gateway for both AZs (cheaper, not AZ-redundant)."
  type        = bool
  default     = true
}

variable "enable_flow_logs" {
  description = "Ship rejected VPC traffic to CloudWatch Logs."
  type        = bool
  default     = true
}

variable "log_retention_days" {
  description = "Retention for VPC flow logs."
  type        = number
  default     = 30
}
