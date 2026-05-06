# AfyaClaim on-call runbook

Practical steps for whoever holds the pager. Keep it short; fix it when it is wrong.

- **Region:** `af-south-1` (Cape Town). **Times:** alarms are in UTC; pharmacies and jobs work in EAT (UTC+3).
- **Environments:** `staging`, `production`. Commands below use shell variables; set them first:

```sh
export AWS_REGION=af-south-1 ENV=production
export CLUSTER=afyaclaim-$ENV DB=afyaclaim-$ENV-pg
```

Related: [architecture](architecture.md) · [spec](SPEC.md) · [Terraform](../infra/terraform/README.md) · [postmortems](postmortems/)

---

## 1. Service map

| Component | Where | Notes |
|---|---|---|
| API `web` | ECS service `web` in cluster `afyaclaim-<env>`, behind the ALB | Puma on :3000, health check `GET /up`, autoscales 2-6 tasks on CPU (prod) |
| Jobs `worker` | ECS service `worker` (1 task) | `bin/jobs` = Solid Queue supervisor. Runs `FraudScanJob` 01:00, `NightlyReconciliationJob` 02:00, `DailyReportJob` 06:00, `StockoutAlertJob` hourly, `MpesaPayoutJob` on demand |
| Database | RDS PostgreSQL 16 `afyaclaim-<env>-pg` | Private subnets, Multi-AZ in prod, 7-day PITR. Holds app data **and** Solid Queue tables |
| Receipts | S3 `afyaclaim-<env>-receipts-<account>` | Private, versioned; clients use presigned URLs |
| Dashboard | S3 + CloudFront | Static Vue build; no server to page about |
| Android POS | Pharmacists' phones | Offline-first; talks to `/api/v1/sync` |
| Secrets | SSM Parameter Store `/afyaclaim/<env>/*` | Injected into tasks at start |
| M-Pesa | Safaricom Daraja B2C | Outbound via NAT EIPs; result callback to `POST /api/v1/mpesa/b2c/result` |
| Logs | CloudWatch `/afyaclaim/<env>/web`, `/afyaclaim/<env>/worker` | JSON (Lograge), 30-day retention |
| Alerts | SNS `afyaclaim-<env>-alerts` -> on-call email | |

## 2. Dashboards and alarms

CloudWatch dashboard: **`afyaclaim-<env>-overview`** (requests/5xx, latency p50/p95/p99, healthy targets, ECS CPU/memory, RDS CPU/connections/storage, job latency, payout failures, recent errors).

| Alarm (`afyaclaim-<env>-...`) | Means | Severity | Go to |
|---|---|---|---|
| `api-5xx-rate` | > 2% of requests returned 5xx for 3 of 5 minutes | Page | [5xx spike](#5xx-spike) |
| `api-unhealthy-hosts` | A web task is failing `/up` | Page | [5xx spike](#5xx-spike) |
| `api-p95-latency` | Target p95 > 300 ms (our SLO) for 4 of 5 minutes | Page in business hours | [High latency](#high-latency) |
| `rds-cpu-high` | RDS CPU > 80% for 15 min | Page | [High latency](#high-latency) |
| `rds-free-storage-low` | < 5 GB free | Page | [RDS storage](#rds-storage) |
| `ecs-web-memory-high` / `ecs-worker-memory-high` | Task memory > 85% for 15 min | Ticket, page if repeating | [High latency](#high-latency) |
| `ecs-worker-not-running` | No Solid Queue worker for 5 min: no payouts, no nightly jobs | Page | [Stuck jobs](#stuck-solid-queue-jobs) |
| `job-queue-latency` | Oldest ready job waited > 5 min | Page | [Stuck jobs](#stuck-solid-queue-jobs) |
| `mpesa-payout-failures` | 3+ B2C failures in 15 min | Page | [M-Pesa](#m-pesa-payout-failures--reconciliation-mismatch) |
| `reconciliation-mismatch` | `NightlyReconciliationJob` found an imbalance or missing receipt | **Page, freeze payouts** | [M-Pesa](#m-pesa-payout-failures--reconciliation-mismatch) |

Useful Logs Insights query (select both log groups):

```
fields @timestamp, @logStream, status, method, path, duration, error
| filter status >= 500 or ispresent(error)
| sort @timestamp desc
| limit 100
```

## 3. Responses

Every incident: acknowledge in the on-call channel, note the start time (EAT), then work the steps. If customer impact lasts > 30 min or money may be wrong, open an incident and escalate (section 9).

### 5xx spike

1. **Did we just deploy?** Check the latest run of the *Deploy* workflow and `aws ecs describe-services --cluster $CLUSTER --services web --query 'services[0].deployments'`. If the spike started with a deploy: [roll back](#rollback) first, investigate after.
2. **Which endpoints?** Run the Logs Insights query above; group with `stats count() by path, status`.
3. **Tasks healthy?** `aws ecs describe-services --cluster $CLUSTER --services web --query 'services[0].events[:10]'`. Repeated `(task ...) failed container health checks` or `OutOfMemory` -> check `ecs-web-memory-high`, raise memory in Terraform or roll back.
4. **Database?** `PG::ConnectionBad` / `ActiveRecord::ConnectionTimeoutError` in logs -> check RDS CPU and `DatabaseConnections`. A Multi-AZ failover causes ~60-120 s of errors and recovers by itself (`aws rds describe-events --source-identifier $DB --duration 60`).
5. **Secrets?** Tasks crash-looping right after a secret change -> see [Rotating secrets](#rotating-secrets); `CHANGE_ME` in a value means it was never set.
6. 5xx only from the ALB (`HTTPCode_ELB_5XX`) with zero healthy targets -> scale up or force a new deployment: `aws ecs update-service --cluster $CLUSTER --service web --force-new-deployment`.

### High latency

1. Dashboard: is p95 up for everything or just one path? `stats pct(duration, 95) by path` in Logs Insights.
2. **RDS CPU high?** Open Performance Insights for `$DB` -> *Top SQL*. Usual suspects: `/claims` list without an index on the active filter, analytics endpoints, `FraudScanJob` running into business hours.
3. Kill a runaway query if it is clearly the cause (from a [Rails console](#rails-console-via-ecs-exec)):
   `ActiveRecord::Base.connection.execute("SELECT pid, now()-query_start AS age, left(query,120) FROM pg_stat_activity WHERE state <> 'idle' ORDER BY age DESC LIMIT 10").to_a`
   then `SELECT pg_cancel_backend(<pid>)`.
4. **Web CPU pinned?** Autoscaling should add tasks. If at `web_max_count`, raise it temporarily: `aws application-autoscaling register-scalable-target --service-namespace ecs --resource-id service/$CLUSTER/web --scalable-dimension ecs:service:DesiredCount --min-capacity 2 --max-capacity 10`, then make the change in Terraform.
5. **Memory creeping up** on long-lived tasks -> `--force-new-deployment` recycles them; open a ticket.

### RDS storage

Storage autoscaling grows the volume up to `max_allocated_storage` (prod 200 GB) but cannot react faster than once every 6 hours.

1. `aws rds describe-db-instances --db-instance-identifier $DB --query 'DBInstances[0].[AllocatedStorage,MaxAllocatedStorage]'`.
2. Find what grew (Rails console):
   `ActiveRecord::Base.connection.execute("SELECT relname, pg_size_pretty(pg_total_relation_size(relid)) FROM pg_catalog.pg_statio_user_tables ORDER BY pg_total_relation_size(relid) DESC LIMIT 10").to_a`
3. Common causes: `solid_queue_*` tables not being cleared (finished jobs are kept; check `config.solid_queue.clear_finished_jobs_after`), runaway `claim_events`, large PostgreSQL logs.
4. If at the autoscaling ceiling: `aws rds modify-db-instance --db-instance-identifier $DB --allocated-storage <GB> --apply-immediately`, then update `db_allocated_storage_gb` / `db_max_allocated_storage_gb` in Terraform. Never delete `claim_events` or `ledger_entries` rows; they are the audit trail.

### Stuck Solid Queue jobs

Symptoms: `ecs-worker-not-running` or `job-queue-latency` alarm, payouts stuck in `processing`, daily report missing.

1. Is the worker running? `aws ecs describe-services --cluster $CLUSTER --services worker --query 'services[0].[runningCount,events[:5]]'`. If 0, look at the stopped task reason: `aws ecs list-tasks --cluster $CLUSTER --service-name worker --desired-status STOPPED` -> `describe-tasks`.
2. Recycle it: `aws ecs update-service --cluster $CLUSTER --service worker --force-new-deployment`.
3. Inspect the queue from a [Rails console](#rails-console-via-ecs-exec):
   ```ruby
   SolidQueue::Process.all.pluck(:kind, :hostname, :last_heartbeat_at)           # stale heartbeats = dead supervisor
   SolidQueue::ReadyExecution.group(:queue_name).count                            # backlog per queue
   SolidQueue::ReadyExecution.order(:created_at).first&.created_at               # oldest waiting
   SolidQueue::ClaimedExecution.joins(:job).pluck("solid_queue_jobs.class_name", :created_at)  # in-flight
   SolidQueue::FailedExecution.joins(:job).group("solid_queue_jobs.class_name").count
   ```
4. Retry failed jobs **except payouts** after reading the error: `SolidQueue::FailedExecution.joins(:job).where.not(solid_queue_jobs: { class_name: "MpesaPayoutJob" }).find_each(&:retry)`.
5. **Never bulk-retry `MpesaPayoutJob`.** Go to the M-Pesa section; each payout must be checked against Daraja first.
6. Missed a scheduled job (worker was down at 02:00)? Enqueue it by hand: `NightlyReconciliationJob.perform_later`.

### M-Pesa payout failures / reconciliation mismatch

Money is involved: **slow down, write down what you do, and do not retry blindly.** See the [2026-07-14 game-day postmortem](postmortems/2026-07-14-payout-double-debit.md) for why.

1. **Freeze payouts** if `reconciliation-mismatch` fired or you suspect duplicates: admins stop creating batches (post in the ops channel), and pause the payout queue from a console: `SolidQueue::Queue.find_by_name("payouts").pause` (if `MpesaPayoutJob` runs on another queue, pause that one). Leave the worker running so result callbacks and other jobs still process.
2. **What did reconciliation find?** Logs Insights on the worker group: `filter event = "reconciliation_mismatch" | fields kind, reimbursement_id, details`. Kinds:
   - `ledger_imbalance` - debits != credits for an `entry_group`. Likely a half-written reversal; do not hand-edit, escalate to the payments owner.
   - `paid_without_receipt` - reimbursement `paid` but no `mpesa_receipt`. Check Daraja (step 4).
   - `stale_processing` - `processing` for > 2 h. Usually a lost callback; check Daraja (step 4).
   - `duplicate_disbursement` - more than one successful B2C result for one reimbursement. **Escalate immediately** (section 9).
3. **Failure spike without mismatch?** Check the error codes in `mpesa_payout_failed` events. Daraja outage / timeouts -> wait; insufficient float on the shortcode -> finance; invalid `mpesa_phone` -> fix the pharmacy record, the claim stays `approved` and is picked up in the next batch.
4. **Check the truth at Daraja** before any retry: use the Transaction Status query for the reimbursement's `OriginatorConversationID` (console: `Mpesa::TransactionStatus.call(reimbursement)` if present) or the Safaricom org portal. Only if Daraja confirms **no** successful transaction may a payout be retried, and only one at a time: `MpesaPayoutJob.perform_later(reimbursement.id)`.
5. If money was sent twice: record both receipts on the incident, do not reverse via the API, hand over to finance for recovery from the pharmacy, and write a postmortem.
6. Unfreeze (`SolidQueue::Queue.find_by_name("payouts").resume`) only after a manual `NightlyReconciliationJob.perform_now` is clean.

### Sync failures reported by pharmacies

Usually reported as "my sales are stuck" or the Sync screen showing conflicts.

1. Get the pharmacy name / licence number and the phone's *last sync* time (Sync screen). Never ask for patient details.
2. Logs Insights on web: `filter path like "/api/v1/sync" and pharmacy_id = <id> | fields @timestamp, status, duration, error | sort @timestamp desc`.
3. By status:
   - **401** - token expired (24 h) and the app could not re-login: ask the pharmacist to log out and in. Check the user is active.
   - **413 / timeouts** - very large backlog; batches are 50 records, so check for a stuck oversize receipt upload.
   - **422 / `conflict` records** - inventory delta rejected (would go negative). Expected behaviour: the pharmacist should do a stock count and adjust stock; then the conflict clears on next sync.
   - **5xx** - our bug; see [5xx spike](#5xx-spike). Records stay `pending` on the phone and are safe; `client_uuid` makes retries idempotent.
   - **No requests at all** - device connectivity, wrong app build (staging vs production URL) or the phone clock is badly wrong (TLS fails).
4. Many pharmacies in one region at once -> probably a Safaricom data outage; tell ops, nothing to fix.
5. Never delete a pharmacy's local database to "fix" sync: unsynced sales would be lost.

## 4. Deploy

- **Staging:** every merge to `main` that touches `api/` or `dashboard/` runs the *Deploy* workflow.
- **Production:** *Actions -> Deploy -> Run workflow -> production*; needs approval on the `production` GitHub environment.
- Steps: build image `:<git sha>` -> register task definitions -> run `db:prepare` as a one-off task -> update `web` and `worker` -> wait for stability -> publish dashboard -> invalidate CloudFront.
- Migrations run **before** new code, so they must be compatible with the code that is currently running (add columns first, remove later).
- The deployment circuit breaker rolls a failed rollout back automatically; the workflow then fails.

### Rollback

App code (fast, no rebuild):

```sh
# list recent revisions
aws ecs list-task-definitions --family-prefix $CLUSTER-web --sort DESC --max-items 5
# point the services at the previous revisions (also printed in the failed workflow's summary)
aws ecs update-service --cluster $CLUSTER --service web    --task-definition $CLUSTER-web:<N-1>
aws ecs update-service --cluster $CLUSTER --service worker --task-definition $CLUSTER-worker:<N-1>
aws ecs wait services-stable --cluster $CLUSTER --services web worker
```

Or re-run the *Deploy* workflow for an older commit (`Run workflow` from that commit / tag); with `skip_migrations` if the schema is already ahead.

- Database migrations are **not** rolled back automatically. Prefer rolling forward with a fix; if you must, run `bin/rails db:rollback STEP=1` from a [console task](#rails-console-via-ecs-exec) only after confirming the old code does not need the new schema.
- Dashboard: re-run *Deploy* for the previous commit, or restore the previous `index.html` version in the versioned bucket and invalidate `/*`.

## 5. Rails console via ECS Exec

Requires the Session Manager plugin for the AWS CLI and an IAM role with `ecs:ExecuteCommand`.

```sh
TASK=$(aws ecs list-tasks --cluster $CLUSTER --service-name worker --query 'taskArns[0]' --output text)
aws ecs execute-command --cluster $CLUSTER --task "$TASK" --container worker --interactive \
  --command "./bin/rails console"
```

- Prefer the `worker` task so you do not take capacity from web traffic; use `--container web` with a `web` task otherwise.
- Start read-only when investigating: `./bin/rails console --sandbox` rolls everything back on exit.
- Anything you change in production by hand: paste the exact commands into the incident notes.

## 6. Restore the database from a snapshot / point in time

Restores always create a **new** instance; nothing is overwritten.

```sh
# Point-in-time (any second within the last 7 days)
aws rds restore-db-instance-to-point-in-time \
  --source-db-instance-identifier $DB \
  --target-db-instance-identifier $DB-restore-$(date +%Y%m%d%H%M) \
  --restore-time 2026-07-14T09:30:00Z \
  --db-subnet-group-name afyaclaim-$ENV-db \
  --vpc-security-group-ids <db security group id> \
  --db-parameter-group-name afyaclaim-$ENV-pg16 \
  --no-publicly-accessible

# Or from a snapshot
aws rds describe-db-snapshots --db-instance-identifier $DB --query 'DBSnapshots[-5:].[DBSnapshotIdentifier,SnapshotCreateTime]'
aws rds restore-db-instance-from-db-snapshot --db-snapshot-identifier <id> \
  --db-instance-identifier $DB-restore-$(date +%Y%m%d%H%M) \
  --db-subnet-group-name afyaclaim-$ENV-db --vpc-security-group-ids <db security group id>
```

Then either:

- **Recover specific rows** (preferred): connect from a console task to the restored host and copy what is needed.
- **Full cutover:** scale `worker` to 0 (stop payouts), put the restored endpoint into `DATABASE_URL` in SSM, `--force-new-deployment` both services, verify, scale `worker` back to 1. Afterwards import the restored instance into Terraform or rename it; do not leave state drift.

Anything restored that touches `reimbursements` or `ledger_entries` must be followed by a reconciliation run and a Daraja check before payouts resume.

## 7. Rotating secrets

All secrets live in SSM under `/afyaclaim/<env>/`. Tasks read them **only at start**, so every rotation ends with a redeploy.

```sh
aws ssm put-parameter --overwrite --type SecureString --name /afyaclaim/$ENV/<NAME> --value '<new value>'
aws ecs update-service --cluster $CLUSTER --service web    --force-new-deployment
aws ecs update-service --cluster $CLUSTER --service worker --force-new-deployment
aws ecs wait services-stable --cluster $CLUSTER --services web worker
```

| Secret | Notes |
|---|---|
| `JWT_SECRET` | Rotating logs everyone out, including pharmacists (they re-login; unsynced data is safe). Do it outside pharmacy opening hours. |
| `RAILS_MASTER_KEY` | Must match the committed `config/credentials/production.yml.enc`; rotate together with a credentials re-encrypt in a PR. |
| `DATABASE_URL` | Managed by Terraform. To rotate the DB password, taint `module.rds.random_password.master` and apply; expect a short burst of errors while tasks restart. |
| `MPESA_CONSUMER_KEY` / `MPESA_CONSUMER_SECRET` | Regenerate in the Daraja portal, update both, redeploy. |
| `MPESA_SECURITY_CREDENTIAL` / `MPESA_INITIATOR_NAME` | Initiator password encrypted with Safaricom's public cert. Freeze payouts while rotating. |
| `MPESA_CALLBACK_SECRET` | Callbacks signed with the old secret will be rejected; rotate when no batch is `processing`. |

If a secret leaked: rotate first, then check CloudTrail for `ssm:GetParameter*` on that path and open an incident.

## 8. Routine checks (weekly, 15 minutes)

- Overview dashboard: p95 trend vs 300 ms, 5xx, RDS storage headroom.
- `SolidQueue::FailedExecution.count` is near zero and understood.
- Last `NightlyReconciliationJob` clean; no reimbursements `processing` > 2 h.
- Dependabot PRs reviewed; ECR scan findings for the running image triaged.

## 9. Escalation

| Order | Who | When |
|---|---|---|
| 1 | Primary on-call engineer | Any page |
| 2 | Secondary on-call engineer | No ack in 15 min, or primary needs help |
| 3 | Engineering lead | Customer impact > 30 min, data loss risk, any rollback of migrations, security concern |
| 4 | Payments owner + finance lead | Any `reconciliation-mismatch`, suspected duplicate or missing payout, float issues |
| 5 | Programme operations lead | Pharmacies unable to submit claims or be paid for > 4 h; needs comms to pharmacies |
| External | Safaricom Daraja support (via the org portal / account manager) | Confirmed Daraja-side outage or disputed transactions |

Contact details live in the on-call tool, not in this repository. After any page that needed more than a restart, write a short blameless postmortem in `docs/postmortems/` within five working days.
