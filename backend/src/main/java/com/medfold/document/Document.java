package com.medfold.document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An uploaded medical document. Owned by exactly one user; every lookup must go through
 * {@link DocumentRepository}, which only offers queries scoped by owner.
 */
@Entity
@Table(name = "documents")
public class Document {

    private static final int MAX_FAILURE_REASON = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "storage_key", nullable = false, unique = true, length = 500, updatable = false)
    private String storageKey;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status = DocumentStatus.UPLOADED;

    @Column(name = "failure_reason", length = MAX_FAILURE_REASON)
    private String failureReason;

    @Column(name = "page_count")
    private Integer pageCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", length = 30)
    private DocumentType docType;

    @Column(name = "doc_date")
    private LocalDate docDate;

    private String title;

    @Column(name = "provider_name")
    private String providerName;

    @Column(name = "facility_name")
    private String facilityName;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(name = "user_edited", nullable = false)
    private boolean userEdited;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Document() {
    }

    public Document(UUID userId, String originalFilename, String storageKey, String mimeType, long sizeBytes,
            String sha256) {
        this.userId = userId;
        this.originalFilename = originalFilename;
        this.storageKey = storageKey;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
    }

    /** The processing worker has picked this document up. Also used when the user asks to reprocess. */
    public void markProcessing() {
        if (status == DocumentStatus.PROCESSING) {
            throw new IllegalStateException("Document " + id + " is already being processed");
        }
        status = DocumentStatus.PROCESSING;
        failureReason = null;
    }

    public void markReady() {
        requireStatus(DocumentStatus.PROCESSING);
        status = DocumentStatus.READY;
        failureReason = null;
    }

    /** The reason is shown to the user, so it must never contain internal error details. */
    public void markFailed(String userFacingReason) {
        requireStatus(DocumentStatus.PROCESSING);
        status = DocumentStatus.FAILED;
        failureReason = userFacingReason == null || userFacingReason.length() <= MAX_FAILURE_REASON
                ? userFacingReason
                : userFacingReason.substring(0, MAX_FAILURE_REASON);
    }

    private void requireStatus(DocumentStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Document " + id + " is " + status + ", expected " + expected);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public void setPageCount(Integer pageCount) {
        this.pageCount = pageCount;
    }

    public DocumentType getDocType() {
        return docType;
    }

    public void setDocType(DocumentType docType) {
        this.docType = docType;
    }

    public LocalDate getDocDate() {
        return docDate;
    }

    public void setDocDate(LocalDate docDate) {
        this.docDate = docDate;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getFacilityName() {
        return facilityName;
    }

    public void setFacilityName(String facilityName) {
        this.facilityName = facilityName;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public boolean isUserEdited() {
        return userEdited;
    }

    public void setUserEdited(boolean userEdited) {
        this.userEdited = userEdited;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
