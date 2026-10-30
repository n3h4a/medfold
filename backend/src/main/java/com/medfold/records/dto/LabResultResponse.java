package com.medfold.records.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.medfold.records.LabFlag;
import com.medfold.records.LabResult;

/** One lab result as printed, with the backend's comparison to the printed reference range. */
public record LabResultResponse(
        UUID id,
        UUID documentId,
        String testName,
        String testKey,
        String valueText,
        BigDecimal valueNumeric,
        String unit,
        String refText,
        BigDecimal refLow,
        BigDecimal refHigh,
        LabFlag flag,
        LocalDate observedOn,
        Integer pageNumber,
        boolean userEdited) {

    public static LabResultResponse from(LabResult r) {
        return new LabResultResponse(r.getId(), r.getDocumentId(), r.getTestName(), r.getTestKey(), r.getValueText(),
                r.getValueNumeric(), r.getUnit(), r.getRefText(), r.getRefLow(), r.getRefHigh(), r.getFlag(),
                r.getObservedOn(), r.getPageNumber(), r.isUserEdited());
    }
}
