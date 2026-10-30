package com.medfold.account.dto;

import java.time.Instant;
import java.util.List;

import com.medfold.document.dto.DocumentResponse;
import com.medfold.records.dto.LabResultResponse;
import com.medfold.records.dto.MedicationResponse;
import com.medfold.records.dto.TermResponse;

/**
 * Everything Medfold holds about a user, in one JSON document. Original files are not embedded;
 * each can be downloaded from its document.
 */
public record AccountExport(
        String format,
        Instant exportedAt,
        Account account,
        List<DocumentExport> documents) {

    public static final String FORMAT = "medfold-export-v1";

    public record Account(String email, String fullName, Instant createdAt) {
    }

    public record Page(int pageNumber, String text, String source, Float confidence) {
    }

    public record DocumentExport(
            DocumentResponse document,
            List<Page> pages,
            List<LabResultResponse> labResults,
            List<MedicationResponse> medications,
            List<TermResponse> terms) {
    }
}
