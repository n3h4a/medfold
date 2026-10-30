package com.medfold.records;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.medfold.document.Document;
import com.medfold.document.DocumentPage;
import com.medfold.document.DocumentPageRepository;
import com.medfold.document.DocumentRepository;
import com.medfold.document.DocumentTerm;
import com.medfold.document.DocumentTermRepository;
import com.medfold.document.TextSource;
import com.medfold.processing.ExtractionRun;
import com.medfold.processing.ExtractionRunRepository;
import com.medfold.processing.JobStatus;
import com.medfold.processing.ProcessingJob;
import com.medfold.processing.ProcessingJobRepository;
import com.medfold.support.TestcontainersConfiguration;
import com.medfold.user.User;
import com.medfold.user.UserRepository;

import jakarta.persistence.EntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class ExtractionResultsRepositoryTest {

    @Autowired private UserRepository userRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private DocumentPageRepository pageRepository;
    @Autowired private DocumentTermRepository termRepository;
    @Autowired private LabResultRepository labRepository;
    @Autowired private MedicationRepository medicationRepository;
    @Autowired private ProcessingJobRepository jobRepository;
    @Autowired private ExtractionRunRepository runRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    private UUID alice;
    private UUID bob;
    private UUID document;

    @BeforeEach
    void setUp() {
        alice = userRepository.saveAndFlush(new User("alice-" + UUID.randomUUID() + "@example.com", "h", null)).getId();
        bob = userRepository.saveAndFlush(new User("bob-" + UUID.randomUUID() + "@example.com", "h", null)).getId();
        document = newDocument(alice);
    }

    private UUID newDocument(UUID owner) {
        String sha = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        return documentRepository.saveAndFlush(new Document(owner, "cbc.pdf", owner + "/" + UUID.randomUUID() + ".pdf",
                "application/pdf", 100, sha)).getId();
    }

    private LabResult lab(UUID doc, int position, String key, LocalDate observedOn) {
        return new LabResult(doc, alice, position, key.toUpperCase(), key, "6.1", "%", "4.0 - 5.6", 1, observedOn);
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table + " WHERE document_id = ?", Integer.class, document);
    }

    @Test
    void storesAndReadsBackResultsInOrderForTheOwnerOnly() {
        pageRepository.saveAll(List.of(
                new DocumentPage(document, alice, 2, "page two", TextSource.TESSERACT, 0.91f, 8),
                new DocumentPage(document, alice, 1, "page one", TextSource.PDF_TEXT, null, 7)));
        labRepository.saveAll(List.of(lab(document, 1, "wbc", null), lab(document, 0, "hba1c", null)));
        medicationRepository.saveAll(List.of(new Medication(document, alice, 0, "Metformin", "500 mg", "1-0-1", null, "30 days", "after food", null)));
        termRepository.saveAll(List.of(new DocumentTerm(document, alice, 0, "HbA1c", "A blood test.")));
        entityManager.flush();
        entityManager.clear();

        List<DocumentPage> pages = pageRepository.findByDocumentIdAndUserIdOrderByPageNumber(document, alice);
        assertThat(pages).extracting(DocumentPage::getPageNumber).containsExactly(1, 2);
        assertThat(pages.get(1).getConfidence()).isEqualTo(0.91f);
        assertThat(pages.get(1).getSource()).isEqualTo(TextSource.TESSERACT);
        assertThat(labRepository.findByDocumentIdAndUserIdOrderByPosition(document, alice))
                .extracting(LabResult::getTestKey).containsExactly("hba1c", "wbc");
        assertThat(medicationRepository.findByDocumentIdAndUserIdOrderByPosition(document, alice)).hasSize(1);
        assertThat(termRepository.findByDocumentIdAndUserIdOrderByPosition(document, alice)).hasSize(1);

        assertThat(pageRepository.findByDocumentIdAndUserIdOrderByPageNumber(document, bob)).isEmpty();
        assertThat(labRepository.findByDocumentIdAndUserIdOrderByPosition(document, bob)).isEmpty();
        assertThat(medicationRepository.findByDocumentIdAndUserIdOrderByPosition(document, bob)).isEmpty();
        assertThat(termRepository.findByDocumentIdAndUserIdOrderByPosition(document, bob)).isEmpty();
    }

    @Test
    void storesComparisonNumbersAndFlag() {
        LabResult result = lab(document, 0, "hba1c", LocalDate.of(2025, 3, 14));
        result.applyComparison(new BigDecimal("6.1"), new BigDecimal("4.0"), new BigDecimal("5.6"), LabFlag.HIGH);
        UUID id = labRepository.save(result).getId();
        entityManager.flush();
        entityManager.clear();

        LabResult reloaded = labRepository.findByIdAndUserId(id, alice).orElseThrow();
        assertThat(reloaded.getValueNumeric()).isEqualByComparingTo("6.1");
        assertThat(reloaded.getRefHigh()).isEqualByComparingTo("5.6");
        assertThat(reloaded.getFlag()).isEqualTo(LabFlag.HIGH);
        assertThat(labRepository.findByIdAndUserId(id, bob)).isEmpty();
    }

    @Test
    void trendsLineUpOneTestAcrossDocumentsOldestFirst() {
        UUID later = newDocument(alice);
        labRepository.saveAll(List.of(
                lab(later, 0, "hba1c", LocalDate.of(2025, 9, 1)),
                lab(document, 0, "hba1c", LocalDate.of(2024, 1, 1)),
                lab(document, 1, "wbc", LocalDate.of(2024, 1, 1))));
        entityManager.flush();

        assertThat(labRepository.findByUserIdAndTestKeyOrderByObservedOnAsc(alice, "hba1c"))
                .extracting(LabResult::getObservedOn)
                .containsExactly(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 9, 1));
        assertThat(labRepository.findByUserIdAndTestKeyOrderByObservedOnAsc(bob, "hba1c")).isEmpty();
    }

    @Test
    void reprocessingCanReplaceAllResultsOfADocument() {
        UUID other = newDocument(alice);
        labRepository.saveAll(List.of(lab(document, 0, "hba1c", null), lab(other, 0, "hba1c", null)));
        pageRepository.saveAll(List.of(new DocumentPage(document, alice, 1, "x", TextSource.PDF_TEXT, null, 1)));
        entityManager.flush();

        assertThat(labRepository.deleteByDocumentId(document)).isEqualTo(1);
        assertThat(pageRepository.deleteByDocumentId(document)).isEqualTo(1);
        assertThat(labRepository.findByDocumentIdAndUserIdOrderByPosition(other, alice)).hasSize(1);
    }

    @Test
    void deletingTheDocumentRemovesEverythingAttachedToIt() {
        pageRepository.saveAll(List.of(new DocumentPage(document, alice, 1, "x", TextSource.PDF_TEXT, null, 1)));
        labRepository.saveAll(List.of(lab(document, 0, "hba1c", null)));
        medicationRepository.saveAll(List.of(new Medication(document, alice, 0, "Metformin", null, null, null, null, null, null)));
        termRepository.saveAll(List.of(new DocumentTerm(document, alice, 0, "CBC", "A blood test.")));
        jobRepository.saveAndFlush(new ProcessingJob(document, 3, Instant.now()));
        runRepository.save(new ExtractionRun(document, alice, "PDF_TEXT", "mock", "m", "extraction-v1", 1, false, null, null, null, 5, "{}"));
        entityManager.flush();
        entityManager.clear();

        jdbcTemplate.update("DELETE FROM documents WHERE id = ?", document);

        for (String table : List.of("document_pages", "lab_results", "medications", "document_terms", "processing_jobs", "extraction_runs")) {
            assertThat(count(table)).as(table).isZero();
        }
    }

    @Test
    void aDocumentCanHaveOnlyOneActiveJob() {
        jobRepository.saveAndFlush(new ProcessingJob(document, 3, Instant.now()));

        assertThatThrownBy(() -> jobRepository.saveAndFlush(new ProcessingJob(document, 3, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void finishedJobsDontBlockANewOne() {
        ProcessingJob done = new ProcessingJob(document, 3, Instant.now());
        done.start(Instant.now());
        done.complete();
        jobRepository.saveAndFlush(done);

        jobRepository.saveAndFlush(new ProcessingJob(document, 3, Instant.now()));

        assertThat(jobRepository.countByDocumentId(document)).isEqualTo(2);
        assertThat(jobRepository.findFirstByDocumentIdAndStatusIn(document, List.of(JobStatus.PENDING, JobStatus.RUNNING))).isPresent();
    }

    @Test
    void extractionRunsKeepTheAuditTrail() {
        runRepository.save(new ExtractionRun(document, alice, "TESSERACT", "anthropic", "claude-opus-5-5", "extraction-v1",
                2, true, "summary: ADVICE", 1200, 300, 4100, "{\"docType\":\"LAB_REPORT\"}"));
        entityManager.flush();
        entityManager.clear();

        ExtractionRun run = runRepository.findFirstByDocumentIdAndUserIdOrderByCreatedAtDesc(document, alice).orElseThrow();
        assertThat(run.getLlmModel()).isEqualTo("claude-opus-5-5");
        assertThat(run.isSafetyFiltered()).isTrue();
        assertThat(run.getRawOutput()).contains("LAB_REPORT");
        assertThat(runRepository.findFirstByDocumentIdAndUserIdOrderByCreatedAtDesc(document, bob)).isEmpty();
    }

    // One rejected statement per test: PostgreSQL aborts the rest of the transaction after an error.
    @Test
    void databaseRejectsDuplicatePageNumbers() {
        pageRepository.saveAll(List.of(new DocumentPage(document, alice, 1, "a", TextSource.PDF_TEXT, null, 1)));
        entityManager.flush();

        assertThatThrownBy(() -> {
            pageRepository.saveAll(List.of(new DocumentPage(document, alice, 1, "b", TextSource.PDF_TEXT, null, 1)));
            entityManager.flush();
        }).hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
    }

    @Test
    void databaseRejectsUnknownFlag() {
        UUID id = labRepository.save(lab(document, 0, "hba1c", null)).getId();
        entityManager.flush();

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE lab_results SET flag = 'CRITICAL' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsConfidenceOutsideZeroToOne() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO document_pages (document_id, user_id, page_number, source, confidence) VALUES (?, ?, 1, 'TESSERACT', 1.5)",
                document, alice)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
