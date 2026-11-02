-- Typo-tolerant search (ADR-011): trigram similarity from pg_trgm, a trusted extension the database
-- owner can create. GIN trigram indexes on the columns search reads most.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_documents_title_trgm ON documents USING gin (title gin_trgm_ops);
CREATE INDEX idx_document_pages_text_trgm ON document_pages USING gin (text gin_trgm_ops);
CREATE INDEX idx_lab_results_test_name_trgm ON lab_results USING gin (test_name gin_trgm_ops);
CREATE INDEX idx_medications_name_trgm ON medications USING gin (name gin_trgm_ops);
