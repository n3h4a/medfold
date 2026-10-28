package com.medfold.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;

class LocalFileStorageServiceTest {

    // printf 'hello medfold' | sha256sum
    private static final String HELLO_SHA256 = "07bbcfe8f5733106a8a33a6e5b1149aca8095cd903494b23a8e84bb8eea0f556";

    @TempDir
    Path root;

    private LocalFileStorageService storage;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        storage = new LocalFileStorageService(new StorageProperties(root.toString()));
    }

    private static InputStream bytes(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void storesFileUnderUserFolderWithSizeAndHash() throws IOException {
        StoredFile stored = storage.store(userId, bytes("hello medfold"), "pdf");

        assertThat(stored.key()).matches(userId + "/[0-9a-f-]{36}\\.pdf");
        assertThat(stored.sizeBytes()).isEqualTo(13);
        assertThat(stored.sha256()).isEqualTo(HELLO_SHA256);
        assertThat(Files.readString(root.resolve(stored.key()))).isEqualTo("hello medfold");
    }

    @Test
    void sameContentGetsSameHashButDifferentKeys() {
        StoredFile first = storage.store(userId, bytes("same"), "png");
        StoredFile second = storage.store(userId, bytes("same"), "png");

        assertThat(first.sha256()).isEqualTo(second.sha256());
        assertThat(first.key()).isNotEqualTo(second.key());
    }

    @Test
    void loadsStoredFile() throws IOException {
        StoredFile stored = storage.store(userId, bytes("report body"), "pdf");

        Resource resource = storage.load(stored.key());

        assertThat(resource.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("report body");
        assertThat(resource.contentLength()).isEqualTo(11);
    }

    @Test
    void loadingMissingFileThrowsNotFound() {
        String missing = userId + "/" + UUID.randomUUID() + ".pdf";

        assertThatThrownBy(() -> storage.load(missing)).isInstanceOf(StoredFileNotFoundException.class);
    }

    @Test
    void deleteRemovesFileAndIsIdempotent() {
        StoredFile stored = storage.store(userId, bytes("x"), "pdf");

        storage.delete(stored.key());
        storage.delete(stored.key());

        assertThat(root.resolve(stored.key())).doesNotExist();
    }

    @Test
    void deleteAllForUserRemovesOnlyThatUsersFiles() {
        UUID otherUser = UUID.randomUUID();
        storage.store(userId, bytes("a"), "pdf");
        storage.store(userId, bytes("b"), "jpg");
        StoredFile other = storage.store(otherUser, bytes("c"), "pdf");

        storage.deleteAllForUser(userId);

        assertThat(root.resolve(userId.toString())).doesNotExist();
        assertThat(root.resolve(other.key())).exists();
        storage.deleteAllForUser(UUID.randomUUID()); // no folder: no error
    }

    @Test
    void rejectsKeysThatCouldEscapeTheRoot() {
        String[] badKeys = {
            "../etc/passwd",
            userId + "/../../secret.pdf",
            "/etc/passwd",
            userId + "/" + UUID.randomUUID() + ".pdf/../x",
            "",
            null,
        };
        for (String key : badKeys) {
            assertThatThrownBy(() -> storage.load(key)).as("load %s", key).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> storage.delete(key)).as("delete %s", key).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsUnsafeExtensions() {
        for (String extension : new String[] {"../pdf", "PDF", "p d f", "", "x".repeat(11), null}) {
            assertThatThrownBy(() -> storage.store(userId, bytes("x"), extension))
                    .as("extension %s", extension)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void failedUploadLeavesNoFileBehind() throws IOException {
        InputStream failsHalfway = new InputStream() {
            private int sent;

            @Override
            public int read() throws IOException {
                if (sent++ > 1000) {
                    throw new IOException("connection reset");
                }
                return 'a';
            }
        };

        assertThatThrownBy(() -> storage.store(userId, failsHalfway, "pdf")).isInstanceOf(StorageException.class);

        try (Stream<Path> files = Files.walk(root)) {
            assertThat(files.filter(Files::isRegularFile)).isEmpty();
        }
    }

    @Test
    void createsRootFolderIfMissing() {
        Path nested = root.resolve("a/b/uploads");

        new LocalFileStorageService(new StorageProperties(nested.toString()));

        assertThat(nested).isDirectory();
    }
}
