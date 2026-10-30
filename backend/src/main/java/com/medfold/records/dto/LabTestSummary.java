package com.medfold.records.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.medfold.records.LabFlag;

/** One test the user has results for, with its most recent value. */
public record LabTestSummary(
        String testKey,
        String testName,
        String unit,
        int resultCount,
        String latestValueText,
        LabFlag latestFlag,
        LocalDate latestObservedOn,
        UUID latestDocumentId) {
}
