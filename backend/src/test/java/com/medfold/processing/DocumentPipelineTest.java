package com.medfold.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;
import com.medfold.auth.JwtService;
import com.medfold.document.Document;
import com.medfold.document.DocumentPage;
import com.medfold.document.DocumentPageRepository;
import com.medfold.document.DocumentRepository;
import com.medfold.document.DocumentStatus;
import com.medfold.document.DocumentTermRepository;
import com.medfold.document.DocumentType;
import com.medfold.document.TextSource;
import com.medfold.records.LabFlag;
import com.medfold.records.LabResult;
import com.medfold.records.LabResultRepository;
import com.medfold.records.Medication;
import com.medfold.records.MedicationRepository;
import com.medfold.support.TestcontainersConfiguration;
import com.medfold.user.User;
import com.medfold.user.UserRepository;
import com.sun.net.httpserver.HttpServer;

/** Upload → worker → AI service (a local stub speaking the real wire format) → stored results. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DocumentPipelineTest {

    private record Reply(int status, String body) {
    }

    private static final HttpServer AI = startStub();
    private static final Map<String, Reply> replies = new ConcurrentHashMap<>();
    private static final List<String> calls = new CopyOnWriteArrayList<>();
    private static final List<String> ocrBodies = new CopyOnWriteArrayList<>();

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                byte[] request = exchange.getRequestBody().readAllBytes();
                calls.add(path);
                if (path.equals("/v1/ocr")) {
                    ocrBodies.add(new String(request, StandardCharsets.ISO_8859_1));
                }
                Reply reply = "secret".equals(exchange.getRequestHeaders().getFirst("X-Internal-Api-Key"))
                        ? replies.getOrDefault(path, new Reply(404, "{}"))
                        : new Reply(401, "{\"detail\":\"Invalid or missing internal API key\"}");
                byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(reply.status(), bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @DynamicPropertySource
    static void aiService(DynamicPropertyRegistry registry) {
        registry.add("medfold.ai.base-url", () -> "http://127.0.0.1:" + AI.getAddress().getPort());
        registry.add("medfold.ai.internal-key", () -> "secret");
    }

    @AfterAll
    static void stopStub() {
        AI.stop(0);
    }

    private static final String OCR_TWO_PAGES = """
            {"fileKind":"PDF","pageCount":2,"ocrProvider":"tesseract","pagesWithoutText":[2],
             "pages":[{"pageNumber":1,"text":"CITY LAB  Haemoglobin 13.5 g/dL 13.0 - 17.0","source":"TESSERACT","charCount":36,"confidence":0.9},
                      {"pageNumber":2,"text":"","source":"NONE","charCount":0,"confidence":null}]}
            """;

    private static final String OCR_NO_TEXT = """
            {"fileKind":"IMAGE","pageCount":1,"ocrProvider":"tesseract","pagesWithoutText":[1],
             "pages":[{"pageNumber":1,"text":"","source":"NONE","charCount":0,"confidence":null}]}
            """;

    private static String extraction(String title, String testName, String value) {
        return """
                {"extraction":{"docType":"LAB_REPORT","docDate":"2026-03-14","title":"%s","providerName":"Dr. A. Rao",
                   "facilityName":"City Lab","summary":"This report lists blood count results.",
                   "termsExplained":[{"term":"CBC","definition":"A common blood test."}],
                   "labResults":[{"testName":"%s","valueText":"%s","unit":"g/dL","referenceRangeText":"13.0 - 17.0","pageNumber":1,"testKey":"hemoglobin"}],
                   "medications":[{"name":"Tab. Ferrous sulfate 200 mg","dosage":"200 mg","frequency":"1-0-0","route":null,"duration":"30 days","instructions":"after food"}]},
                 "meta":{"provider":"mock","model":"medfold-rule-based-v1","promptVersion":"extraction-v1","attempts":2,
                   "durationMs":40,"inputTokens":1200,"outputTokens":300,"safetyFiltered":true,"safetyIssues":["summary: ADVICE"]}}
                """.formatted(title, testName, value);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private DocumentPageRepository pageRepository;
    @Autowired private LabResultRepository labRepository;
    @Autowired private MedicationRepository medicationRepository;
    @Autowired private DocumentTermRepository termRepository;
    @Autowired private ExtractionRunRepository runRepository;
    @Autowired private JobRunner runner;
    @Autowired private JobQueue queue;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactions;

    private User user;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("UPDATE processing_jobs SET status = 'DONE', locked_at = NULL WHERE status IN ('PENDING', 'RUNNING')");
        replies.clear();
        calls.clear();
        ocrBodies.clear();
        user = userRepository.saveAndFlush(new User("pipeline-" + UUID.randomUUID() + "@example.com", "h", null));
    }

    private UUID upload() throws Exception {
        byte[] pdf = ("%PDF-1.4\n% pipeline " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        String body = mockMvc.perform(multipart("/api/v1/documents")
                        .file(new MockMultipartFile("file", "cbc.pdf", "application/pdf", pdf))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueAccessToken(user).value()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private Document document(UUID id) {
        return documentRepository.findByIdAndUserId(id, user.getId()).orElseThrow();
    }

    private void reprocess(UUID documentId) {
        queue.enqueue(documentId, user.getId());
        runner.runDue();
    }

    @Test
    void anUploadIsReadExtractedAndStored() throws Exception {
        replies.put("/v1/ocr", new Reply(200, OCR_TWO_PAGES));
        replies.put("/v1/extract", new Reply(200, extraction("Complete Blood Count", "Haemoglobin", "13.5")));
        UUID id = upload();

        assertThat(runner.runDue()).isEqualTo(1);

        assertThat(calls).containsExactly("/v1/ocr", "/v1/extract");
        assertThat(ocrBodies.getFirst()).contains("filename=\"cbc.pdf\"", "%PDF-1.4\n% pipeline");

        Document document = document(id);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.READY);
        assertThat(document.getPageCount()).isEqualTo(2);
        assertThat(document.getDocType()).isEqualTo(DocumentType.LAB_REPORT);
        assertThat(document.getDocDate()).isEqualTo(LocalDate.of(2026, 3, 14));
        assertThat(document.getTitle()).isEqualTo("Complete Blood Count");
        assertThat(document.getProviderName()).isEqualTo("Dr. A. Rao");
        assertThat(document.getFacilityName()).isEqualTo("City Lab");
        assertThat(document.getSummary()).isEqualTo("This report lists blood count results.");

        List<DocumentPage> pages = pageRepository.findByDocumentIdAndUserIdOrderByPageNumber(id, user.getId());
        assertThat(pages).extracting(DocumentPage::getPageNumber).containsExactly(1, 2);
        assertThat(pages.getFirst().getSource()).isEqualTo(TextSource.TESSERACT);
        assertThat(pages.getFirst().getConfidence()).isEqualTo(0.9f);
        assertThat(pages.get(1).getSource()).isEqualTo(TextSource.NONE);

        List<LabResult> labs = labRepository.findByDocumentIdAndUserIdOrderByPosition(id, user.getId());
        assertThat(labs).singleElement().satisfies(lab -> {
            assertThat(lab.getTestName()).isEqualTo("Haemoglobin");
            assertThat(lab.getTestKey()).isEqualTo("hemoglobin");
            assertThat(lab.getValueText()).isEqualTo("13.5");
            assertThat(lab.getRefText()).isEqualTo("13.0 - 17.0");
            assertThat(lab.getObservedOn()).isEqualTo(LocalDate.of(2026, 3, 14));
            assertThat(lab.getPageNumber()).isEqualTo(1);
            assertThat(lab.getValueNumeric()).isEqualByComparingTo("13.5");
            assertThat(lab.getRefLow()).isEqualByComparingTo("13.0");
            assertThat(lab.getRefHigh()).isEqualByComparingTo("17.0");
            assertThat(lab.getFlag()).isEqualTo(LabFlag.NORMAL);
        });

        List<Medication> medicines = medicationRepository.findByDocumentIdAndUserIdOrderByPosition(id, user.getId());
        assertThat(medicines).singleElement().satisfies(m -> {
            assertThat(m.getName()).isEqualTo("Tab. Ferrous sulfate 200 mg");
            assertThat(m.getFrequency()).isEqualTo("1-0-0");
            assertThat(m.getRoute()).isNull();
            assertThat(m.getPrescribedOn()).isEqualTo(LocalDate.of(2026, 3, 14));
        });
        assertThat(termRepository.findByDocumentIdAndUserIdOrderByPosition(id, user.getId()))
                .singleElement().satisfies(t -> assertThat(t.getTerm()).isEqualTo("CBC"));

        ExtractionRun run = runRepository.findFirstByDocumentIdAndUserIdOrderByCreatedAtDesc(id, user.getId()).orElseThrow();
        assertThat(run.getOcrProvider()).isEqualTo("tesseract");
        assertThat(run.getLlmProvider()).isEqualTo("mock");
        assertThat(run.getPromptVersion()).isEqualTo("extraction-v1");
        assertThat(run.getAttempts()).isEqualTo(2);
        assertThat(run.isSafetyFiltered()).isTrue();
        assertThat(run.getSafetyIssues()).isEqualTo("summary: ADVICE");
        assertThat(run.getInputTokens()).isEqualTo(1200);
        assertThat(run.getRawOutput()).contains("\"promptVersion\":\"extraction-v1\"");
    }

    @Test
    void reprocessingReplacesResultsButKeepsWhatTheUserEdited() throws Exception {
        replies.put("/v1/ocr", new Reply(200, OCR_TWO_PAGES));
        replies.put("/v1/extract", new Reply(200, extraction("First title", "Haemoglobin", "13.5")));
        UUID id = upload();
        runner.runDue();
        jdbcTemplate.update("UPDATE documents SET title = 'My own title', user_edited = TRUE WHERE id = ?", id);

        replies.put("/v1/extract", new Reply(200, extraction("Second title", "Hb", "14.1")));
        reprocess(id);

        Document document = document(id);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.READY);
        assertThat(document.getTitle()).isEqualTo("My own title");
        assertThat(labRepository.findByDocumentIdAndUserIdOrderByPosition(id, user.getId()))
                .extracting(LabResult::getTestName, LabResult::getValueText)
                .containsExactly(tuple("Hb", "14.1"));
        assertThat(labRepository.findByDocumentIdAndUserIdOrderByPosition(id, user.getId()).getFirst().getFlag())
                .isEqualTo(LabFlag.NORMAL);
        assertThat(medicationRepository.findByDocumentIdAndUserIdOrderByPosition(id, user.getId())).hasSize(1);
        assertThat(termRepository.findByDocumentIdAndUserIdOrderByPosition(id, user.getId())).hasSize(1);
        assertThat(pageRepository.findByDocumentIdAndUserIdOrderByPageNumber(id, user.getId())).hasSize(2);
        assertThat(runRepository.countByDocumentId(id)).isEqualTo(2);
    }

    @Test
    void reprocessingKeepsResultsTheUserCorrectedWithoutDuplicatingThem() throws Exception {
        replies.put("/v1/ocr", new Reply(200, OCR_TWO_PAGES));
        replies.put("/v1/extract", new Reply(200, extraction("CBC", "Haemoglobin", "1.35")));
        UUID id = upload();
        runner.runDue();
        LabResult misread = labRepository.findByDocumentIdAndUserIdOrderByPosition(id, user.getId()).getFirst();
        transactions.executeWithoutResult(status -> {
            LabResult row = labRepository.findByIdAndUserId(misread.getId(), user.getId()).orElseThrow();
            row.edit("Haemoglobin", "13.5", "g/dL", "13.0 - 17.0");
        });

        replies.put("/v1/extract", new Reply(200, extraction("CBC", "Haemoglobin", "1.35")));
        reprocess(id);

        assertThat(labRepository.findByDocumentIdAndUserIdOrderByPosition(id, user.getId()))
                .singleElement().satisfies(lab -> {
                    assertThat(lab.getId()).isEqualTo(misread.getId());
                    assertThat(lab.getValueText()).isEqualTo("13.5");
                    assertThat(lab.isUserEdited()).isTrue();
                });
        assertThat(medicationRepository.findByDocumentIdAndUserIdOrderByPosition(id, user.getId())).hasSize(1);
    }

    @Test
    void renamedCorrectionsAreStillRecognisedWhenReprocessing() throws Exception {
        replies.put("/v1/ocr", new Reply(200, OCR_TWO_PAGES));
        replies.put("/v1/extract", new Reply(200, extraction("CBC", "Haemoglobin", "13.5")));
        UUID id = upload();
        runner.runDue();
        Medication medicine = medicationRepository.findByDocumentIdAndUserIdOrderByPosition(id, user.getId()).getFirst();
        LabResult lab = labRepository.findByDocumentIdAndUserIdOrderByPosition(id, user.getId()).getFirst();
        transactions.executeWithoutResult(status -> {
            medicationRepository.findByIdAndUserId(medicine.getId(), user.getId()).orElseThrow()
                    .edit("Ferrous sulphate 200 mg (iron)", "200 mg", "1-0-0", null, "30 days", "after food");
            labRepository.findByIdAndUserId(lab.getId(), user.getId()).orElseThrow()
                    .edit("Hb (my label)", "13.5", "g/dL", "13.0 - 17.0");
        });

        reprocess(id);

        assertThat(medicationRepository.findByDocumentIdAndUserIdOrderByPosition(id, user.getId()))
                .singleElement().satisfies(m -> {
                    assertThat(m.getId()).isEqualTo(medicine.getId());
                    assertThat(m.getName()).isEqualTo("Ferrous sulphate 200 mg (iron)");
                    assertThat(m.getExtractedName()).isEqualTo("Tab. Ferrous sulfate 200 mg");
                });
        assertThat(labRepository.findByDocumentIdAndUserIdOrderByPosition(id, user.getId()))
                .singleElement().satisfies(l -> assertThat(l.getTestName()).isEqualTo("Hb (my label)"));
    }

    @Test
    void aProblemWithTheDocumentFailsAtOnceWithTheServicesMessage() throws Exception {
        replies.put("/v1/ocr", new Reply(422, "{\"status\":422,\"code\":\"PDF_ENCRYPTED\","
                + "\"message\":\"This PDF is password-protected. Remove the password and upload it again\"}"));
        UUID id = upload();

        runner.runDue();

        Document document = document(id);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(document.getFailureReason()).isEqualTo("This PDF is password-protected. Remove the password and upload it again");
        assertThat(calls).containsExactly("/v1/ocr");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM processing_jobs WHERE document_id = ?", String.class, id))
                .isEqualTo("FAILED");
    }

    @Test
    void aDocumentWithoutTextKeepsItsPagesAndFailsWithoutCallingTheModel() throws Exception {
        replies.put("/v1/ocr", new Reply(200, OCR_NO_TEXT));
        UUID id = upload();

        runner.runDue();

        Document document = document(id);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(document.getFailureReason()).isEqualTo(AiDocumentProcessor.NO_TEXT);
        assertThat(document.getPageCount()).isEqualTo(1);
        assertThat(pageRepository.findByDocumentIdAndUserIdOrderByPageNumber(id, user.getId())).hasSize(1);
        assertThat(calls).containsExactly("/v1/ocr");
    }

    @Test
    void anAiOutageIsRetriedLater() throws Exception {
        replies.put("/v1/ocr", new Reply(200, OCR_TWO_PAGES));
        replies.put("/v1/extract", new Reply(503, "{\"status\":503,\"code\":\"LLM_UNAVAILABLE\","
                + "\"message\":\"The AI service is temporarily unavailable. Please try again later\"}"));
        UUID id = upload();

        runner.runDue();

        assertThat(document(id).getStatus()).isEqualTo(DocumentStatus.PROCESSING);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM processing_jobs WHERE document_id = ?", String.class, id))
                .isEqualTo("PENDING");
        assertThat(labRepository.findByDocumentIdAndUserIdOrderByPosition(id, user.getId())).isEmpty();
    }

    @Test
    void aMissingStoredFileFailsWithoutCallingTheAiService() throws Exception {
        UUID id = upload();
        jdbcTemplate.update("UPDATE documents SET storage_key = ? WHERE id = ?", user.getId() + "/" + UUID.randomUUID() + ".pdf", id);

        runner.runDue();

        assertThat(document(id).getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(document(id).getFailureReason()).isEqualTo(AiDocumentProcessor.FILE_MISSING);
        assertThat(calls).isEmpty();
    }
}
