package com.medfold.document.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.medfold.document.Document;
import com.medfold.document.DocumentStatus;
import com.medfold.document.DocumentType;

/** A document as the client sees it. Never exposes the storage key or owner id. */
public record DocumentResponse(
        UUID id,
        String originalFilename,
        String mimeType,
        long sizeBytes,
        DocumentStatus status,
        String failureReason,
        Integer pageCount,
        DocumentType docType,
        LocalDate docDate,
        String title,
        String providerName,
        String facilityName,
        String summary,
        boolean userEdited,
        Instant createdAt,
        Instant updatedAt) {

    public static DocumentResponse from(Document d) {
        return new DocumentResponse(d.getId(), d.getOriginalFilename(), d.getMimeType(), d.getSizeBytes(),
                d.getStatus(), d.getFailureReason(), d.getPageCount(), d.getDocType(), d.getDocDate(), d.getTitle(),
                d.getProviderName(), d.getFacilityName(), d.getSummary(), d.isUserEdited(), d.getCreatedAt(),
                d.getUpdatedAt());
    }
}
