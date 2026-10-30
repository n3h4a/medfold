package com.medfold.processing;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medfold.ai.ExtractionResult;
import com.medfold.ai.OcrResult;
import com.medfold.document.Document;
import com.medfold.document.DocumentPage;
import com.medfold.document.DocumentPageRepository;
import com.medfold.document.DocumentRepository;
import com.medfold.document.DocumentTerm;
import com.medfold.document.DocumentTermRepository;
import com.medfold.document.TextSource;
import com.medfold.records.LabFlagCalculator;
import com.medfold.records.LabResult;
import com.medfold.records.LabResultRepository;
import com.medfold.records.Medication;
import com.medfold.records.MedicationRepository;

/**
 * Saves the outcome of a processing run in one transaction. A new run replaces the previous run's
 * pages, lab results, medicines and terms, so results from two runs are never mixed. Only rows the
 * user corrected are kept.
 */
@Service
public class ProcessingResultWriter {

    private final DocumentRepository documentRepository;
    private final DocumentPageRepository pageRepository;
    private final LabResultRepository labResultRepository;
    private final MedicationRepository medicationRepository;
    private final DocumentTermRepository termRepository;
    private final ExtractionRunRepository runRepository;

    public ProcessingResultWriter(DocumentRepository documentRepository, DocumentPageRepository pageRepository,
            LabResultRepository labResultRepository, MedicationRepository medicationRepository,
            DocumentTermRepository termRepository, ExtractionRunRepository runRepository) {
        this.documentRepository = documentRepository;
        this.pageRepository = pageRepository;
        this.labResultRepository = labResultRepository;
        this.medicationRepository = medicationRepository;
        this.termRepository = termRepository;
        this.runRepository = runRepository;
    }

    /**
     * Stores everything a successful run produced.
     *
     * Metadata the user has edited (document.user_edited) is kept; everything else is replaced.
     *
     * @return false if the document was deleted while it was being processed (nothing is saved)
     */
    @Transactional
    public boolean save(UUID documentId, UUID userId, OcrResult ocr, ExtractionResult result) {
        Optional<Document> found = documentRepository.findByIdAndUserId(documentId, userId);
        if (found.isEmpty()) {
            return false;
        }
        Document document = found.get();
        ExtractionResult.Extraction extraction = result.extraction();

        replacePages(document, ocr);
        labResultRepository.deleteExtractedByDocumentId(documentId);
        medicationRepository.deleteExtractedByDocumentId(documentId);
        termRepository.deleteByDocumentId(documentId);
        // Rows the user corrected stay; a new reading of the same test or medicine doesn't duplicate them.
        Set<String> correctedTests = labResultRepository.findByDocumentIdAndUserIdOrderByPosition(documentId, userId)
                .stream().map(LabResult::getTestKey).collect(Collectors.toSet());
        Set<String> correctedMedicines = medicationRepository.findByDocumentIdAndUserIdOrderByPosition(documentId, userId)
                .stream().map(m -> m.getName().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());

        if (!document.isUserEdited()) {
            document.setDocType(extraction.docType());
            document.setDocDate(extraction.docDate());
            document.setTitle(extraction.title());
            document.setProviderName(extraction.providerName());
            document.setFacilityName(extraction.facilityName());
            document.setSummary(extraction.summary());
        }
        // Results are dated by the record itself; an edited date wins over the extracted one.
        LocalDate recordDate = document.getDocDate();

        List<LabResult> labs = new ArrayList<>();
        for (ExtractionResult.Lab lab : extraction.labResults()) {
            if (correctedTests.contains(lab.testKey())) {
                continue;
            }
            LabResult row = new LabResult(documentId, userId, labs.size(), lab.testName(), lab.testKey(),
                    lab.valueText(), lab.unit(), lab.referenceRangeText(), lab.pageNumber(), recordDate);
            LabFlagCalculator.apply(row);
            labs.add(row);
        }
        labResultRepository.saveAll(labs);

        List<Medication> medicines = new ArrayList<>();
        for (ExtractionResult.Medicine m : extraction.medications()) {
            if (correctedMedicines.contains(m.name().toLowerCase(Locale.ROOT))) {
                continue;
            }
            medicines.add(new Medication(documentId, userId, medicines.size(), m.name(), m.dosage(), m.frequency(),
                    m.route(), m.duration(), m.instructions(), recordDate));
        }
        medicationRepository.saveAll(medicines);

        List<DocumentTerm> terms = new ArrayList<>();
        for (ExtractionResult.Term t : extraction.termsExplained()) {
            terms.add(new DocumentTerm(documentId, userId, terms.size(), t.term(), t.definition()));
        }
        termRepository.saveAll(terms);

        ExtractionResult.Meta meta = result.meta();
        runRepository.save(new ExtractionRun(documentId, userId, ocr.ocrProvider(), meta.provider(), meta.model(),
                meta.promptVersion(), meta.attempts(), meta.safetyFiltered(),
                meta.safetyIssues().isEmpty() ? null : String.join("\n", meta.safetyIssues()),
                meta.inputTokens(), meta.outputTokens(), meta.durationMs(), result.rawJson()));
        return true;
    }

    /** OCR ran but found no text: keep the page records and count, clear stale extraction results. */
    @Transactional
    public void saveOcrOnly(UUID documentId, UUID userId, OcrResult ocr) {
        documentRepository.findByIdAndUserId(documentId, userId).ifPresent(document -> {
            replacePages(document, ocr);
            labResultRepository.deleteExtractedByDocumentId(documentId);
            medicationRepository.deleteExtractedByDocumentId(documentId);
            termRepository.deleteByDocumentId(documentId);
        });
    }

    private void replacePages(Document document, OcrResult ocr) {
        pageRepository.deleteByDocumentId(document.getId());
        List<DocumentPage> pages = ocr.pages().stream()
                .map(p -> new DocumentPage(document.getId(), document.getUserId(), p.pageNumber(), p.text(),
                        p.source() == null ? TextSource.NONE : p.source(), p.confidence(), p.charCount()))
                .toList();
        pageRepository.saveAll(pages);
        document.setPageCount(ocr.pageCount());
    }
}
