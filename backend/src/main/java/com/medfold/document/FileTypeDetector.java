package com.medfold.document;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Optional;

/**
 * Identifies uploads by their leading bytes. The browser-supplied Content-Type and the file
 * name are both under the uploader's control, so neither is trusted.
 */
public final class FileTypeDetector {

    private static final byte[] PDF = { '%', 'P', 'D', 'F', '-' };
    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };
    private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
    private static final int HEADER_LENGTH = 8;

    private FileTypeDetector() {
    }

    public static Optional<DetectedFileType> detect(InputStream content) throws IOException {
        byte[] header = content.readNBytes(HEADER_LENGTH);
        if (startsWith(header, PDF)) {
            return Optional.of(DetectedFileType.PDF);
        }
        if (startsWith(header, PNG)) {
            return Optional.of(DetectedFileType.PNG);
        }
        if (startsWith(header, JPEG)) {
            return Optional.of(DetectedFileType.JPEG);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] header, byte[] signature) {
        return header.length >= signature.length
                && Arrays.equals(header, 0, signature.length, signature, 0, signature.length);
    }
}
