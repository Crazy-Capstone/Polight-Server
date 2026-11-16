CREATE TABLE consent_history (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    consent_type VARCHAR(50) NOT NULL,
    document_version VARCHAR(30) NOT NULL,
    agreed BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_consent_history_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_consent_history_user_type_id
    ON consent_history (user_id, consent_type, id DESC);
