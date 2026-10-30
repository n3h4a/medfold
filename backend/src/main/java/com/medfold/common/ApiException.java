package com.medfold.common;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;

/** An error with a stable machine-readable code, mapped to an {@link ApiError} response. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> details;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public ApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    /** For rate limits: how long the client should wait, also sent as a Retry-After header. */
    public Optional<Long> retryAfterSeconds() {
        return details != null && details.get("retryAfterSeconds") instanceof Number n
                ? Optional.of(n.longValue())
                : Optional.empty();
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
