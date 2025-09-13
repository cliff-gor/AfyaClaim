# AfyaClaim — Product & Technical Specification

> A pharmacy subsidy claims platform. Pharmacies record sales and submit subsidy claims
> (malaria rapid tests, ACTs, family-planning products). Ops staff review and approve claims,
> and approved claims are reimbursed via M-Pesa.

| | |
|---|---|
| **Status** | v1.0 — implemented |
| **Owner** | Cliff Gor |
| **Last revised** | September 2026 |
| **Repos** | Monorepo: `api/` (Rails), `dashboard/` (Vue), `android/` (Kotlin), `infra/` (Terraform) |

---

## 1. Problem

Subsidy programmes in East Africa pay community pharmacies to dispense malaria tests,
antimalarials and family-planning products below cost. The pharmacy is reimbursed the
difference. Today that loop is mostly paper and WhatsApp:

- Pharmacies wait **6–10 weeks** to be reimbursed, which starves them of working capital.
- Ops teams can't tell a genuine surge (rainy-season malaria) from a pharmacy inflating claims.
- Programme managers have no live view of stock-outs or test positivity by region, even
  though that data is a leading indicator of outbreaks.

## 2. Goals & non-goals

**Goals**

1. A pharmacist can record a sale and attach a subsidy claim in **under 30 seconds**, offline.
2. Ops can clear a day's review queue in one sitting, with fraud signals surfaced *before* review.
3. Every claim has a tamper-evident audit trail from submission to payment.
4. Approved claims are paid within **48 hours** via M-Pesa B2C.
5. Programme managers see claims, positivity and stock-outs by region and product.

**Non-goals (v1)**

- Patient-identifiable records. Patients are pseudonymised on device; no names or phone numbers leave the phone.
- Full pharmacy ERP (purchasing, supplier invoices).
- Multi-currency. All amounts are KES, stored as integer cents.

## 3. Users & roles

| Role | Surface | Can |
|---|---|---|
| `pharmacist` | Android POS | Record sales, update stock, submit claims for **own pharmacy** only |
| `reviewer` | Vue dashboard | View all claims, start review, approve / reject, see flags |
| `admin` | Vue dashboard | Everything a reviewer can, plus trigger payouts, manage pharmacies & products |

## 4. System architecture

```
 ┌──────────────┐  HTTPS/JSON (JWT)   ┌──────────────────────────────┐
 │ Android POS  │ ──── /api/v1/sync ─▶│  Rails 8 API (ECS Fargate)   │
 │ Compose+Room │ ◀── delta pull ──── │  ├─ Claims state machine     │
 └──────────────┘                     │  ├─ Fraud rules engine       │
 ┌──────────────┐                     │  ├─ Ledger + M-Pesa B2C      │
 │ Vue 3 back-  │ ──── /api/v1/* ───▶ │  └─ Solid Queue workers      │
 │ office (S3+  │                     └───────┬──────────┬───────────┘
 │ CloudFront)  │                             │          │
 └──────────────┘                     ┌───────▼───┐ ┌────▼─────┐  ┌─────────────┐
                                      │ RDS Pg 16 │ │ S3       │  │ Safaricom   │
                                      │ (Multi-AZ)│ │ receipts │  │ Daraja B2C  │
                                      └───────────┘ └──────────┘  └─────────────┘
                     CloudWatch logs + alarms ─▶ SNS ─▶ on-call email
```

- **API**: Rails 8.1 API-only, PostgreSQL 16, Solid Queue (DB-backed jobs — no Redis to operate).
- **Dashboard**: Vue 3 + Vite + TypeScript + Pinia + Vue Router + Chart.js; static build on S3/CloudFront.
- **POS**: Kotlin, Jetpack Compose (Material 3), Room, WorkManager, Retrofit/OkHttp, Hilt.
- **Infra**: Terraform (VPC, ECS Fargate, ALB, RDS, S3, CloudWatch, SNS, ECR). GitHub Actions CI/CD.

## 5. Domain model

```
Region 1─* Pharmacy 1─* User(pharmacist)
Pharmacy 1─* InventoryItem *─1 Product
Pharmacy 1─* Sale 1─* SaleItem *─1 Product
Sale *─1 Patient (pseudonymous)
Sale 1─* Claim *─1 Product
Claim 1─* ClaimEvent (audit trail, append-only)
Claim 1─* ClaimFlag (fraud/quality signals)
Reimbursement 1─* Claim ; Reimbursement 1─* LedgerEntry
```

| Model | Key fields |
|---|---|
| `Region` | `name`, `code` (Kenyan county) |
| `Pharmacy` | `name`, `license_number` (unique), `region_id`, `town`, `latitude`, `longitude`, `mpesa_phone`, `status` (active/suspended), `risk_score` 0–100 |
| `User` | `email`, `password_digest`, `name`, `role`, `pharmacy_id` (pharmacists only) |
| `Product` | `sku`, `name`, `category` (`malaria_test`, `antimalarial`, `family_planning`, `ors_zinc`, `other`), `unit_price_cents`, `subsidy_cents`, `subsidised` |
| `InventoryItem` | `pharmacy_id`, `product_id`, `quantity_on_hand`, `reorder_level`, `lock_version` |
| `Patient` | `pseudonym` (SHA-256 of phone + programme salt, computed on device), `age_band`, `sex` |
| `Sale` | `client_uuid` (unique, idempotency key), `pharmacy_id`, `patient_id`, `sold_at`, `total_cents`, `receipt_key` (S3) |
| `SaleItem` | `sale_id`, `product_id`, `quantity`, `unit_price_cents` |
| `Claim` | `client_uuid`, `reference` (`AC-2026-000123`), `pharmacy_id`, `sale_id` (nullable — a claim *without* a sale is flagged), `product_id`, `quantity`, `amount_cents`, `test_result` (malaria only: positive/negative), `status`, `submitted_at`, `reviewed_by_id`, `reviewed_at`, `rejection_reason`, `reimbursement_id`, `flags_count` |
| `ClaimEvent` | `claim_id`, `from_status`, `to_status`, `actor_id`, `note`, `metadata` jsonb, `prev_digest`, `digest` |
| `ClaimFlag` | `claim_id`, `rule` , `severity` (low/medium/high), `message`, `resolved` |
| `Reimbursement` | `pharmacy_id`, `amount_cents`, `status` (pending/processing/paid/failed), `mpesa_conversation_id`, `mpesa_receipt`, `paid_at` |
| `LedgerEntry` | `reimbursement_id`, `account` (`programme_float`, `payable:pharmacy:<id>`), `direction` (debit/credit), `amount_cents`, `entry_group` |

## 6. Claim workflow

```
             ┌──────────── reject (reason required) ───────────┐
             │                                                 ▼
 submitted ──┴─ start_review ─▶ under_review ── approve ─▶ approved ── pay ─▶ paid
                                     │                          │
                                     └── reject ─▶ rejected     └─ payout failed → stays approved, retried
```

Rules

- Transitions live in `Claims::StateMachine`; any other transition raises `InvalidTransition` → HTTP 422.
- `approve` is blocked while a **high**-severity flag is unresolved unless the reviewer passes `override: true` with a note (recorded in the event metadata).
- `reject` requires a `reason`.
- Every transition writes a `ClaimEvent` in the same DB transaction.
- **Tamper evidence**: each event stores `digest = SHA256(prev_digest + payload)`. `GET /claims/:id` returns `audit_intact: true/false` after re-verifying the chain.

## 7. Fraud & quality checks

Rules run synchronously on claim creation (cheap ones) and nightly across the whole dataset (`FraudScanJob`).

| Code | Rule | Severity |
|---|---|---|
| `velocity` | > 20 claims of the same product from one pharmacy within 60 min (e.g. 50 malaria tests in an hour) | high |
| `no_matching_sale` | Claim has no sale, or the sale doesn't contain the claimed product/quantity | high |
| `duplicate_patient` | Same patient + same product claimed within 7 days | medium |
| `amount_mismatch` | `amount_cents ≠ product.subsidy_cents × quantity` | high |
| `stock_inconsistency` | Claimed quantity exceeds what the pharmacy's stock could support | medium |
| `off_hours` | Sale recorded between 23:00 and 05:00 EAT | low |
| `positivity_outlier` | Pharmacy malaria positivity > regional mean + 2σ over 14 days (nightly) | medium |

`Pharmacy#risk_score` = weighted count of unresolved flags over 30 days, capped at 100. Pharmacies above 70 appear in the dashboard’s alert rail.

## 8. API (v1)

Base path `/api/v1`, JSON, JWT bearer auth (`Authorization: Bearer <token>`), 24h expiry.
Errors: `{ "error": { "code": "invalid_transition", "message": "…" } }`.
Pagination: `?page=&per_page=` with `meta: { page, per_page, total }`.
Full contract: [`api/swagger/v1/openapi.yaml`](../api/swagger/v1/openapi.yaml), served at `/api-docs`.

| Method | Path | Role | Purpose |
|---|---|---|---|
| POST | `/auth/login` | public | `{email,password}` → `{token,user}` |
| GET | `/me` | any | current user |
| GET | `/products` | any | catalogue |
| GET | `/inventory` | pharmacist | own stock |
| PATCH | `/inventory/:id` | pharmacist | adjust stock (optimistic lock via `lock_version`) |
| GET/POST | `/sales` | pharmacist | list / create sale (idempotent on `client_uuid`) |
| POST | `/sync` | pharmacist | batch push of offline sales+claims; returns per-record `accepted`/`duplicate`/`conflict` and server deltas |
| GET | `/sync?since=ISO8601` | pharmacist | pull products, inventory, claim statuses changed since cursor |
| GET | `/claims` | reviewer | filters: `status`, `flagged`, `pharmacy_id`, `region_id`, `product_id`, `q` |
| GET | `/claims/:id` | reviewer / owner | claim + sale + flags + events + `audit_intact` |
| POST | `/claims` | pharmacist | submit claim |
| POST | `/claims/:id/start_review` | reviewer | |
| POST | `/claims/:id/approve` | reviewer | `{note?, override?}` |
| POST | `/claims/:id/reject` | reviewer | `{reason}` |
| POST | `/claims/bulk_approve` | reviewer | `{ids:[]}` — skips flagged claims, returns per-id result |
| GET | `/pharmacies` | reviewer | list with claim stats & risk score |
| GET | `/pharmacies/:id` | reviewer | detail + claim history + flags |
| GET | `/analytics/overview` | reviewer | KPIs |
| GET | `/analytics/claims_by_region` | reviewer | |
| GET | `/analytics/claims_by_product` | reviewer | |
| GET | `/analytics/trend?days=30` | reviewer | daily claims + positivity |
| GET | `/analytics/stockouts` | reviewer | pharmacies at/below reorder level |
| GET/POST | `/reimbursements` | admin | list / create payout batch for approved claims |
| POST | `/mpesa/b2c/result` | Safaricom | B2C result callback (shared-secret validated) |
| GET | `/up` | public | health check for ALB |

## 9. Offline sync (Android ⇄ API)

- Every sale and claim gets a **UUIDv4 `client_uuid`** on device. The server treats it as an idempotency key, so retries are safe.
- Local rows carry `sync_state` = `pending | synced | conflict | rejected`.
- `SyncWorker` (WorkManager, unique periodic work + expedited on connectivity) pushes pending rows in batches of 50 to `POST /sync`, then pulls `GET /sync?since=cursor`.
- **Conflict policy**
  - Sales & claims: device is the source of truth for *creation*; server is source of truth for *status*. A duplicate `client_uuid` returns the existing record (`duplicate`) and the device marks it synced.
  - Inventory: server wins. Device sends deltas (`-2 RDT`), not absolute counts, so concurrent sales merge. If the server rejects a delta (would go negative) the row is marked `conflict` and surfaced in the app's Sync screen.
  - Claim status changes made by reviewers flow down on pull and update the local claim card.
- Cursor = server `updated_at` high-water mark, persisted in DataStore.

## 10. Reimbursements & ledger (M-Pesa)

1. Admin creates a payout batch → one `Reimbursement` per pharmacy for its approved claims.
2. **Debit the ledger first**: in one transaction write `debit programme_float` / `credit payable:pharmacy:<id>` and lock the claims to the reimbursement.
3. `MpesaPayoutJob` calls Daraja **B2C** (sandbox) with the pharmacy's `mpesa_phone`.
4. The result callback marks the reimbursement `paid` and claims `paid`; failure writes a reversing ledger entry and leaves claims `approved` for retry.
5. `NightlyReconciliationJob` checks that ledger debits = credits, every `paid` reimbursement has an M-Pesa receipt, and every `processing` one older than 2h is re-queried. Mismatches page on-call through CloudWatch.

## 11. Background jobs (Solid Queue)

| Job | Schedule | Purpose |
|---|---|---|
| `FraudScanJob` | 01:00 EAT | run all rules, recompute risk scores |
| `NightlyReconciliationJob` | 02:00 EAT | ledger balance + M-Pesa reconciliation |
| `DailyReportJob` | 06:00 EAT | daily summary (claims, approvals, payouts) emailed to ops |
| `StockoutAlertJob` | hourly | flag pharmacies at zero stock of subsidised products |
| `MpesaPayoutJob` | on demand | send a B2C payment |

## 12. Back-office dashboard (Vue)

| Screen | Content |
|---|---|
| **Overview** | KPI tiles (pending review, approved today, value awaiting payout, flagged %), 30-day trend, flagged-pharmacy rail |
| **Review queue** | Filterable table, status pills, flag badges, keyboard shortcuts (`j/k`, `a` approve, `r` reject), bulk approve, side panel with sale, flags and audit timeline |
| **Pharmacies** | Searchable list with risk score meter; detail page with claim history and flag alerts |
| **Analytics** | Claims by region (bar), by product (donut), malaria positivity trend (line), stock-outs table |
| **Payouts** | Reimbursement batches, ledger status |

## 13. Android POS

| Screen | Content |
|---|---|
| **Today** | greeting, day's sales total, pending-sync chip, quick actions |
| **New sale** | product picker, quantity steppers, patient (age band, sex, optional phone → hashed), malaria test result, "attach subsidy claim" toggle, receipt photo |
| **Stock** | inventory list with low-stock indicators, adjust stock |
| **Claims** | own claims with live status (synced down) |
| **Sync** | pending/conflict records, last sync time, manual sync |

## 14. Design system

Derived from eight health/wellness references (Senso, Rythea heart platform, Elli, Biorg, Fitness Coach, OTO, PulseFit, Airofit). Common threads: a single high-energy **lime accent** against **ink-black** hero cards, calm off-white surfaces, generous 24px radii, pill chips, and **dot-matrix numerals** for headline metrics.

| Token | Light | Dark | Use |
|---|---|---|---|
| `--bg` | `#F3F4EF` | `#0B0C0A` | app background |
| `--surface` | `#FFFFFF` | `#16181A` | cards |
| `--ink` | `#0E0F0C` | `#F2F3EE` | primary text / hero cards |
| `--muted` | `#6B6F66` | `#9A9E95` | secondary text |
| `--lime` | `#C8F169` | `#C8F169` | accent, primary CTA, active nav |
| `--moss` | `#43511C` | `#BCC388` | on-lime text, positive states |
| `--sage` | `#BCC388` | `#43511C` | soft fills |
| `--coral` | `#F0605D` | `#FF7A76` | flags, rejection, danger |
| `--amber` | `#F2B33D` | `#F2B33D` | under review / warnings |
| `--lavender` | `#E7E6F7` | `#2A2840` | info chips |

- **Type**: Inter (UI), JetBrains Mono (tabular amounts), *Doto* (dot-matrix KPI numerals).
- **Shape**: cards 24px, inputs 14px, chips fully rounded. Elevation by contrast, not shadow.
- **Status colour is never alone** — every pill has an icon and label.
- Motion 150–250ms ease-out; respects `prefers-reduced-motion`.

## 15. Non-functional requirements

| Area | Target |
|---|---|
| Availability | 99.5% monthly for the API |
| Latency | p95 < 300ms for `/claims` list at 100k claims |
| Security | JWT, bcrypt, role-scoped queries, Rack::Attack throttling on login, secrets in SSM Parameter Store, S3 receipts private with presigned URLs |
| Privacy | no direct patient identifiers server-side; pseudonym only |
| Observability | structured JSON logs (Lograge), CloudWatch alarms on 5xx rate, p95 latency, RDS CPU/storage, queue latency, reconciliation failures |
| Testing | RSpec models/requests/jobs/rules, ≥ 85% line coverage (SimpleCov); Vitest for dashboard; JUnit + Room in-memory tests for Android |

## 16. Delivery plan

| Phase | Scope |
|---|---|
| 1 | Rails models, claim workflow, RSpec, API deployed to AWS early |
| 2 | Vue claims review dashboard |
| 3 | Android POS with offline sync |
| 4 | Terraform, CI/CD, monitoring, runbook |
| 5 | Fraud flags, analytics, README + architecture diagram, contributor onboarding |
| 6 | M-Pesa B2C payouts, ledger, reconciliation, postmortem |

## 17. Community & contribution

Run as an open-source project with the Nairuby / Africa Ruby Community: `good first issue` labels,
issue templates, a [contributing guide](../CONTRIBUTING.md), PR review within 48h, and a kickoff call.

## 18. Open questions

- Should approved-claim payouts be batched daily or weekly per programme?
- Do programmes want positivity alerts pushed to county health officers directly?
