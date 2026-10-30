package com.medfold.processing;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The processing queue: adding jobs and handing them out to workers. */
@Service
public class JobQueue {

    /*
     * Claims due jobs in one atomic statement. FOR UPDATE SKIP LOCKED makes concurrent workers
     * (several threads or several backend instances) skip rows another worker is claiming,
     * so no job is ever handed out twice and nobody waits on a lock.
     */
    private static final String CLAIM_SQL = """
            UPDATE processing_jobs
            SET status = 'RUNNING', attempts = attempts + 1, locked_at = ?, updated_at = ?
            WHERE id IN (
                SELECT id FROM processing_jobs
                WHERE status = 'PENDING' AND next_run_at <= ?
                ORDER BY next_run_at
                LIMIT ?
                FOR UPDATE SKIP LOCKED)
            RETURNING id
            """;

    private final ProcessingJobRepository jobRepository;
    private final ProcessingProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public JobQueue(ProcessingJobRepository jobRepository, ProcessingProperties properties, JdbcTemplate jdbcTemplate,
            Clock clock) {
        this.jobRepository = jobRepository;
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    /**
     * Queues a document for processing unless it already has a waiting or running job.
     * Joins the caller's transaction, so a document and its job are saved together or not at all.
     *
     * @return true if a new job was queued
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public boolean enqueue(UUID documentId, UUID userId) {
        boolean active = jobRepository
                .findFirstByDocumentIdAndStatusIn(documentId, List.of(JobStatus.PENDING, JobStatus.RUNNING))
                .isPresent();
        if (active) {
            return false;
        }
        jobRepository.saveAndFlush(new ProcessingJob(documentId, userId, properties.maxAttempts(), clock.instant()));
        return true;
    }

    /** Marks up to {@code limit} due jobs RUNNING and returns their ids. */
    @Transactional
    public List<UUID> claimDue(int limit) {
        Timestamp now = Timestamp.from(clock.instant());
        return jdbcTemplate.queryForList(CLAIM_SQL, UUID.class, now, now, now, limit);
    }
}
