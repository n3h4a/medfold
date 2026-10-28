package com.medfold.storage;

import java.io.InputStream;
import java.util.UUID;

import org.springframework.core.io.Resource;

/**
 * Stores uploaded files by opaque key. Callers never see filesystem paths, so the local
 * implementation can be replaced by object storage (e.g. S3) without touching them.
 *
 * Keys look like {@code {userId}/{uuid}.{extension}} and are generated here, never taken from user input.
 */
public interface StorageService {

    /**
     * Streams {@code content} into storage under a new key in the user's folder.
     *
     * @param extension lowercase file extension without the dot, e.g. "pdf"
     */
    StoredFile store(UUID userId, InputStream content, String extension);

    /** @throws StoredFileNotFoundException if nothing is stored under the key */
    Resource load(String key);

    /** Deletes the file if it exists. Deleting a missing key is not an error. */
    void delete(String key);

    /** Removes every file of a user, used when an account is deleted. */
    void deleteAllForUser(UUID userId);
}
