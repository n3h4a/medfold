package com.medfold.records;

import java.util.List;
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

    @Modifying
    @Query("DELETE FROM Medication m WHERE m.documentId = :documentId")
    int deleteByDocumentId(@Param("documentId") UUID documentId);
}
