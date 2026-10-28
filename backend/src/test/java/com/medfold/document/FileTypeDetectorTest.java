package com.medfold.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class FileTypeDetectorTest {

    private static Optional<DetectedFileType> detect(byte[] bytes) throws IOException {
        return FileTypeDetector.detect(new ByteArrayInputStream(bytes));
    }

    @Test
    void detectsSupportedTypesFromContent() throws IOException {
        assertThat(detect(TestFiles.pdf("x"))).contains(DetectedFileType.PDF);
        assertThat(detect(TestFiles.png())).contains(DetectedFileType.PNG);
        assertThat(detect(TestFiles.jpeg())).contains(DetectedFileType.JPEG);
    }

    @Test
    void rejectsEverythingElse() throws IOException {
        assertThat(detect("MZ\u0090\u0000 windows exe".getBytes())).isEmpty();
        assertThat(detect("just some text".getBytes())).isEmpty();
        assertThat(detect("<html><script>".getBytes())).isEmpty();
        assertThat(detect(new byte[] { 'P', 'K', 3, 4 })).isEmpty(); // zip / docx
        assertThat(detect(new byte[] { '%', 'P', 'D' })).isEmpty(); // truncated header
        assertThat(detect(new byte[0])).isEmpty();
    }
}
