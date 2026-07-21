# Postmortem: duplicate M-Pesa B2C payout after callback timeouts (game day)

> **This was a game-day simulation.** The incident was deliberately induced in **staging** against the
> **Safaricom Daraja sandbox**. No real money moved and no pharmacy was affected. We write it up as if it
> were real because the bug it exposed was real and would have reached production.

| | |
|---|---|
| **Date** | Tuesday 14 July 2026 |
| **Environment** | staging, Daraja B2C sandbox |
| **Simulated severity** | SEV-2 (money paid incorrectly, no customer-visible outage) |
| **Duration** | 10:05 - 13:30 EAT (fault injected to all-clear); duplicate undetected for 1 h 28 min |
| **Status** | Closed. Action items tracked below |
| **Format** | Blameless. We describe systems and decisions, not people |
| **Last updated** | 15 September 2026 |

## Summary

During a planned game day we made a share of Daraja B2C responses slow. The `MpesaPayoutJob` HTTP client timed
out on a request that Daraja had in fact accepted, and the job's `retry_on Net::ReadTimeout` sent a **second**
B2C request with a **new `OriginatorConversationID`**. Daraja treated it as a new payment and disbursed twice. Both
result callbacks were accepted, so reimbursement `#118` (KES 4,350.00) was paid twice and recorded twice in the ledger.

`NightlyReconciliationJob` caught it and the `reconciliation_mismatch` alarm fired, but the alarm's email
subscription on staging had never been confirmed, so nobody was notified. The game-day facilitator spotted the red
alarm 34 minutes later.

## Impact

- **Actual:** none. Sandbox only; one test MSISDN received two sandbox credits.
- **If this had been production:** one pharmacy overpaid KES 4,350. The fault rate we injected (30% of B2C calls
  slower than our 10 s timeout) on a typical 120-pharmacy batch would have produced roughly 36 timeouts; in the game
  day about half of the timed-out requests had actually succeeded, so we estimate **~18 duplicate payouts, about
  KES 80,000**, per batch. Production reconciliation runs at 02:00 EAT, so a morning batch could have gone unnoticed for
  up to 16 hours, and without a working alarm, until someone read the daily report.
- Recovering money from pharmacies is slow and damages trust in a programme whose whole point is paying them reliably.

## Background

Payout flow at the time (simplified; see [SPEC §10](../SPEC.md#10-reimbursements--ledger-m-pesa)):

1. Admin creates a payout batch -> one `Reimbursement` per pharmacy, status `pending`.
2. `MpesaPayoutJob` builds a B2C request, generating `OriginatorConversationID = SecureRandom.uuid` **inside `perform`**,
   stores it on the reimbursement and posts to Daraja. Daraja replies synchronously with an acknowledgement; the actual
   outcome arrives later on `POST /api/v1/mpesa/b2c/result`.
3. The result callback marks the reimbursement and its claims `paid` and **then** writes the ledger pair
   (`debit programme_float` / `credit payable:pharmacy:<id>`).

The spec asked for the ledger to be debited *first* (at batch creation). The implementation had deferred it to the
callback during phase 6 to "avoid reversals for failed payouts". That change was reviewed and seemed reasonable at the time.

## Timeline (EAT, 14 July 2026)

| Time | Event |
|---|---|
| 09:30 | Game-day briefing. Scenario: "Daraja is slow". Hypothesis: *slow responses cause failed payouts that are safely retried*. |
| 10:05 | Fault enabled: a proxy on staging egress adds 12 s latency to 30% of responses from the Daraja B2C endpoint. Our client read timeout is 10 s. |
| 10:12 | Admin (participant) creates a payout batch of 6 reimbursements. Six `MpesaPayoutJob`s enqueued. |
| 10:12:40 | Job for reimbursement **#118** sends B2C request, OCID `a1f3...`. Daraja accepts it; our client times out at 10:12:50. |
| 10:12:51 | `Net::ReadTimeout` raised; `retry_on` schedules attempt 2 in ~30 s. Same happens for **#121**, whose request Daraja never received (dropped by the proxy). |
| 10:13:22 | Attempt 2 for #118 generates a **new** OCID `b7c0...`, overwrites `mpesa_conversation_id`, gets a fast acknowledgement. #121 attempt 2 also succeeds (correctly, its first attempt never arrived). |
| 10:14:05 | Callback for `b7c0...`: success, receipt `SGE7...`. #118 marked `paid`, ledger pair written. |
| 10:14:41 | Callback for `a1f3...`: success, receipt `SGE3...`. No reimbursement has that OCID any more, so the handler falls back to matching on the `Occasion` field (reimbursement id), marks #118 `paid` again, **writes a second ledger pair** and overwrites the receipt. |
| 10:20 | Fault disabled. Dashboard shows 6/6 reimbursements paid. Participants record the hypothesis as "confirmed". |
| 11:40 | Per the game-day script, `NightlyReconciliationJob` is triggered manually. It logs `reconciliation_mismatch` (`kind=reimbursement_ledger_mismatch`, #118 ledger total KES 8,700 vs amount KES 4,350). Metric filter fires; alarm goes to ALARM and publishes to SNS. |
| 11:40 | SNS delivers to zero endpoints: the staging email subscription was still `PendingConfirmation`. |
| 12:14 | Facilitator, reviewing the CloudWatch dashboard for the wrap-up, sees the red `reconciliation-mismatch` alarm. Simulated incident declared, SEV-2. |
| 12:20 | Payout queue paused from a Rails console. |
| 12:35 | Daraja sandbox Transaction Status queries for `a1f3...` and `b7c0...` both return success: two disbursements confirmed. |
| 13:10 | Cause identified in `MpesaPayoutJob` (new OCID per attempt + retry on timeout) and the callback handler's `Occasion` fallback. |
| 13:30 | Staging ledger corrected with reversing entries, #118 annotated, queue resumed. Game day closed. |

## Root cause

A **timeout is an unknown outcome, not a failure**, and the payout path treated it as a failure on a non-idempotent call:

1. **No idempotency key per reimbursement.** The `OriginatorConversationID` was generated inside `perform`, so each
   retry was, from Daraja's point of view, a brand-new payment. Daraja does not de-duplicate by amount or recipient.
2. **Blind retry on timeout.** `retry_on Net::ReadTimeout` re-sent the request without asking Daraja what happened to
   the first one. Daraja provides a Transaction Status API exactly for this.

### Contributing factors

- **Ledger written on callback, not debit-first.** Because the ledger pair was written per success callback, a second
  callback created a second pair instead of being rejected against an already-settled reimbursement.
- **Callback handler was not idempotent.** It accepted a success for a reimbursement that was already `paid`, and
  its `Occasion` fallback matched a callback whose OCID had been overwritten.
- **Client timeout (10 s) below Daraja's slow-path latency.** Daraja's documented behaviour under load includes
  multi-second acknowledgements; we had tuned the timeout for sandbox happy-path speed.
- **Alarm routing untested.** The `reconciliation_mismatch` metric filter and alarm worked, but the only subscription
  had never been confirmed. Nothing checked that alarms reach a human.
- **The success signal looked right.** 6/6 paid on the dashboard is what we expected to see, so participants stopped looking.

## What went well

- `NightlyReconciliationJob` did exactly its job: it found the duplicate on its first run after the event.
- Structured JSON logs (`event`, `reimbursement_id`, OCID) made the timeline easy to reconstruct in Logs Insights.
- Pausing the payout queue was quick and left callbacks and other jobs running.
- Running this in staging with the sandbox meant we found a money bug for free.
- The game-day script included a reconciliation run; without it, staging would not have run one until 02:00.

## What went badly

- The duplicate went unnoticed for 1 h 28 min, and only by luck after the alarm fired.
- Participants declared the hypothesis confirmed from the dashboard alone, without checking the ledger or Daraja.
- The debit-first rule in the spec had been relaxed without recording why, so nobody connected it to retry safety.
- There was no runbook section for "money may have been sent twice", so the first 15 minutes were spent agreeing what to do.

## Where we got lucky

- The fault was injected in staging. The same code was already deployed to production behind the `mpesa_environment = sandbox` setting and would have gone live with the next configuration change.
- Only one of the six reimbursements hit the "accepted but timed out" window.

## Action items

Owners are roles; the named person for each role is in the tracker.

| # | Action | Type | Owner | Status |
|---|---|---|---|---|
| 1 | **Idempotency key per reimbursement:** derive `OriginatorConversationID` deterministically from the reimbursement (`AC-RB-<id>-<attempt_group>`), persist it *before* the HTTP call, reuse it on every retry; unique index on `mpesa_conversation_id`. | Prevent | Payments engineer | Done (2026-07-24) |
| 2 | **Ledger debit-first** as SPEC §10 requires: write `debit programme_float` / `credit payable:pharmacy:<id>` in the batch-creation transaction; callbacks only transition state; failures write a reversing pair. | Prevent | Payments engineer | Done (2026-07-31) |
| 3 | **Query status before retry:** on timeout, mark the reimbursement `processing` with `outcome_unknown`, enqueue `MpesaStatusCheckJob` (Transaction Status API). Re-send only when Daraja confirms the original was not processed. Remove `retry_on Net::ReadTimeout` from `MpesaPayoutJob`. | Prevent | Payments engineer | Done (2026-07-31) |
| 4 | Make the B2C result callback idempotent: a second success for a `paid` reimbursement is rejected, logged as `event=duplicate_disbursement` and counted as a reconciliation mismatch. Remove the `Occasion` fallback. | Prevent / detect | Backend engineer | Done (2026-07-24) |
| 5 | **Alarm on `reconciliation_mismatch`** routed to on-call: metric filter + alarm managed in Terraform (`modules/monitoring`), SNS subscriptions confirmed in every environment, alarm posts both ALARM and the reconciliation details link. | Detect | Platform engineer | Done (2026-07-21) |
| 6 | Add a reconciliation check: exactly one successful B2C result per reimbursement, cross-checked against Daraja for any reimbursement paid in the last 24 h. | Detect | Payments engineer | Done (2026-08-07) |
| 7 | Monthly synthetic test that publishes a fake `reconciliation_mismatch` log line in staging and confirms the page arrives. | Detect | Platform engineer | In progress |
| 8 | Raise the Daraja read timeout to 30 s and emit `mpesa_request_timeout` so slow-path latency is visible. | Mitigate | Payments engineer | Done (2026-07-24) |
| 9 | Runbook section for payout failures and suspected duplicates (freeze, check Daraja, no blind retries). | Respond | On-call lead | Done ([runbook](../runbook.md#m-pesa-payout-failures--reconciliation-mismatch)) |
| 10 | Review checklist item and a custom RuboCop cop: no `retry_on` timeout errors in jobs that call non-idempotent external APIs. | Prevent | Tech lead | In progress |
| 11 | Finance procedure for recovering a duplicate payout from a pharmacy, agreed with programme operations. | Respond | Finance lead | Open |
| 12 | Re-run this game day against the fixes; success criterion is zero duplicates and a page within 5 minutes of reconciliation. | Verify | Platform engineer | Done (2026-09-10, passed) |

## Lessons

- For any call that moves money, decide up front what "timed out" means. The answer is almost never "try again".
- An idempotency key only works if it is created before the first attempt and survives every retry.
- An alarm that has never paged anyone is a hypothesis, not a control.
- Game days should check outcomes in the system of record (ledger, Daraja), not just the UI.
