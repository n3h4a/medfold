package com.medfold.common;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard error body returned by every endpoint.
 * {@code fields} holds per-field validation messages; {@code details} holds extra data the
 * client can act on (e.g. the id of an existing duplicate document).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(int status, String code, String message, Map<String, String> fields,
        Map<String, Object> details) {

    public ApiError(int status, String code, String message) {
        this(status, code, message, null, null);
    }

    public ApiError(int status, String code, String message, Map<String, String> fields) {
        this(status, code, message, fields, null);
    }
}
