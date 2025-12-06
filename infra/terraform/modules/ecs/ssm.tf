# ---------------------------------------------------------------------------
# Secrets in SSM Parameter Store (SecureString)
#
# DATABASE_URL and JWT_SECRET are generated here. Every other secret is created
# with a placeholder and `ignore_changes = [value]`: set the real value once with
#   aws ssm put-parameter --overwrite --type SecureString \
#     --name /afyaclaim/<env>/<NAME> --value '...'
# and Terraform will never overwrite it. See docs/runbook.md "Rotating secrets".
# ---------------------------------------------------------------------------

locals {
  operator_managed_secrets = toset(concat([
    "RAILS_MASTER_KEY",
    "MPESA_CONSUMER_KEY",
    "MPESA_CONSUMER_SECRET",
    "MPESA_SHORTCODE",
    "MPESA_INITIATOR_NAME",
    "MPESA_SECURITY_CREDENTIAL",
    "MPESA_CALLBACK_SECRET",
  ], var.extra_secret_names))

  secret_names = toset(concat(["DATABASE_URL", "JWT_SECRET"], tolist(local.operator_managed_secrets)))
}

resource "random_password" "jwt_secret" {
  length  = 64
  special = false
}

resource "aws_ssm_parameter" "database_url" {
  name        = "${local.ssm_prefix}/DATABASE_URL"
  description = "Primary PostgreSQL URL (managed by Terraform)"
  type        = "SecureString"
  value       = var.database_url
}

resource "aws_ssm_parameter" "jwt_secret" {
  name        = "${local.ssm_prefix}/JWT_SECRET"
  description = "HMAC secret for API JWTs. Rotating it logs every user out."
  type        = "SecureString"
  value       = random_password.jwt_secret.result

  lifecycle {
    ignore_changes = [value]
  }
}

resource "aws_ssm_parameter" "operator_managed" {
  for_each = local.operator_managed_secrets

  name        = "${local.ssm_prefix}/${each.key}"
  description = "Set by an operator; Terraform only creates the placeholder"
  type        = "SecureString"
  value       = "CHANGE_ME"

  lifecycle {
    ignore_changes = [value]
  }
}

locals {
  secret_arns = merge(
    {
      DATABASE_URL = aws_ssm_parameter.database_url.arn
      JWT_SECRET   = aws_ssm_parameter.jwt_secret.arn
    },
    { for k, p in aws_ssm_parameter.operator_managed : k => p.arn },
  )

  container_secrets = [
    for name in sort(tolist(local.secret_names)) : {
      name      = name
      valueFrom = local.secret_arns[name]
    }
  ]
}
