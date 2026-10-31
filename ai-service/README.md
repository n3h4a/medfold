# AI service (FastAPI)

Internal service that reads documents: PDF text layer or OCR, then structured extraction with a
language model. Python 3.12, FastAPI, pypdf, pypdfium2, Pillow, Tesseract, Anthropic SDK,
Pydantic. Reachable only by the backend (`X-Internal-Api-Key`); it stores nothing.

## Run

```bash
python -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
uvicorn app.main:app --reload --port 8000     # API docs: http://localhost:8000/docs
```

Install Tesseract (`apt install tesseract-ocr tesseract-ocr-eng tesseract-ocr-osd`, or
`brew install tesseract`) for scans and photos; digital PDFs don't need it. The Docker image
includes it.

Dependencies are declared in [`pyproject.toml`](pyproject.toml). [`requirements.txt`](requirements.txt)
is the lock file with exact versions, used by the Docker image; regenerate it after changing
dependencies:

```bash
pip install pip-tools && pip-compile --strip-extras --output-file=requirements.txt pyproject.toml
```

| Setting | Default | |
|---|---|---|
| `AI_INTERNAL_KEY` | required | Shared secret with the backend |
| `LLM_PROVIDER` | `mock` | `mock` (offline, rule-based) or `anthropic` (Claude) |
| `LLM_MODEL`, `LLM_API_KEY` | | For `anthropic` |
| `OCR_LANGUAGES` | `eng` | Tesseract languages, e.g. `eng+hin` |

## Endpoints

| Method | Path | |
|---|---|---|
| GET | `/health` | OCR and LLM configuration, Tesseract version |
| GET | `/v1/ping` | Internal key check |
| POST | `/v1/ocr` | Multipart file → per-page text, source, confidence |
| POST | `/v1/extract` | Page texts → validated, safety-checked `ExtractionResult` |

## Test

```bash
pytest             # PDFs and images generated in memory; OCR tests skip without Tesseract
```

## Layout

```
app/
├── ocr/           file type detection, PDF text layer, rendering, image preparation, providers
├── extraction/    schema (the safety boundary), prompt, lab test keys, safety check, service
├── llm/           provider interface, Anthropic provider, offline mock provider
├── routers/       /health, /v1/ocr, /v1/extract
├── config.py      settings from the environment
└── security.py    internal key check
```

How the pipeline works and why: [architecture §6](../docs/03-design/architecture.md#6-ai-service-pipeline),
[ADR-004](../docs/03-design/adr/ADR-004-ocr.md), [ADR-007](../docs/03-design/adr/ADR-007-ai-safety-boundary.md).
