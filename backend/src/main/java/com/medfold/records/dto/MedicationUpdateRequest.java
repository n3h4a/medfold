package com.medfold.records.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Corrections to one medicine. A field left out (null) is unchanged; an empty string clears an optional one. */
public record MedicationUpdateRequest(
        @Pattern(regexp = "(?s).*\\S.*", message = "Name can't be empty")
        @Size(max = 255, message = "Name must be at most 255 characters") String name,
        @Size(max = 100, message = "Dose must be at most 100 characters") String dosage,
        @Size(max = 100, message = "Frequency must be at most 100 characters") String frequency,
        @Size(max = 50, message = "Route must be at most 50 characters") String route,
        @Size(max = 100, message = "Duration must be at most 100 characters") String duration,
        @Size(max = 500, message = "Instructions must be at most 500 characters") String instructions) {
}
