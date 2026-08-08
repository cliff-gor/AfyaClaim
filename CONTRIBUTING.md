# Contributing to AfyaClaim

Karibu! AfyaClaim is built in the open with the Nairuby and Africa Ruby Community. Whether you write
Ruby, Vue, Kotlin or Terraform — or you just want to improve the docs — there is room for you.

## Ground rules

- Be kind. We follow the [Contributor Covenant](https://www.contributor-covenant.org/version/2/1/code_of_conduct/).
- **No real patient data, ever.** Use the seed data. Patients are pseudonymous by design.
- Small PRs get reviewed fast. Aim for < 400 changed lines; split bigger work.
- Maintainers aim to give a first review within **48 hours**.

## Finding something to work on

1. Browse issues labelled [`good first issue`](../../labels/good%20first%20issue) or [`help wanted`](../../labels/help%20wanted).
2. Comment "I'd like to take this" — a maintainer will assign it to you so two people don't collide.
3. Not sure where to start? Ask in the issue or in `#afyaclaim` on the Nairuby Slack.

Labels you'll see: `area:api`, `area:dashboard`, `area:android`, `area:infra`, `fraud-rule`, `docs`.

## Local setup

```bash
docker compose up -d db                 # Postgres 16 on :5433
cd api && bundle install && bin/rails db:prepare db:seed && bin/rails server
cd dashboard && npm ci && npm run dev   # http://localhost:5173
```

Android: open `android/` in Android Studio (JDK 17) and run on an emulator — it talks to the API at `10.0.2.2:3000`.

Demo logins (password `password123`): `reviewer@afyaclaim.test`, `admin@afyaclaim.test`, `pharmacist@afyaclaim.test`.

## Making a change

1. Fork and branch from `main`: `feat/velocity-window-config`, `fix/sync-duplicate-claims`.
2. Write a test first where you can. Every fraud rule needs a spec that shows it **firing** and **staying quiet**.
3. Run the checks for the part you touched:

   | Area | Command |
   |---|---|
   | API | `bundle exec rspec && bundle exec rubocop && bundle exec brakeman -q` |
   | Dashboard | `npm run lint && npm test && npm run build` |
   | Android | `./gradlew testDebugUnitTest assembleDebug` |
   | Infra | `terraform fmt -check -recursive && terraform validate` |

4. Use [Conventional Commits](https://www.conventionalcommits.org/): `feat(api): add positivity outlier rule`.
5. Open a PR using the template. Link the issue (`Closes #12`) and add screenshots for UI changes.

## Adding a fraud rule

Rules live in `api/app/services/fraud/rules/`. Subclass `Fraud::Rule`, set `code` and `severity`, return
`finding("…")` when it fires and `nil` otherwise, then register it in `Fraud::Engine::RULES`.
Set `self.inline = false` if it's too expensive to run on every submission — the nightly
`FraudScanJob` will pick it up. Add the rule to the table in `docs/SPEC.md` §7.

## Review checklist we use

- Does it keep the claim audit trail intact (every status change goes through `Claims::StateMachine`)?
- Are money values integer cents?
- Are queries scoped to the user's role/pharmacy?
- Is offline behaviour considered for anything the POS touches?
- Is there a test, and does it fail without the change?

Asante for helping pharmacies get paid faster.
