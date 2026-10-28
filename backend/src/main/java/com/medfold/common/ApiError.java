package com.medfold.common;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Standard error body returned by every endpoint. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(int status, String code, String message, Map<String, String> fields) {

    public ApiError(int status, String code, String message) {
        this(status, code, message, null);
    }
}
