-- Polight-Server 베이스라인 스키마
--
-- 기존 V1~V8 마이그레이션을 하나로 통합한 것이다.
-- 기존 마이그레이션들은 Hibernate ddl-auto가 만든 스키마를 사후 보정하는 패치
-- 성격이었기 때문에, 빈 DB에서는 재현이 불가능했다(V6가 coverage_items를 FK로
-- 참조하면서 실패). 이 파일은 빈 DB에서 전체 스키마를 그대로 만들어낸다.
--
-- FK와 UNIQUE 제약 이름은 Hibernate가 자동 생성하는 이름(fk..., UK...)을 의도적으로
-- 그대로 유지한다. ddl-auto: update가 동일한 이름을 기대하므로, 이름을 바꾸면 Hibernate가
-- 같은 컬럼에 제약을 하나 더 추가한다. 참고로 ddl-auto: create는 인라인 unique(...)를
-- 써서 Postgres 자동 이름(<table>_<col>_key)을 남기므로 update와 이름이 다르다.

CREATE EXTENSION IF NOT EXISTS vector;

-- ---------------------------------------------------------------------------
-- 사용자 / 여행 / 보험 계약
-- ---------------------------------------------------------------------------

CREATE TABLE users (
    id uuid NOT NULL,
    provider character varying(30) NOT NULL,
    provider_id character varying(100) NOT NULL,
    name character varying(100) NOT NULL,
    email character varying(100),
    phone character varying(30),
    passport_name character varying(100),
    passport_no_encrypted character varying(500),
    password_hash character varying(255),
    nationality_code character varying(10),
    avatar_emoji character varying(10),
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT UKcbysvpk95086ud4n4g6mkspai UNIQUE (provider, provider_id),
    CONSTRAINT users_provider_check CHECK (((provider)::text = 'KAKAO'::text))
);

CREATE TABLE trips (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    title character varying(100) NOT NULL,
    country_code character varying(10) NOT NULL,
    country_name character varying(100) NOT NULL,
    city_name character varying(100),
    flag_emoji character varying(10),
    start_date date NOT NULL,
    end_date date NOT NULL,
    status character varying(20) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT trips_pkey PRIMARY KEY (id),
    CONSTRAINT trips_status_check CHECK (status IN ('PLANNED', 'ACTIVE', 'COMPLETED', 'CANCELLED'))
);

CREATE TABLE policies (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    trip_id uuid NOT NULL,
    insurer_name character varying(200) NOT NULL,
    product_name character varying(200) NOT NULL,
    display_name character varying(200) NOT NULL,
    policy_number_encrypted character varying(500),
    start_date date NOT NULL,
    end_date date NOT NULL,
    status character varying(20) NOT NULL,
    coverage_count integer NOT NULL,
    coverage_score integer,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT policies_pkey PRIMARY KEY (id),
    CONSTRAINT policies_status_check CHECK (status IN ('PENDING', 'ACTIVE', 'EXPIRED', 'CANCELLED'))
);

-- ---------------------------------------------------------------------------
-- 약관 문서 / 분석 실행
-- ---------------------------------------------------------------------------

CREATE TABLE policy_documents (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    trip_id uuid,
    policy_id uuid,
    original_filename character varying(255) NOT NULL,
    stored_file_path character varying(500) NOT NULL,
    content_type character varying(100),
    file_size bigint,
    parse_status character varying(20) NOT NULL,
    uploaded_at timestamp(6) without time zone NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT policy_documents_pkey PRIMARY KEY (id),
    CONSTRAINT policy_documents_parse_status_check CHECK (parse_status IN ('UPLOADED', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

-- 문서당 분석 결과는 1건만 유지한다(uk_analysis_results_document_id).
CREATE TABLE analysis_results (
    id uuid NOT NULL,
    document_id uuid NOT NULL,
    policy_id uuid,
    summary text,
    raw_result_json text,
    accuracy_score real,
    status character varying(20) NOT NULL,
    embedding_model character varying(100),
    embedding_dimension integer,
    started_at timestamp(6) without time zone NOT NULL,
    completed_at timestamp(6) without time zone,
    failure_reason text,
    analyzed_at timestamp(6) without time zone,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT analysis_results_pkey PRIMARY KEY (id),
    CONSTRAINT uk_analysis_results_document_id UNIQUE (document_id),
    CONSTRAINT analysis_results_status_check CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED'))
);

-- ---------------------------------------------------------------------------
-- 분석 결과 상세 (보장 항목과 그 하위 정보)
-- ---------------------------------------------------------------------------

CREATE TABLE coverage_items (
    id uuid NOT NULL,
    analysis_result_id uuid NOT NULL,
    title character varying(200) NOT NULL,
    subtitle character varying(500),
    category character varying(100),
    limit_label character varying(100),
    is_covered boolean NOT NULL,
    coverage_status character varying(20) NOT NULL,
    limit_amount bigint,
    limit_currency character varying(10),
    conditions text,
    sort_order integer NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT coverage_items_pkey PRIMARY KEY (id),
    CONSTRAINT coverage_items_coverage_status_check CHECK (coverage_status IN ('COVERED', 'PARTIALLY_COVERED', 'NOT_COVERED', 'EXCLUDED'))
);

CREATE TABLE coverage_detail_items (
    id uuid NOT NULL,
    coverage_item_id uuid NOT NULL,
    title character varying(200) NOT NULL,
    subtitle character varying(500),
    is_covered boolean NOT NULL,
    sort_order integer NOT NULL,
    CONSTRAINT coverage_detail_items_pkey PRIMARY KEY (id)
);

CREATE TABLE sub_coverage_limits (
    id uuid NOT NULL,
    coverage_item_id uuid NOT NULL,
    label character varying(100) NOT NULL,
    value character varying(200) NOT NULL,
    description character varying(500),
    limit_amount bigint,
    limit_currency character varying(10),
    sort_order integer NOT NULL,
    CONSTRAINT sub_coverage_limits_pkey PRIMARY KEY (id)
);

CREATE TABLE required_documents (
    id uuid NOT NULL,
    coverage_item_id uuid NOT NULL,
    document_name character varying(200) NOT NULL,
    is_mandatory boolean NOT NULL,
    sort_order integer NOT NULL,
    CONSTRAINT required_documents_pkey PRIMARY KEY (id)
);

CREATE TABLE exclusion_conditions (
    id uuid NOT NULL,
    coverage_item_id uuid NOT NULL,
    title character varying(200) NOT NULL,
    description text,
    source_text text,
    severity character varying(20) NOT NULL,
    sort_order integer NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT exclusion_conditions_pkey PRIMARY KEY (id),
    CONSTRAINT exclusion_conditions_severity_check CHECK (severity IN ('GENERAL', 'WARNING', 'CRITICAL'))
);

-- ---------------------------------------------------------------------------
-- RAG (검색 대상 청크와 근거 연결)
-- ---------------------------------------------------------------------------

-- user_id / trip_id / policy_id / document_id는 RAG 필터링 조인을 없애기 위한
-- 의도적 비정규화다. 값은 PolicyChunk 생성 시 resolvePolicy()/resolveTrip()이 채운다.
CREATE TABLE policy_chunks (
    id uuid NOT NULL,
    analysis_result_id uuid NOT NULL,
    user_id uuid NOT NULL,
    trip_id uuid,
    policy_id uuid,
    document_id uuid NOT NULL,
    chunk_index integer NOT NULL,
    source_content_type character varying(30) NOT NULL,
    clause_type character varying(30) NOT NULL,
    page_start integer,
    page_end integer,
    section_title character varying(500),
    clause_path character varying(300),
    coverage_category character varying(100),
    content text NOT NULL,
    summary text,
    embedding vector(1536),
    char_count integer NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT policy_chunks_pkey PRIMARY KEY (id),
    CONSTRAINT uk_policy_chunks_analysis_chunk_index UNIQUE (analysis_result_id, chunk_index),
    CONSTRAINT policy_chunks_clause_type_check CHECK (clause_type IN ('GENERAL', 'COVERAGE', 'EXCLUSION', 'CONDITION', 'LIMIT', 'DEFINITION', 'PROCEDURE', 'REQUIRED_DOCUMENT')),
    CONSTRAINT policy_chunks_source_content_type_check CHECK (source_content_type IN ('TEXT', 'TABLE', 'OCR_TEXT', 'IMAGE_CAPTION'))
);

CREATE TABLE coverage_item_sources (
    id uuid NOT NULL,
    coverage_item_id uuid NOT NULL,
    policy_chunk_id uuid NOT NULL,
    source_role character varying(30) NOT NULL,
    quote_text text,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT coverage_item_sources_pkey PRIMARY KEY (id),
    CONSTRAINT uk_coverage_item_sources_item_chunk_role UNIQUE (coverage_item_id, policy_chunk_id, source_role),
    CONSTRAINT coverage_item_sources_source_role_check CHECK (source_role IN ('PRIMARY', 'CONDITION', 'EXCLUSION', 'LIMIT', 'PROCEDURE', 'REQUIRED_DOCUMENT', 'DEFINITION'))
);

-- ---------------------------------------------------------------------------
-- 챗 / 긴급 연락처 / 알림
-- ---------------------------------------------------------------------------

CREATE TABLE chat_sessions (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    trip_id uuid,
    policy_id uuid,
    title character varying(100) NOT NULL,
    status character varying(20) NOT NULL,
    started_at timestamp(6) without time zone NOT NULL,
    last_active_at timestamp(6) without time zone NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT chat_sessions_pkey PRIMARY KEY (id),
    CONSTRAINT chat_sessions_status_check CHECK (status IN ('OPEN', 'CLOSED'))
);

CREATE TABLE chat_messages (
    id uuid NOT NULL,
    session_id uuid NOT NULL,
    sender character varying(20) NOT NULL,
    response_type character varying(30) NOT NULL,
    content text NOT NULL,
    metadata_json text,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT chat_messages_pkey PRIMARY KEY (id),
    CONSTRAINT chat_messages_sender_check CHECK (sender IN ('USER', 'ASSISTANT', 'SYSTEM')),
    CONSTRAINT chat_messages_response_type_check CHECK (response_type IN ('TEXT', 'HOSPITAL_CARDS', 'COVERAGE_CARDS', 'EMERGENCY_CONTACTS', 'POLICY_SUMMARY'))
);

-- 국가별 공용 데이터이므로 policy_id를 참조하지 않는다.
CREATE TABLE emergency_contacts (
    id uuid NOT NULL,
    country_code character varying(10) NOT NULL,
    type character varying(30) NOT NULL,
    name character varying(200) NOT NULL,
    phone character varying(50),
    insurer_name character varying(200),
    description character varying(500),
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT emergency_contacts_pkey PRIMARY KEY (id),
    CONSTRAINT emergency_contacts_type_check CHECK (type IN ('EMBASSY', 'POLICE', 'AMBULANCE', 'INSURER', 'PARTNER'))
);

CREATE TABLE notifications (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    type character varying(30) NOT NULL,
    title character varying(200) NOT NULL,
    body character varying(500) NOT NULL,
    deep_link character varying(500),
    read_at timestamp(6) without time zone,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT notifications_pkey PRIMARY KEY (id),
    CONSTRAINT notifications_type_check CHECK (type IN ('POLICY_EXPIRING', 'RENEWAL', 'ANALYSIS_DONE', 'SYSTEM'))
);

CREATE TABLE notification_preferences (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    push_enabled boolean NOT NULL,
    analysis_done_enabled boolean NOT NULL,
    policy_expiry_enabled boolean NOT NULL,
    renewal_enabled boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT notification_preferences_pkey PRIMARY KEY (id),
    CONSTRAINT UKn2jopkbm16qv3xelbvoyjkd0g UNIQUE (user_id)
);

-- ---------------------------------------------------------------------------
-- 인덱스 (V7에서 MVP 기준으로 축소한 결과가 반영되어 있다)
-- ---------------------------------------------------------------------------

CREATE INDEX idx_policies_user_id ON policies USING btree (user_id);
CREATE INDEX idx_policies_trip_id ON policies USING btree (trip_id);

CREATE INDEX idx_policy_documents_user_id ON policy_documents USING btree (user_id);
CREATE INDEX idx_policy_documents_policy_id ON policy_documents USING btree (policy_id);

CREATE INDEX idx_analysis_results_policy_id ON analysis_results USING btree (policy_id);
CREATE INDEX idx_analysis_results_status ON analysis_results USING btree (status);

CREATE INDEX idx_coverage_items_analysis_sort ON coverage_items USING btree (analysis_result_id, sort_order);
CREATE INDEX idx_coverage_detail_items_coverage_sort ON coverage_detail_items USING btree (coverage_item_id, sort_order);
CREATE INDEX idx_sub_coverage_limits_coverage_sort ON sub_coverage_limits USING btree (coverage_item_id, sort_order);
CREATE INDEX idx_required_documents_coverage_sort ON required_documents USING btree (coverage_item_id, sort_order);
CREATE INDEX idx_exclusion_conditions_coverage_sort ON exclusion_conditions USING btree (coverage_item_id, sort_order);

-- RAG 스코프 검색용 복합 인덱스
CREATE INDEX idx_policy_chunks_user_trip ON policy_chunks USING btree (user_id, trip_id);
CREATE INDEX idx_policy_chunks_user_policy ON policy_chunks USING btree (user_id, policy_id);
CREATE INDEX idx_policy_chunks_user_document ON policy_chunks USING btree (user_id, document_id);

CREATE INDEX idx_coverage_item_sources_policy_chunk_id ON coverage_item_sources USING btree (policy_chunk_id);

CREATE INDEX idx_chat_sessions_user_id ON chat_sessions USING btree (user_id);
CREATE INDEX idx_chat_messages_session_created ON chat_messages USING btree (session_id, created_at);

CREATE INDEX idx_emergency_contacts_country_type ON emergency_contacts USING btree (country_code, type);

CREATE INDEX idx_notifications_user_created ON notifications USING btree (user_id, created_at);

-- ---------------------------------------------------------------------------
-- 외래 키
--
-- 이름은 Hibernate 자동 생성 이름을 그대로 유지한다(위 헤더 주석 참고).
-- ---------------------------------------------------------------------------

ALTER TABLE ONLY trips
    ADD CONSTRAINT fk8wb14dx6ed0bpp3planbay88u FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE ONLY policies
    ADD CONSTRAINT fkah8s5yurk7145x8c18kv9um0l FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE ONLY policies
    ADD CONSTRAINT fkpuq1lug8rmylnj4rgslc1uqgj FOREIGN KEY (trip_id) REFERENCES trips(id);

ALTER TABLE ONLY policy_documents
    ADD CONSTRAINT fkgek2ry28e5qdqp0voj17q8fds FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE ONLY policy_documents
    ADD CONSTRAINT fk6x9r0lmxq9dwp9g7dmfn586f2 FOREIGN KEY (trip_id) REFERENCES trips(id);
ALTER TABLE ONLY policy_documents
    ADD CONSTRAINT fkh1s3eovwjkoyh11iffiwlwn8r FOREIGN KEY (policy_id) REFERENCES policies(id);

ALTER TABLE ONLY analysis_results
    ADD CONSTRAINT fk1v9ifqtty6t35rq8f2by2kab9 FOREIGN KEY (document_id) REFERENCES policy_documents(id);
ALTER TABLE ONLY analysis_results
    ADD CONSTRAINT fkra4yqh5mb2x0f92ccmcno7nyt FOREIGN KEY (policy_id) REFERENCES policies(id);

ALTER TABLE ONLY coverage_items
    ADD CONSTRAINT fk7xwx2otnpo6mhngjbrnjvhml1 FOREIGN KEY (analysis_result_id) REFERENCES analysis_results(id);

ALTER TABLE ONLY coverage_detail_items
    ADD CONSTRAINT fkfv882rj97loav3m485emomy1d FOREIGN KEY (coverage_item_id) REFERENCES coverage_items(id);

ALTER TABLE ONLY sub_coverage_limits
    ADD CONSTRAINT fkaomrfk3cu2igi4wvdtp80n9m1 FOREIGN KEY (coverage_item_id) REFERENCES coverage_items(id);

ALTER TABLE ONLY required_documents
    ADD CONSTRAINT fks63ysv13tq57unqd695sn5o3n FOREIGN KEY (coverage_item_id) REFERENCES coverage_items(id);

ALTER TABLE ONLY exclusion_conditions
    ADD CONSTRAINT fk1c3q1mohdn09j00duss7h53ww FOREIGN KEY (coverage_item_id) REFERENCES coverage_items(id);

ALTER TABLE ONLY policy_chunks
    ADD CONSTRAINT fka089lqavhfpcqxgnortltyle1 FOREIGN KEY (analysis_result_id) REFERENCES analysis_results(id);
ALTER TABLE ONLY policy_chunks
    ADD CONSTRAINT fkb3gj57i7eq7q1n6co18m54vi9 FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE ONLY policy_chunks
    ADD CONSTRAINT fkp6gn5uow440t4jt9uvjpp9e8s FOREIGN KEY (trip_id) REFERENCES trips(id);
ALTER TABLE ONLY policy_chunks
    ADD CONSTRAINT fknlix1f5w7erhayuj2yr9lat48 FOREIGN KEY (policy_id) REFERENCES policies(id);
ALTER TABLE ONLY policy_chunks
    ADD CONSTRAINT fkli9pgvg9catbrhajamg0393k7 FOREIGN KEY (document_id) REFERENCES policy_documents(id);

ALTER TABLE ONLY coverage_item_sources
    ADD CONSTRAINT fkrssvvtrg55e8e7r4ydsodkra6 FOREIGN KEY (coverage_item_id) REFERENCES coverage_items(id);
ALTER TABLE ONLY coverage_item_sources
    ADD CONSTRAINT fkcepsmnqoc7yukn8633syjgpyq FOREIGN KEY (policy_chunk_id) REFERENCES policy_chunks(id);

ALTER TABLE ONLY chat_sessions
    ADD CONSTRAINT fk82ky97glaomlmhjqae1d0esmy FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE ONLY chat_sessions
    ADD CONSTRAINT fkmo9mucjwynf0pd1uexaf92oko FOREIGN KEY (trip_id) REFERENCES trips(id);
ALTER TABLE ONLY chat_sessions
    ADD CONSTRAINT fkqyaei9cw6pr0oqnuuxolrg5se FOREIGN KEY (policy_id) REFERENCES policies(id);

ALTER TABLE ONLY chat_messages
    ADD CONSTRAINT fk3cpkdtwdxndrjhrx3gt9q5ux9 FOREIGN KEY (session_id) REFERENCES chat_sessions(id);

ALTER TABLE ONLY notifications
    ADD CONSTRAINT fk9y21adhxn0ayjhfocscqox7bh FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE ONLY notification_preferences
    ADD CONSTRAINT fkt9qjvmcl36i14utm5uptyqg84 FOREIGN KEY (user_id) REFERENCES users(id);
