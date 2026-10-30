"""Versioned prompts. The version is stored with every extraction so results can be traced and re-run."""

import re
from functools import lru_cache
from pathlib import Path

PROMPT_VERSION = "extraction-v1"
_PROMPTS_DIR = Path(__file__).parent / "prompts"
_DOCUMENT_TAG = re.compile(r"</?\s*(document|page)\b[^>]*>", re.IGNORECASE)


@lru_cache
def system_prompt(version: str = PROMPT_VERSION) -> str:
    name = version.replace("-", "_") + ".md"
    return (_PROMPTS_DIR / name).read_text(encoding="utf-8")


def _neutralize(text: str) -> str:
    """Stops document text from closing or faking our <document>/<page> tags (prompt injection)."""
    return _DOCUMENT_TAG.sub(lambda m: m.group(0).replace("<", "‹").replace(">", "›"), text)


def user_message(pages: list[tuple[int, str]], doc_type_hint: str | None = None) -> str:
    """Wraps the document's page texts in clearly delimited tags for the model."""
    parts = ["Extract the structured information from this medical document."]
    if doc_type_hint:
        parts.append(f"The user indicated it may be a {doc_type_hint}; verify this against the content.")
    parts.append("<document>")
    for number, text in pages:
        parts.append(f'<page number="{number}">\n{_neutralize(text)}\n</page>')
    parts.append("</document>")
    return "\n".join(parts)
