-- 증권에서 읽은 보험기간을 분석 결과에 저장한다.
--
-- AI 서버는 이 값을 이미 콜백으로 보내고 있다(analysis_service.py 의 insurance_period).
-- 그런데 백엔드 DTO 에 필드가 없어 Jackson 이 무시하고 버려 왔다. 에러는 나지 않지만
-- raw_result_json 안에만 남아 조회 조건으로 쓸 수 없었다.
--
-- 원래는 policies.start_date/end_date 가 받을 값이었다. 그 테이블을 만들지 않기로 하면서
-- (V11 참고) 보험기간이 영구히 살 자리는 여기가 된다.
--
-- 당장 쓰는 곳은 약관 개정판 선택이다. 같은 상품 약관이 여러 개정판으로 등록되어 있을 때
-- "이 증권이 적용받는 판"을 고르려면 가입 시점이 필요하다. 지금은 그 값이 없어 여행 시작일을
-- 대용으로 쓰는데, 여행이 연결되지 않은 문서면 그마저 없어 최신 개정판을 추측으로 고른다.
--
-- nullable 인 이유가 둘이다.
--   - 약관 분석에는 이 값이 없다. 약관은 상품 공용 문서라 개인의 가입 기간이 없다
--   - 증권이라도 에이전트가 기간을 못 읽는 경우가 있다. 그때 분석 전체를 실패시킬 이유는 없다
--     -- 담보 목록은 정상이고, 개정판 선택만 여행 시작일로 되돌아간다
ALTER TABLE analysis_results
    ADD COLUMN insurance_start_date date;

ALTER TABLE analysis_results
    ADD COLUMN insurance_end_date date;
