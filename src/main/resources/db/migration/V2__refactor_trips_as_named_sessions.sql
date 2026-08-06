-- 여행은 국가/도시 기반 일정이 아니라 사용자가 이름 붙인 약관 분석 세션이다.
ALTER TABLE trips RENAME COLUMN title TO trip_name;
ALTER TABLE trips DROP COLUMN country_code;
ALTER TABLE trips DROP COLUMN country_name;
ALTER TABLE trips DROP COLUMN city_name;
ALTER TABLE trips DROP COLUMN flag_emoji;

CREATE INDEX idx_trips_user_created ON trips (user_id, created_at DESC);
CREATE INDEX idx_policy_documents_trip_id ON policy_documents (trip_id);
