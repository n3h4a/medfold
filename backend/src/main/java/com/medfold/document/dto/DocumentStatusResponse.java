package com.medfold.document.dto;

import java.util.UUID;

import com.medfold.document.Document;
import com.medfold.document.DocumentStatus;

/** Small payload for polling while a document is being processed. */
public record DocumentStatusResponse(UUID id, DocumentStatus status, String failureReason) {

    public static DocumentStatusResponse from(Document document) {
        return new DocumentStatusResponse(document.getId(), document.getStatus(), document.getFailureReason());
    }
}
