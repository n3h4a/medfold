package com.medfold.document;

/** Where a page's text came from (mirrors the AI service's OCR response). */
public enum TextSource {
    PDF_TEXT,
    TESSERACT,
    CLOUD,
    NONE
}
