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
public interface MedicationRepository extends Repository<Medication, UUID> {

    <S extends Medication> List<S> saveAll(Iterable<S> medications);

    Optional<Medication> findByIdAndUserId(UUID id, UUID userId);

    List<Medication> findByDocumentIdAndUserIdOrderByPosition(UUID documentId, UUID userId);

    /** All of a user's medicines, most recently prescribed first (undated last). */
    @Query("""
            SELECT m FROM Medication m
            WHERE m.userId = :userId
            ORDER BY m.prescribedOn DESC NULLS LAST, m.position
            """)
    List<Medication> findAllForUser(@Param("userId") UUID userId);

    void delete(Medication entity);

    /** Results are dated by their document; follows a corrected document date. */
    @Modifying
    @Query("UPDATE Medication m SET m.prescribedOn = :date WHERE m.documentId = :documentId AND m.userId = :userId")
    int redate(@Param("documentId") UUID documentId, @Param("userId") UUID userId, @Param("date") LocalDate date);

    /** Reprocessing replaces extracted rows but keeps the ones the user corrected. */
    @Modifying
    @Query("DELETE FROM Medication m WHERE m.documentId = :documentId AND m.userEdited = false")
    int deleteExtractedByDocumentId(@Param("documentId") UUID documentId);

    @Modifying
    @Query("DELETE FROM Medication m WHERE m.documentId = :documentId")
    int deleteByDocumentId(@Param("documentId") UUID documentId);
}
