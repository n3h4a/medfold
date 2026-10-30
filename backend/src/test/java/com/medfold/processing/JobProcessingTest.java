package com.medfold.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;
import com.medfold.auth.JwtService;
import com.medfold.document.Document;
import com.medfold.document.DocumentRepository;
import com.medfold.document.DocumentStatus;
import com.medfold.support.TestcontainersConfiguration;
import com.medfold.user.User;
import com.medfold.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, JobProcessingTest.ScriptedProcessorConfig.class })
class JobProcessingTest {

    /** Replaces the real processor; each test scripts what it does. */
    static class ScriptedProcessor implements DocumentProcessor {
        volatile Consumer<Document> behaviour = document -> { };
        final List<DocumentStatus> statusesSeen = new ArrayList<>();

        @Override
        public void process(Document document) {
            statusesSeen.add(document.getStatus());
            behaviour.accept(document);
        }
    }

    @TestConfiguration
    static class ScriptedProcessorConfig {
        @Bean
        @Primary
        ScriptedProcessor scriptedProcessor() {
            return new ScriptedProcessor();
        }
    }

    @Autowired private ScriptedProcessor processor;
    @Autowired private JobRunner runner;
    @Autowired private JobQueue queue;
    @Autowired private ProcessingJobRepository jobRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactions;
    @Autowired private MockMvc mockMvc;
    @Autowired private ApplicationContext context;

    private User user;

    @BeforeEach
    void setUp() {
        // Tests share one database: retire jobs left by other test classes so they aren't claimed here.
        jdbcTemplate.update("UPDATE processing_jobs SET status = 'DONE', locked_at = NULL WHERE status IN ('PENDING', 'RUNNING')");
        processor.behaviour = document -> { };
        processor.statusesSeen.clear();
        user = userRepository.saveAndFlush(new User("worker-" + UUID.randomUUID() + "@example.com", "h", null));
    }

    private UUID newQueuedDocument() {
        String sha = (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
        return transactions.execute(status -> {
            Document document = documentRepository.saveAndFlush(new Document(user.getId(), "report.pdf",
                    user.getId() + "/" + UUID.randomUUID() + ".pdf", "application/pdf", 100, sha));
            queue.enqueue(document.getId(), user.getId());
            return document.getId();
        });
    }

    private ProcessingJob jobOf(UUID documentId) {
        return jobRepository.findAll().stream().filter(j -> j.getDocumentId().equals(documentId))
                .max((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt())).orElseThrow();
    }

    private Document documentOf(UUID documentId) {
        return documentRepository.findByIdAndUserId(documentId, user.getId()).orElseThrow();
    }

    private void makeDue(UUID documentId) {
        jdbcTemplate.update("UPDATE processing_jobs SET next_run_at = now() - interval '1 second' WHERE document_id = ? AND status = 'PENDING'", documentId);
    }

    @Test
    void uploadingQueuesExactlyOneJobForTheOwner() throws Exception {
        String token = jwtService.issueAccessToken(user).value();
        byte[] pdf = ("%PDF-1.4\n% " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        String body = mockMvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", "cbc.pdf", "application/pdf", pdf))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID documentId = UUID.fromString(JsonPath.read(body, "$.id"));

        ProcessingJob job = jobOf(documentId);
        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getUserId()).isEqualTo(user.getId());
        assertThat(job.getAttempts()).isZero();
        assertThat(job.getMaxAttempts()).isEqualTo(3);
        assertThat(queue.enqueue(documentId, user.getId())).as("second enqueue is a no-op").isFalse();
        assertThat(jobRepository.countByDocumentId(documentId)).isEqualTo(1);
    }

    @Test
    void claimsOnlyDueJobsAndNeverTwice() {
        List<UUID> due = List.of(newQueuedDocument(), newQueuedDocument(), newQueuedDocument());
        UUID later = newQueuedDocument();
        jdbcTemplate.update("UPDATE processing_jobs SET next_run_at = now() + interval '1 hour' WHERE document_id = ?", later);

        List<UUID> claimed = queue.claimDue(10);

        assertThat(claimed).hasSize(3);
        assertThat(claimed.stream().map(id -> jobRepository.findById(id).orElseThrow().getDocumentId()))
                .containsExactlyInAnyOrderElementsOf(due);
        for (UUID id : claimed) {
            ProcessingJob job = jobRepository.findById(id).orElseThrow();
            assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
            assertThat(job.getAttempts()).isEqualTo(1);
            assertThat(job.getLockedAt()).isNotNull();
        }
        assertThat(queue.claimDue(10)).isEmpty();
    }

    @Test
    void concurrentWorkersSkipEachOthersJobsWithoutWaiting() throws Exception {
        for (int i = 0; i < 3; i++) {
            newQueuedDocument();
        }
        CountDownLatch firstClaimed = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        // Worker A claims two jobs and keeps its transaction (and row locks) open.
        CompletableFuture<List<UUID>> workerA = CompletableFuture.supplyAsync(() -> transactions.execute(status -> {
            List<UUID> ids = queue.claimDue(2);
            firstClaimed.countDown();
            try {
                releaseFirst.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return ids;
        }));
        assertThat(firstClaimed.await(10, TimeUnit.SECONDS)).isTrue();

        // Worker B must not block on A's locks and must get only the remaining job.
        List<UUID> b = CompletableFuture.supplyAsync(() -> queue.claimDue(2)).get(5, TimeUnit.SECONDS);
        releaseFirst.countDown();
        List<UUID> a = workerA.get(10, TimeUnit.SECONDS);

        assertThat(a).hasSize(2);
        assertThat(b).hasSize(1);
        assertThat(new HashSet<>(a)).doesNotContainAnyElementsOf(b);
    }

    @Test
    void successfulProcessingMarksTheDocumentReady() {
        UUID documentId = newQueuedDocument();

        assertThat(runner.runDue()).isEqualTo(1);

        assertThat(processor.statusesSeen).containsExactly(DocumentStatus.PROCESSING);
        assertThat(documentOf(documentId).getStatus()).isEqualTo(DocumentStatus.READY);
        ProcessingJob job = jobOf(documentId);
        assertThat(job.getStatus()).isEqualTo(JobStatus.DONE);
        assertThat(job.getLockedAt()).isNull();
    }

    @Test
    void temporaryFailuresRetryWithGrowingDelaysThenFailWithAReadableReason() {
        UUID documentId = newQueuedDocument();
        processor.behaviour = d -> {
            throw new ProcessingFailure("AI service unavailable (503)", true, "The AI service was unavailable. Please try again later");
        };

        Instant before = Instant.now();
        runner.runDue();
        ProcessingJob afterFirst = jobOf(documentId);
        assertThat(afterFirst.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(afterFirst.getNextRunAt()).isBetween(before.plusSeconds(29), Instant.now().plusSeconds(31));
        assertThat(afterFirst.getLastError()).contains("503");
        assertThat(documentOf(documentId).getStatus()).isEqualTo(DocumentStatus.PROCESSING);
        assertThat(runner.runDue()).as("not due yet").isZero();

        makeDue(documentId);
        runner.runDue();
        assertThat(jobOf(documentId).getNextRunAt()).isAfter(Instant.now().plusSeconds(55));

        makeDue(documentId);
        runner.runDue();
        ProcessingJob last = jobOf(documentId);
        assertThat(last.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(last.getAttempts()).isEqualTo(3);
        Document document = documentOf(documentId);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(document.getFailureReason()).isEqualTo("The AI service was unavailable. Please try again later");
    }

    @Test
    void permanentFailuresStopImmediately() {
        UUID documentId = newQueuedDocument();
        processor.behaviour = d -> {
            throw new ProcessingFailure("PDF_ENCRYPTED", false, "This PDF is password-protected. Remove the password and upload it again");
        };

        runner.runDue();

        assertThat(jobOf(documentId).getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(jobOf(documentId).getAttempts()).isEqualTo(1);
        assertThat(documentOf(documentId).getFailureReason()).startsWith("This PDF is password-protected");
    }

    @Test
    void unexpectedErrorsAreRetriedAndNeverShownToTheUser() {
        UUID documentId = newQueuedDocument();
        jdbcTemplate.update("UPDATE processing_jobs SET max_attempts = 1 WHERE document_id = ?", documentId);
        processor.behaviour = d -> {
            throw new IllegalStateException("NullPointer deep in some library at line 42");
        };

        runner.runDue();

        Document document = documentOf(documentId);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(document.getFailureReason()).isEqualTo(JobRunner.GENERIC_FAILURE).doesNotContain("NullPointer");
        assertThat(jobOf(documentId).getLastError()).contains("NullPointer");
    }

    @Test
    void jobsAbandonedByACrashedWorkerAreRecovered() {
        UUID documentId = newQueuedDocument();
        queue.claimDue(1);
        jdbcTemplate.update("UPDATE processing_jobs SET locked_at = now() - interval '20 minutes' WHERE document_id = ?", documentId);
        jdbcTemplate.update("UPDATE documents SET status = 'PROCESSING' WHERE id = ?", documentId);

        runner.runDue();

        ProcessingJob job = jobOf(documentId);
        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getLastError()).isEqualTo(JobRunner.INTERRUPTED);
        assertThat(job.getLockedAt()).isNull();

        makeDue(documentId);
        runner.runDue();
        assertThat(documentOf(documentId).getStatus()).isEqualTo(DocumentStatus.READY);
    }

    @Test
    void aDocumentDeletedDuringProcessingIsHandledQuietly() {
        UUID documentId = newQueuedDocument();
        processor.behaviour = d -> jdbcTemplate.update("DELETE FROM documents WHERE id = ?", d.getId());

        assertThat(runner.runDue()).isEqualTo(1);

        assertThat(jobRepository.countByDocumentId(documentId)).isZero();
    }

    @Test
    void retryDelayDoublesAndIsCapped() {
        assertThat(runner.retryDelay(1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(runner.retryDelay(2)).isEqualTo(Duration.ofSeconds(60));
        assertThat(runner.retryDelay(3)).isEqualTo(Duration.ofSeconds(120));
        assertThat(runner.retryDelay(20)).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void schedulerDoesNotRunWhenProcessingIsDisabled() {
        assertThat(context.getBeansOfType(ProcessingScheduler.class)).isEmpty();
    }
}
