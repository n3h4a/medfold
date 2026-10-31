# Sequence diagrams

The main flows, step by step. Sources are in [`assets/diagrams/src`](../../assets/diagrams/src).

## Login, refresh and reuse detection

Access tokens live 15 minutes in memory; the refresh cookie rotates on every use. Details:
[ADR-001](adr/ADR-001-authentication.md).

![Login, an expired token, refresh and retry](../../assets/diagrams/auth-sequence.svg)

## Upload

The file is checked, stored and queued in one request; reading happens later.

![Upload: type detection, storage, duplicate check, queueing](../../assets/diagrams/upload-sequence.svg)

## Processing

The worker never holds a transaction during AI calls. Details:
[ADR-006](adr/ADR-006-job-queue.md).

![Processing: claim, OCR, extraction, save results](../../assets/diagrams/processing-sequence.svg)

## Authenticated request

![Request path through the security filter to an owner-scoped repository](../../assets/diagrams/request-flow.svg)

## Search

![Search: words, owner-scoped SQL, snippets](../../assets/diagrams/search-sequence.svg)

## Account deletion

![Account deletion: password check, cascading delete, files removed after commit](../../assets/diagrams/account-deletion-sequence.svg)
