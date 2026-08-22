# AfyaClaim architecture

AfyaClaim lets community pharmacies record subsidised sales offline, submit subsidy claims, and get reimbursed
over M-Pesa after ops review. This page is the map; the details live in [SPEC.md](SPEC.md) (domain, API, rules),
[infra/terraform](../infra/terraform/README.md) (infrastructure) and the [runbook](runbook.md) (operations).

## 1. System context

```mermaid
flowchart LR
    pharmacist(["Pharmacist<br/>(community pharmacy)"])
    reviewer(["Reviewer / Admin<br/>(programme ops)"])
    manager(["Programme manager"])
    oncall(["On-call engineer"])

    subgraph afya["AfyaClaim"]
        pos["Android POS<br/>Kotlin, Compose, Room<br/>offline-first"]
        dash["Back-office dashboard<br/>Vue 3 + Vite (static)"]
        api["Rails 8.1 API<br/>claims state machine, fraud rules,<br/>ledger, Solid Queue jobs"]
        db[("PostgreSQL 16<br/>app data + job queue")]
        s3[("S3<br/>receipt photos")]
    end

    daraja["Safaricom Daraja<br/>M-Pesa B2C"]
    mpesa(["Pharmacy M-Pesa wallet"])
    cw["CloudWatch + SNS"]

    pharmacist --> pos
    reviewer --> dash
    manager --> dash
    pos -- "HTTPS JSON, JWT<br/>/api/v1/sync" --> api
    pos -- "presigned PUT" --> s3
    dash -- "HTTPS JSON, JWT<br/>/api/v1/*" --> api
    api --> db
    api -- "presigned URLs" --> s3
    api -- "B2C payment request" --> daraja
    daraja -- "result callback" --> api
    daraja --> mpesa
    api -- "logs, metrics" --> cw
    cw -- "alarms" --> oncall
```

Key properties:

- **Offline-first POS.** Every sale and claim gets a `client_uuid` on the device; the API treats it as an idempotency key, so sync retries are always safe.
- **Server owns status.** Devices create records; only the API changes a claim's status, via `Claims::StateMachine`, writing a hash-chained `ClaimEvent` for each transition.
- **One datastore.** PostgreSQL holds the app data *and* Solid Queue jobs: no Redis to run, and jobs can be enqueued in the same transaction as the data they act on.
- **No patient identifiers server-side.** Patients are a SHA-256 pseudonym computed on the phone.

## 2. AWS deployment

```mermaid
flowchart TB
    users(["Pharmacies (Android)<br/>Ops staff (browser)"])
    gha["GitHub Actions<br/>(OIDC, no static keys)"]
    daraja["Safaricom Daraja"]

    subgraph global["AWS global edge"]
        cf["CloudFront<br/>dashboard distribution (OAC)"]
    end

    subgraph region["AWS af-south-1 (Cape Town)"]
        ecr[("ECR<br/>afyaclaim-env-api")]
        ssm["SSM Parameter Store<br/>/afyaclaim/env/*"]
        dashbucket[("S3 dashboard bucket<br/>private")]
        receipts[("S3 receipts bucket<br/>private, versioned, SSE")]
        cwl["CloudWatch Logs, alarms,<br/>dashboard"]
        sns["SNS alerts -> on-call email"]

        subgraph vpc["VPC 10.x.0.0/16"]
            subgraph pub["Public subnets (AZ a, AZ b)"]
                alb["Application Load Balancer<br/>HTTPS :443 (ACM), :80 -> 301<br/>health check GET /up"]
                nat["NAT gateway(s)<br/>fixed egress IPs"]
            end
            subgraph priv["Private subnets (AZ a, AZ b)"]
                web["ECS Fargate service: web<br/>Puma :3000, 2-6 tasks<br/>CPU target tracking"]
                worker["ECS Fargate service: worker<br/>bin/jobs (Solid Queue)<br/>1 task"]
                rds[("RDS PostgreSQL 16<br/>Multi-AZ (prod), encrypted<br/>7-day PITR, Perf Insights")]
            end
            s3ep["S3 gateway endpoint"]
        end
    end

    users -- "dashboard" --> cf --> dashbucket
    users -- "API" --> alb --> web
    web --> rds
    worker --> rds
    web -- "presign" --> receipts
    users -. "presigned PUT/GET" .-> receipts
    worker -- "B2C" --> nat --> daraja
    daraja -- "callback" --> alb
    web & worker -. "receipts I/O" .-> s3ep -.-> receipts
    ssm -. "secrets at task start" .-> web & worker
    web & worker -- "JSON logs" --> cwl --> sns
    gha -- "push image" --> ecr
    gha -- "register task def,<br/>run db:prepare,<br/>update services" --> web & worker
    gha -- "sync dist/ + invalidate" --> dashbucket
    ecr -. "pull" .-> web & worker
```

| Concern | Choice | Why |
|---|---|---|
| Region | `af-south-1` | Lowest latency to Kenya among AWS regions; data stays on the continent |
| Compute | ECS Fargate, two services from one image | No servers to patch; `web` scales on CPU, `worker` stays at one supervisor |
| Jobs | Solid Queue in PostgreSQL | Transactional enqueue, one fewer system to operate |
| Database | RDS PostgreSQL 16, Multi-AZ in production | 99.5% availability target; automated failover; point-in-time restore |
| Secrets | SSM Parameter Store SecureString | Injected by ECS at task start; never in images or Terraform outputs |
| Static UI | S3 + CloudFront with Origin Access Control | Bucket stays private; global edge, including African PoPs |
| CI/CD | GitHub Actions with OIDC roles per environment | Short-lived credentials; production deploys gated by environment reviewers |
| Observability | Lograge JSON -> CloudWatch Logs, metric filters, alarms -> SNS | Alarms map 1:1 to runbook sections |

Infrastructure is Terraform: modules `network`, `ecr`, `rds`, `s3`, `ecs`, `monitoring`, `iam`, composed by
`environments/staging` and `environments/production`. Terraform owns the task definition *shape*; the deploy
workflow owns the *image*.

### Deploy pipeline

```mermaid
flowchart LR
    pr["Pull request"] --> ci["CI: api.yml / dashboard.yml /<br/>android.yml / terraform.yml"]
    ci -->|"review + merge"| main["main"]
    main --> build["Build API image<br/>tag = git SHA, push to ECR"]
    build --> td["Register new task<br/>definition revisions"]
    td --> mig["One-off ECS task:<br/>bin/rails db:prepare"]
    mig -->|"exit 0"| roll["Update web + worker<br/>circuit breaker w/ rollback"]
    roll --> stable["Wait for steady state"]
    stable --> spa["Build dashboard, sync to S3,<br/>invalidate CloudFront"]
    mig -->|"non-zero"| stop["Stop: services untouched"]
```

## 3. Claim lifecycle: offline sale to payout

```mermaid
sequenceDiagram
    autonumber
    actor P as Pharmacist
    participant App as Android POS<br/>(Room + WorkManager)
    participant S3 as S3 receipts
    participant API as Rails API
    participant DB as PostgreSQL
    participant Q as Solid Queue worker
    actor R as Reviewer / Admin
    participant D as Daraja B2C

    Note over P,App: Offline at the counter
    P->>App: Record sale + attach subsidy claim (under 30 s)
    App->>App: client_uuid (UUIDv4), patient pseudonym (SHA-256),<br/>rows saved with sync_state = pending

    Note over App,API: Connectivity returns, SyncWorker runs
    App->>API: POST /api/v1/sync (batch of up to 50 sales + claims)
    API->>DB: Upsert by client_uuid, apply inventory deltas
    API->>API: Synchronous fraud rules (velocity, amount_mismatch, no_matching_sale...)
    API->>DB: Claim status = submitted, ClaimEvent (hash-chained), ClaimFlags
    API-->>App: Per record: accepted / duplicate / conflict
    App->>API: GET /api/v1/sync?since=cursor
    API-->>App: Product, inventory, claim status deltas
    App->>API: Request presigned URL for receipt photo
    API-->>App: Presigned PUT URL
    App->>S3: PUT receipt image

    Note over R,API: Review (dashboard)
    R->>API: POST /claims/:id/start_review
    API->>DB: submitted -> under_review + ClaimEvent
    R->>API: POST /claims/:id/approve (override + note if high flag)
    API->>DB: under_review -> approved + ClaimEvent

    Note over R,D: Payout
    R->>API: POST /reimbursements (admin creates batch)
    API->>DB: One transaction: Reimbursement (pending), claims locked,<br/>ledger debit programme_float / credit payable:pharmacy,<br/>OriginatorConversationID stored (idempotency key)
    API->>Q: Enqueue MpesaPayoutJob (same transaction)
    Q->>D: B2C request with the stored OriginatorConversationID
    D-->>Q: Acknowledgement (or timeout = unknown, check status, never blind retry)
    D->>API: POST /api/v1/mpesa/b2c/result (shared-secret validated)
    alt Success
        API->>DB: Reimbursement paid + receipt, claims approved -> paid + ClaimEvents
    else Failure
        API->>DB: Reversing ledger pair, claims stay approved for the next batch
    end

    Note over App,API: Next sync
    App->>API: GET /api/v1/sync?since=cursor
    API-->>App: Claim status = paid (shown on the Claims screen)

    Note over Q,DB: 02:00 EAT NightlyReconciliationJob
    Q->>DB: Ledger balanced? Every paid reimbursement has one receipt?
    Q->>D: Transaction Status for processing > 2 h
    Q-->>Q: Mismatch -> log reconciliation_mismatch -> CloudWatch alarm -> on-call
```

## 4. Failure modes designed for

| Failure | Behaviour |
|---|---|
| Phone offline for days | Records stay `pending` locally; sync later is idempotent on `client_uuid` |
| Two devices sell the same stock | Inventory is synced as deltas; a delta that would go negative is a `conflict`, surfaced on the Sync screen |
| A web task dies | ALB health check on `/up` removes it; ECS replaces it; autoscaling keeps capacity |
| Bad deploy | Migrations run first in a one-off task (failure stops the deploy); circuit breaker rolls back failed rollouts |
| Primary DB AZ fails | RDS Multi-AZ failover (~1-2 min of errors) in production |
| Daraja slow or times out | Outcome treated as unknown; status queried before any retry (see [postmortem](postmortems/2026-07-14-payout-double-debit.md)) |
| Ledger or payout drift | Nightly reconciliation pages on-call via `reconciliation_mismatch` |
| Tampering with claim history | `ClaimEvent` hash chain; `GET /claims/:id` reports `audit_intact` |
