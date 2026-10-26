-- Written in the withdrawal transaction, consumed only after commit.
CREATE TABLE pending_document_deletions (
    id uuid PRIMARY KEY,
    stored_file_path character varying(500) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    next_attempt_at timestamp(6) without time zone NOT NULL
);

CREATE INDEX idx_pending_document_deletions_next_attempt_at
    ON pending_document_deletions (next_attempt_at);
