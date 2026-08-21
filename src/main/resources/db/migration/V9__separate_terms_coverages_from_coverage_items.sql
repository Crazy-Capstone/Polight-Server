-- 약관의 보장 규칙과 사용자의 가입 담보를 다른 테이블로 가른다.
--
-- 지금 이 둘이 coverage_items 한 테이블에 섞여 있다. AI 서버의 두 경로가 같은 콜백 형식으로
-- 같은 테이블에 쓰기 때문이다.
--
--   증권 분석  -> coverage_items = "채민이 실제 가입한 담보"      (가입금액 O, 면책·서류 없음)
--   약관 분석  -> coverage_items = "이 상품 약관에 있는 보장 규칙" (면책·서류 O, 금액은 약관 예시값)
--
-- 같은 컬럼 limit_amount 가 한쪽에서는 "채민이 든 3,000만원", 다른 쪽에서는 "약관에 인쇄된
-- 예시 3,000만원"이다. 타입도 제약도 같아 데이터만 보고는 구분할 수 없다.
--
-- 나눠야 하는 이유는 공유 범위가 다르기 때문이다. 면책 조항과 청구 서류는 같은 상품을 산
-- 사람 모두에게 같다. 사용자마다 복제하면 같은 사실을 1000벌 저장하고, 약관이 개정될 때
-- 1000벌을 고쳐야 하며, 그중 하나만 놓쳐도 그 사용자만 낡은 면책 조항을 보게 된다.
-- 반대로 가입금액은 그 사람만의 사실이라 공유하면 남의 가입금액을 보여주게 된다.
--
-- 지금 하는 이유: 자식 4종과 coverage_item_sources 가 운영에서 전부 0건이다. 증권 경로가
-- 이 값들을 만들지 않고(AI 의 to_payloads 가 채우지 않는다) 약관 업로드 경로는 아직 켜지지
-- 않았다. 그래서 소유자를 바꾸는 데 이관할 데이터가 없다. 담보가 쌓인 뒤에는 사용자별로
-- 복제된 면책·서류를 상품 단위로 되접는 작업이 필요해진다.

-- ---------------------------------------------------------------------------
-- 가드: 옮길 데이터가 없어야 한다
-- ---------------------------------------------------------------------------

-- 이 마이그레이션에는 안전한 자동 변환이 없다.
--
-- 자식 행 하나하나가 "어느 사용자의 담보"에 매달려 있는데, 그것을 "어느 상품의 보장 규칙"으로
-- 옮기려면 그 담보가 어느 약관의 어느 규칙에 해당하는지 알아야 한다. 그 연결이 아직 없다.
-- 임의로 합치면 서로 다른 상품의 면책 조항이 한 규칙 아래 섞이고, 조용히 틀린 근거가 된다.
--
-- 작성 시점 운영 DB 는 다섯 테이블 모두 0건이라 실무상 이 가드는 걸리지 않는다. 걸린다면
-- 그 전제가 깨졌다는 뜻이므로 데이터를 건드리기 전에 사람이 봐야 한다.
DO $$
DECLARE
    target_table text;
    existing_count bigint;
BEGIN
    FOREACH target_table IN ARRAY ARRAY[
        'coverage_detail_items',
        'sub_coverage_limits',
        'required_documents',
        'exclusion_conditions',
        'coverage_item_sources'
    ] LOOP
        EXECUTE format('SELECT count(*) FROM %I', target_table) INTO existing_count;

        IF existing_count > 0 THEN
            RAISE EXCEPTION
                '% 에 행이 % 건 있어 policy_terms_coverages 로 옮길 수 없습니다. '
                '이 행들은 사용자 담보에 매달려 있는데, 어느 약관의 어느 보장 규칙에 해당하는지 '
                '아는 연결이 아직 없습니다. 이관 방침을 정한 뒤 다시 진행하세요.',
                target_table, existing_count;
        END IF;
    END LOOP;
END
$$;

-- ---------------------------------------------------------------------------
-- policy_terms_coverages : 약관상 보장 규칙
-- ---------------------------------------------------------------------------

-- "이 상품 약관에 이런 보장이 있다"는 사실. 가입 여부와 무관하게 상품마다 한 벌 존재한다.
--
-- coverage_items 와 컬럼이 비슷하지만 뜻이 다르다. 여기 limit_label 은 약관에 인쇄된 표기이지
-- 특정 사용자가 가입한 금액이 아니다. 그래서 limit_amount(BIGINT)를 두지 않는다 -- 정수 금액은
-- 가입 사실이고, 그것은 coverage_items 에만 있어야 한다.
CREATE TABLE policy_terms_coverages (
    id uuid NOT NULL,
    terms_id uuid NOT NULL,

    title character varying(200) NOT NULL,
    subtitle character varying(500),
    category character varying(100),

    -- 약관에 적힌 한도 표기 그대로. "가입금액의 100%", "1사고당 1억원" 같은 문구가 온다.
    limit_label character varying(100),

    -- 보장 조건 서술. 어떤 상황에서 보장되는지.
    conditions text,

    sort_order integer NOT NULL,

    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,

    CONSTRAINT policy_terms_coverages_pkey PRIMARY KEY (id),

    -- 같은 약관을 다시 적재해도 규칙이 두 벌 쌓이지 않게 한다. 적재는 DELETE 후 INSERT 다.
    --
    -- title 이 아니라 sort_order 로 잡는 이유: 한 약관 안에 같은 이름의 보장이 여러 관에 걸쳐
    -- 서술되는 일이 있어 title 에 유니크를 걸면 정상 약관의 적재가 실패한다. 대신 담보를 이
    -- 규칙에 붙일 때(매칭) 이름이 여럿이면 붙이지 않는 쪽으로 처리한다.
    CONSTRAINT uk_policy_terms_coverages_terms_sort UNIQUE (terms_id, sort_order)
);

ALTER TABLE ONLY policy_terms_coverages
    ADD CONSTRAINT fk_policy_terms_coverages_terms
    FOREIGN KEY (terms_id) REFERENCES policy_terms(id);

-- 증권 담보명으로 이 규칙을 찾는 경로가 탄다.
CREATE INDEX idx_policy_terms_coverages_terms_title
    ON policy_terms_coverages USING btree (terms_id, title);

-- ---------------------------------------------------------------------------
-- 자식 4종의 소유자를 담보에서 보장 규칙으로 옮긴다
-- ---------------------------------------------------------------------------

-- 컬럼 구조는 그대로 두고 부모만 바꾼다. 면책 문구도, 서류 이름도, 세부 한도도 약관에서 나온
-- 값이라 담을 그릇을 새로 만들 이유가 없다. 바뀌는 것은 "누구에게 매달려 있는가" 하나다.

ALTER TABLE coverage_detail_items DROP CONSTRAINT fkfv882rj97loav3m485emomy1d;
DROP INDEX idx_coverage_detail_items_coverage_sort;
ALTER TABLE coverage_detail_items RENAME COLUMN coverage_item_id TO terms_coverage_id;
ALTER TABLE ONLY coverage_detail_items
    ADD CONSTRAINT fk_coverage_detail_items_terms_coverage
    FOREIGN KEY (terms_coverage_id) REFERENCES policy_terms_coverages(id);
CREATE INDEX idx_coverage_detail_items_terms_coverage_sort
    ON coverage_detail_items USING btree (terms_coverage_id, sort_order);

ALTER TABLE sub_coverage_limits DROP CONSTRAINT fkaomrfk3cu2igi4wvdtp80n9m1;
DROP INDEX idx_sub_coverage_limits_coverage_sort;
ALTER TABLE sub_coverage_limits RENAME COLUMN coverage_item_id TO terms_coverage_id;
ALTER TABLE ONLY sub_coverage_limits
    ADD CONSTRAINT fk_sub_coverage_limits_terms_coverage
    FOREIGN KEY (terms_coverage_id) REFERENCES policy_terms_coverages(id);
CREATE INDEX idx_sub_coverage_limits_terms_coverage_sort
    ON sub_coverage_limits USING btree (terms_coverage_id, sort_order);

ALTER TABLE required_documents DROP CONSTRAINT fks63ysv13tq57unqd695sn5o3n;
DROP INDEX idx_required_documents_coverage_sort;
ALTER TABLE required_documents RENAME COLUMN coverage_item_id TO terms_coverage_id;
ALTER TABLE ONLY required_documents
    ADD CONSTRAINT fk_required_documents_terms_coverage
    FOREIGN KEY (terms_coverage_id) REFERENCES policy_terms_coverages(id);
CREATE INDEX idx_required_documents_terms_coverage_sort
    ON required_documents USING btree (terms_coverage_id, sort_order);

ALTER TABLE exclusion_conditions DROP CONSTRAINT fk1c3q1mohdn09j00duss7h53ww;
DROP INDEX idx_exclusion_conditions_coverage_sort;
ALTER TABLE exclusion_conditions RENAME COLUMN coverage_item_id TO terms_coverage_id;
ALTER TABLE ONLY exclusion_conditions
    ADD CONSTRAINT fk_exclusion_conditions_terms_coverage
    FOREIGN KEY (terms_coverage_id) REFERENCES policy_terms_coverages(id);
CREATE INDEX idx_exclusion_conditions_terms_coverage_sort
    ON exclusion_conditions USING btree (terms_coverage_id, sort_order);

-- ---------------------------------------------------------------------------
-- 근거 인용도 공용 영역으로 옮긴다
-- ---------------------------------------------------------------------------

-- V8 에서 이 테이블의 청크 쪽 FK 를 policy_terms_chunks 로 돌렸다. 그런데 담보 쪽 FK 는
-- 여전히 coverage_items(사용자)를 가리켜, 한 행이 사용자 영역과 공용 영역에 걸쳐 있었다.
--
-- "이 보장 규칙의 근거는 약관 몇 조다"는 상품의 사실이지 가입자의 사실이 아니다. 양쪽 끝을
-- 모두 공용으로 두면 불변식도 단순해진다 -- 규칙과 청크가 같은 약관에 속하는지만 보면 된다.
-- 사용자 담보를 거쳐 확인할 필요가 없다.
--
-- 이름도 맞춘다. coverage_item 을 더 이상 참조하지 않으므로 그 이름을 남겨두면 다음 사람이
-- 사용자 담보에 달린 것으로 읽는다.
ALTER TABLE coverage_item_sources DROP CONSTRAINT fkrssvvtrg55e8e7r4ydsodkra6;
ALTER TABLE coverage_item_sources DROP CONSTRAINT uk_coverage_item_sources_item_chunk_role;

ALTER TABLE coverage_item_sources RENAME COLUMN coverage_item_id TO terms_coverage_id;
ALTER TABLE coverage_item_sources RENAME TO policy_terms_coverage_sources;

ALTER TABLE ONLY policy_terms_coverage_sources
    ADD CONSTRAINT fk_policy_terms_coverage_sources_terms_coverage
    FOREIGN KEY (terms_coverage_id) REFERENCES policy_terms_coverages(id);

ALTER TABLE policy_terms_coverage_sources
    ADD CONSTRAINT uk_policy_terms_coverage_sources_coverage_chunk_role
    UNIQUE (terms_coverage_id, terms_chunk_id, source_role);

-- 제약·인덱스 이름은 테이블을 RENAME 해도 따라오지 않는다. 남은 것들을 새 이름에 맞춘다.
ALTER TABLE policy_terms_coverage_sources
    RENAME CONSTRAINT coverage_item_sources_pkey TO policy_terms_coverage_sources_pkey;
ALTER TABLE policy_terms_coverage_sources
    RENAME CONSTRAINT coverage_item_sources_source_role_check
    TO policy_terms_coverage_sources_source_role_check;
ALTER TABLE policy_terms_coverage_sources
    RENAME CONSTRAINT fk_coverage_item_sources_terms_chunk
    TO fk_policy_terms_coverage_sources_terms_chunk;
ALTER INDEX idx_coverage_item_sources_terms_chunk_id
    RENAME TO idx_policy_terms_coverage_sources_terms_chunk_id;

-- ---------------------------------------------------------------------------
-- 사용자 담보가 어느 보장 규칙을 적용받는지
-- ---------------------------------------------------------------------------

-- 보장 상세 응답은 이 연결을 타고 두 영역을 합친다.
--   가입 여부·가입금액  <- coverage_items       (사용자)
--   면책·서류·세부한도  <- policy_terms_coverages (공용)
--
-- nullable 이다. 약관을 못 찾았거나(matched_terms_id 가 null), 찾았어도 증권 담보명이 약관의
-- 어느 규칙과도 맞지 않을 수 있다. 그때는 가입 정보만 내려간다 -- 지금과 같은 응답이다.
-- 억지로 붙이면 다른 담보의 면책 조항을 보여주게 되고, 사용자는 그것을 보고 청구를 포기한다.
ALTER TABLE coverage_items
    ADD COLUMN terms_coverage_id uuid;

ALTER TABLE ONLY coverage_items
    ADD CONSTRAINT fk_coverage_items_terms_coverage
    FOREIGN KEY (terms_coverage_id) REFERENCES policy_terms_coverages(id);

CREATE INDEX idx_coverage_items_terms_coverage_id
    ON coverage_items USING btree (terms_coverage_id);

-- ---------------------------------------------------------------------------
-- AI 서버 계정 권한
-- ---------------------------------------------------------------------------

-- 약관에서 보장 규칙을 뽑는 것도 AI 쪽 일이라(파싱·추출이 그쪽에 있다) 청크와 같은 권한을 준다.
-- UPDATE 를 주지 않는 이유는 V7 과 같다 -- 적재는 DELETE 후 INSERT 로 하면 되고, 그러면
-- 부분적으로 수정되다 만 규칙이 남을 수 없다.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rag_service') THEN
        GRANT SELECT, INSERT, DELETE ON policy_terms_coverages TO rag_service;
        GRANT SELECT, INSERT, DELETE ON policy_terms_coverage_sources TO rag_service;
        GRANT SELECT, INSERT, DELETE ON coverage_detail_items TO rag_service;
        GRANT SELECT, INSERT, DELETE ON sub_coverage_limits TO rag_service;
        GRANT SELECT, INSERT, DELETE ON required_documents TO rag_service;
        GRANT SELECT, INSERT, DELETE ON exclusion_conditions TO rag_service;
    ELSE
        RAISE NOTICE 'rag_service 역할이 없어 GRANT 를 건너뜁니다. 배포 환경이라면 역할을 만든 뒤 수동으로 부여하세요.';
    END IF;
END
$$;
