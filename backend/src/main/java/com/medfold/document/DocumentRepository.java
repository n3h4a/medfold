package com.medfold.document;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Deliberately extends {@link Repository} rather than JpaRepository: there is no
 * findById or findAll, so every read must name the owning user. That makes reading
 * another user's document impossible to write by accident.
 */
public interface DocumentRepository extends Repository<Document, UUID> {

    Document save(Document document);

    Document saveAndFlush(Document document);

    void delete(Document document);

    Optional<Document> findByIdAndUserId(UUID id, UUID userId);

    Optional<Document> findByUserIdAndSha256(UUID userId, String sha256);

    /** Timeline order: most recent record date first, undated documents last, then newest upload. */
    @Query("""
            SELECT d FROM Document d
            WHERE d.userId = :userId
            ORDER BY d.docDate DESC NULLS LAST, d.createdAt DESC
            """)
    Page<Document> findTimeline(@Param("userId") UUID userId, Pageable pageable);

    long countByUserId(UUID userId);
}
