package com.medfold.processing;

import java.time.Duration;
import java.time.Instant;
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
 * A unit of work for the processing worker: "run OCR and extraction for this document".
 * Lifecycle: PENDING → RUNNING → DONE, or back to PENDING with a delay after a failure,
 * until max attempts are used up (FAILED).
 */
@Entity
@Table(name = "processing_jobs")
public class ProcessingJob {

    private static final int MAX_ERROR_LENGTH = 4000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status = JobStatus.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProcessingJob() {
    }

    public ProcessingJob(UUID documentId, UUID userId, int maxAttempts, Instant runAt) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        this.documentId = documentId;
        this.userId = userId;
        this.maxAttempts = maxAttempts;
        this.nextRunAt = runAt;
    }

    /**
     * The worker has taken the job. In production jobs are claimed in bulk by an atomic SQL update
     * (see JobQueue.claimDue) that makes the same change; this method is the in-memory equivalent.
     */
    public void start(Instant now) {
        if (status != JobStatus.PENDING) {
            throw new IllegalStateException("Job " + id + " is " + status + ", expected PENDING");
        }
        status = JobStatus.RUNNING;
        attempts++;
        lockedAt = now;
    }

    public void complete() {
        requireRunning();
        status = JobStatus.DONE;
        lockedAt = null;
        lastError = null;
    }

    /**
     * Records a failure. Retryable failures go back to the queue after {@code retryDelay}
     * unless all attempts are used; permanent failures stop immediately.
     *
     * @return true if the job will be retried
     */
    public boolean fail(String error, boolean retryable, Instant now, Duration retryDelay) {
        requireRunning();
        lockedAt = null;
        lastError = error == null || error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
        if (retryable && attempts < maxAttempts) {
            status = JobStatus.PENDING;
            nextRunAt = now.plus(retryDelay);
            return true;
        }
        status = JobStatus.FAILED;
        return false;
    }

    private void requireRunning() {
        if (status != JobStatus.RUNNING) {
            throw new IllegalStateException("Job " + id + " is " + status + ", expected RUNNING");
        }
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

    public JobStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Instant getNextRunAt() {
        return nextRunAt;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
