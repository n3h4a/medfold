package com.medfold.document;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface DocumentTermRepository extends Repository<DocumentTerm, UUID> {

    <S extends DocumentTerm> List<S> saveAll(Iterable<S> terms);

    List<DocumentTerm> findByDocumentIdAndUserIdOrderByPosition(UUID documentId, UUID userId);

    @Modifying
    @Query("DELETE FROM DocumentTerm t WHERE t.documentId = :documentId")
    int deleteByDocumentId(@Param("documentId") UUID documentId);
}
