import re
import unicodedata

_LONG_SPACE_RUN = re.compile(r"[ \t]{3,}")
_MANY_BLANK_LINES = re.compile(r"\n{3,}")
_CONTROL_CHARS = re.compile(r"[\x00-\x08\x0b-\x1f\x7f]")
_WORD_CHARS = re.compile(r"\w", re.UNICODE)


def clean_text(text: str) -> str:
    """
    Normalizes extracted text for the language model without losing table structure:
    runs of 3+ spaces (column gaps) are kept but shortened to 3 spaces, trailing spaces and
    control characters are removed, and blank lines are collapsed.
    """
    text = unicodedata.normalize("NFC", text).replace("\r\n", "\n").replace("\r", "\n")
    text = _CONTROL_CHARS.sub("", text)
    lines = [_LONG_SPACE_RUN.sub("   ", line).rstrip() for line in text.split("\n")]
    text = "\n".join(lines)
    text = _MANY_BLANK_LINES.sub("\n\n", text)
    return text.strip("\n")


def meaningful_char_count(text: str) -> int:
    """Letters and digits only, so stray symbols or whitespace don't count as real text."""
    return len(_WORD_CHARS.findall(text))
