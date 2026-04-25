terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

data "aws_caller_identity" "current" {}

locals {
  receipts_bucket  = "${var.name}-receipts-${data.aws_caller_identity.current.account_id}"
  dashboard_bucket = "${var.name}-dashboard-${data.aws_caller_identity.current.account_id}"

  # AWS managed CloudFront policies (stable, documented IDs)
  cache_policy_caching_optimized = "658327ea-f89d-4fab-a63d-7e88639e58f6"
  response_headers_security      = "67f7725c-6f97-4210-82d7-5512b31e9d03"
}

# ===========================================================================
# Receipts bucket: private, versioned, SSE, presigned PUT/GET only
# ===========================================================================

resource "aws_s3_bucket" "receipts" {
  bucket        = local.receipts_bucket
  force_destroy = var.force_destroy
}

resource "aws_s3_bucket_ownership_controls" "receipts" {
  bucket = aws_s3_bucket.receipts.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_public_access_block" "receipts" {
  bucket                  = aws_s3_bucket.receipts.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "receipts" {
  bucket = aws_s3_bucket.receipts.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "receipts" {
  bucket = aws_s3_bucket.receipts.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = var.receipts_kms_key_arn == null ? "AES256" : "aws:kms"
      kms_master_key_id = var.receipts_kms_key_arn
    }
    bucket_key_enabled = var.receipts_kms_key_arn != null
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "receipts" {
  bucket = aws_s3_bucket.receipts.id

  rule {
    id     = "tiering"
    status = "Enabled"
    filter {}

    transition {
      days          = var.receipts_ia_after_days
      storage_class = "STANDARD_IA"
    }

    transition {
      days          = var.receipts_glacier_after_days
      storage_class = "GLACIER_IR"
    }

    noncurrent_version_expiration {
      noncurrent_days = 90
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }

  depends_on = [aws_s3_bucket_versioning.receipts]
}

resource "aws_s3_bucket_cors_configuration" "receipts" {
  bucket = aws_s3_bucket.receipts.id

  cors_rule {
    allowed_methods = ["PUT", "GET", "HEAD"]
    allowed_origins = var.receipts_cors_allowed_origins
    allowed_headers = ["Content-Type", "Content-MD5", "x-amz-*"]
    expose_headers  = ["ETag"]
    max_age_seconds = 3000
  }
}

data "aws_iam_policy_document" "receipts" {
  statement {
    sid     = "DenyInsecureTransport"
    effect  = "Deny"
    actions = ["s3:*"]
    resources = [
      aws_s3_bucket.receipts.arn,
      "${aws_s3_bucket.receipts.arn}/*",
    ]
    principals {
      type        = "*"
      identifiers = ["*"]
    }
    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_s3_bucket_policy" "receipts" {
  bucket = aws_s3_bucket.receipts.id
  policy = data.aws_iam_policy_document.receipts.json

  depends_on = [aws_s3_bucket_public_access_block.receipts]
}

# ===========================================================================
# Dashboard bucket: private origin for CloudFront (Origin Access Control)
# ===========================================================================

resource "aws_s3_bucket" "dashboard" {
  bucket        = local.dashboard_bucket
  force_destroy = true # contents are a reproducible build artefact
}

resource "aws_s3_bucket_ownership_controls" "dashboard" {
  bucket = aws_s3_bucket.dashboard.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_public_access_block" "dashboard" {
  bucket                  = aws_s3_bucket.dashboard.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "dashboard" {
  bucket = aws_s3_bucket.dashboard.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_versioning" "dashboard" {
  bucket = aws_s3_bucket.dashboard.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "dashboard" {
  bucket = aws_s3_bucket.dashboard.id

  rule {
    id     = "expire-old-builds"
    status = "Enabled"
    filter {}

    noncurrent_version_expiration {
      noncurrent_days = 30
    }
  }

  depends_on = [aws_s3_bucket_versioning.dashboard]
}

resource "aws_cloudfront_origin_access_control" "dashboard" {
  name                              = "${var.name}-dashboard"
  description                       = "AfyaClaim dashboard bucket"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_distribution" "dashboard" {
  enabled             = true
  is_ipv6_enabled     = true
  comment             = "${var.name} dashboard"
  default_root_object = "index.html"
  price_class         = var.cloudfront_price_class
  aliases             = var.dashboard_aliases

  origin {
    origin_id                = "dashboard-s3"
    domain_name              = aws_s3_bucket.dashboard.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.dashboard.id
  }

  default_cache_behavior {
    target_origin_id           = "dashboard-s3"
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD", "OPTIONS"]
    cached_methods             = ["GET", "HEAD"]
    compress                   = true
    cache_policy_id            = local.cache_policy_caching_optimized
    response_headers_policy_id = local.response_headers_security
  }

  # Vue Router uses HTML5 history mode: unknown paths must serve index.html.
  custom_error_response {
    error_code            = 403
    response_code         = 200
    response_page_path    = "/index.html"
    error_caching_min_ttl = 0
  }

  custom_error_response {
    error_code            = 404
    response_code         = 200
    response_page_path    = "/index.html"
    error_caching_min_ttl = 0
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = var.dashboard_certificate_arn == null
    acm_certificate_arn            = var.dashboard_certificate_arn
    ssl_support_method             = var.dashboard_certificate_arn == null ? null : "sni-only"
    minimum_protocol_version       = var.dashboard_certificate_arn == null ? "TLSv1" : "TLSv1.2_2021"
  }
}

data "aws_iam_policy_document" "dashboard" {
  statement {
    sid       = "AllowCloudFrontOAC"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.dashboard.arn}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.dashboard.arn]
    }
  }

  statement {
    sid     = "DenyInsecureTransport"
    effect  = "Deny"
    actions = ["s3:*"]
    resources = [
      aws_s3_bucket.dashboard.arn,
      "${aws_s3_bucket.dashboard.arn}/*",
    ]
    principals {
      type        = "*"
      identifiers = ["*"]
    }
    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_s3_bucket_policy" "dashboard" {
  bucket = aws_s3_bucket.dashboard.id
  policy = data.aws_iam_policy_document.dashboard.json

  depends_on = [aws_s3_bucket_public_access_block.dashboard]
}
