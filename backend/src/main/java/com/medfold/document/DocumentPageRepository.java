package com.medfold.document;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Owner-scoped like DocumentRepository: no findById or findAll. */
public interface DocumentPageRepository extends Repository<DocumentPage, UUID> {

    <S extends DocumentPage> List<S> saveAll(Iterable<S> pages);

    List<DocumentPage> findByDocumentIdAndUserIdOrderByPageNumber(UUID documentId, UUID userId);

    /** Used when a document is reprocessed: old results are replaced, never mixed with new ones. */
    @Modifying
    @Query("DELETE FROM DocumentPage p WHERE p.documentId = :documentId")
    int deleteByDocumentId(@Param("documentId") UUID documentId);
}
