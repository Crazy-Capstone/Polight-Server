DROP INDEX IF EXISTS idx_analysis_results_document_active;
DROP INDEX IF EXISTS idx_analysis_results_policy_active;
DROP INDEX IF EXISTS idx_analysis_results_document_id;

ALTER TABLE IF EXISTS analysis_results
  DROP COLUMN IF EXISTS is_active,
  DROP COLUMN IF EXISTS analysis_version,
  DROP COLUMN IF EXISTS chunking_version;

DO
$$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = 'public'
      AND table_name = 'analysis_results'
  )
  AND NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'uk_analysis_results_document_id'
  ) THEN
    ALTER TABLE analysis_results
      ADD CONSTRAINT uk_analysis_results_document_id UNIQUE (document_id);
  END IF;
END
$$;

DROP INDEX IF EXISTS idx_coverage_items_policy_id;
DROP INDEX IF EXISTS idx_coverage_items_policy_sort;
DROP INDEX IF EXISTS idx_coverage_items_analysis_result_id;

ALTER TABLE IF EXISTS coverage_items
  DROP CONSTRAINT IF EXISTS fk_coverage_items_policy,
  DROP CONSTRAINT IF EXISTS coverage_items_policy_id_fkey,
  DROP COLUMN IF EXISTS policy_id;

ALTER TABLE IF EXISTS policy_chunks
  ADD COLUMN IF NOT EXISTS user_id UUID,
  ADD COLUMN IF NOT EXISTS trip_id UUID,
  ADD COLUMN IF NOT EXISTS policy_id UUID,
  ADD COLUMN IF NOT EXISTS document_id UUID;

DO
$$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = 'public'
      AND table_name = 'policy_chunks'
  ) THEN
    UPDATE policy_chunks pc
    SET
      user_id = pd.user_id,
      document_id = pd.id,
      policy_id = COALESCE(pd.policy_id, ar.policy_id),
      trip_id = COALESCE(pd.trip_id, document_policy.trip_id, analysis_policy.trip_id)
    FROM analysis_results ar
    JOIN policy_documents pd ON pd.id = ar.document_id
    LEFT JOIN policies document_policy ON document_policy.id = pd.policy_id
    LEFT JOIN policies analysis_policy ON analysis_policy.id = ar.policy_id
    WHERE pc.analysis_result_id = ar.id
      AND (
        pc.user_id IS NULL
        OR pc.document_id IS NULL
        OR pc.policy_id IS DISTINCT FROM COALESCE(pd.policy_id, ar.policy_id)
        OR pc.trip_id IS DISTINCT FROM COALESCE(pd.trip_id, document_policy.trip_id, analysis_policy.trip_id)
      );
  END IF;
END
$$;

ALTER TABLE IF EXISTS policy_chunks
  ALTER COLUMN user_id SET NOT NULL,
  ALTER COLUMN document_id SET NOT NULL;

ALTER TABLE IF EXISTS policy_chunks
  ADD CONSTRAINT fk_policy_chunks_user
    FOREIGN KEY (user_id) REFERENCES users(id),
  ADD CONSTRAINT fk_policy_chunks_trip
    FOREIGN KEY (trip_id) REFERENCES trips(id),
  ADD CONSTRAINT fk_policy_chunks_policy
    FOREIGN KEY (policy_id) REFERENCES policies(id),
  ADD CONSTRAINT fk_policy_chunks_document
    FOREIGN KEY (document_id) REFERENCES policy_documents(id);

DO
$$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = 'public'
      AND table_name = 'policy_chunks'
  ) THEN
    CREATE INDEX IF NOT EXISTS idx_policy_chunks_user_trip ON policy_chunks(user_id, trip_id);
    CREATE INDEX IF NOT EXISTS idx_policy_chunks_user_policy ON policy_chunks(user_id, policy_id);
    CREATE INDEX IF NOT EXISTS idx_policy_chunks_user_document ON policy_chunks(user_id, document_id);
  END IF;
END
$$;
