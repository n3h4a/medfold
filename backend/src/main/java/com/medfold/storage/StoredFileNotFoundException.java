package com.medfold.storage;

public class StoredFileNotFoundException extends StorageException {

    public StoredFileNotFoundException(String key) {
        super("No stored file for key " + key);
    }
}
