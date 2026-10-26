-- Keep V15 intact for databases where it has already been applied.
-- Do not silently discard paths for files that still need manual cleanup.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pending_document_deletions) THEN
        RAISE EXCEPTION
            'pending_document_deletions is not empty. Delete the listed files manually, '
            'then clear the completed rows before retrying this migration.';
    END IF;
END
$$;

DROP TABLE pending_document_deletions;
