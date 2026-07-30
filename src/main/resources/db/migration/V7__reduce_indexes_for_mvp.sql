DROP INDEX IF EXISTS idx_trips_user_id;
DROP INDEX IF EXISTS idx_trips_user_status;
DROP INDEX IF EXISTS idx_trips_user_dates;

DROP INDEX IF EXISTS idx_policies_user_status;
DROP INDEX IF EXISTS idx_policies_user_dates;

DROP INDEX IF EXISTS idx_policy_documents_trip_id;
DROP INDEX IF EXISTS idx_policy_documents_parse_status;

DROP INDEX IF EXISTS idx_exclusion_conditions_severity;

DROP INDEX IF EXISTS idx_policy_chunks_coverage_category;

DROP INDEX IF EXISTS idx_chat_sessions_trip_id;
DROP INDEX IF EXISTS idx_chat_sessions_policy_id;
DROP INDEX IF EXISTS idx_chat_sessions_user_status;

DROP INDEX IF EXISTS idx_notifications_user_read;
