terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.31.0"
    }

    local = {
      source  = "hashicorp/local"
      version = "~> 2.4.0"
    }
  }

  required_version = ">= 1.5.0"
}

variable "prefix" {
  type = string
}


variable "environment" {
  type = string
}

variable "indexName" {
  type = string
  default = "jobs"
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
  # visibility_timeout_seconds = 900

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
    posix_user {
      uid = 1000
      gid = 1000
    }

    root_directory {
      path = "/data"
      creation_info {
        owner_gid = 1000
        owner_uid = 1000
        permissions = 770
      }
    }
}


resource aws_lambda_function query {
    function_name="${var.prefix}query"
    runtime="provided"
    
    handler="native.handler"
    filename="${path.module}/target/function.zip"
    source_code_hash = filebase64sha256("${path.module}/target/function.zip")
    role = aws_iam_role.role.arn

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
            index = var.indexName
        }
    }
}

resource aws_lambda_function index {
    function_name="${var.prefix}index"
    runtime="provided"
    handler="native.handler"
    filename="${path.module}/target/function.zip"
    source_code_hash = filebase64sha256("${path.module}/target/function.zip")
    role = aws_iam_role.role.arn
    memory_size = 1024
    timeout = 10
    # reserved_concurrent_executions = 1

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
            index = var.indexName
        }
    }
}

resource aws_lambda_function "enqueue-index" {
    function_name="${var.prefix}enqueue-index"
    runtime="provided"
    handler="native.handler"
    filename="${path.module}/target/function.zip"
    source_code_hash = filebase64sha256("${path.module}/target/function.zip")
    role = aws_iam_role.role.arn
    timeout = 60

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
            QUEUE_URL = aws_sqs_queue.queue.id

            index = var.indexName
        }
    }
}

resource "aws_iam_role_policy_attachment" "terraform_lambda_policy" {
  role       = aws_iam_role.role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

data "aws_iam_policy_document" "AWSLambdaTrustPolicy" {
  statement {
    actions = ["sts:AssumeRole"]
    effect  = "Allow"
    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}
resource aws_iam_role role {
    name="${var.prefix}ui-role-lucene"
    assume_role_policy = data.aws_iam_policy_document.AWSLambdaTrustPolicy.json
}


data "aws_iam_policy_document" "LambdaSQS" {
  statement {
    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.queue.arn]
    effect    = "Allow"
  }
  statement {
    actions = ["elasticfilesystem:ClientMount", "elasticfilesystem:ClientWrite"]
    resources = [aws_efs_file_system.fs.arn]
    effect    = "Allow"
  }
  statement {
    actions   = ["sqs:ReceiveMessage","sqs:DeleteMessage","sqs:GetQueueAttributes"]
    resources = [aws_sqs_queue.queue.arn]
    effect    = "Allow"
  }
}

resource "aws_iam_policy" "LambdaSQSPolicy" {
  policy = data.aws_iam_policy_document.LambdaSQS.json
  name   = "${var.prefix}ui-sqs-lucene"
  tags   = { terraform = true }
}

resource "aws_iam_role_policy_attachment" "terraform_lambda_sqs" {
  role       = aws_iam_role.role.name
  policy_arn = aws_iam_policy.LambdaSQSPolicy.arn
}

resource "aws_iam_role_policy_attachment" "basic_lambda" {
  role       = aws_iam_role.role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource aws_lambda_function "delete-index" {
    function_name="${var.prefix}delete-index"
    runtime="provided"
    handler="native.handler"
    filename="${path.module}/target/function.zip"
    source_code_hash = filebase64sha256("${path.module}/target/function.zip")
    role = aws_iam_role.role.arn

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
            QUARKUS_LAMBDA_HANDLER = "deleteIndex"
            QUARKUS_PROFILE = "production"
            index = var.indexName
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
  security_groups = [data.aws_security_group.selected.id]
}

# resource aws_vpc_endpoint queue-ep {
#   vpc_id = data.aws_vpc.selected.id
#   service_name = "com.amazonaws.eu-west-3.sqs"
#   vpc_endpoint_type = "Interface"
#   subnet_ids = [data.aws_subnet.selected.id]
#   private_dns_enabled = true
#   security_group_ids = [data.aws_security_group.selected.id]

#   tags = {
#     Name="lucene-queue"
#   }
# }


resource "aws_lambda_event_source_mapping" "lucene-index" {
  event_source_arn  = aws_sqs_queue.queue.arn
  function_name     = aws_lambda_function.index.arn
  scaling_config {
    maximum_concurrency = 2
  }
}


resource "aws_cloudwatch_log_group" "cloudwatch" {
  for_each          = toset(["enqueue-index", "index", "delete-index", "query"])
  name              = "/aws/lambda/${var.prefix}${each.key}"
  retention_in_days = var.environment != "prod" ? 1:365
}
