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
        AND column_name = 'embedding'
    ) THEN
      ALTER TABLE policy_chunks
        ALTER COLUMN embedding TYPE vector(1536)
        USING CASE
          WHEN embedding IS NULL THEN NULL
          WHEN btrim(embedding::text) = '' THEN NULL
          ELSE embedding::vector(1536)
        END;
    ELSE
      ALTER TABLE policy_chunks
        ADD COLUMN embedding vector(1536);
    END IF;
  END IF;
END
$$;
