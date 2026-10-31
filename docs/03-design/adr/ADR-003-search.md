# ADR-003: Substring search in PostgreSQL, no search engine

**Status:** Accepted

## Context

"Find everything" is the product's promise: a user should find a document by a test name, a
medicine, a doctor, a hospital or any word in the page text. Medical terms are often partial or
abbreviated ("metfor", "hba1c"), and OCR text has small errors. One person has tens to a few
hundred documents.

## Decision

`GET /search?q=` splits the query into up to six words; a document matches when **every** word
appears (`ILIKE '%word%'`, with `%`, `_` and `\` escaped) somewhere in it: title, summary, doctor,
facility, file name, page text, a lab test name or a medicine name. Every statement, including
the sub-queries, is scoped to the user. Results come in timeline order, at most 50, each with a
snippet of the page text around the first word.

## Alternatives considered

| Option | Why not |
|---|---|
| PostgreSQL full-text search (`tsvector`, GIN) | Stems and tokenises words, so "metfor" or "hba1" don't match prefixes or inside tokens; language-specific stemming suits prose, not lab names |
| `pg_trgm` trigram index | Would add typo tolerance; unnecessary at one person's data size, a natural next step |
| Elasticsearch / OpenSearch | A second datastore to secure, sync and back up, for medical data, for no gain at this scale |

## Consequences

- Simple, predictable, and private: data never leaves PostgreSQL, and ownership is enforced in SQL.
- Sequential scans over one user's rows are fast at personal scale (milliseconds for hundreds of
  documents). Ranking and typo tolerance are missing; `pg_trgm` is the planned step if needed
  ([ROADMAP.md](../../../ROADMAP.md)).
