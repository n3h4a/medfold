package com.medfold.records.dto;

import com.medfold.document.DocumentTerm;

public record TermResponse(String term, String definition) {

    public static TermResponse from(DocumentTerm t) {
        return new TermResponse(t.getTerm(), t.getDefinition());
    }
}
