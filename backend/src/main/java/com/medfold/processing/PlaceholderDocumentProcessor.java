package com.medfold.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.medfold.document.Document;

/**
 * Stand-in until the AI service client is connected (Task 25): finishes without extracting
 * anything. Processing is disabled by default until then, so this only runs when explicitly enabled.
 */
@Component
public class PlaceholderDocumentProcessor implements DocumentProcessor {

    private static final Logger log = LoggerFactory.getLogger(PlaceholderDocumentProcessor.class);

    @Override
    public void process(Document document) {
        log.info("Placeholder processor: document {} has no extraction yet", document.getId());
    }
}
