-- Medfold database schema, as created by the Flyway migrations V1–V7
-- (backend/src/main/resources/db/migration). Reference only: Flyway is the source of truth.
-- Regenerate: docker compose exec db pg_dump -U medfold -d medfold --schema-only --no-owner --no-privileges --exclude-table=flyway_schema_history


CREATE TABLE public.document_pages (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    document_id uuid NOT NULL,
    user_id uuid NOT NULL,
    page_number integer NOT NULL,
    text text DEFAULT ''::text NOT NULL,
    source character varying(20) NOT NULL,
    confidence real,
    char_count integer DEFAULT 0 NOT NULL,
    CONSTRAINT ck_document_pages_confidence CHECK (((confidence IS NULL) OR ((confidence >= (0)::double precision) AND (confidence <= (1)::double precision)))),
    CONSTRAINT ck_document_pages_page_number CHECK ((page_number > 0)),
    CONSTRAINT ck_document_pages_source CHECK (((source)::text = ANY ((ARRAY['PDF_TEXT'::character varying, 'TESSERACT'::character varying, 'CLOUD'::character varying, 'NONE'::character varying])::text[])))
);

CREATE TABLE public.document_terms (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    document_id uuid NOT NULL,
    user_id uuid NOT NULL,
    "position" integer NOT NULL,
    term character varying(100) NOT NULL,
    definition character varying(300) NOT NULL
);

CREATE TABLE public.documents (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    original_filename character varying(255) NOT NULL,
    storage_key character varying(500) NOT NULL,
    mime_type character varying(100) NOT NULL,
    size_bytes bigint NOT NULL,
    sha256 character varying(64) NOT NULL,
    status character varying(20) DEFAULT 'UPLOADED'::character varying NOT NULL,
    failure_reason character varying(500),
    page_count integer,
    doc_type character varying(30),
    doc_date date,
    title character varying(255),
    provider_name character varying(255),
    facility_name character varying(255),
    summary text,
    user_edited boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_documents_doc_type CHECK (((doc_type IS NULL) OR ((doc_type)::text = ANY ((ARRAY['LAB_REPORT'::character varying, 'PRESCRIPTION'::character varying, 'DISCHARGE_SUMMARY'::character varying, 'IMAGING_REPORT'::character varying, 'CONSULTATION_NOTE'::character varying, 'OTHER'::character varying])::text[])))),
    CONSTRAINT ck_documents_page_count CHECK (((page_count IS NULL) OR (page_count > 0))),
    CONSTRAINT ck_documents_size_positive CHECK ((size_bytes > 0)),
    CONSTRAINT ck_documents_status CHECK (((status)::text = ANY ((ARRAY['UPLOADED'::character varying, 'PROCESSING'::character varying, 'READY'::character varying, 'FAILED'::character varying])::text[])))
);

CREATE TABLE public.extraction_runs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    document_id uuid NOT NULL,
    user_id uuid NOT NULL,
    ocr_provider character varying(20),
    llm_provider character varying(50) NOT NULL,
    llm_model character varying(100) NOT NULL,
    prompt_version character varying(50) NOT NULL,
    attempts integer NOT NULL,
    safety_filtered boolean DEFAULT false NOT NULL,
    safety_issues text,
    input_tokens integer,
    output_tokens integer,
    duration_ms integer,
    raw_output text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.lab_results (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    document_id uuid NOT NULL,
    user_id uuid NOT NULL,
    "position" integer NOT NULL,
    test_name character varying(255) NOT NULL,
    test_key character varying(100) NOT NULL,
    value_text character varying(100) NOT NULL,
    value_numeric numeric(18,6),
    unit character varying(50),
    ref_text character varying(100),
    ref_low numeric(18,6),
    ref_high numeric(18,6),
    flag character varying(10),
    observed_on date,
    page_number integer,
    user_edited boolean DEFAULT false NOT NULL,
    CONSTRAINT ck_lab_results_flag CHECK (((flag IS NULL) OR ((flag)::text = ANY ((ARRAY['LOW'::character varying, 'NORMAL'::character varying, 'HIGH'::character varying])::text[]))))
);

CREATE TABLE public.login_failures (
    id bigint NOT NULL,
    throttle_key character(64) NOT NULL,
    failed_at timestamp with time zone NOT NULL
);
CREATE SEQUENCE public.login_failures_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.login_failures_id_seq OWNED BY public.login_failures.id;

CREATE TABLE public.medications (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    document_id uuid NOT NULL,
    user_id uuid NOT NULL,
    "position" integer NOT NULL,
    name character varying(255) NOT NULL,
    dosage character varying(100),
    frequency character varying(100),
    route character varying(50),
    duration character varying(100),
    instructions character varying(500),
    prescribed_on date,
    user_edited boolean DEFAULT false NOT NULL,
    extracted_name character varying(255) NOT NULL
);

CREATE TABLE public.processing_jobs (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    document_id uuid NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    max_attempts integer DEFAULT 3 NOT NULL,
    next_run_at timestamp with time zone DEFAULT now() NOT NULL,
    locked_at timestamp with time zone,
    last_error text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    user_id uuid NOT NULL,
    CONSTRAINT ck_processing_jobs_attempts CHECK (((attempts >= 0) AND (max_attempts > 0))),
    CONSTRAINT ck_processing_jobs_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'RUNNING'::character varying, 'DONE'::character varying, 'FAILED'::character varying])::text[])))
);

CREATE TABLE public.refresh_tokens (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    token_hash character varying(64) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.users (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    email character varying(255) NOT NULL,
    password_hash character varying(100) NOT NULL,
    full_name character varying(120),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_users_email_normalized CHECK (((email)::text = lower(btrim((email)::text))))
);

ALTER TABLE ONLY public.login_failures ALTER COLUMN id SET DEFAULT nextval('public.login_failures_id_seq'::regclass);

ALTER TABLE ONLY public.document_pages
    ADD CONSTRAINT document_pages_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.document_terms
    ADD CONSTRAINT document_terms_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.documents
    ADD CONSTRAINT documents_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.extraction_runs
    ADD CONSTRAINT extraction_runs_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.lab_results
    ADD CONSTRAINT lab_results_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.login_failures
    ADD CONSTRAINT login_failures_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.medications
    ADD CONSTRAINT medications_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.processing_jobs
    ADD CONSTRAINT processing_jobs_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.document_pages
    ADD CONSTRAINT uq_document_pages_page UNIQUE (document_id, page_number);

ALTER TABLE ONLY public.documents
    ADD CONSTRAINT uq_documents_storage_key UNIQUE (storage_key);

ALTER TABLE ONLY public.documents
    ADD CONSTRAINT uq_documents_user_sha256 UNIQUE (user_id, sha256);

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uq_users_email UNIQUE (email);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);

CREATE INDEX idx_document_terms_document ON public.document_terms USING btree (document_id, "position");

CREATE INDEX idx_documents_user_doc_type ON public.documents USING btree (user_id, doc_type);

CREATE INDEX idx_documents_user_status ON public.documents USING btree (user_id, status);

CREATE INDEX idx_documents_user_timeline ON public.documents USING btree (user_id, doc_date DESC NULLS LAST, created_at DESC);

CREATE INDEX idx_extraction_runs_document ON public.extraction_runs USING btree (document_id, created_at DESC);

CREATE INDEX idx_lab_results_document ON public.lab_results USING btree (document_id, "position");

CREATE INDEX idx_lab_results_user_test ON public.lab_results USING btree (user_id, test_key, observed_on);

CREATE INDEX idx_login_failures_key_time ON public.login_failures USING btree (throttle_key, failed_at DESC);

CREATE INDEX idx_login_failures_time ON public.login_failures USING btree (failed_at);

CREATE INDEX idx_medications_document ON public.medications USING btree (document_id, "position");

CREATE INDEX idx_medications_user ON public.medications USING btree (user_id, prescribed_on DESC);

CREATE INDEX idx_processing_jobs_due ON public.processing_jobs USING btree (status, next_run_at);

CREATE INDEX idx_refresh_tokens_user_id ON public.refresh_tokens USING btree (user_id);

CREATE UNIQUE INDEX uq_processing_jobs_active ON public.processing_jobs USING btree (document_id) WHERE ((status)::text = ANY ((ARRAY['PENDING'::character varying, 'RUNNING'::character varying])::text[]));

ALTER TABLE ONLY public.document_pages
    ADD CONSTRAINT document_pages_document_id_fkey FOREIGN KEY (document_id) REFERENCES public.documents(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.document_terms
    ADD CONSTRAINT document_terms_document_id_fkey FOREIGN KEY (document_id) REFERENCES public.documents(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.documents
    ADD CONSTRAINT documents_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.extraction_runs
    ADD CONSTRAINT extraction_runs_document_id_fkey FOREIGN KEY (document_id) REFERENCES public.documents(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.lab_results
    ADD CONSTRAINT lab_results_document_id_fkey FOREIGN KEY (document_id) REFERENCES public.documents(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.medications
    ADD CONSTRAINT medications_document_id_fkey FOREIGN KEY (document_id) REFERENCES public.documents(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.processing_jobs
    ADD CONSTRAINT processing_jobs_document_id_fkey FOREIGN KEY (document_id) REFERENCES public.documents(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;
