package com.medfold.document;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The text of one page, as read from the PDF text layer or by OCR. Used for search and re-extraction. */
@Entity
@Table(name = "document_pages")
public class DocumentPage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "page_number", nullable = false)
    private int pageNumber;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TextSource source;

    private Float confidence;

    @Column(name = "char_count", nullable = false)
    private int charCount;

    protected DocumentPage() {
    }

    public DocumentPage(UUID documentId, UUID userId, int pageNumber, String text, TextSource source, Float confidence,
            int charCount) {
        this.documentId = documentId;
        this.userId = userId;
        this.pageNumber = pageNumber;
        this.text = text == null ? "" : text;
        this.source = source;
        this.confidence = confidence;
        this.charCount = charCount;
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

    public int getPageNumber() {
        return pageNumber;
    }

    public String getText() {
        return text;
    }

    public TextSource getSource() {
        return source;
    }

    public Float getConfidence() {
        return confidence;
    }

    public int getCharCount() {
        return charCount;
    }
}
