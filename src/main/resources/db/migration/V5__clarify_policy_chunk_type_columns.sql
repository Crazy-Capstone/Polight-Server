DO
$$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = 'public'
      AND table_name = 'policy_chunks'
  ) THEN
    IF EXISTS (
      SELECT 1
      FROM information_schema.columns
      WHERE table_schema = 'public'
        AND table_name = 'policy_chunks'
        AND column_name = 'content_type'
    )
    AND NOT EXISTS (
      SELECT 1
      FROM information_schema.columns
      WHERE table_schema = 'public'
        AND table_name = 'policy_chunks'
        AND column_name = 'source_content_type'
    ) THEN
      ALTER TABLE policy_chunks
        RENAME COLUMN content_type TO source_content_type;
    END IF;

    ALTER TABLE policy_chunks
      ADD COLUMN IF NOT EXISTS source_content_type VARCHAR(30);

    UPDATE policy_chunks
    SET source_content_type = 'TEXT'
    WHERE source_content_type IS NULL;

    ALTER TABLE policy_chunks
      ALTER COLUMN source_content_type SET NOT NULL;

    ALTER TABLE policy_chunks
      DROP COLUMN IF EXISTS coverage_type;

    ALTER TABLE policy_chunks
      ADD COLUMN IF NOT EXISTS clause_type VARCHAR(30);

    UPDATE policy_chunks
    SET clause_type = 'GENERAL'
    WHERE clause_type IS NULL;

    ALTER TABLE policy_chunks
      ALTER COLUMN clause_type SET NOT NULL;
  END IF;
END
$$;
