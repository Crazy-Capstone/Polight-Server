-- 증권 분석을 약관에 연결하고, 담보의 근거 조항을 약관 청크에서 가리키게 한다.
--
-- V7 이 약관을 상품 단위로 떼어냈으니, 이제 "이 증권은 어느 약관인가"를 저장할 자리가 필요하다.
-- 그 연결이 서는 순간 챗봇과 보장 상세내역이 약관 본문을 근거로 답할 수 있다.

-- ---------------------------------------------------------------------------
-- analysis_results : 증권에서 읽은 보험사/상품명과, 그것으로 찾아낸 약관
-- ---------------------------------------------------------------------------

-- AI 는 증권에서 읽은 보험사/상품명을 콜백으로 보내는데, 지금은 저장할 컬럼이 없어
-- raw_result_json 안에만 남아 있다. JSON 안에 있으면 조회 조건으로 쓸 수 없다.
--
-- policies 테이블에 넣지 않는 이유: policies 는 start_date/end_date 가 NOT NULL 인데
-- 콜백에 보험기간이 없어 행을 만들 수 없다. 보험기간이 콜백에 실려 오기 전까지는
-- 분석 결과 옆에 두는 것이 유일하게 가능한 자리다.
ALTER TABLE analysis_results
    ADD COLUMN insurer_name character varying(200);

ALTER TABLE analysis_results
    ADD COLUMN product_name character varying(200);

-- 이 분석이 어느 약관에 해당하는지. 위 두 값으로 policy_terms 를 찾아 채운다.
--
-- nullable 이다. 매칭에 실패하는 경우가 정상 흐름의 일부이기 때문이다 -- 아직 등록되지 않은
-- 상품이면 찾을 약관이 없다. 그때는 null 로 두고 사용자에게 약관 업로드를 요청한다.
-- 애매한 후보를 억지로 붙이는 것보다 비워 두는 편이 낫다. 다른 상품의 약관을 근거로
-- "보장되지 않습니다"라고 답하면 사용자는 받을 수 있는 보험금을 포기한다.
ALTER TABLE analysis_results
    ADD COLUMN matched_terms_id uuid;

ALTER TABLE ONLY analysis_results
    ADD CONSTRAINT fk_analysis_results_matched_terms
    FOREIGN KEY (matched_terms_id) REFERENCES policy_terms(id);

CREATE INDEX idx_analysis_results_matched_terms_id
    ON analysis_results USING btree (matched_terms_id);

-- ---------------------------------------------------------------------------
-- coverage_item_sources : 근거 조항을 policy_chunks 대신 policy_terms_chunks 에서 가리킨다
-- ---------------------------------------------------------------------------

-- 담보의 근거는 약관 조항이다. 증권에는 "해외여행중 상해의료비 3,000만원" 같은 표만 있고,
-- 무엇이 보장되고 무엇이 면책인지는 약관에만 있다. 그래서 이 테이블이 가리켜야 할 것은
-- 처음부터 약관 청크였다.
--
-- 기존 행이 있으면 멈춘다.
--
-- policy_chunk_id 가 가리키던 id 는 policy_terms_chunks 에 존재하지 않는다. 두 테이블은
-- 별개의 행 집합이고 옮겨 심을 대응 관계가 없다. 그래서 이 전환에 안전한 자동 변환이 없다 --
-- 남길 수도, 옮길 수도 없고, 조용히 지우면 이미 화면에 보이던 근거가 말없이 사라진다.
--
-- 지금 이 테이블은 비어 있다. 근거를 넣는 코드가 아직 없기 때문이다(AI 콜백에 sources[] 가
-- 오지 않는다). 그래서 실무상 이 가드는 걸리지 않는다. 걸린다면 그것은 이 마이그레이션을
-- 쓸 때의 전제가 깨졌다는 뜻이므로, 데이터를 건드리기 전에 사람이 봐야 한다.
DO $$
DECLARE
    existing_count bigint;
BEGIN
    SELECT count(*) INTO existing_count FROM coverage_item_sources;

    IF existing_count > 0 THEN
        RAISE EXCEPTION
            'coverage_item_sources 에 행이 % 건 있어 policy_terms_chunks 로 전환할 수 없습니다. '
            'policy_chunks 의 id 를 policy_terms_chunks 로 옮길 대응 관계가 없습니다. '
            '데이터를 확인하고 이관 방침을 정한 뒤 다시 진행하세요.',
            existing_count;
    END IF;
END
$$;

ALTER TABLE coverage_item_sources
    DROP CONSTRAINT uk_coverage_item_sources_item_chunk_role;

ALTER TABLE coverage_item_sources
    DROP CONSTRAINT fkcepsmnqoc7yukn8633syjgpyq;

DROP INDEX idx_coverage_item_sources_policy_chunk_id;

ALTER TABLE coverage_item_sources
    RENAME COLUMN policy_chunk_id TO terms_chunk_id;

ALTER TABLE ONLY coverage_item_sources
    ADD CONSTRAINT fk_coverage_item_sources_terms_chunk
    FOREIGN KEY (terms_chunk_id) REFERENCES policy_terms_chunks(id);

ALTER TABLE coverage_item_sources
    ADD CONSTRAINT uk_coverage_item_sources_item_chunk_role
    UNIQUE (coverage_item_id, terms_chunk_id, source_role);

CREATE INDEX idx_coverage_item_sources_terms_chunk_id
    ON coverage_item_sources USING btree (terms_chunk_id);

-- ---------------------------------------------------------------------------
-- policy_documents : 기본값을 실제 업로드 분포에 맞춘다
-- ---------------------------------------------------------------------------

-- V4 는 그때 있던 행이 전부 약관이라 기본값을 TERMS 로 두었다. 지금은 반대다 -- 사용자가
-- 올리는 것은 거의 전부 증권이고, 약관은 운영자가 policy_terms 로 등록한다.
--
-- 기본값이 틀리면 프론트가 documentKind 를 빠뜨렸을 때 증권이 약관으로 저장되고, AI 에
-- documentType=TERMS 로 나가 증권을 100페이지 약관처럼 파싱하려 든다. 조용히 어긋나는 종류의
-- 오류라 콜백이 이상하게 올 때까지 아무도 모른다.
--
-- 기존 행의 값은 건드리지 않는다. 그때 저장된 것은 실제로 약관이 맞다.
--
-- 최종 목표는 기본값 없이 필수로 받는 것이다. 프론트가 항상 명시해 보내게 된 뒤 DEFAULT 를
-- 떼는 것이 다음 단계다. 지금 떼면 명시하지 않는 기존 클라이언트의 업로드가 전부 실패한다.
ALTER TABLE policy_documents
    ALTER COLUMN document_kind SET DEFAULT 'CERTIFICATE';
