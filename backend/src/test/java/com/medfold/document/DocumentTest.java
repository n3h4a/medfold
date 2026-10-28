package com.medfold.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class DocumentTest {

    private static Document newDocument() {
        return new Document(UUID.randomUUID(), "cbc.pdf", "key", "application/pdf", 100, "a".repeat(64));
    }

    @Test
    void startsAsUploaded() {
        assertThat(newDocument().getStatus()).isEqualTo(DocumentStatus.UPLOADED);
    }

    @Test
    void movesThroughProcessingToReady() {
        Document document = newDocument();

        document.markProcessing();
        document.markReady();

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.READY);
        assertThat(document.getFailureReason()).isNull();
    }

    @Test
    void failureKeepsUserFacingReasonAndCanBeRetried() {
        Document document = newDocument();
        document.markProcessing();

        document.markFailed("We couldn't read any text in this file");
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(document.getFailureReason()).isEqualTo("We couldn't read any text in this file");

        document.markProcessing();
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PROCESSING);
        assertThat(document.getFailureReason()).isNull();
    }

    @Test
    void longFailureReasonIsTruncatedToColumnSize() {
        Document document = newDocument();
        document.markProcessing();

        document.markFailed("x".repeat(800));

        assertThat(document.getFailureReason()).hasSize(500);
    }

    @Test
    void rejectsInvalidTransitions() {
        Document document = newDocument();

        assertThatThrownBy(document::markReady).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> document.markFailed("x")).isInstanceOf(IllegalStateException.class);

        document.markProcessing();
        assertThatThrownBy(document::markProcessing).isInstanceOf(IllegalStateException.class);
    }
}
