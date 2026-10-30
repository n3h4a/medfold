package com.medfold.document;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A medical term that appears in a document, with a neutral general definition. */
@Entity
@Table(name = "document_terms")
public class DocumentTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 100)
    private String term;

    @Column(nullable = false, length = 300)
    private String definition;

    protected DocumentTerm() {
    }

    public DocumentTerm(UUID documentId, UUID userId, int position, String term, String definition) {
        this.documentId = documentId;
        this.userId = userId;
        this.position = position;
        this.term = term;
        this.definition = definition;
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

    public String getTerm() {
        return term;
    }

    public String getDefinition() {
        return definition;
    }
}
