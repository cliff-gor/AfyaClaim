output "receipts_bucket_name" {
  value = aws_s3_bucket.receipts.id
}

output "receipts_bucket_arn" {
  value = aws_s3_bucket.receipts.arn
}

output "dashboard_bucket_name" {
  value = aws_s3_bucket.dashboard.id
}

output "dashboard_bucket_arn" {
  value = aws_s3_bucket.dashboard.arn
}

output "cloudfront_distribution_id" {
  value = aws_cloudfront_distribution.dashboard.id
}

output "cloudfront_distribution_arn" {
  value = aws_cloudfront_distribution.dashboard.arn
}

output "cloudfront_domain_name" {
  value = aws_cloudfront_distribution.dashboard.domain_name
}
