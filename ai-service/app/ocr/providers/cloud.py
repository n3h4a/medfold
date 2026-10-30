from PIL import Image

from app.errors import ServiceError
from app.ocr.models import TextSource
from app.ocr.providers.base import OcrProvider, RecognizedPage


class CloudOcrProvider(OcrProvider):
    """
    Placeholder for a managed OCR service (e.g. Google Cloud Vision or AWS Textract), for when
    phone photos need better accuracy than Tesseract gives. Not implemented in the MVP.
    """

    source = TextSource.CLOUD

    def __init__(self) -> None:
        raise ServiceError(503, "OCR_UNAVAILABLE", "Cloud OCR is not configured on this server")

    def recognize(self, image: Image.Image) -> RecognizedPage:  # pragma: no cover
        raise NotImplementedError
