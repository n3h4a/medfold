package com.medfold.records.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.medfold.records.LabFlag;

/**
 * One test the user has results for, with its most recent value and the dated value before it
 * (null when there is none), so a change can be shown without loading the whole trend.
 */
public record LabTestSummary(
        String testKey,
        String testName,
        String unit,
        int resultCount,
        String latestValueText,
        LabFlag latestFlag,
        LocalDate latestObservedOn,
        UUID latestDocumentId,
        String latestRefText,
        String previousValueText,
        LocalDate previousObservedOn) {
}
