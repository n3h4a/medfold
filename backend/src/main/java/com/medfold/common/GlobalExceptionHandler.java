package com.medfold.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex) {
        ApiError body = new ApiError(ex.getStatus().value(), ex.getCode(), ex.getMessage(), null, ex.getDetails());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ApiError body = new ApiError(HttpStatus.BAD_REQUEST.value(), "VALIDATION_ERROR",
                "Some fields are invalid", fields);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        ApiError body = new ApiError(HttpStatus.BAD_REQUEST.value(), "MALFORMED_REQUEST",
                "Request body is missing or not valid JSON");
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleTooLarge(MaxUploadSizeExceededException ex) {
        ApiError body = new ApiError(HttpStatus.CONTENT_TOO_LARGE.value(), "FILE_TOO_LARGE",
                "File is larger than the 10 MB limit");
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(body);
    }

    @ExceptionHandler({ MissingServletRequestPartException.class, MultipartException.class })
    public ResponseEntity<ApiError> handleMissingFile(Exception ex) {
        ApiError body = new ApiError(HttpStatus.BAD_REQUEST.value(), "FILE_REQUIRED",
                "Choose a file to upload");
        return ResponseEntity.badRequest().body(body);
    }
}
