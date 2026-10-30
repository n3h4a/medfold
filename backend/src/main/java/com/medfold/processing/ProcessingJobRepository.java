package com.medfold.processing;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Internal to the processing worker; jobs are never exposed to users directly. */
public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, UUID> {

    Optional<ProcessingJob> findFirstByDocumentIdAndStatusIn(UUID documentId, Collection<JobStatus> statuses);

    long countByDocumentId(UUID documentId);
}
