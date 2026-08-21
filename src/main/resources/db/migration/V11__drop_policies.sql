-- policies 테이블을 제거한다.
--
-- 이 테이블은 만들어진 뒤 한 번도 쓰이지 않았다. 행을 만드는 코드가 없어 항상 비어 있고,
-- 읽는 코드도 없다(PolicyRepository 호출자 0건, findByPolicyId 계열 쿼리 3개도 호출자 0건).
--
-- 비어 있는 채로 둔 대가가 이미 나왔다. 네 테이블이 절대 채워지지 않는 policy_id 를 들고
-- 있어서, 스키마만 보면 왜 항상 null 인지 알 수 없다. AI 서버는 그것 때문에 실제로 버그를
-- 냈다 -- pg_repository 의 스코프 필터가 policy_id 로 조인하다가 "= NULL 은 아무 행과도
-- 일치하지 않는다"로 검색이 조용히 0건을 반환했고, 로컬에서는 값을 직접 채워 테스트해서
-- 한참 뒤에야 발견했다.
--
-- 채우는 쪽으로 갈 수도 있었다. 그러려면 정할 것이 여럿이다 -- trip_id 가 NOT NULL 인데
-- policy_documents.trip_id 는 nullable 이고, display_name 을 만드는 규칙이 없고, status
-- 전이(PENDING/ACTIVE/EXPIRED) 주체가 없고, 증권번호를 넣을 암호화 수단이 코드에 없다.
-- 그 결정들이 필요한 기능(만기 알림, "내 보험" 목록)이 아직 로드맵에 없어서, 만들지 않고
-- 지우는 쪽을 택했다.
--
-- 보험 정보는 analysis_results 가 갖는다. 보험사·상품명은 V8 에서, 보험기간은 V10 에서
-- 이미 그쪽에 들어갔다. 나중에 보험 단위 개념이 필요해지면 그 값들로 다시 만들면 된다.

-- ---------------------------------------------------------------------------
-- 가드: 쓰이지 않는 테이블이라는 전제를 먼저 확인한다
-- ---------------------------------------------------------------------------

-- 행이 있으면 멈춘다.
--
-- 작성 시점 운영 DB 는 0건이다(행을 만드는 코드가 없다). 만약 행이 있다면 이 마이그레이션을
-- 쓸 때의 전제 -- "쓰이지 않는 테이블" -- 가 깨졌다는 뜻이므로, 지우기 전에 사람이 봐야 한다.
--
-- 파괴적인 문장보다 앞에 둔다. Flyway 는 마이그레이션을 트랜잭션으로 감싸 어디서 멈추든
-- 되돌리지만, 사람이 psql 로 직접 실행하면 문장마다 커밋된다. 그때 가드가 뒤에 있으면
-- 컬럼이 이미 지워진 뒤에 멈춘다.
DO $$
DECLARE
    existing_count bigint;
BEGIN
    SELECT count(*) INTO existing_count FROM policies;

    IF existing_count > 0 THEN
        RAISE EXCEPTION
            'policies 에 행이 % 건 있어 테이블을 지울 수 없습니다. 이 테이블은 쓰이지 않는다는 '
            '전제로 제거하는 것이므로, 값이 있다면 어디서 들어왔는지 확인한 뒤 진행하세요.',
            existing_count;
    END IF;
END
$$;

-- ---------------------------------------------------------------------------
-- policies 를 가리키던 FK 를 끊는다
-- ---------------------------------------------------------------------------

-- 백엔드가 소유한 세 테이블은 컬럼째 지운다. 값이 들어간 적이 없어 잃을 것이 없다.

ALTER TABLE analysis_results DROP CONSTRAINT fkra4yqh5mb2x0f92ccmcno7nyt;
DROP INDEX idx_analysis_results_policy_id;
ALTER TABLE analysis_results DROP COLUMN policy_id;

ALTER TABLE policy_documents DROP CONSTRAINT fkh1s3eovwjkoyh11iffiwlwn8r;
DROP INDEX idx_policy_documents_policy_id;
ALTER TABLE policy_documents DROP COLUMN policy_id;

ALTER TABLE chat_sessions DROP CONSTRAINT fkqyaei9cw6pr0oqnuuxolrg5se;
ALTER TABLE chat_sessions DROP COLUMN policy_id;

-- policy_chunks 는 FK 만 끊고 컬럼을 남긴다.
--
-- 이 테이블에 INSERT 하는 것은 AI 서버다(rag_service 계정). 그쪽 pg_mapper 의 COLUMNS 목록에
-- policy_id 가 들어 있어, 컬럼을 지우면 AI 가 pgvector 저장소로 전환하는 순간 "column does
-- not exist" 로 적재가 통째로 실패한다. 지금은 DATABASE_URL 이 비어 있어 파일 저장소로 돌기
-- 때문에 당장 깨지지는 않지만, 켜는 날 깨지는 지뢰를 남길 이유가 없다.
--
-- 남는 컬럼은 FK 없는 uuid 다. AI 가 항상 null 을 넣으므로 무해하다. policy_chunks 자체가
-- 약관 이관 후 제거 대상이라 그때 컬럼도 함께 사라진다.
ALTER TABLE policy_chunks DROP CONSTRAINT fknlix1f5w7erhayuj2yr9lat48;

-- (user_id, policy_id) 인덱스는 지운다. policy_id 가 항상 null 이라 이 인덱스로 좁혀지는
-- 것이 없고, AI 의 검색 조건에도 policy_id 가 없다.
DROP INDEX idx_policy_chunks_user_policy;

-- ---------------------------------------------------------------------------
-- 테이블 제거
-- ---------------------------------------------------------------------------

DROP TABLE policies;
