-- Uploaded medical documents. One row per file; extracted data lives in child tables (later migrations).

CREATE TABLE documents (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,

    -- File
    original_filename VARCHAR(255) NOT NULL,
    storage_key       VARCHAR(500) NOT NULL,
    mime_type         VARCHAR(100) NOT NULL,
    size_bytes        BIGINT       NOT NULL,
    sha256            VARCHAR(64)  NOT NULL,

    -- Processing
    status            VARCHAR(20)  NOT NULL DEFAULT 'UPLOADED',
    failure_reason    VARCHAR(500),
    page_count        INTEGER,

    -- Extracted metadata (filled by AI extraction, editable by the user)
    doc_type          VARCHAR(30),
    doc_date          DATE,
    title             VARCHAR(255),
    provider_name     VARCHAR(255),
    facility_name     VARCHAR(255),
    summary           TEXT,
    user_edited       BOOLEAN      NOT NULL DEFAULT FALSE,

    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_documents_storage_key UNIQUE (storage_key),
    -- The same file can't be uploaded twice by one user, but two users may upload identical files.
    CONSTRAINT uq_documents_user_sha256 UNIQUE (user_id, sha256),
    CONSTRAINT ck_documents_size_positive CHECK (size_bytes > 0),
    CONSTRAINT ck_documents_page_count CHECK (page_count IS NULL OR page_count > 0),
    CONSTRAINT ck_documents_status CHECK (status IN ('UPLOADED', 'PROCESSING', 'READY', 'FAILED')),
    CONSTRAINT ck_documents_doc_type CHECK (doc_type IS NULL OR doc_type IN (
        'LAB_REPORT', 'PRESCRIPTION', 'DISCHARGE_SUMMARY', 'IMAGING_REPORT', 'CONSULTATION_NOTE', 'OTHER'))
);

-- Timeline: newest record date first, undated documents last.
CREATE INDEX idx_documents_user_timeline ON documents (user_id, doc_date DESC NULLS LAST, created_at DESC);
CREATE INDEX idx_documents_user_status ON documents (user_id, status);
CREATE INDEX idx_documents_user_doc_type ON documents (user_id, doc_type);
