package com.medfold.records.dto;

import java.util.List;

/** Everything extracted from one document, in the order it appears there. */
public record DocumentResultsResponse(
        List<LabResultResponse> labResults,
        List<MedicationResponse> medications,
        List<TermResponse> terms) {
}
