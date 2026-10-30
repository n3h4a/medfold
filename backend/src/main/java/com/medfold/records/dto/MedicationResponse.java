package com.medfold.records.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.medfold.records.Medication;

public record MedicationResponse(
        UUID id,
        UUID documentId,
        String name,
        String dosage,
        String frequency,
        String route,
        String duration,
        String instructions,
        LocalDate prescribedOn,
        boolean userEdited) {

    public static MedicationResponse from(Medication m) {
        return new MedicationResponse(m.getId(), m.getDocumentId(), m.getName(), m.getDosage(), m.getFrequency(),
                m.getRoute(), m.getDuration(), m.getInstructions(), m.getPrescribedOn(), m.isUserEdited());
    }
}
