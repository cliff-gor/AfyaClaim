## What & why

<!-- One or two sentences. Link the issue: "Closes #123". -->

## Area

- [ ] api
- [ ] dashboard
- [ ] android
- [ ] infra / CI
- [ ] docs

## How it was tested

<!-- Specs added, manual steps, screenshots for UI changes (light + dark). -->

## Checklist

- [ ] Tests added or updated, CI is green
- [ ] No patient identifiers, secrets or real M-Pesa data in code, fixtures, logs or screenshots
- [ ] Database migrations are backwards compatible with the currently deployed code (deploys migrate before rolling out)
- [ ] Claim state changes still go through `Claims::StateMachine` and write a `ClaimEvent`
- [ ] Money is integer cents; ledger entries balance
- [ ] Offline sync stays idempotent on `client_uuid` (if touched)
- [ ] `docs/SPEC.md`, `docs/runbook.md` or the OpenAPI spec updated (if behaviour, alarms or endpoints changed)

## Deploy notes

<!-- New SSM secrets? Terraform changes to apply first? Backfill jobs? Write "None" if none. -->
