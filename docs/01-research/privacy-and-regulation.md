# Privacy and regulation

Health data is among the most sensitive personal data. This page summarises how common
frameworks treat it and where Medfold's design lines up. It is design input, **not legal advice**;
anyone running Medfold for other people should get their own legal review.

## How major frameworks see health records

| Framework | What it says about health data | What it implies for an app like Medfold |
|---|---|---|
| **GDPR** (EU/EEA, UK GDPR similar) | Health data is a *special category* (Art. 9) that needs an explicit legal basis such as explicit consent. Data subjects have rights of access (Art. 15), erasure (Art. 17) and portability (Art. 20); controllers must protect data by design and by default (Art. 25) | Clear consent and purpose; export and deletion built in; security by default; a processing agreement with any LLM provider |
| **India: Digital Personal Data Protection Act, 2023** | Covers digital personal data generally; gives rights to access, correction and erasure; requires reasonable security safeguards and breach notification | Correction and deletion in the product; safeguards documented in [security design](../03-design/security-design.md) |
| **United States: HIPAA** | Applies to covered entities (providers, insurers) and their business associates, not to an app a person chooses to keep their own records | Self-hosted personal use is generally outside HIPAA, but a provider offering Medfold to patients would be in scope |
| **United States: FTC Health Breach Notification Rule** | Applies to vendors of personal health records not covered by HIPAA; requires notifying users and the FTC after a breach | A hosted Medfold service for the public would need a breach-notification process |

## Design responses

| Principle | In Medfold |
|---|---|
| Data minimisation | Only what's needed: email, optional name, password hash, the documents and what is read from them. No tracking or analytics |
| Purpose limitation | Data is used only to show the user their own records |
| Access and portability | `GET /account/export`: everything in one JSON file; original files downloadable per document |
| Erasure | `DELETE /account` removes every row and file; documents can be deleted one by one |
| Correction | Every extracted field can be edited |
| Security | Owner-scoped queries, isolation tests, hashed secrets, rate limiting, HTTPS, CSP ([security design](../03-design/security-design.md)) |
| Third-party processing | Off by default (offline reader); with `LLM_PROVIDER=anthropic`, page text goes to Anthropic's API, which an operator must disclose |
| Retention | Users decide; expired sessions and login-failure records are deleted automatically |

## Open items before offering Medfold to the public

- Terms of service and a privacy notice naming any LLM provider.
- Explicit consent at registration for processing health data.
- A breach-response and notification process.
- Encryption of uploaded files at rest, beyond disk encryption.
- A data processing agreement with the LLM provider, if one is used.
