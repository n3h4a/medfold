package com.medfold.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medfold.common.ApiException;
import com.medfold.document.Document;
import com.medfold.document.DocumentRepository;
import com.medfold.document.dto.DocumentResponse;

/**
 * Finds the user's documents by any word in them. Substring matching (so "metfor" and "hba1c" work)
 * is fast enough at one person's scale; every statement is scoped to the user.
 */
@Service
public class SearchService {

    static final int MAX_RESULTS = 50;
    private static final int MAX_QUERY_LENGTH = 100;
    private static final int MAX_WORDS = 6;
    private static final int SNIPPET_RADIUS = 70;

    /** One word must appear in at least one of these places. Each placeholder takes the pattern. */
    private static final String WORD_MATCHES = """
            (d.title ILIKE ? ESCAPE '\\' OR d.summary ILIKE ? ESCAPE '\\' OR d.provider_name ILIKE ? ESCAPE '\\'
             OR d.facility_name ILIKE ? ESCAPE '\\' OR d.original_filename ILIKE ? ESCAPE '\\'
             OR EXISTS (SELECT 1 FROM document_pages p WHERE p.document_id = d.id AND p.user_id = d.user_id AND p.text ILIKE ? ESCAPE '\\')
             OR EXISTS (SELECT 1 FROM lab_results r WHERE r.document_id = d.id AND r.user_id = d.user_id AND r.test_name ILIKE ? ESCAPE '\\')
             OR EXISTS (SELECT 1 FROM medications m WHERE m.document_id = d.id AND m.user_id = d.user_id AND m.name ILIKE ? ESCAPE '\\'))
            """;
    private static final int PLACEHOLDERS_PER_WORD = 8;

    private static final String FIRST_MATCHING_PAGE = """
            SELECT DISTINCT ON (document_id) document_id, page_number, text
            FROM document_pages
            WHERE user_id = ? AND document_id = ANY (?) AND text ILIKE ? ESCAPE '\\'
            ORDER BY document_id, page_number
            """;

    private final JdbcTemplate jdbcTemplate;
    private final DocumentRepository documentRepository;

    public SearchService(JdbcTemplate jdbcTemplate, DocumentRepository documentRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.documentRepository = documentRepository;
    }

    @Transactional(readOnly = true)
    public List<SearchResult> search(UUID userId, String query) {
        List<String> words = words(query);
        if (words.isEmpty()) {
            return List.of();
        }

        StringBuilder sql = new StringBuilder("SELECT d.id FROM documents d WHERE d.user_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        for (String word : words) {
            sql.append(" AND ").append(WORD_MATCHES);
            String pattern = "%" + escapeLike(word) + "%";
            for (int i = 0; i < PLACEHOLDERS_PER_WORD; i++) {
                args.add(pattern);
            }
        }
        sql.append(" ORDER BY d.doc_date DESC NULLS LAST, d.created_at DESC LIMIT ").append(MAX_RESULTS);
        List<UUID> ids = jdbcTemplate.queryForList(sql.toString(), UUID.class, args.toArray());
        if (ids.isEmpty()) {
            return List.of();
        }

        Map<UUID, Document> documents = documentRepository.findByUserIdAndIdIn(userId, ids).stream()
                .collect(Collectors.toMap(Document::getId, Function.identity()));
        Map<UUID, String[]> snippets = snippets(userId, ids, words.getFirst());
        return ids.stream()
                .filter(documents::containsKey)
                .map(id -> {
                    String[] snippet = snippets.get(id);
                    return new SearchResult(DocumentResponse.from(documents.get(id)), snippet == null ? null : snippet[1],
                            snippet == null ? null : Integer.valueOf(snippet[0]));
                })
                .toList();
    }

    static List<String> words(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "QUERY_TOO_LONG", "Search for at most 100 characters");
        }
        return Arrays.stream(query.strip().split("\\s+"))
                .filter(w -> !w.isEmpty())
                .map(w -> w.toLowerCase(Locale.ROOT))
                .distinct()
                .limit(MAX_WORDS)
                .toList();
    }

    static String escapeLike(String word) {
        return word.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** For each document whose page text contains the word: [page number, text around the first occurrence]. */
    private Map<UUID, String[]> snippets(UUID userId, List<UUID> ids, String word) {
        Map<UUID, String[]> result = new HashMap<>();
        jdbcTemplate.query(FIRST_MATCHING_PAGE, rs -> {
            result.put(rs.getObject("document_id", UUID.class),
                    new String[] { String.valueOf(rs.getInt("page_number")), snippet(rs.getString("text"), word) });
        }, userId, ids.toArray(UUID[]::new), "%" + escapeLike(word) + "%");
        return result;
    }

    static String snippet(String text, String word) {
        String flat = text.replaceAll("\\s+", " ").strip();
        int at = flat.toLowerCase(Locale.ROOT).indexOf(word.toLowerCase(Locale.ROOT));
        if (at < 0) {
            return null;
        }
        int start = Math.max(0, at - SNIPPET_RADIUS);
        int end = Math.min(flat.length(), at + word.length() + SNIPPET_RADIUS);
        return (start > 0 ? "…" : "") + flat.substring(start, end).strip() + (end < flat.length() ? "…" : "");
    }
}
