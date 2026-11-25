CREATE UNIQUE INDEX uk_chat_sessions_unregistered_user
    ON chat_sessions (user_id)
    WHERE trip_id IS NULL;
