package com.medfold.processing;

import com.medfold.document.Document;

/** Turns an uploaded document into stored results (page text, labs, medicines, summary). */
public interface DocumentProcessor {

    /**
     * Processes one document. Runs outside the job's transactions.
     *
     * @throws ProcessingFailure with a retry decision and a user-facing message
     */
    void process(Document document);
}
