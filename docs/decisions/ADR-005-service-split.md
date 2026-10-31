# ADR-005: Spring Boot for the product, a separate FastAPI service for AI

**Status:** Accepted

## Context

The product needs a robust API with authentication, transactions, ownership rules and a job
queue. The AI work (PDF parsing, image processing, OCR, LLM calls with structured outputs) is best
served by the Python ecosystem.

## Decision

- **Backend: Java 21 + Spring Boot.** Mature security (Spring Security resource server), strong
  typing, transactions, Flyway and Testcontainers. It is the only public backend and owns all data.
- **AI service: Python + FastAPI.** `pypdf`, `pypdfium2`, Pillow, Tesseract bindings and the
  official Anthropic SDK; Pydantic doubles as the validation layer for model output. It is
  stateless and internal-only, authenticated with a shared key.
- **Frontend: React + TypeScript + Vite + Tailwind**, TanStack Query for server state.
- **Database: PostgreSQL** ([ADR-002](ADR-002-storage.md)).

The AI service never touches the database or storage: the backend sends it file bytes or page
texts and stores what comes back.

## Alternatives considered

| Option | Why not |
|---|---|
| One Python service for everything (Django/FastAPI) | Possible, but Spring's security, transaction and testing story is stronger for the core |
| One Java service with Java OCR/PDF libraries | Weaker OCR and LLM tooling; Tess4J and PDFBox are workable but the Python stack is better |
| AI calls directly from the frontend | Would expose API keys and send documents to the model without the backend's validation |

## Consequences

- Each side uses the best tools for its job, and the AI service can scale or be replaced on its own.
- Two languages and two build systems; CI runs both, plus an end-to-end job across the whole stack.
- One internal HTTP hop per step. The client pins HTTP/1.1, since uvicorn rejects HTTP/2 upgrade
  requests that have a body.
