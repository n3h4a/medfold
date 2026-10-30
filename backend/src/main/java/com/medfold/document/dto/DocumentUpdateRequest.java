package com.medfold.document.dto;

import java.time.LocalDate;

import com.medfold.document.DocumentType;

import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/**
 * Corrections to a document's details. A field left out (null) is unchanged; an empty string clears it.
 * The record date can be changed but not cleared.
 */
public record DocumentUpdateRequest(
        DocumentType docType,
        @PastOrPresent(message = "The record date can't be in the future") LocalDate docDate,
        @Size(max = 255, message = "Title must be at most 255 characters") String title,
        @Size(max = 255, message = "Doctor must be at most 255 characters") String providerName,
        @Size(max = 255, message = "Hospital or lab must be at most 255 characters") String facilityName,
        @Size(max = 2000, message = "Summary must be at most 2000 characters") String summary) {
}
