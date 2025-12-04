variable "repository_name" {
  description = "ECR repository name, e.g. afyaclaim-staging-api."
  type        = string
}

variable "keep_last_images" {
  description = "How many images to retain. Older images are expired, so this bounds how far back you can roll back."
  type        = number
  default     = 30
}

variable "force_delete" {
  description = "Allow terraform destroy to delete the repository even if it contains images."
  type        = bool
  default     = false
}
