-- Results of processing a document: page text, extracted lab results, medicines and term
-- definitions, plus the job queue and an audit record of every AI run.
-- Every table carries user_id so reads can always be scoped to the owner.

CREATE TABLE document_pages (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id  UUID        NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    user_id      UUID        NOT NULL,
    page_number  INTEGER     NOT NULL,
    text         TEXT        NOT NULL DEFAULT '',
    source       VARCHAR(20) NOT NULL,
    confidence   REAL,
    char_count   INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT uq_document_pages_page UNIQUE (document_id, page_number),
    CONSTRAINT ck_document_pages_page_number CHECK (page_number > 0),
    CONSTRAINT ck_document_pages_source CHECK (source IN ('PDF_TEXT', 'TESSERACT', 'CLOUD', 'NONE')),
    CONSTRAINT ck_document_pages_confidence CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1))
);

CREATE TABLE lab_results (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id   UUID          NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    user_id       UUID          NOT NULL,
    position      INTEGER       NOT NULL,
    test_name     VARCHAR(255)  NOT NULL,
    test_key      VARCHAR(100)  NOT NULL,
    value_text    VARCHAR(100)  NOT NULL,
    value_numeric NUMERIC(18, 6),
    unit          VARCHAR(50),
    ref_text      VARCHAR(100),
    ref_low       NUMERIC(18, 6),
    ref_high      NUMERIC(18, 6),
    -- Computed by the backend from the report's own reference range, never by the AI.
    flag          VARCHAR(10),
    observed_on   DATE,
    page_number   INTEGER,
    user_edited   BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_lab_results_flag CHECK (flag IS NULL OR flag IN ('LOW', 'NORMAL', 'HIGH'))
);

-- Trends: all values of one test for one user over time.
CREATE INDEX idx_lab_results_user_test ON lab_results (user_id, test_key, observed_on);
CREATE INDEX idx_lab_results_document ON lab_results (document_id, position);

CREATE TABLE medications (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id   UUID         NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    user_id       UUID         NOT NULL,
    position      INTEGER      NOT NULL,
    name          VARCHAR(255) NOT NULL,
    dosage        VARCHAR(100),
    frequency     VARCHAR(100),
    route         VARCHAR(50),
    duration      VARCHAR(100),
    instructions  VARCHAR(500),
    prescribed_on DATE,
    user_edited   BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_medications_user ON medications (user_id, prescribed_on DESC);
CREATE INDEX idx_medications_document ON medications (document_id, position);

CREATE TABLE document_terms (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID         NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    user_id     UUID         NOT NULL,
    position    INTEGER      NOT NULL,
    term        VARCHAR(100) NOT NULL,
    definition  VARCHAR(300) NOT NULL
);

CREATE INDEX idx_document_terms_document ON document_terms (document_id, position);

-- Work queue for the processing worker (a table, not a message broker: simple and durable).
CREATE TABLE processing_jobs (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id  UUID        NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts     INTEGER     NOT NULL DEFAULT 0,
    max_attempts INTEGER     NOT NULL DEFAULT 3,
    next_run_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_at    TIMESTAMPTZ,
    last_error   TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_processing_jobs_status CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED')),
    CONSTRAINT ck_processing_jobs_attempts CHECK (attempts >= 0 AND max_attempts > 0)
);

-- A document can have at most one job waiting or running.
CREATE UNIQUE INDEX uq_processing_jobs_active ON processing_jobs (document_id) WHERE status IN ('PENDING', 'RUNNING');
CREATE INDEX idx_processing_jobs_due ON processing_jobs (status, next_run_at);

-- Audit trail of every successful AI run: what produced the stored data, and how.
CREATE TABLE extraction_runs (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID         NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    user_id         UUID         NOT NULL,
    ocr_provider    VARCHAR(20),
    llm_provider    VARCHAR(50)  NOT NULL,
    llm_model       VARCHAR(100) NOT NULL,
    prompt_version  VARCHAR(50)  NOT NULL,
    attempts        INTEGER      NOT NULL,
    safety_filtered BOOLEAN      NOT NULL DEFAULT FALSE,
    safety_issues   TEXT,
    input_tokens    INTEGER,
    output_tokens   INTEGER,
    duration_ms     INTEGER,
    -- Exactly what the AI service returned, for debugging and re-deriving data later.
    raw_output      TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_extraction_runs_document ON extraction_runs (document_id, created_at DESC);
