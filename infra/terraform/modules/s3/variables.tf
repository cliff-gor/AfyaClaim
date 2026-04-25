variable "name" {
  description = "Name prefix, e.g. afyaclaim-staging. The account ID is appended to bucket names for global uniqueness."
  type        = string
}

variable "receipts_cors_allowed_origins" {
  description = "Origins allowed to PUT/GET receipts via presigned URLs (the dashboard origin). Android uploads are not subject to CORS."
  type        = list(string)
}

variable "receipts_kms_key_arn" {
  description = "Optional customer-managed KMS key for receipts. Null uses SSE-S3 (AES256)."
  type        = string
  default     = null
}

variable "receipts_ia_after_days" {
  type    = number
  default = 90
}

variable "receipts_glacier_after_days" {
  description = "Move receipts to Glacier Instant Retrieval after this many days (audit retention, rarely read)."
  type        = number
  default     = 365
}

variable "force_destroy" {
  description = "Allow terraform destroy to empty the receipts bucket. Keep false outside throwaway environments."
  type        = bool
  default     = false
}

variable "dashboard_aliases" {
  description = "Custom domain names for the dashboard distribution (requires dashboard_certificate_arn)."
  type        = list(string)
  default     = []
}

variable "dashboard_certificate_arn" {
  description = "ACM certificate ARN in us-east-1 for dashboard_aliases. Null uses the *.cloudfront.net certificate."
  type        = string
  default     = null
}

variable "cloudfront_price_class" {
  description = "PriceClass_All is needed for edge locations in Africa (Johannesburg, Nairobi, Lagos...)."
  type        = string
  default     = "PriceClass_All"
}
