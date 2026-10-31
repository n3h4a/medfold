package com.medfold.ai;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Calls the internal AI service: {@code /v1/ocr} for page text and {@code /v1/extract} for structured data.
 *
 * Every failure becomes an {@link AiServiceException} with a retry decision and a message that is safe
 * to show to the user:
 * <ul>
 * <li>Problems with the document itself (password-protected PDF, unreadable image, no text, ...) are
 * permanent, and the AI service's own plain-language message is shown.</li>
 * <li>Outages, timeouts, a rejected internal key, server errors and unreadable responses are retried.
 * Their details are logged and stored on the job, never shown to the user.</li>
 * </ul>
 */
@Component
public class AiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AiServiceClient.class);

    static final String UNAVAILABLE = "The AI service was unavailable. Please try again later";
    static final String GENERIC = "Something went wrong while processing this document. Please try again later";

    /** Error codes caused by the document itself: retrying the same file gives the same answer. */
    private static final Set<String> DOCUMENT_PROBLEMS = Set.of(
            "PDF_ENCRYPTED", "PDF_UNREADABLE", "TOO_MANY_PAGES", "UNSUPPORTED_FILE_TYPE", "FILE_TOO_LARGE",
            "IMAGE_TOO_LARGE", "IMAGE_UNREADABLE", "NO_TEXT", "TEXT_TOO_LONG", "EXTRACTION_REFUSED");

    /** Temporary errors whose AI service message is written for users and may be shown as is. */
    private static final Set<String> USER_FACING_TEMPORARY = Set.of("LLM_UNAVAILABLE", "EXTRACTION_INVALID");

    private static final String KEY_HEADER = "X-Internal-Api-Key";
    private static final Duration PING_TIMEOUT = Duration.ofSeconds(2);

    /** Whether documents can be processed right now, as far as the AI service is concerned. */
    public enum Availability {
        UP,
        /** Unreachable, failing, or rejecting the internal key. */
        DOWN,
        /** AI_INTERNAL_KEY is not set on the backend. */
        NOT_CONFIGURED
    }

    private final RestClient restClient;
    /** Short timeouts: a status check must never hang the health endpoint. */
    private final RestClient pingClient;
    private final JsonMapper jsonMapper;
    private final String internalKey;

    public AiServiceClient(AiServiceProperties properties, JsonMapper jsonMapper) {
        HttpClient httpClient = HttpClient.newBuilder()
                // HTTP/1.1 only: the default (HTTP/2) sends an "Upgrade: h2c" request over plain HTTP,
                // which uvicorn rejects with 400 for every request that has a body.
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                // The AI service is reached directly (internal network), never through an outbound proxy.
                .proxy(HttpClient.Builder.NO_PROXY)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
        JdkClientHttpRequestFactory pingFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(PING_TIMEOUT)
                .proxy(HttpClient.Builder.NO_PROXY)
                .build());
        pingFactory.setReadTimeout(PING_TIMEOUT);
        this.pingClient = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(pingFactory).build();
        this.jsonMapper = jsonMapper;
        this.internalKey = properties.internalKey();
        if (internalKey == null || internalKey.isBlank()) {
            log.warn("AI_INTERNAL_KEY is not set; documents cannot be processed until it is");
        }
    }

    /** Calls /v1/ping, which also proves the internal key is accepted. Never throws. */
    public Availability availability() {
        if (internalKey == null || internalKey.isBlank()) {
            return Availability.NOT_CONFIGURED;
        }
        try {
            pingClient.get().uri("/v1/ping").header(KEY_HEADER, internalKey).retrieve().toBodilessEntity();
            return Availability.UP;
        } catch (RestClientException ex) {
            log.debug("AI service ping failed: {}", ex.getMessage());
            return Availability.DOWN;
        }
    }

    /** Text of every page of a PDF or image, from the text layer or OCR. */
    public OcrResult ocr(Resource file, String filename, String mimeType) {
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(mimeType));
        partHeaders.setContentDisposition(ContentDisposition.formData().name("file").filename(filename).build());
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(file, partHeaders));
        String raw = call("/v1/ocr", spec -> spec.contentType(MediaType.MULTIPART_FORM_DATA).body(body));
        return parse("/v1/ocr", raw, OcrResult.class);
    }

    public record PageText(int pageNumber, String text) {
    }

    /**
     * Structured data (type, date, doctor, labs, medicines, summary) from page texts.
     *
     * @param docTypeHint optional, e.g. "LAB_REPORT"; null lets the model decide
     */
    public ExtractionResult extract(List<PageText> pages, String docTypeHint) {
        String request = jsonMapper.writeValueAsString(new ExtractRequest(pages, docTypeHint));
        String raw = call("/v1/extract", spec -> spec.contentType(MediaType.APPLICATION_JSON).body(request));
        ExtractResponse response = parse("/v1/extract", raw, ExtractResponse.class);
        if (response.extraction() == null || response.extraction().docType() == null || response.meta() == null) {
            throw invalidResponse("/v1/extract", "missing extraction, docType or meta", null);
        }
        return new ExtractionResult(response.extraction(), response.meta(), raw);
    }

    private record ExtractRequest(List<PageText> pages, String docTypeHint) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ExtractResponse(ExtractionResult.Extraction extraction, ExtractionResult.Meta meta) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ErrorBody(String code, String message, Map<String, String> fields) {
    }

    private String call(String path, UnaryOperator<RestClient.RequestBodySpec> withBody) {
        if (internalKey == null || internalKey.isBlank()) {
            throw new AiServiceException(0, "AI_NOT_CONFIGURED", "AI_INTERNAL_KEY is not set", true, UNAVAILABLE, null);
        }
        try {
            RestClient.RequestBodySpec spec = restClient.post().uri(path).header(KEY_HEADER, internalKey)
                    .accept(MediaType.APPLICATION_JSON);
            return withBody.apply(spec).retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        throw errorFor(path, response.getStatusCode().value(), body);
                    })
                    .body(String.class);
        } catch (AiServiceException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            // Connection refused, DNS failure or timeout.
            throw new AiServiceException(0, "AI_UNREACHABLE", path + ": " + ex.getMessage(), true, UNAVAILABLE, ex);
        } catch (RestClientException ex) {
            throw new AiServiceException(0, "AI_CLIENT_ERROR", path + ": " + ex.getMessage(), true, GENERIC, ex);
        }
    }

    private AiServiceException errorFor(String path, int status, String body) {
        ErrorBody error = null;
        try {
            error = jsonMapper.readValue(body, ErrorBody.class);
        } catch (JacksonException ignored) {
            // Not our error shape (e.g. a proxy's HTML page); handled below by status.
        }
        String code = error != null && error.code() != null ? error.code() : "HTTP_" + status;
        String message = error != null ? error.message() : null;
        String internal = path + " returned " + status + " " + code + (message == null ? "" : ": " + message);

        if (DOCUMENT_PROBLEMS.contains(code) && message != null) {
            return new AiServiceException(status, code, internal, false, message, null);
        }
        if (status == 401) {
            log.error("The AI service rejected the internal API key; check AI_INTERNAL_KEY on both services");
            return new AiServiceException(status, code, internal, true, UNAVAILABLE, null);
        }
        if (USER_FACING_TEMPORARY.contains(code) && message != null) {
            return new AiServiceException(status, code, internal, true, message, null);
        }
        if (status >= 500 || status == 429) {
            return new AiServiceException(status, code, internal, true, UNAVAILABLE, null);
        }
        // Any other 4xx means the backend sent something the service didn't accept: a bug, not the user's
        // fault. Retrying won't fix it, but the details must stay internal.
        log.error("AI service rejected a request: {} {}", internal, error != null && error.fields() != null ? error.fields() : "");
        return new AiServiceException(status, code, internal, false, GENERIC, null);
    }

    private <T> T parse(String path, String raw, Class<T> type) {
        if (raw == null || raw.isBlank()) {
            throw invalidResponse(path, "empty body", null);
        }
        try {
            return jsonMapper.readValue(raw, type);
        } catch (JacksonException ex) {
            throw invalidResponse(path, ex.getOriginalMessage(), ex);
        }
    }

    private static AiServiceException invalidResponse(String path, String detail, Throwable cause) {
        return new AiServiceException(200, "AI_INVALID_RESPONSE", path + " returned an unreadable response: " + detail,
                true, GENERIC, cause);
    }
}
