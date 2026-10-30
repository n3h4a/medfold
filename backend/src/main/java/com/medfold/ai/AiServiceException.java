package com.medfold.ai;

import com.medfold.processing.ProcessingFailure;

/**
 * A call to the AI service failed. Extends ProcessingFailure, so the job runner applies its retry
 * decision and user-facing message directly.
 *
 * @see AiServiceClient for how AI service errors map to retryable and permanent failures
 */
public class AiServiceException extends ProcessingFailure {

    private final int httpStatus;
    private final String code;

    public AiServiceException(int httpStatus, String code, String internalMessage, boolean retryable,
            String userMessage, Throwable cause) {
        super(internalMessage, retryable, userMessage, cause);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    /** HTTP status from the AI service, or 0 if no response was received. */
    public int getHttpStatus() {
        return httpStatus;
    }

    /** The AI service's error code (e.g. PDF_ENCRYPTED), or a client-side one such as AI_UNREACHABLE. */
    public String getCode() {
        return code;
    }
}
