package com.medfold.processing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.medfold.document.Document;
import com.medfold.document.DocumentRepository;
import com.medfold.document.DocumentStatus;

/**
 * Runs claimed jobs: marks the document PROCESSING, calls the DocumentProcessor, then records
 * success (READY) or failure (retry later, or FAILED with a user-facing reason).
 *
 * The processor runs between two short transactions, so slow AI calls never hold a database
 * transaction or row lock open.
 */
@Service
public class JobRunner {

    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);
    private static final Duration MAX_RETRY_DELAY = Duration.ofHours(1);
    static final String GENERIC_FAILURE = "Something went wrong while processing this document. Please try again later";
    static final String INTERRUPTED = "Processing was interrupted";

    private final JobQueue jobQueue;
    private final ProcessingJobRepository jobRepository;
    private final DocumentRepository documentRepository;
    private final DocumentProcessor processor;
    private final ProcessingProperties properties;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public JobRunner(JobQueue jobQueue, ProcessingJobRepository jobRepository, DocumentRepository documentRepository,
            DocumentProcessor processor, ProcessingProperties properties, TransactionTemplate transactions, Clock clock) {
        this.jobQueue = jobQueue;
        this.jobRepository = jobRepository;
        this.documentRepository = documentRepository;
        this.processor = processor;
        this.properties = properties;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** One worker cycle: recover abandoned jobs, then claim and run a batch. Returns how many jobs ran. */
    public int runDue() {
        recoverStaleJobs();
        List<UUID> claimed = jobQueue.claimDue(properties.batchSize());
        claimed.forEach(this::run);
        return claimed.size();
    }

    void run(UUID jobId) {
        Optional<Document> document = transactions.execute(status -> start(jobId));
        if (document == null || document.isEmpty()) {
            return; // the document (and with it the job) was deleted meanwhile
        }
        try {
            processor.process(document.get());
            transactions.executeWithoutResult(status -> finishSuccess(jobId));
        } catch (ProcessingFailure failure) {
            log.warn("Job {} failed (retryable={}): {}", jobId, failure.isRetryable(), failure.getMessage());
            transactions.executeWithoutResult(status -> finishFailure(jobId, failure.getMessage(), failure.isRetryable(), failure.getUserMessage()));
        } catch (RuntimeException unexpected) {
            log.error("Job {} failed unexpectedly", jobId, unexpected);
            transactions.executeWithoutResult(status -> finishFailure(jobId, unexpected.toString(), true, GENERIC_FAILURE));
        }
    }

    private Optional<Document> start(UUID jobId) {
        Optional<ProcessingJob> job = jobRepository.findById(jobId);
        if (job.isEmpty()) {
            return Optional.empty();
        }
        Optional<Document> document = documentRepository.findByIdAndUserId(job.get().getDocumentId(), job.get().getUserId());
        document.ifPresent(d -> {
            if (d.getStatus() != DocumentStatus.PROCESSING) {
                d.markProcessing();
            }
        });
        return document;
    }

    private void finishSuccess(UUID jobId) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.complete();
            ownedDocument(job).ifPresent(Document::markReady);
        });
    }

    private void finishFailure(UUID jobId, String internalError, boolean retryable, String userMessage) {
        jobRepository.findById(jobId).ifPresent(job -> {
            Instant now = clock.instant();
            boolean willRetry = job.fail(internalError, retryable, now, retryDelay(job.getAttempts()));
            if (!willRetry) {
                ownedDocument(job).ifPresent(document -> document.markFailed(userMessage));
            }
        });
    }

    /** Jobs left RUNNING by a crashed or restarted worker go back to the queue (or fail if out of attempts). */
    void recoverStaleJobs() {
        Instant cutoff = clock.instant().minus(properties.staleAfter());
        for (ProcessingJob stale : jobRepository.findByStatusAndLockedAtBefore(JobStatus.RUNNING, cutoff)) {
            log.warn("Recovering job {} left running since {}", stale.getId(), stale.getLockedAt());
            transactions.executeWithoutResult(status -> finishFailure(stale.getId(), INTERRUPTED, true, GENERIC_FAILURE));
        }
    }

    private Optional<Document> ownedDocument(ProcessingJob job) {
        return documentRepository.findByIdAndUserId(job.getDocumentId(), job.getUserId());
    }

    /** 30s, 60s, 120s, ... capped at one hour. */
    Duration retryDelay(int attemptsSoFar) {
        Duration delay = properties.retryDelay().multipliedBy(1L << Math.min(Math.max(attemptsSoFar - 1, 0), 16));
        return delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay;
    }
}
