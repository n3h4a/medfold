package com.medfold.processing;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

public interface ExtractionRunRepository extends Repository<ExtractionRun, UUID> {

    ExtractionRun save(ExtractionRun run);

    Optional<ExtractionRun> findFirstByDocumentIdAndUserIdOrderByCreatedAtDesc(UUID documentId, UUID userId);

    long countByDocumentId(UUID documentId);
}
