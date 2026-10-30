package com.medfold.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import com.medfold.document.DocumentType;
import com.medfold.document.TextSource;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.json.JsonMapper;

/** Runs the client against a real local HTTP server standing in for the AI service. */
class AiServiceClientTest {

    private static final String KEY = "test-internal-key";

    private static final String OCR_RESPONSE = """
            {"fileKind":"PDF","pageCount":2,"ocrProvider":"tesseract","pagesWithoutText":[2],
             "pages":[{"pageNumber":1,"text":"Haemoglobin 13.5 g/dL","source":"TESSERACT","charCount":18,"confidence":0.91},
                      {"pageNumber":2,"text":"","source":"NONE","charCount":0,"confidence":null}]}
            """;

    private static final String EXTRACT_RESPONSE = """
            {"extraction":{"docType":"LAB_REPORT","docDate":"2026-03-14","title":"Complete Blood Count",
               "providerName":null,"facilityName":"City Lab","summary":"A blood count report.",
               "termsExplained":[{"term":"CBC","definition":"A common blood test."}],
               "labResults":[{"testName":"Haemoglobin","valueText":"13.5","unit":"g/dL","referenceRangeText":"13.0 - 17.0",
                              "pageNumber":1,"testKey":"hemoglobin"}],
               "medications":[]},
             "meta":{"provider":"mock","model":"medfold-rule-based-v1","promptVersion":"extraction-v1","attempts":1,
                     "durationMs":12,"inputTokens":null,"outputTokens":null,"safetyFiltered":false,"safetyIssues":[],
                     "someFutureField":true}}
            """;

    private record Captured(String method, String path, String key, String contentType, String body) {
    }

    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private HttpServer server;
    private final AtomicReference<Captured> captured = new AtomicReference<>();
    private volatile Handler handler;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            captured.set(new Captured(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("X-Internal-Api-Key"),
                    exchange.getRequestHeaders().getFirst("Content-Type"), new String(body, StandardCharsets.UTF_8)));
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private AiServiceClient client() {
        return client(KEY, Duration.ofSeconds(5));
    }

    private AiServiceClient client(String key, Duration readTimeout) {
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        return new AiServiceClient(new AiServiceProperties(url, key, Duration.ofSeconds(2), readTimeout), jsonMapper);
    }

    private void respond(int status, String body) {
        handler = exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        };
    }

    private static String error(int status, String code, String message) {
        return "{\"status\":%d,\"code\":\"%s\",\"message\":\"%s\"}".formatted(status, code, message);
    }

    private AiServiceException extractFailure() {
        return catchThrowableOfType(AiServiceException.class,
                () -> client().extract(List.of(new AiServiceClient.PageText(1, "text")), null));
    }

    @Test
    void ocrUploadsTheFileWithTheInternalKeyAndReadsEveryPage() {
        respond(200, OCR_RESPONSE);
        byte[] pdf = "%PDF-1.4 test".getBytes(StandardCharsets.UTF_8);

        OcrResult result = client().ocr(new ByteArrayResource(pdf), "report.pdf", "application/pdf");

        Captured request = captured.get();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/v1/ocr");
        assertThat(request.key()).isEqualTo(KEY);
        assertThat(request.contentType()).startsWith("multipart/form-data");
        assertThat(request.body()).contains("name=\"file\"", "filename=\"report.pdf\"", "Content-Type: application/pdf",
                "%PDF-1.4 test");

        assertThat(result.pageCount()).isEqualTo(2);
        assertThat(result.ocrProvider()).isEqualTo("tesseract");
        assertThat(result.pagesWithoutText()).containsExactly(2);
        assertThat(result.pages()).hasSize(2);
        OcrResult.Page first = result.pages().getFirst();
        assertThat(first.source()).isEqualTo(TextSource.TESSERACT);
        assertThat(first.confidence()).isEqualTo(0.91f);
        assertThat(first.text()).isEqualTo("Haemoglobin 13.5 g/dL");
        assertThat(result.pages().get(1).confidence()).isNull();
    }

    @Test
    void extractSendsPagesInTheServicesShapeAndKeepsTheRawResponse() {
        respond(200, EXTRACT_RESPONSE);

        ExtractionResult result = client().extract(
                List.of(new AiServiceClient.PageText(1, "Haemoglobin 13.5"), new AiServiceClient.PageText(2, "")),
                "LAB_REPORT");

        Captured request = captured.get();
        assertThat(request.path()).isEqualTo("/v1/extract");
        assertThat(request.key()).isEqualTo(KEY);
        assertThat(request.contentType()).startsWith("application/json");
        // The service rejects unknown fields, so the request must have exactly these.
        assertThat(jsonMapper.readTree(request.body()).propertyNames()).containsExactlyInAnyOrder("pages", "docTypeHint");
        assertThat(request.body()).contains("\"pageNumber\":1", "\"text\":\"Haemoglobin 13.5\"",
                "\"docTypeHint\":\"LAB_REPORT\"");

        ExtractionResult.Extraction extraction = result.extraction();
        assertThat(extraction.docType()).isEqualTo(DocumentType.LAB_REPORT);
        assertThat(extraction.docDate()).isEqualTo(LocalDate.of(2026, 3, 14));
        assertThat(extraction.providerName()).isNull();
        assertThat(extraction.termsExplained()).containsExactly(new ExtractionResult.Term("CBC", "A common blood test."));
        assertThat(extraction.labResults()).containsExactly(new ExtractionResult.Lab("Haemoglobin", "hemoglobin", "13.5",
                "g/dL", "13.0 - 17.0", 1));
        assertThat(extraction.medications()).isEmpty();
        assertThat(result.meta().promptVersion()).isEqualTo("extraction-v1");
        assertThat(result.meta().model()).isEqualTo("medfold-rule-based-v1");
        assertThat(result.meta().attempts()).isEqualTo(1);
        assertThat(result.rawJson()).isEqualTo(EXTRACT_RESPONSE);
    }

    @Test
    void documentProblemsArePermanentAndShowTheServicesMessage() {
        String message = "This PDF is password-protected. Remove the password and upload it again";
        respond(422, error(422, "PDF_ENCRYPTED", message));

        AiServiceException failure = catchThrowableOfType(AiServiceException.class,
                () -> client().ocr(new ByteArrayResource(new byte[] { 1 }), "a.pdf", "application/pdf"));

        assertThat(failure.isRetryable()).isFalse();
        assertThat(failure.getHttpStatus()).isEqualTo(422);
        assertThat(failure.getCode()).isEqualTo("PDF_ENCRYPTED");
        assertThat(failure.getUserMessage()).isEqualTo(message);
        assertThat(failure.getMessage()).contains("/v1/ocr", "422", "PDF_ENCRYPTED");
    }

    @Test
    void anLlmOutageIsRetried() {
        respond(503, error(503, "LLM_UNAVAILABLE", "The AI service is temporarily unavailable. Please try again later"));

        AiServiceException failure = extractFailure();

        assertThat(failure.isRetryable()).isTrue();
        assertThat(failure.getUserMessage()).isEqualTo("The AI service is temporarily unavailable. Please try again later");
    }

    @Test
    void internalDetailsInServerErrorsAreNeverShownToTheUser() {
        respond(503, error(503, "LLM_NOT_CONFIGURED", "Unknown LLM provider 'opeanai'"));

        AiServiceException failure = extractFailure();

        assertThat(failure.isRetryable()).isTrue();
        assertThat(failure.getUserMessage()).isEqualTo(AiServiceClient.UNAVAILABLE).doesNotContain("opeanai");
        assertThat(failure.getMessage()).contains("LLM_NOT_CONFIGURED", "opeanai");
    }

    @Test
    void aRejectedInternalKeyIsRetriedWithAGenericMessage() {
        respond(401, "{\"detail\":\"Invalid or missing internal API key\"}");

        AiServiceException failure = extractFailure();

        assertThat(failure.isRetryable()).isTrue();
        assertThat(failure.getHttpStatus()).isEqualTo(401);
        assertThat(failure.getUserMessage()).isEqualTo(AiServiceClient.UNAVAILABLE);
    }

    @Test
    void aRequestTheServiceRejectsIsABugAndStaysInternal() {
        respond(422, "{\"status\":422,\"code\":\"VALIDATION_ERROR\",\"message\":\"Some fields are invalid\","
                + "\"fields\":{\"pages.0.text\":\"String should have at most 100000 characters\"}}");

        AiServiceException failure = extractFailure();

        assertThat(failure.isRetryable()).isFalse();
        assertThat(failure.getUserMessage()).isEqualTo(AiServiceClient.GENERIC);
    }

    @Test
    void nonJsonErrorPagesAreHandledByStatus() {
        respond(502, "<html>Bad gateway</html>");

        AiServiceException failure = extractFailure();

        assertThat(failure.isRetryable()).isTrue();
        assertThat(failure.getCode()).isEqualTo("HTTP_502");
        assertThat(failure.getUserMessage()).isEqualTo(AiServiceClient.UNAVAILABLE);
    }

    @Test
    void anUnreadableSuccessResponseIsRetried() {
        respond(200, "{\"extraction\": {\"docType\": \"LAB_REP");

        AiServiceException failure = extractFailure();

        assertThat(failure.isRetryable()).isTrue();
        assertThat(failure.getCode()).isEqualTo("AI_INVALID_RESPONSE");
        assertThat(failure.getUserMessage()).isEqualTo(AiServiceClient.GENERIC);
    }

    @Test
    void aResponseWithoutAnExtractionIsRejected() {
        respond(200, "{\"meta\":{\"provider\":\"mock\",\"model\":\"m\",\"promptVersion\":\"v\",\"attempts\":1}}");

        assertThat(extractFailure().getCode()).isEqualTo("AI_INVALID_RESPONSE");
    }

    @Test
    void anUnreachableServiceIsRetried() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        AiServiceClient client = new AiServiceClient(new AiServiceProperties("http://127.0.0.1:" + closedPort, KEY,
                Duration.ofSeconds(2), Duration.ofSeconds(2)), jsonMapper);

        AiServiceException failure = catchThrowableOfType(AiServiceException.class,
                () -> client.extract(List.of(new AiServiceClient.PageText(1, "text")), null));

        assertThat(failure.isRetryable()).isTrue();
        assertThat(failure.getCode()).isEqualTo("AI_UNREACHABLE");
        assertThat(failure.getUserMessage()).isEqualTo(AiServiceClient.UNAVAILABLE);
    }

    @Test
    void aSlowServiceTimesOutAndIsRetried() {
        handler = exchange -> {
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        };

        long started = System.nanoTime();
        AiServiceException failure = catchThrowableOfType(AiServiceException.class,
                () -> client(KEY, Duration.ofMillis(300)).extract(List.of(new AiServiceClient.PageText(1, "t")), null));

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(1_500));
        assertThat(failure.isRetryable()).isTrue();
        assertThat(failure.getCode()).isEqualTo("AI_UNREACHABLE");
    }

    @Test
    void withoutAnInternalKeyNothingIsSent() {
        respond(200, EXTRACT_RESPONSE);

        assertThatThrownBy(() -> client("", Duration.ofSeconds(5)).extract(List.of(new AiServiceClient.PageText(1, "t")), null))
                .isInstanceOfSatisfying(AiServiceException.class, failure -> {
                    assertThat(failure.isRetryable()).isTrue();
                    assertThat(failure.getCode()).isEqualTo("AI_NOT_CONFIGURED");
                });
        assertThat(captured.get()).isNull();
    }

    @Test
    void availabilityPingsWithTheInternalKey() {
        respond(200, "{\"pong\":true}");

        assertThat(client().availability()).isEqualTo(AiServiceClient.Availability.UP);
        assertThat(captured.get().method()).isEqualTo("GET");
        assertThat(captured.get().path()).isEqualTo("/v1/ping");
        assertThat(captured.get().key()).isEqualTo(KEY);
    }

    @Test
    void availabilityIsDownWhenTheKeyIsRejectedOrNobodyAnswers() throws IOException {
        respond(401, "{\"detail\":\"Invalid or missing internal API key\"}");
        assertThat(client().availability()).isEqualTo(AiServiceClient.Availability.DOWN);

        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        AiServiceClient unreachable = new AiServiceClient(new AiServiceProperties("http://127.0.0.1:" + closedPort, KEY,
                Duration.ofSeconds(2), Duration.ofSeconds(2)), jsonMapper);
        assertThat(unreachable.availability()).isEqualTo(AiServiceClient.Availability.DOWN);
    }

    @Test
    void availabilityWithoutAKeyIsNotConfiguredAndSendsNothing() {
        respond(200, "{}");

        assertThat(client("", Duration.ofSeconds(5)).availability()).isEqualTo(AiServiceClient.Availability.NOT_CONFIGURED);
        assertThat(captured.get()).isNull();
    }
}
