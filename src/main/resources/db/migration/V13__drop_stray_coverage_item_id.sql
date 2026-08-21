-- 자식 4종에 남아 있는 coverage_item_id 를 지운다.
--
-- V9 가 이 컬럼을 terms_coverage_id 로 RENAME 했으므로 원래는 남아 있을 수 없다. 그런데
-- 운영 DB 에서 두 컬럼이 함께, 둘 다 NOT NULL 로 관측됐다. flyway_schema_history 에는
-- V9 가 success 로 찍혀 있으므로 RENAME 은 정상 수행됐고, 그 뒤에 coverage_item_id 가
-- 다시 생겼다는 뜻이다.
--
-- 애플리케이션이 만든 것은 아니다. 확인한 것:
--   - 자바 코드에 coverage_item_id 매핑이 0건이다(V9 에서 부모를 바꾸며 전부 정리했다)
--   - ddl-auto 는 validate 다. 스키마를 만들지 않고, compose 에 오버라이드도 없다
--   - Hibernate 는 어느 엔티티도 매핑하지 않는 컬럼을 추가하지 않는다
-- 즉 마이그레이션 밖에서(수동 DDL 이나 외부 스크립트) 들어온 것으로 본다.
--
-- 지우는 이유는 단순히 "우리 모델에 없는 컬럼"이기 때문만이 아니다. 두 부모 컬럼이 동시에
-- NOT NULL 이면 이 테이블들에 INSERT 자체가 불가능하다 -- 한 자식이 두 부모에 동시에 속할
-- 수 없으므로 어느 쪽을 채워도 다른 쪽이 NOT NULL 위반이다. 실제로 AI 서버의 보장 규칙
-- 적재가 이것으로 막혔다.
--
--   null value in column "coverage_item_id" ... violates not-null constraint
--
-- Hibernate 의 validate 는 엔티티에 없는 여분 컬럼을 문제 삼지 않는다. 그래서 앱은 정상
-- 기동하고, 막히는 것은 INSERT 뿐이라 콜백이 오기 전까지는 드러나지 않았다.
--
-- IF EXISTS 로 두는 이유: 정상적으로 V9 만 거친 DB(로컬·CI·새로 만든 환경)에는 이 컬럼이
-- 없다. 그런 곳에서도 이 마이그레이션이 그냥 지나가야 한다.

-- 지우기 전에 무엇이 있었는지 남긴다.
--
-- 가드로 막지 않고 알리기만 하는 이유: 이 컬럼은 우리 스키마 모델에 존재한 적이 없어
-- 지키기로 한 데이터가 아니다. 반대로 남겨두면 적재가 계속 막힌다. 다만 값이 들어 있었다면
-- 누군가 이 컬럼을 쓰고 있었다는 뜻이므로 로그에 남겨 추적할 수 있게 한다.
DO $$
DECLARE
    target_table text;
    total bigint;
    filled bigint;
BEGIN
    FOREACH target_table IN ARRAY ARRAY[
        'coverage_detail_items',
        'exclusion_conditions',
        'required_documents',
        'sub_coverage_limits'
    ] LOOP
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = target_table AND column_name = 'coverage_item_id'
        ) THEN
            EXECUTE format(
                'SELECT count(*), count(coverage_item_id) FROM %I', target_table)
                INTO total, filled;
            RAISE NOTICE
                '% 에서 coverage_item_id 를 제거합니다 (행 % 건, 값이 있는 행 % 건)',
                target_table, total, filled;
        END IF;
    END LOOP;
END
$$;

ALTER TABLE coverage_detail_items DROP COLUMN IF EXISTS coverage_item_id;
ALTER TABLE exclusion_conditions  DROP COLUMN IF EXISTS coverage_item_id;
ALTER TABLE required_documents    DROP COLUMN IF EXISTS coverage_item_id;
ALTER TABLE sub_coverage_limits   DROP COLUMN IF EXISTS coverage_item_id;

-- 그 컬럼을 쓰던 인덱스도 함께 정리한다.
--
-- V9 가 idx_*_coverage_sort 를 지우고 idx_*_terms_coverage_sort 를 만들었으므로 정상 경로로는
-- 남아 있지 않다. 컬럼과 같은 경위로 다시 생겼을 수 있어 확인해 둔다. 컬럼을 DROP 하면
-- 그 컬럼만 쓰는 인덱스는 Postgres 가 함께 지우지만, 복합 인덱스는 남을 수 있다.
DROP INDEX IF EXISTS idx_coverage_detail_items_coverage_sort;
DROP INDEX IF EXISTS idx_exclusion_conditions_coverage_sort;
DROP INDEX IF EXISTS idx_required_documents_coverage_sort;
DROP INDEX IF EXISTS idx_sub_coverage_limits_coverage_sort;
