## What and why

<!-- What this changes and the reason. Link the issue: Closes #… -->

## How it was tested

<!-- Tests added or changed; commands you ran. -->

## Checklist

- [ ] Tests cover the change (a bug fix includes a test that failed before)
- [ ] New endpoints are classified in `UserIsolationTest`; new user-data queries are owner-scoped
- [ ] No new extraction field for diagnosis, risk or advice
- [ ] Database changes are a new Flyway migration
- [ ] Docs updated (README, docs/, CHANGELOG) where behaviour changed
