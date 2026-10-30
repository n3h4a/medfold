package com.medfold.records;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medfold.common.ApiException;
import com.medfold.document.DocumentRepository;
import com.medfold.document.DocumentTermRepository;
import com.medfold.records.dto.DocumentResultsResponse;
import com.medfold.records.dto.LabResultResponse;
import com.medfold.records.dto.LabTestSummary;
import com.medfold.records.dto.LabTrendResponse;
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

    /** Every test the user has results for, alphabetically, with the latest value of each. */
    @Transactional(readOnly = true)
    public List<LabTestSummary> labTests(UUID userId) {
        Map<String, List<LabResult>> byTest = new LinkedHashMap<>();
        for (LabResult r : labResultRepository.findAllForUserByTest(userId)) {
            byTest.computeIfAbsent(r.getTestKey(), key -> new ArrayList<>()).add(r);
        }
        return byTest.entrySet().stream()
                .map(e -> {
                    LabResult latest = e.getValue().getLast();
                    return new LabTestSummary(e.getKey(), latest.getTestName(), latest.getUnit(), e.getValue().size(),
                            latest.getValueText(), latest.getFlag(), latest.getObservedOn(), latest.getDocumentId());
                })
                .sorted(Comparator.comparing(LabTestSummary::testName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** One test over time. 404 if the user has no result for it. */
    @Transactional(readOnly = true)
    public LabTrendResponse labTrend(UUID userId, String testKey) {
        List<LabResult> results = labResultRepository.findByUserIdAndTestKeyOrderByObservedOnAsc(userId, testKey);
        if (results.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "LAB_TEST_NOT_FOUND", "No results found for this test");
        }
        // Name it as printed on the most recent dated report.
        String name = results.stream().filter(r -> r.getObservedOn() != null).reduce((a, b) -> b)
                .orElse(results.getLast()).getTestName();
        return new LabTrendResponse(testKey, name, results.stream().map(LabResultResponse::from).toList());
    }

    /** All of the user's medicines, most recently prescribed first. */
    @Transactional(readOnly = true)
    public List<MedicationResponse> medications(UUID userId) {
        return medicationRepository.findAllForUser(userId).stream().map(MedicationResponse::from).toList();
    }

    /** Same 404 as DocumentService for a missing or someone else's document. */
    private void requireOwnedDocument(UUID userId, UUID documentId) {
        if (documentRepository.findByIdAndUserId(documentId, userId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Document not found");
        }
    }
}
