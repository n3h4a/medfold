package com.medfold.records.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Corrections to one lab result. A field left out (null) is unchanged; an empty string clears an optional one. */
public record LabResultUpdateRequest(
        @Pattern(regexp = "(?s).*\\S.*", message = "Test name can't be empty")
        @Size(max = 255, message = "Test name must be at most 255 characters") String testName,
        @Pattern(regexp = "(?s).*\\S.*", message = "Result can't be empty")
        @Size(max = 100, message = "Result must be at most 100 characters") String valueText,
        @Size(max = 50, message = "Unit must be at most 50 characters") String unit,
        @Size(max = 100, message = "Reference range must be at most 100 characters") String refText) {
}
