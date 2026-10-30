package com.medfold.document;

/** PATCH semantics for text fields: null leaves a value unchanged, a blank string clears it. */
public final class Edits {

    private Edits() {
    }

    public static String apply(String current, String requested) {
        if (requested == null) {
            return current;
        }
        String trimmed = requested.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
