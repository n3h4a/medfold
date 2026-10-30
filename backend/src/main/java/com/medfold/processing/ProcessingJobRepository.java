package com.medfold.processing;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Internal to the processing worker; jobs are never exposed to users directly. */
public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, UUID> {

    Optional<ProcessingJob> findFirstByDocumentIdAndStatusIn(UUID documentId, Collection<JobStatus> statuses);

    long countByDocumentId(UUID documentId);

    /** RUNNING jobs whose worker has gone quiet (crashed or restarted mid-job). */
    List<ProcessingJob> findByStatusAndLockedAtBefore(JobStatus status, Instant cutoff);
}
