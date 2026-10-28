package com.medfold.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentServiceFilenameTest {

    @Test
    void keepsOnlyTheBaseName() {
        assertThat(DocumentService.cleanFilename("C:\\Users\\asha\\Desktop\\cbc.pdf", DetectedFileType.PDF)).isEqualTo("cbc.pdf");
        assertThat(DocumentService.cleanFilename("../../etc/passwd.pdf", DetectedFileType.PDF)).isEqualTo("passwd.pdf");
    }

    @Test
    void stripsControlCharactersAndWhitespace() {
        assertThat(DocumentService.cleanFilename("  lab\u0000\nreport.pdf ", DetectedFileType.PDF)).isEqualTo("labreport.pdf");
    }

    @Test
    void fallsBackToGenericNameAndLimitsLength() {
        assertThat(DocumentService.cleanFilename(null, DetectedFileType.JPEG)).isEqualTo("document.jpg");
        assertThat(DocumentService.cleanFilename("   ", DetectedFileType.PNG)).isEqualTo("document.png");
        assertThat(DocumentService.cleanFilename("a".repeat(300) + ".pdf", DetectedFileType.PDF))
                .hasSize(255).endsWith(".pdf");
    }
}
