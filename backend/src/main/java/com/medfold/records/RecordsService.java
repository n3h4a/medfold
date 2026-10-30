package com.medfold.records;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medfold.common.ApiException;
import com.medfold.document.DocumentRepository;
import com.medfold.document.DocumentTermRepository;
import com.medfold.document.Edits;
import com.medfold.records.dto.DocumentResultsResponse;
import com.medfold.records.dto.LabResultResponse;
import com.medfold.records.dto.LabResultUpdateRequest;
import com.medfold.records.dto.MedicationUpdateRequest;
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

    /** Corrects a lab result and recomputes its flag. The correction survives reprocessing. */
    @Transactional
    public LabResultResponse updateLabResult(UUID userId, UUID id, LabResultUpdateRequest request) {
        LabResult result = ownedLabResult(userId, id);
        String testName = Edits.apply(result.getTestName(), request.testName());
        String valueText = Edits.apply(result.getValueText(), request.valueText());
        String unit = Edits.apply(result.getUnit(), request.unit());
        String refText = Edits.apply(result.getRefText(), request.refText());
        if (!Objects.equals(testName, result.getTestName()) || !Objects.equals(valueText, result.getValueText())
                || !Objects.equals(unit, result.getUnit()) || !Objects.equals(refText, result.getRefText())) {
            result.edit(testName, valueText, unit, refText);
            LabFlagCalculator.apply(result);
        }
        return LabResultResponse.from(result);
    }

    /** Removes a row the reader got wrong (e.g. a line that isn't a test result). */
    @Transactional
    public void deleteLabResult(UUID userId, UUID id) {
        labResultRepository.delete(ownedLabResult(userId, id));
    }

    @Transactional
    public MedicationResponse updateMedication(UUID userId, UUID id, MedicationUpdateRequest request) {
        Medication m = ownedMedication(userId, id);
        String name = Edits.apply(m.getName(), request.name());
        String dosage = Edits.apply(m.getDosage(), request.dosage());
        String frequency = Edits.apply(m.getFrequency(), request.frequency());
        String route = Edits.apply(m.getRoute(), request.route());
        String duration = Edits.apply(m.getDuration(), request.duration());
        String instructions = Edits.apply(m.getInstructions(), request.instructions());
        if (!Objects.equals(name, m.getName()) || !Objects.equals(dosage, m.getDosage())
                || !Objects.equals(frequency, m.getFrequency()) || !Objects.equals(route, m.getRoute())
                || !Objects.equals(duration, m.getDuration()) || !Objects.equals(instructions, m.getInstructions())) {
            m.edit(name, dosage, frequency, route, duration, instructions);
        }
        return MedicationResponse.from(m);
    }

    @Transactional
    public void deleteMedication(UUID userId, UUID id) {
        medicationRepository.delete(ownedMedication(userId, id));
    }

    private LabResult ownedLabResult(UUID userId, UUID id) {
        return labResultRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LAB_RESULT_NOT_FOUND", "Lab result not found"));
    }

    private Medication ownedMedication(UUID userId, UUID id) {
        return medicationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEDICATION_NOT_FOUND", "Medicine not found"));
    }

    /** Same 404 as DocumentService for a missing or someone else's document. */
    private void requireOwnedDocument(UUID userId, UUID documentId) {
        if (documentRepository.findByIdAndUserId(documentId, userId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Document not found");
        }
    }
}
