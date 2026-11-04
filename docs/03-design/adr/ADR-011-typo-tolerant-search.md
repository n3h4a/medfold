# ADR-011: Typo-tolerant, ranked search with pg_trgm

**Status:** Accepted · **Amends** [ADR-003](ADR-003-search.md) · Closes
[issue #3](https://github.com/n3h4a/medfold/issues/3)

## Context

ADR-003 chose exact substring search inside PostgreSQL. It works, but medical text is exactly
where exact matching hurts: OCR misreads letters ("Haemoglobn"), people misspell drug and test
names ("cholestrol", "platlet"), and results came back in date order with no sense of how well
they matched.

## Decision

Keep search in PostgreSQL and add the `pg_trgm` extension (migration V8):

- **Every word must still match the same document**, either **as typed** (unchanged: `ILIKE`
  substring) or, for words of 4+ characters, **as a similar spelling**: pg_trgm **strict** word
  similarity above **0.45** against the title, summary, doctor, facility, page text, test names or
  medicine names. The threshold is set per transaction (`set_config(..., true)`), so it affects
  nothing else.
- **Ranking:** each word scores 1.0 when found as typed, otherwise its best similarity; documents
  are ordered by the total, then by record date. Exact matches therefore always come first.
- **Responses say what happened:** `exact: false` when a word matched only a similar spelling,
  and `highlights` lists the text in the snippet that matched each word, so the UI can mark
  "Similar spelling" and highlight the right words.
- **Snippets** come from the page that matches best: as typed first, otherwise the closest
  spelling.
- **GIN trigram indexes** on document titles, page text, test names and medicine names.

### Strict similarity, and choosing the threshold

The first version used plain `word_similarity`. Trying it on the demo reports showed a false match:
"metfor" (meant to find Metformin) also matched every report whose footer says "invented **for
Med**fold", because plain word similarity may compare against a stretch of text that crosses a word
boundary. `strict_word_similarity` only compares against whole words, which fixed it (test
`similarSpellingsMatchWholeWordsOnly`).

Measured with vocabulary from the demo reports:

| Query | Text | Plain | Strict |
|---|---|---|---|
| `haemoglobn` | Haemoglobin | 0.82 | 0.64 |
| `cholestrol` | Cholesterol | 0.64 | 0.64 |
| `metformn` | Metformin | 0.78 | 0.58 |
| `amlodepine` | Amlodipine | 0.57 | 0.57 |
| `platlet` | Platelet | 0.55 | 0.55 |
| `diabetic` | diabetes | 0.67 | 0.50 |
| `metfor` | "invented for Medfold" (unrelated) | **0.57** | 0.27 |
| `urea` | Urine (unrelated) | 0.40 | 0.22 |

pg_trgm's default strict threshold (0.5) misses "diabetic"; 0.45 catches every misspelling above
while unrelated text stays below 0.3. Words of three letters or fewer match only as typed: their
few trigrams look similar to almost anything.

## Alternatives considered

| Option | Why not |
|---|---|
| Keep exact matching | Misses OCR errors and misspellings, which are common in exactly this domain |
| Levenshtein distance (`fuzzystrmatch`) | Needs word-by-word comparison in SQL and has no index support |
| Full-text search (`tsvector`) | Still no typo tolerance, and stemming breaks prefixes like "metfor" |
| A search engine | Same reasons as in ADR-003: a second store of medical data for no gain at this scale |

## Consequences

- Misspellings and OCR errors find the document, clearly labelled as a similar match; exact
  matches rank first, so nothing that worked before got worse (all earlier search tests pass
  unchanged).
- **Limit:** trigrams handle missing, extra or wrong letters well, swapped letters poorly
  ("metfromin" scores 0.43 against "Metformin" and isn't found).
- **Cost:** similarity scoring runs per document of the user. At personal scale this is
  milliseconds; the GIN indexes keep the "is there a similar word" checks cheap as text grows.
- Still one datastore, still owner-scoped in every statement (`SearchApiTest` checks that similar
  spellings never reach another user's documents).
