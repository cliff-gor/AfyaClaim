output "web_log_group_name" {
  value = aws_cloudwatch_log_group.web.name
}

output "worker_log_group_name" {
  value = aws_cloudwatch_log_group.worker.name
}

output "web_log_group_arn" {
  value = aws_cloudwatch_log_group.web.arn
}

output "worker_log_group_arn" {
  value = aws_cloudwatch_log_group.worker.arn
}

output "alerts_topic_arn" {
  value = aws_sns_topic.alerts.arn
}

output "dashboard_name" {
  value = aws_cloudwatch_dashboard.this.dashboard_name
}

output "metric_namespace" {
  value = local.namespace
}
