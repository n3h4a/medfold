package com.medfold.processing;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

/** Internal to the processing worker; jobs are never exposed to users directly. */
public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, UUID> {

    Optional<ProcessingJob> findFirstByDocumentIdAndStatusIn(UUID documentId, Collection<JobStatus> statuses);

    long countByDocumentId(UUID documentId);

    /** Row-locks the job, so finishing it and recovering it as stale cannot interleave. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM ProcessingJob j WHERE j.id = :id")
    Optional<ProcessingJob> findByIdForUpdate(@Param("id") UUID id);

    /** RUNNING jobs whose worker has gone quiet (crashed or restarted mid-job). */
    List<ProcessingJob> findByStatusAndLockedAtBefore(JobStatus status, Instant cutoff);
}
