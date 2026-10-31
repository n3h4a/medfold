-- Wrong-password attempts for login throttling, shared by every backend instance.
-- Keys ("account:<email>", "address:<ip>") are stored as SHA-256 hashes: attempted emails and
-- client addresses are never kept in readable form. Rows older than the window are deleted.
CREATE TABLE login_failures (
    id           BIGSERIAL   PRIMARY KEY,
    throttle_key CHAR(64)    NOT NULL,
    failed_at    TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_login_failures_key_time ON login_failures (throttle_key, failed_at DESC);
CREATE INDEX idx_login_failures_time ON login_failures (failed_at);
