package com.medfold.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import com.medfold.storage.StorageException;
import com.medfold.storage.StoredFileNotFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    /** e.g. /documents/not-a-uuid */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ApiError body = new ApiError(HttpStatus.BAD_REQUEST.value(), "INVALID_PARAMETER",
                "Invalid value for " + ex.getName());
        return ResponseEntity.badRequest().body(body);
    }

    /** The row exists but its file is gone: data problem worth an error log, not a 500 page. */
    @ExceptionHandler(StoredFileNotFoundException.class)
    public ResponseEntity<ApiError> handleMissingFile(StoredFileNotFoundException ex) {
        log.error("Stored file missing", ex);
        ApiError body = new ApiError(HttpStatus.NOT_FOUND.value(), "FILE_NOT_FOUND",
                "The original file for this document is missing");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ApiError> handleStorage(StorageException ex) {
        log.error("Storage failure", ex);
        ApiError body = new ApiError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "STORAGE_ERROR",
                "Something went wrong while handling the file. Please try again");
        return ResponseEntity.internalServerError().body(body);
    }
}
