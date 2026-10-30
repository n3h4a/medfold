from app.errors import ServiceError
from app.ocr.models import FileKind

_PDF = b"%PDF-"
_PNG = b"\x89PNG\r\n\x1a\n"
_JPEG = b"\xff\xd8\xff"


def detect_file_kind(content: bytes) -> FileKind:
    """Identify the upload by its leading bytes, like the backend does. Names and declared types are ignored."""
    if content.startswith(_PDF):
        return FileKind.PDF
    if content.startswith(_PNG) or content.startswith(_JPEG):
        return FileKind.IMAGE
    raise ServiceError(415, "UNSUPPORTED_FILE_TYPE", "Only PDF, JPG and PNG files are supported")
