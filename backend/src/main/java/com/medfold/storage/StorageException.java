package com.medfold.storage;

/** Storage failed for an infrastructure reason (disk full, permissions, ...). Never shown to users verbatim. */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
