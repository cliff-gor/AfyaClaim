output "identifier" {
  value = aws_db_instance.this.identifier
}

output "address" {
  value = aws_db_instance.this.address
}

output "port" {
  value = aws_db_instance.this.port
}

output "database_name" {
  value = aws_db_instance.this.db_name
}

output "security_group_id" {
  value = aws_security_group.db.id
}

output "database_url" {
  description = "postgres:// URL for the Rails app. Written to SSM by the ecs module; never print it."
  value       = "postgres://${var.master_username}:${random_password.master.result}@${aws_db_instance.this.address}:${aws_db_instance.this.port}/${var.database_name}?sslmode=require"
  sensitive   = true
}
