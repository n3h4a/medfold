package com.medfold.search;

import com.medfold.document.dto.DocumentResponse;

/**
 * A document that matches every search word somewhere: its details, its page text, or the names of
 * its tests and medicines. {@code snippet} is a short piece of page text around the first word, if
 * the page text matched.
 */
public record SearchResult(DocumentResponse document, String snippet, Integer pageNumber) {
}
