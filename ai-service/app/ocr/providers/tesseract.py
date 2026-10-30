import logging
import re

import pytesseract
from PIL import Image

from app.ocr.models import TextSource
from app.ocr.providers.base import OcrPageError, OcrProvider, RecognizedPage

log = logging.getLogger(__name__)

_ROTATE = re.compile(r"Rotate:\s*(\d+)")


class TesseractProvider(OcrProvider):
    source = TextSource.TESSERACT

    def __init__(self, languages: str = "eng", timeout_seconds: int = 60) -> None:
        self.languages = languages
        self.timeout_seconds = timeout_seconds

    def recognize(self, image: Image.Image) -> RecognizedPage:
        image = self._fix_orientation(image)
        try:
            data = pytesseract.image_to_data(
                image,
                lang=self.languages,
                config="--psm 3",
                output_type=pytesseract.Output.DICT,
                timeout=self.timeout_seconds,
            )
        except RuntimeError as exc:  # pytesseract signals timeouts with RuntimeError
            raise OcrPageError("OCR timed out") from exc
        except pytesseract.TesseractError as exc:
            raise OcrPageError(f"Tesseract failed: {exc}") from exc
        return _assemble(data)

    def _fix_orientation(self, image: Image.Image) -> Image.Image:
        """
        Phone photos are often sideways or upside down. Tesseract's orientation detection says how
        far to rotate clockwise; it needs a fair amount of text, so failures just keep the image as is.
        """
        try:
            osd = pytesseract.image_to_osd(image, config="--psm 0", timeout=self.timeout_seconds)
        except (pytesseract.TesseractError, RuntimeError):
            return image
        match = _ROTATE.search(osd)
        angle = int(match.group(1)) if match else 0
        if angle in (90, 180, 270):
            log.info("Rotating page %d degrees before OCR", angle)
            # PIL rotates counter-clockwise for positive angles.
            return image.rotate(-angle, expand=True)
        return image


def _assemble(data: dict) -> RecognizedPage:
    """Rebuilds lines and paragraphs from Tesseract's word boxes and averages word confidence."""
    lines: dict[tuple[int, int, int], list[str]] = {}
    confidences: list[float] = []
    for i, word in enumerate(data["text"]):
        word = word.strip()
        conf = float(data["conf"][i])
        if not word or conf < 0:
            continue
        key = (data["block_num"][i], data["par_num"][i], data["line_num"][i])
        lines.setdefault(key, []).append(word)
        confidences.append(conf)

    text_parts: list[str] = []
    previous_paragraph: tuple[int, int] | None = None
    for (block, paragraph, _line), words in lines.items():
        if previous_paragraph is not None and (block, paragraph) != previous_paragraph:
            text_parts.append("")
        text_parts.append(" ".join(words))
        previous_paragraph = (block, paragraph)

    confidence = round(sum(confidences) / len(confidences) / 100, 3) if confidences else None
    return RecognizedPage(text="\n".join(text_parts), confidence=confidence)
