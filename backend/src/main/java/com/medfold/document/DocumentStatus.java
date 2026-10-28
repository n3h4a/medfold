package com.medfold.document;

/** Lifecycle of an uploaded document: UPLOADED → PROCESSING → READY or FAILED. */
public enum DocumentStatus {
    UPLOADED,
    PROCESSING,
    READY,
    FAILED
}
