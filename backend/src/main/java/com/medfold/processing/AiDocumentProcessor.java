package com.medfold.processing;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.medfold.ai.AiServiceClient;
import com.medfold.ai.ExtractionResult;
import com.medfold.ai.OcrResult;
import com.medfold.document.Document;
import com.medfold.storage.StorageService;
import com.medfold.storage.StoredFileNotFoundException;

/**
 * The real pipeline: stored file → AI service OCR → AI service extraction → results saved in one
 * transaction. Both AI calls run without a database transaction open.
 */
@Component
public class AiDocumentProcessor implements DocumentProcessor {

    private static final Logger log = LoggerFactory.getLogger(AiDocumentProcessor.class);
    static final String FILE_MISSING = "The original file could not be found. Please upload it again";
    static final String NO_TEXT = "No text was found in this document";

    private final StorageService storageService;
    private final AiServiceClient aiServiceClient;
    private final ProcessingResultWriter resultWriter;

    public AiDocumentProcessor(StorageService storageService, AiServiceClient aiServiceClient,
            ProcessingResultWriter resultWriter) {
        this.storageService = storageService;
        this.aiServiceClient = aiServiceClient;
        this.resultWriter = resultWriter;
    }

    @Override
    public void process(Document document) {
        Resource file;
        try {
            file = storageService.load(document.getStorageKey());
        } catch (StoredFileNotFoundException ex) {
            throw new ProcessingFailure("Stored file missing: " + document.getStorageKey(), false, FILE_MISSING, ex);
        }

        OcrResult ocr = aiServiceClient.ocr(file, document.getOriginalFilename(), document.getMimeType());
        List<AiServiceClient.PageText> pages = ocr.pages().stream()
                .filter(page -> page.text() != null && !page.text().isBlank())
                .map(page -> new AiServiceClient.PageText(page.pageNumber(), page.text()))
                .toList();
        if (pages.isEmpty()) {
            // Keep what OCR found (page count, empty pages) so the user can see the file was read.
            resultWriter.saveOcrOnly(document.getId(), document.getUserId(), ocr);
            throw new ProcessingFailure("OCR found no text on " + ocr.pageCount() + " page(s)", false, NO_TEXT);
        }

        ExtractionResult extraction = aiServiceClient.extract(pages, null);
        boolean saved = resultWriter.save(document.getId(), document.getUserId(), ocr, extraction);
        if (saved) {
            log.info("Document {} processed: {} page(s), {} lab result(s), {} medicine(s)", document.getId(),
                    ocr.pageCount(), extraction.extraction().labResults().size(),
                    extraction.extraction().medications().size());
        }
    }
}
