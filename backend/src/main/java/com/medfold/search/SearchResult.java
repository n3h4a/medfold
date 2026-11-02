package com.medfold.search;

import java.util.List;

import com.medfold.document.dto.DocumentResponse;

/**
 * A document that matches every search word somewhere: its details, its page text, or the names of
 * its tests and medicines.
 *
 * @param snippet    a short piece of page text around the first word, if the page text matched
 * @param pageNumber the page the snippet comes from
 * @param highlights the text in the snippet that matched each word: the word itself, or the
 *                   similarly spelled word that was found instead
 * @param exact      true if every word was found as typed; false if at least one matched a similar
 *                   spelling ("haemoglobn" finding "Haemoglobin")
 */
public record SearchResult(DocumentResponse document, String snippet, Integer pageNumber, List<String> highlights, boolean exact) {
}
