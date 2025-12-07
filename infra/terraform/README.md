# AfyaClaim infrastructure (Terraform)

AWS, region `af-south-1` (Cape Town) by default. Two root modules share one set of modules.

```
infra/terraform/
├── modules/
│   ├── network     VPC, 2 public + 2 private subnets, NAT, S3 gateway endpoint, flow logs
│   ├── ecr         API image repository (immutable tags, scan on push, lifecycle)
│   ├── rds         PostgreSQL 16, private, encrypted, Multi-AZ toggle, 7-day backups, Performance Insights
│   ├── s3          receipts bucket (private, versioned, SSE, lifecycle, CORS) + dashboard bucket/CloudFront (OAC)
│   ├── ecs         Fargate cluster, ALB (/up health check), web + worker services, IAM, SSM secrets
│   ├── monitoring  log groups (30d), SNS alerts, alarms, log metric filters, CloudWatch dashboard
│   └── iam         GitHub Actions OIDC: deploy role (per environment) + read-only plan role
└── environments/
    ├── staging
    └── production
```

## Bootstrap (once per AWS account)

1. Create the state bucket and lock table (not managed here, to avoid a chicken-and-egg problem):

   ```sh
   ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
   aws s3api create-bucket --bucket afyaclaim-terraform-state-$ACCOUNT \
     --region af-south-1 --create-bucket-configuration LocationConstraint=af-south-1
   aws s3api put-bucket-versioning --bucket afyaclaim-terraform-state-$ACCOUNT \
     --versioning-configuration Status=Enabled
   aws s3api put-public-access-block --bucket afyaclaim-terraform-state-$ACCOUNT \
     --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
   aws dynamodb create-table --table-name afyaclaim-terraform-locks --region af-south-1 \
     --attribute-definitions AttributeName=LockID,AttributeType=S \
     --key-schema AttributeName=LockID,KeyType=HASH --billing-mode PAY_PER_REQUEST
   ```

2. Enable the `af-south-1` opt-in region on the account if it is not already enabled.
3. Request ACM certificates: the API hostname in `af-south-1`, the dashboard hostname in `us-east-1` (CloudFront).

## Apply an environment

```sh
cd infra/terraform/environments/staging
cp backend.hcl.example backend.hcl            # edit bucket name
cp terraform.tfvars.example terraform.tfvars  # edit values
terraform init -backend-config=backend.hcl
terraform plan -out tfplan
terraform apply tfplan
```

After the first apply:

1. Set the real secret values (Terraform only creates `CHANGE_ME` placeholders):

   ```sh
   for k in RAILS_MASTER_KEY MPESA_CONSUMER_KEY MPESA_CONSUMER_SECRET MPESA_SHORTCODE \
            MPESA_INITIATOR_NAME MPESA_SECURITY_CREDENTIAL MPESA_CALLBACK_SECRET; do
     read -rsp "$k: " v; echo
     aws ssm put-parameter --overwrite --type SecureString --name "/afyaclaim/staging/$k" --value "$v"
   done
   ```

2. Copy `terraform output github_environment_variables` into the GitHub environment
   (`staging` / `production`) as **variables** (none of them are secret).
3. Confirm the SNS subscription email sent to each `alert_emails` address.
4. Run the **Deploy** workflow. Until it runs, the services point at the placeholder
   `:bootstrap` tag and will not become healthy; that is expected.
5. Point DNS: API hostname -> `alb_dns_name` (alias), dashboard hostname -> CloudFront domain.

## Who owns what

| Concern | Owner |
|---|---|
| Task CPU/memory, env vars, secrets list, logging | Terraform (`modules/ecs`) |
| Which image is running | `deploy.yml` (registers a new task definition revision per deploy) |
| Desired count of `web` | Application Auto Scaling (CPU target 60%) |
| Secret values | Operators via `aws ssm put-parameter` (`DATABASE_URL` is Terraform-managed) |

Services ignore `task_definition` drift, so `terraform apply` never rolls back a deploy.
A Terraform change to the task definition shape takes effect on the next deploy, which
always starts from the latest registered revision of each family.

## Notes

- `DATABASE_URL` points at the single primary database. Solid Queue tables must live in
  that database (or the API must derive its queue connection from it).
- Egress to Safaricom Daraja leaves via the NAT gateway EIPs (`terraform output nat_public_ips`).
- The GitHub OIDC provider is account-wide: set `create_github_oidc_provider = true` in exactly
  one root module per account.
