terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.9.0"
    }

    local = {
      source  = "hashicorp/local"
      version = "~> 2.4.0"
    }
  }

  backend "s3" {
    bucket = "medzie-terraform2"
    key    = "lucene"
    region = "eu-west-3"
  }

  required_version = ">= 1.5.0"
}

variable "prefix" {
  type = string
}


variable "environment" {
  type = string
}


resource "aws_sqs_queue_redrive_policy" "queue" {
  queue_url = aws_sqs_queue.queue.id
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlqueue.arn
    maxReceiveCount     = 5
  })
}

resource "aws_sqs_queue" "queue" {
  name                       = "${var.prefix}lucene-write-queue"
  visibility_timeout_seconds = 900
}

resource "aws_sqs_queue" "dlqueue" {
  name                      = "${var.prefix}lucene-write-dlqueue"
  message_retention_seconds = 1209600
}

resource "aws_efs_file_system" "fs" {
    performance_mode="generalPurpose"
    creation_token = "${var.prefix}fs-lucene"

    tags = {
        Name="${var.prefix}fs-lucene"
    }
}

data aws_iam_policy_document policy {
    statement {
        effect = "Allow"

        principals {
        type        = "AWS"
        identifiers = ["*"]
        }

        actions = [
        "elasticfilesystem:ClientMount",
        "elasticfilesystem:ClientWrite",
        ]

        resources = [aws_efs_file_system.fs.arn]

        condition {
        test     = "Bool"
        variable = "aws:SecureTransport"
        values   = ["true"]
        }
    }
}

resource aws_efs_file_system_policy policy {
    file_system_id=aws_efs_file_system.fs.id
    policy=data.aws_iam_policy_document.policy.json
}

resource aws_efs_access_point lucene {
    file_system_id=aws_efs_file_system.fs.id
}


resource aws_lambda_function query {
    function_name="${var.prefix}query"
    runtime="provided"
    handler="native.handler"
    filename="${path.module}/target/function.zip"
    role = data.aws_iam_role.role.arn

    vpc_config {
        security_group_ids = [data.aws_security_group.selected.id]
        subnet_ids = [data.aws_subnet.selected.id]
    }
    
    file_system_config {
      arn=aws_efs_access_point.lucene.arn
      local_mount_path = "/mnt/data"
    }

    environment{
        variables = {
            QUARKUS_LAMBDA_HANDLER = "query"
            QUARKUS_PROFILE = "production"
            index = "jobs"
        }
    }
}

resource aws_lambda_function index {
    function_name="${var.prefix}index"
    runtime="provided"
    handler="native.handler"
    filename="${path.module}/target/function.zip"
    role = data.aws_iam_role.role.arn

    vpc_config {
        security_group_ids = [data.aws_security_group.selected.id]
        subnet_ids = [data.aws_subnet.selected.id]
    }
    
    file_system_config {
      arn=aws_efs_access_point.lucene.arn
      local_mount_path = "/mnt/data"
    }

    environment{
        variables = {
            QUARKUS_LAMBDA_HANDLER = "index"
            QUARKUS_PROFILE = "production"
            index = "jobs"
        }
    }
}

resource aws_lambda_function "enqueue-index" {
    function_name="${var.prefix}enqueue-index"
    runtime="provided"
    handler="native.handler"
    filename="${path.module}/target/function.zip"
    role = data.aws_iam_role.role.arn

    vpc_config {
        security_group_ids = [data.aws_security_group.selected.id]
        subnet_ids = [data.aws_subnet.selected.id]
    }

    file_system_config {
      arn=aws_efs_access_point.lucene.arn
      local_mount_path = "/mnt/data"
    }

    environment{
        variables = {
            QUARKUS_LAMBDA_HANDLER = "enqueue-index"
            QUARKUS_PROFILE = "production"
            QUEUE_URL = aws_sqs_queue.queue.url
            index = "jobs"
        }
    }
}

data aws_iam_role role {
    name="${var.prefix}ui-role"
}

resource aws_lambda_function "delete-index" {
    function_name="${var.prefix}delete-index"
    runtime="provided"
    handler="native.handler"
    filename="${path.module}/target/function.zip"
    role = data.aws_iam_role.role.arn

    vpc_config {
        security_group_ids = [data.aws_security_group.selected.id]
        subnet_ids = [data.aws_subnet.selected.id]
    }

    file_system_config {
      arn=aws_efs_access_point.lucene.arn
      local_mount_path = "/mnt/data"
    }

    environment{
        variables = {
            QUARKUS_LAMBDA_HANDLER = "delete-index"
            QUARKUS_PROFILE = "production"
            index = "jobs"
        }
    }
}

data aws_subnet selected {
    filter {
        name="tag:Name"
        values=[var.environment]
    }
}

data "aws_vpc" "selected" {
  filter {
        name="tag:Name"
        values=[var.environment]
    }
}
data "aws_security_group" "selected" {
  filter {
        name="tag:Name"
        values=[var.environment]
    }
}

resource "aws_efs_mount_target" "alpha" {
  file_system_id = aws_efs_file_system.fs.id
  subnet_id      = data.aws_subnet.selected.id
}