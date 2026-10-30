package com.medfold.records;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medfold.common.ApiException;
import com.medfold.document.DocumentRepository;
import com.medfold.document.DocumentTermRepository;
import com.medfold.records.dto.DocumentResultsResponse;
import com.medfold.records.dto.LabResultResponse;
import com.medfold.records.dto.MedicationResponse;
import com.medfold.records.dto.TermResponse;

/** Read access to extracted medical records. Every query is scoped to the calling user. */
@Service
public class RecordsService {

    private final DocumentRepository documentRepository;
    private final LabResultRepository labResultRepository;
    private final MedicationRepository medicationRepository;
    private final DocumentTermRepository termRepository;

    public RecordsService(DocumentRepository documentRepository, LabResultRepository labResultRepository,
            MedicationRepository medicationRepository, DocumentTermRepository termRepository) {
        this.documentRepository = documentRepository;
        this.labResultRepository = labResultRepository;
        this.medicationRepository = medicationRepository;
        this.termRepository = termRepository;
    }

    @Transactional(readOnly = true)
    public DocumentResultsResponse forDocument(UUID userId, UUID documentId) {
        requireOwnedDocument(userId, documentId);
        return new DocumentResultsResponse(
                labResultRepository.findByDocumentIdAndUserIdOrderByPosition(documentId, userId).stream()
                        .map(LabResultResponse::from).toList(),
                medicationRepository.findByDocumentIdAndUserIdOrderByPosition(documentId, userId).stream()
                        .map(MedicationResponse::from).toList(),
                termRepository.findByDocumentIdAndUserIdOrderByPosition(documentId, userId).stream()
                        .map(TermResponse::from).toList());
    }

    /** Same 404 as DocumentService for a missing or someone else's document. */
    private void requireOwnedDocument(UUID userId, UUID documentId) {
        if (documentRepository.findByIdAndUserId(documentId, userId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Document not found");
        }
    }
}
