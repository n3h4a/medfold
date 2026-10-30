package com.medfold.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.medfold.document.TextSource;

/** Response of the AI service's POST /v1/ocr: the text of every page and where it came from. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OcrResult(
        String fileKind,
        int pageCount,
        List<Page> pages,
        String ocrProvider,
        List<Integer> pagesWithoutText) {

    public OcrResult {
        pages = pages == null ? List.of() : List.copyOf(pages);
        pagesWithoutText = pagesWithoutText == null ? List.of() : List.copyOf(pagesWithoutText);
    }

    /** {@code confidence} is null for text-layer pages (exact) and pages without text. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Page(int pageNumber, String text, TextSource source, int charCount, Float confidence) {
    }
}
