ALTER TABLE required_documents
    ADD COLUMN IF NOT EXISTS requirement_type VARCHAR(20);
ALTER TABLE required_documents
    ADD COLUMN IF NOT EXISTS condition_text TEXT;

UPDATE required_documents
   SET requirement_type = CASE WHEN is_mandatory THEN 'REQUIRED' ELSE 'UNKNOWN' END
 WHERE requirement_type IS NULL;

ALTER TABLE required_documents
    ALTER COLUMN requirement_type SET DEFAULT 'REQUIRED';
ALTER TABLE required_documents
    ALTER COLUMN requirement_type SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conname = 'ck_required_documents_requirement_type'
           AND conrelid = 'required_documents'::regclass
    ) THEN
        ALTER TABLE required_documents
            ADD CONSTRAINT ck_required_documents_requirement_type
            CHECK (requirement_type IN ('REQUIRED', 'CONDITIONAL', 'UNKNOWN'));
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS required_document_sources (
    id UUID PRIMARY KEY,
    required_document_id UUID NOT NULL,
    terms_chunk_id UUID NOT NULL,
    quote_text TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_required_document_sources_document_id
        FOREIGN KEY (required_document_id) REFERENCES required_documents(id) ON DELETE CASCADE,
    CONSTRAINT fk_required_document_sources_chunk_id
        FOREIGN KEY (terms_chunk_id) REFERENCES policy_terms_chunks(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_required_document_sources_document
    ON required_document_sources(required_document_id);
CREATE INDEX IF NOT EXISTS idx_required_document_sources_chunk
    ON required_document_sources(terms_chunk_id);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rag_service') THEN
        GRANT SELECT ON required_documents, required_document_sources, policy_terms_coverages
            TO rag_service;
    END IF;
END $$;
