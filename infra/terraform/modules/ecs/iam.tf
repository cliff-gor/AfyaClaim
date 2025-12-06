data "aws_iam_policy_document" "ecs_tasks_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
    condition {
      test     = "ArnLike"
      variable = "aws:SourceArn"
      values   = ["arn:aws:ecs:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:*"]
    }
  }
}

# ---------------------------------------------------------------------------
# Execution role: used by the ECS agent to pull the image, write logs and
# inject secrets from SSM into the container environment.
# ---------------------------------------------------------------------------

resource "aws_iam_role" "execution" {
  name               = "${var.name}-ecs-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

resource "aws_iam_role_policy_attachment" "execution_managed" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "execution_secrets" {
  statement {
    sid       = "ReadAppSecrets"
    actions   = ["ssm:GetParameters"]
    resources = values(local.secret_arns)
  }
}

resource "aws_iam_role_policy" "execution_secrets" {
  name   = "read-ssm-secrets"
  role   = aws_iam_role.execution.id
  policy = data.aws_iam_policy_document.execution_secrets.json
}

# ---------------------------------------------------------------------------
# Task role: what the Rails process itself may call.
# ---------------------------------------------------------------------------

resource "aws_iam_role" "task" {
  name               = "${var.name}-ecs-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

data "aws_iam_policy_document" "task" {
  statement {
    sid = "ReceiptsObjects"
    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject",
      "s3:GetObjectVersion",
    ]
    resources = ["${var.receipts_bucket_arn}/*"]
  }

  statement {
    sid       = "ReceiptsList"
    actions   = ["s3:ListBucket"]
    resources = [var.receipts_bucket_arn]
  }

  statement {
    sid = "ReadOwnParameters"
    actions = [
      "ssm:GetParameter",
      "ssm:GetParameters",
      "ssm:GetParametersByPath",
    ]
    resources = [
      "arn:aws:ssm:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:parameter${local.ssm_prefix}",
      "arn:aws:ssm:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:parameter${local.ssm_prefix}/*",
    ]
  }

  # ECS Exec (rails console on a running task)
  statement {
    sid = "EcsExec"
    actions = [
      "ssmmessages:CreateControlChannel",
      "ssmmessages:CreateDataChannel",
      "ssmmessages:OpenControlChannel",
      "ssmmessages:OpenDataChannel",
    ]
    resources = ["*"]
  }

  dynamic "statement" {
    for_each = var.receipts_kms_key_arn == null ? [] : [1]
    content {
      sid       = "ReceiptsKms"
      actions   = ["kms:Decrypt", "kms:GenerateDataKey"]
      resources = [var.receipts_kms_key_arn]
    }
  }
}

resource "aws_iam_role_policy" "task" {
  name   = "afyaclaim-app"
  role   = aws_iam_role.task.id
  policy = data.aws_iam_policy_document.task.json
}
