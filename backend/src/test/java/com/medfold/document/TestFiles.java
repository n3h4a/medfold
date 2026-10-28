package com.medfold.document;

import java.nio.charset.StandardCharsets;

/** Minimal files that pass magic-byte detection. */
final class TestFiles {

    private TestFiles() {
    }

    static byte[] pdf(String body) {
        return ("%PDF-1.4\n% Medfold test\n" + body + "\n%%EOF\n").getBytes(StandardCharsets.UTF_8);
    }

    static byte[] png() {
        return new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R' };
    }

    static byte[] jpeg() {
        return new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F' };
    }
}
