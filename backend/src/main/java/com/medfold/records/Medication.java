package com.medfold.records;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A medicine as written on a prescription or discharge summary. */
@Entity
@Table(name = "medications")
public class Medication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private String name;

    @Column(length = 100)
    private String dosage;

    @Column(length = 100)
    private String frequency;

    @Column(length = 50)
    private String route;

    @Column(length = 100)
    private String duration;

    @Column(length = 500)
    private String instructions;

    @Column(name = "prescribed_on")
    private LocalDate prescribedOn;

    @Column(name = "user_edited", nullable = false)
    private boolean userEdited;

    protected Medication() {
    }

    public Medication(UUID documentId, UUID userId, int position, String name, String dosage, String frequency,
            String route, String duration, String instructions, LocalDate prescribedOn) {
        this.documentId = documentId;
        this.userId = userId;
        this.position = position;
        this.name = name;
        this.dosage = dosage;
        this.frequency = frequency;
        this.route = route;
        this.duration = duration;
        this.instructions = instructions;
        this.prescribedOn = prescribedOn;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public UUID getUserId() {
        return userId;
    }

    public int getPosition() {
        return position;
    }

    public String getName() {
        return name;
    }

    public String getDosage() {
        return dosage;
    }

    public String getFrequency() {
        return frequency;
    }

    public String getRoute() {
        return route;
    }

    public String getDuration() {
        return duration;
    }

    public String getInstructions() {
        return instructions;
    }

    public LocalDate getPrescribedOn() {
        return prescribedOn;
    }

    public boolean isUserEdited() {
        return userEdited;
    }
}
