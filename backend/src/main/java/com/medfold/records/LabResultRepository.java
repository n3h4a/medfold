package com.medfold.records;

import java.util.List;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Owner-scoped: every read names the user. */
public interface LabResultRepository extends Repository<LabResult, UUID> {

    <S extends LabResult> List<S> saveAll(Iterable<S> results);

    LabResult save(LabResult result);

    Optional<LabResult> findByIdAndUserId(UUID id, UUID userId);

    List<LabResult> findByDocumentIdAndUserIdOrderByPosition(UUID documentId, UUID userId);

    /** One test across all of a user's reports, oldest first, for trends. */
    List<LabResult> findByUserIdAndTestKeyOrderByObservedOnAsc(UUID userId, String testKey);

    /** All of a user's results grouped by test, each group oldest first so its last row is the latest. */
    @Query("""
            SELECT r FROM LabResult r
            WHERE r.userId = :userId
            ORDER BY r.testKey, r.observedOn ASC NULLS FIRST, r.position
            """)
    List<LabResult> findAllForUserByTest(@Param("userId") UUID userId);

    void delete(LabResult entity);

    /** Results are dated by their document; follows a corrected document date. */
    @Modifying
    @Query("UPDATE LabResult r SET r.observedOn = :date WHERE r.documentId = :documentId AND r.userId = :userId")
    int redate(@Param("documentId") UUID documentId, @Param("userId") UUID userId, @Param("date") LocalDate date);

    /** Reprocessing replaces extracted rows but keeps the ones the user corrected. */
    @Modifying
    @Query("DELETE FROM LabResult r WHERE r.documentId = :documentId AND r.userEdited = false")
    int deleteExtractedByDocumentId(@Param("documentId") UUID documentId);

    @Modifying
    @Query("DELETE FROM LabResult r WHERE r.documentId = :documentId")
    int deleteByDocumentId(@Param("documentId") UUID documentId);
}
