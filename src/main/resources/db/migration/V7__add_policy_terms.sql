-- 약관 본문을 상품 단위로 한 번만 저장하는 저장소.
--
-- 지금 약관 청크는 policy_chunks 에 있고, 그 테이블은 analysis_result_id 를 필수로 가진다.
-- 즉 "누가 언제 올려서 돌린 분석"에 묶여 있다. 약관은 그런 성격의 문서가 아니다 -- 같은 상품을
-- 산 사용자 1000명은 글자 하나 다르지 않은 같은 약관을 본다. 분석마다 청크를 다시 만들면
-- 같은 본문을 1000벌 저장하고 1000번 임베딩한다. 더 나쁜 것은, 사용자가 증권만 올리고
-- 약관을 올리지 않으면(대부분의 경우다) 그 사용자에게는 약관 청크가 아예 없다는 점이다.
-- 챗봇이 "이 특약은 어디까지 보장되나요"에 답할 근거가 없다.
--
-- 그래서 약관을 분석에서 떼어내 상품 단위로 둔다. 증권 분석은 "어느 약관인가"만 찾아
-- policy_terms 를 가리키고(V8 의 matched_terms_id), 본문은 모두가 같은 행을 공유한다.
--
-- policy_chunks 는 이 마이그레이션에서 건드리지 않는다. AI 서버가 rag_service 계정으로
-- 직접 INSERT 하고 있어, 구조를 바꾸면 백엔드 배포 순간 AI 쪽이 깨진다. AI 가 이 테이블로
-- 옮겨온 뒤에 별도로 정리한다.

-- ---------------------------------------------------------------------------
-- policy_terms : 약관 한 건 (= 보험사 + 상품 + 개정판)
-- ---------------------------------------------------------------------------

CREATE TABLE policy_terms (
    id uuid NOT NULL,

    insurer_name character varying(200) NOT NULL,
    product_name character varying(200) NOT NULL,

    -- 같은 상품이라도 약관은 개정된다. 2024년 가입자와 2026년 가입자는 다른 조항을 적용받으므로
    -- 개정판을 다른 행으로 둔다. 어느 개정판을 적용할지는 effective_date 로 고른다.
    revision character varying(100),
    effective_date date,

    -- UNVERIFIED : 사용자가 올린 약관. 그 사용자에게만 보인다.
    -- VERIFIED   : 운영자가 확인한 공용 약관. 같은 상품 가입자 모두가 공유한다.
    --
    -- 사용자 업로드를 곧바로 공용으로 쓰면, 잘못된 파일 하나가 같은 상품 가입자 전원의
    -- 답변 근거를 오염시킨다. 확인 전에는 올린 사람 밖으로 나가지 않게 막는다.
    verification_status character varying(20) NOT NULL,

    -- OFFICIAL    : 운영자가 보험사 공시자료에서 받아 등록한 것
    -- USER_UPLOAD : 사용자가 올린 것
    --
    -- verification_status 와 별개로 둔다. "사용자가 올렸지만 운영자가 확인해 공용이 된 약관"이
    -- 있을 수 있고, 그때 출처를 잃지 않아야 한다.
    source character varying(20) NOT NULL,

    -- 사용자 업로드일 때 원본 문서. 나중에 원문을 다시 열어보거나 재파싱할 때 쓴다.
    source_document_id uuid,

    -- UNVERIFIED 약관의 주인. 이 값으로 접근 범위를 가른다.
    owner_user_id uuid,

    -- 같은 파일을 여러 사용자가 올렸을 때 이미 만들어 둔 약관을 재사용하기 위한 지문(sha-256).
    --
    -- UNIQUE 를 일부러 걸지 않았다. 같은 본문이라도 보험사/상품명을 다르게 입력해 들어올 수 있고,
    -- 그때 INSERT 가 실패하면 업로드 자체가 막힌다. 중복은 조회해서 피하고, 놓치면 행이 하나
    -- 더 생길 뿐이다 -- 업로드가 실패하는 것보다 낫다.
    file_hash character varying(64),

    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,

    CONSTRAINT policy_terms_pkey PRIMARY KEY (id),
    CONSTRAINT policy_terms_verification_status_check
        CHECK (verification_status IN ('UNVERIFIED', 'VERIFIED')),
    CONSTRAINT policy_terms_source_check
        CHECK (source IN ('OFFICIAL', 'USER_UPLOAD')),

    -- 주인 없는 UNVERIFIED 약관을 막는다.
    --
    -- 접근 판정이 "VERIFIED 이거나 owner 가 나"인데, owner 가 null 인 UNVERIFIED 행은 그 판정에서
    -- 누구의 것도 아니게 된다. 아무도 볼 수 없어 조용히 죽은 데이터가 되거나, 판정을 잘못
    -- 고치는 순간 전원에게 노출된다. 애초에 그런 행이 생기지 않게 한다.
    CONSTRAINT policy_terms_unverified_requires_owner_check
        CHECK (verification_status = 'VERIFIED' OR owner_user_id IS NOT NULL)
);

ALTER TABLE ONLY policy_terms
    ADD CONSTRAINT fk_policy_terms_source_document
    FOREIGN KEY (source_document_id) REFERENCES policy_documents(id);

ALTER TABLE ONLY policy_terms
    ADD CONSTRAINT fk_policy_terms_owner_user
    FOREIGN KEY (owner_user_id) REFERENCES users(id);

-- 공용 약관은 (보험사, 상품, 개정판)마다 하나뿐이어야 한다.
--
-- 부분 인덱스인 이유: UNVERIFIED 에는 이 제약을 걸면 안 된다. 사용자 열 명이 같은 상품 약관을
-- 각자 올리면 열 행이 생기는 것이 정상이고, 여기서 막으면 두 번째 사용자의 업로드가 실패한다.
--
-- revision 을 coalesce 로 감싼 이유: Postgres 는 NULL 을 서로 다른 값으로 보아, 그냥 두면
-- 개정판 표기가 없는 VERIFIED 행이 같은 상품에 몇 개든 들어간다. 그러면 매칭이 무엇을 고를지
-- 알 수 없어진다.
CREATE UNIQUE INDEX uk_policy_terms_verified_product
    ON policy_terms (insurer_name, product_name, coalesce(revision, ''))
    WHERE verification_status = 'VERIFIED';

-- 증권에서 읽은 보험사/상품명으로 약관을 찾는 경로(V8 의 매칭)가 이 인덱스를 탄다.
CREATE INDEX idx_policy_terms_insurer_product
    ON policy_terms USING btree (insurer_name, product_name);

-- 매칭 후보를 모을 때 "내가 올린 UNVERIFIED" 를 추려내는 데 쓴다.
CREATE INDEX idx_policy_terms_owner_user_id
    ON policy_terms USING btree (owner_user_id);

CREATE INDEX idx_policy_terms_file_hash
    ON policy_terms USING btree (file_hash);

-- ---------------------------------------------------------------------------
-- policy_terms_chunks : 약관 본문 청크
-- ---------------------------------------------------------------------------

-- policy_chunks 와 달리 user_id / trip_id / policy_id 를 두지 않는다.
--
-- 그 컬럼들은 "이 청크를 누가 검색할 수 있는가"를 청크 자체에 박아 둔 것이었다. 약관은 상품
-- 공용 문서라 그 질문의 답이 청크가 아니라 policy_terms 에 있다(verification_status/owner).
-- 여기에 다시 두면 같은 사실을 두 곳에 적는 셈이고, 공용 약관을 쓰는 사용자가 늘 때마다
-- 청크를 복제해야 한다 -- 이 리팩토링이 없애려던 바로 그것이다.
CREATE TABLE policy_terms_chunks (
    id uuid NOT NULL,
    terms_id uuid NOT NULL,

    chunk_index integer NOT NULL,
    source_content_type character varying(30) NOT NULL,
    clause_type character varying(30) NOT NULL,

    page_start integer,
    page_end integer,
    section_title character varying(500),

    -- "제3관 제12조 제2항" 같은 조항 위치. 담보의 근거 조항을 찾을 때(clause_path 매칭)와
    -- 답변에 출처를 표시할 때 쓴다.
    clause_path character varying(300),
    coverage_category character varying(100),

    content text NOT NULL,
    summary text,

    embedding vector(1536),

    char_count integer NOT NULL,

    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,

    CONSTRAINT policy_terms_chunks_pkey PRIMARY KEY (id),

    -- 같은 약관을 두 번 적재해도 청크가 두 벌 쌓이지 않게 한다. 적재는 DELETE 후 INSERT 로 한다.
    CONSTRAINT uk_policy_terms_chunks_terms_chunk_index UNIQUE (terms_id, chunk_index),

    CONSTRAINT policy_terms_chunks_clause_type_check
        CHECK (clause_type IN ('GENERAL', 'COVERAGE', 'EXCLUSION', 'CONDITION', 'LIMIT', 'DEFINITION', 'PROCEDURE', 'REQUIRED_DOCUMENT')),
    CONSTRAINT policy_terms_chunks_source_content_type_check
        CHECK (source_content_type IN ('TEXT', 'TABLE', 'OCR_TEXT', 'IMAGE_CAPTION'))
);

ALTER TABLE ONLY policy_terms_chunks
    ADD CONSTRAINT fk_policy_terms_chunks_terms
    FOREIGN KEY (terms_id) REFERENCES policy_terms(id);

-- 벡터 인덱스(HNSW)는 만들지 않는다.
--
-- 지금 이관할 공용 약관이 8건 2225청크다. 이 규모에서는 순차 스캔이 수 ms 라 인덱스가
-- 의미 없고, 오히려 손해다 -- HNSW 는 근사 검색이라 재현율을 떨어뜨리고, m/ef_construction 을
-- 데이터 분포를 보고 정해야 하는데 아직 볼 데이터가 없다.
--
-- 청크가 수만 건이 되어 검색 지연이 실제로 보이기 시작하면 그때 만든다. 그때는 실제 분포로
-- 파라미터를 정할 수 있다.

-- ---------------------------------------------------------------------------
-- AI 서버 계정 권한
-- ---------------------------------------------------------------------------

-- AI 서버는 rag_service 계정으로 이 두 테이블에 직접 접근한다. 약관을 적재하는 주체가 AI 이고
-- (파싱·청킹·임베딩이 그쪽에 있다), 챗봇 검색도 그쪽에서 돈다.
--
-- UPDATE 를 주지 않는다. 적재는 DELETE 후 INSERT 로 하면 되고, 그러면 부분적으로 수정되다 만
-- 청크가 남을 수 없다. 더 중요한 것은 verification_status 다 -- UPDATE 권한이 있으면 AI 쪽
-- 버그 하나가 UNVERIFIED 를 VERIFIED 로 바꿔 검증 안 된 약관을 전 사용자에게 노출시킨다.
-- 승격은 운영자만 하는 일이라 백엔드 계정에만 남긴다.
--
-- 역할이 없는 환경(로컬 개발 DB 등)에서도 마이그레이션이 돌아야 하므로 존재를 확인하고 건다.
-- GRANT 는 없는 역할에 대해 그냥 실패하는데, 그 실패로 스키마 전체가 막힐 이유가 없다.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rag_service') THEN
        GRANT SELECT, INSERT, DELETE ON policy_terms TO rag_service;
        GRANT SELECT, INSERT, DELETE ON policy_terms_chunks TO rag_service;
    ELSE
        RAISE NOTICE 'rag_service 역할이 없어 GRANT 를 건너뜁니다. 배포 환경이라면 역할을 만든 뒤 수동으로 부여하세요.';
    END IF;
END
$$;
