output "deploy_role_arn" {
  value = aws_iam_role.deploy.arn
}

output "plan_role_arn" {
  value = var.create_plan_role ? aws_iam_role.plan[0].arn : null
}

output "oidc_provider_arn" {
  value = local.oidc_provider_arn
}
