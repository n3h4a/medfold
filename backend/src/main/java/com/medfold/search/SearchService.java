package com.medfold.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * Finds the user's documents by any word in them (ADR-003, ADR-011). Every word must match the same
 * document, either as typed (a substring, so "metfor" and "hba1c" work) or, for words of four letters
 * or more, as a similar spelling (pg_trgm strict word similarity, so "haemoglobn" finds "Haemoglobin").
 * Exact matches rank first, then closer spellings, then newer records. Every statement is scoped to
 * the user.
 */
@Service
public class SearchService {

    static final int MAX_RESULTS = 50;
    private static final int MAX_QUERY_LENGTH = 100;
    private static final int MAX_WORDS = 6;
    private static final int SNIPPET_RADIUS = 70;
    /** Shorter words only match as typed: the trigrams of a very short word match almost anything. */
    static final int MIN_SIMILAR_LENGTH = 4;
    /**
     * pg_trgm strict word similarity (0..1) a different spelling must exceed to count as a match. Strict:
     * only whole words are compared, so "metfor" doesn't match across "for Medfold".
     */
    static final String SIMILARITY_THRESHOLD = "0.45";

    /** The word appears as typed in one of these places. Each placeholder takes the LIKE pattern. */
    private static final String EXACT = """
            (d.title ILIKE ? ESCAPE '\\' OR d.summary ILIKE ? ESCAPE '\\' OR d.provider_name ILIKE ? ESCAPE '\\'
             OR d.facility_name ILIKE ? ESCAPE '\\' OR d.original_filename ILIKE ? ESCAPE '\\'
             OR EXISTS (SELECT 1 FROM document_pages p WHERE p.document_id = d.id AND p.user_id = d.user_id AND p.text ILIKE ? ESCAPE '\\')
             OR EXISTS (SELECT 1 FROM lab_results r WHERE r.document_id = d.id AND r.user_id = d.user_id AND r.test_name ILIKE ? ESCAPE '\\')
             OR EXISTS (SELECT 1 FROM medications m WHERE m.document_id = d.id AND m.user_id = d.user_id AND m.name ILIKE ? ESCAPE '\\'))
            """;
    private static final int EXACT_PLACEHOLDERS = 8;

    /** A similarly spelled whole word appears (pg_trgm {@code <<%}, indexable). Each placeholder takes the word. */
    private static final String SIMILAR = """
            (? <<% d.title OR ? <<% d.summary OR ? <<% d.provider_name OR ? <<% d.facility_name
             OR EXISTS (SELECT 1 FROM document_pages p WHERE p.document_id = d.id AND p.user_id = d.user_id AND ? <<% p.text)
             OR EXISTS (SELECT 1 FROM lab_results r WHERE r.document_id = d.id AND r.user_id = d.user_id AND ? <<% r.test_name)
             OR EXISTS (SELECT 1 FROM medications m WHERE m.document_id = d.id AND m.user_id = d.user_id AND ? <<% m.name))
            """;

    /** How close the closest spelling is, for ranking. Each placeholder takes the word. */
    private static final String SIMILARITY = """
            GREATEST(strict_word_similarity(?, coalesce(d.title, '')), strict_word_similarity(?, coalesce(d.summary, '')),
                     strict_word_similarity(?, coalesce(d.provider_name, '')), strict_word_similarity(?, coalesce(d.facility_name, '')),
                     (SELECT coalesce(max(strict_word_similarity(?, p.text)), 0) FROM document_pages p WHERE p.document_id = d.id AND p.user_id = d.user_id),
                     (SELECT coalesce(max(strict_word_similarity(?, r.test_name)), 0) FROM lab_results r WHERE r.document_id = d.id AND r.user_id = d.user_id),
                     (SELECT coalesce(max(strict_word_similarity(?, m.name)), 0) FROM medications m WHERE m.document_id = d.id AND m.user_id = d.user_id))
            """;
    private static final int SIMILAR_PLACEHOLDERS = 7;

    /** The page whose text matches the word best: as typed first, then the closest spelling. */
    private static final String BEST_PAGE = """
            SELECT DISTINCT ON (document_id) document_id, page_number, text
            FROM document_pages
            WHERE user_id = ? AND document_id = ANY (?) AND (text ILIKE ? ESCAPE '\\' OR (? AND ? <<% text))
            ORDER BY document_id, (text ILIKE ? ESCAPE '\\') DESC, strict_word_similarity(?, text) DESC, page_number
            """;

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+");

    private final JdbcTemplate jdbcTemplate;
    private final DocumentRepository documentRepository;

    public SearchService(JdbcTemplate jdbcTemplate, DocumentRepository documentRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.documentRepository = documentRepository;
    }

    private record Match(UUID id, boolean exact) {
    }

    @Transactional(readOnly = true)
    public List<SearchResult> search(UUID userId, String query) {
        List<String> words = words(query);
        if (words.isEmpty()) {
            return List.of();
        }
        // Applies to this transaction only: the threshold pg_trgm's <<% operator compares against.
        jdbcTemplate.queryForObject("SELECT set_config('pg_trgm.strict_word_similarity_threshold', ?, true)", String.class,
                SIMILARITY_THRESHOLD);

        StringBuilder sql = new StringBuilder("SELECT d.id, ");
        StringBuilder from = new StringBuilder(" FROM documents d");
        StringBuilder where = new StringBuilder(" WHERE d.user_id = ?");
        List<String> scores = new ArrayList<>();
        List<String> exacts = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            String word = words.get(i);
            String pattern = "%" + escapeLike(word) + "%";
            from.append(" CROSS JOIN LATERAL (SELECT coalesce(").append(EXACT).append(", false) AS exact) e").append(i);
            for (int k = 0; k < EXACT_PLACEHOLDERS; k++) {
                args.add(pattern);
            }
            from.append(" CROSS JOIN LATERAL (SELECT CASE WHEN e").append(i).append(".exact THEN 1.0");
            if (word.length() >= MIN_SIMILAR_LENGTH) {
                from.append(" WHEN ").append(SIMILAR).append(" THEN ").append(SIMILARITY);
                for (int k = 0; k < 2 * SIMILAR_PLACEHOLDERS; k++) {
                    args.add(word);
                }
            }
            from.append(" ELSE 0 END AS score) w").append(i);
            where.append(" AND w").append(i).append(".score > 0");
            scores.add("w" + i + ".score");
            exacts.add("e" + i + ".exact");
        }
        args.add(userId);
        sql.append(String.join(" AND ", exacts)).append(" AS exact")
                .append(from).append(where)
                .append(" ORDER BY ").append(String.join(" + ", scores))
                .append(" DESC, d.doc_date DESC NULLS LAST, d.created_at DESC LIMIT ").append(MAX_RESULTS);

        List<Match> matches = jdbcTemplate.query(sql.toString(),
                (rs, row) -> new Match(rs.getObject("id", UUID.class), rs.getBoolean("exact")), args.toArray());
        if (matches.isEmpty()) {
            return List.of();
        }

        List<UUID> ids = matches.stream().map(Match::id).toList();
        Map<UUID, Document> documents = documentRepository.findByUserIdAndIdIn(userId, ids).stream()
                .collect(Collectors.toMap(Document::getId, Function.identity()));
        Map<UUID, PageSnippet> snippets = snippets(userId, ids, words.getFirst());
        return matches.stream()
                .filter(m -> documents.containsKey(m.id()))
                .map(m -> {
                    PageSnippet s = snippets.get(m.id());
                    return new SearchResult(DocumentResponse.from(documents.get(m.id())),
                            s == null ? null : s.snippet().text(), s == null ? null : s.pageNumber(),
                            s == null ? List.of() : highlights(s.snippet().text(), words), m.exact());
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

    /** Text around a match, and the matched text itself (for highlighting). */
    record Snippet(String text, String highlight) {
    }

    private record PageSnippet(int pageNumber, Snippet snippet) {
    }

    /** For each document whose page text contains the word, or a similar spelling: the best page and its snippet. */
    private Map<UUID, PageSnippet> snippets(UUID userId, List<UUID> ids, String word) {
        Map<UUID, PageSnippet> result = new HashMap<>();
        String pattern = "%" + escapeLike(word) + "%";
        jdbcTemplate.query(BEST_PAGE, rs -> {
            Snippet snippet = snippet(rs.getString("text"), word);
            if (snippet != null) {
                result.put(rs.getObject("document_id", UUID.class), new PageSnippet(rs.getInt("page_number"), snippet));
            }
        }, userId, ids.toArray(UUID[]::new), pattern, word.length() >= MIN_SIMILAR_LENGTH, word, pattern, word);
        return result;
    }

    /** The word as typed if the text contains it, otherwise the most similar word in the text. */
    static Snippet snippet(String text, String word) {
        String flat = text.replaceAll("\\s+", " ").strip();
        int at = flat.toLowerCase(Locale.ROOT).indexOf(word.toLowerCase(Locale.ROOT));
        int length = word.length();
        if (at < 0) {
            double best = 0;
            Matcher token = TOKEN.matcher(flat);
            while (token.find()) {
                double similarity = similarity(word, token.group());
                if (similarity > best) {
                    best = similarity;
                    at = token.start();
                    length = token.end() - token.start();
                }
            }
            if (at < 0) {
                return null;
            }
        }
        int start = Math.max(0, at - SNIPPET_RADIUS);
        int end = Math.min(flat.length(), at + length + SNIPPET_RADIUS);
        String around = (start > 0 ? "…" : "") + flat.substring(start, end).strip() + (end < flat.length() ? "…" : "");
        return new Snippet(around, flat.substring(at, at + length));
    }

    /** For each word, the text in the snippet that matches it as typed or, failing that, the closest whole word. */
    static List<String> highlights(String snippet, List<String> words) {
        Set<String> result = new LinkedHashSet<>();
        String lower = snippet.toLowerCase(Locale.ROOT);
        for (String word : words) {
            int at = lower.indexOf(word.toLowerCase(Locale.ROOT));
            if (at >= 0) {
                result.add(snippet.substring(at, at + word.length()));
                continue;
            }
            if (word.length() < MIN_SIMILAR_LENGTH) {
                continue;
            }
            String best = null;
            double bestSimilarity = Double.parseDouble(SIMILARITY_THRESHOLD);
            Matcher token = TOKEN.matcher(snippet);
            while (token.find()) {
                double similarity = similarity(word, token.group());
                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity;
                    best = token.group();
                }
            }
            if (best != null) {
                result.add(best);
            }
        }
        return List.copyOf(result);
    }

    /** Shared trigrams over all trigrams of two words, padded like pg_trgm (0..1). */
    static double similarity(String a, String b) {
        Set<String> x = trigrams(a);
        Set<String> y = trigrams(b);
        Set<String> union = new HashSet<>(x);
        union.addAll(y);
        x.retainAll(y);
        return union.isEmpty() ? 0 : (double) x.size() / union.size();
    }

    private static Set<String> trigrams(String word) {
        String padded = "  " + word.toLowerCase(Locale.ROOT) + " ";
        Set<String> result = new HashSet<>();
        for (int i = 0; i + 3 <= padded.length(); i++) {
            result.add(padded.substring(i, i + 3));
        }
        return result;
    }
}
