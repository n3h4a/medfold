package com.medfold.records;

/**
 * Where a value sits relative to the reference range printed on the same report.
 * Computed by the backend, never by the AI, and shown as a comparison, not a medical judgment.
 */
public enum LabFlag {
    LOW,
    NORMAL,
    HIGH
}
