package com.medfold.ai;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.medfold.document.DocumentType;

/**
 * Response of the AI service's POST /v1/extract. The extraction has already been validated against
 * the service's schema (which has no field for diagnosis, risk or advice) and safety-checked.
 *
 * @param rawJson the response body exactly as received, kept in the extraction_runs audit trail
 */
public record ExtractionResult(Extraction extraction, Meta meta, String rawJson) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Extraction(
            DocumentType docType,
            LocalDate docDate,
            String title,
            String providerName,
            String facilityName,
            String summary,
            List<Term> termsExplained,
            List<Lab> labResults,
            List<Medicine> medications) {

        public Extraction {
            termsExplained = termsExplained == null ? List.of() : List.copyOf(termsExplained);
            labResults = labResults == null ? List.of() : List.copyOf(labResults);
            medications = medications == null ? List.of() : List.copyOf(medications);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Term(String term, String definition) {
    }

    /** Values as printed; {@code testKey} is computed deterministically by the AI service, not by the model. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Lab(String testName, String testKey, String valueText, String unit, String referenceRangeText,
            Integer pageNumber) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Medicine(String name, String dosage, String frequency, String route, String duration,
            String instructions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(
            String provider,
            String model,
            String promptVersion,
            int attempts,
            Integer durationMs,
            Integer inputTokens,
            Integer outputTokens,
            boolean safetyFiltered,
            List<String> safetyIssues) {

        public Meta {
            safetyIssues = safetyIssues == null ? List.of() : List.copyOf(safetyIssues);
        }
    }
}
