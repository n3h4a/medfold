# References and acknowledgements

## Standards and specifications

- RFC 7519, *JSON Web Token (JWT)*.
- RFC 6265, *HTTP State Management Mechanism* (cookies), and the `SameSite` attribute drafts.
- RFC 9700, *Best Current Practice for OAuth 2.0 Security* (refresh-token rotation and reuse detection).
- OWASP Password Storage Cheat Sheet and OWASP Application Security Verification Standard (ASVS).
- W3C Content Security Policy Level 3.
- HL7 FHIR R4 (for the planned export).
- Hoelzel W. et al., *IFCC reference system for measurement of hemoglobin A1c in human blood and
  the national standardization schemes in the United States, Japan, and Sweden: a
  method-comparison study*, Clinical Chemistry 50(1), 2004: source of the IFCC–NGSP master
  equation used for HbA1c unit conversion.

## Regulation (context for [privacy and regulation](../01-research/privacy-and-regulation.md))

- Regulation (EU) 2016/679, General Data Protection Regulation, Articles 9, 15, 17, 20 and 25.
- India, Digital Personal Data Protection Act, 2023.
- US Health Insurance Portability and Accountability Act (HIPAA) and the FTC Health Breach
  Notification Rule (16 CFR Part 318).

## Practices

- Michael Nygard, *Documenting Architecture Decisions* (2011): the ADR format.
- [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), [Semantic Versioning](https://semver.org/),
  [Conventional Commits](https://www.conventionalcommits.org/).
- [Contributor Covenant](https://www.contributor-covenant.org/) 2.1: the code of conduct.

## Open-source software

Medfold is built on Spring Boot, Spring Security, Hibernate, Flyway, PostgreSQL, React, Vite,
Tailwind CSS, TanStack Query, FastAPI, Pydantic, pypdf, pypdfium2, Pillow, Tesseract OCR,
the Anthropic Python SDK, Testcontainers, Vitest, Testing Library, pytest, k6, Caddy, nginx and
Mermaid. Thank you to everyone who maintains them. Licenses: [third-party licenses](third-party-licenses.md).
