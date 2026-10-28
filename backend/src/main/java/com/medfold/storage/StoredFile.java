package com.medfold.storage;

/**
 * Result of storing a file. The SHA-256 and size are computed while streaming the upload,
 * so the file is read exactly once.
 */
public record StoredFile(String key, long sizeBytes, String sha256) {
}
