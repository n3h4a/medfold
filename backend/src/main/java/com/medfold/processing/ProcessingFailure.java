package com.medfold.processing;

/**
 * Processing a document failed. {@code userMessage} is shown on the document if this was the last
 * attempt, so it must be plain language and contain no internal details.
 */
public class ProcessingFailure extends RuntimeException {

    private final boolean retryable;
    private final String userMessage;

    public ProcessingFailure(String internalMessage, boolean retryable, String userMessage, Throwable cause) {
        super(internalMessage, cause);
        this.retryable = retryable;
        this.userMessage = userMessage;
    }

    public ProcessingFailure(String internalMessage, boolean retryable, String userMessage) {
        this(internalMessage, retryable, userMessage, null);
    }

    public boolean isRetryable() {
        return retryable;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
