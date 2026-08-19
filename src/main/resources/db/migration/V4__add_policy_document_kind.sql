-- 업로드된 문서가 증권인지 약관인지 구분한다.
--
-- 콜백을 받았을 때 증권 분석 결과인지 약관 분석 결과인지에 따라 처리가 갈리고, AI 서버에
-- 분석을 요청할 때 documentType 으로 실어 보내야 한다. 보내지 않으면 AI 가 페이지 수로
-- 추측하는데(증권 1~2p, 약관 100p+), 조용히 오분류되면 증권이 약관 파이프라인으로 넘어간다.
--
-- 기존 행은 전부 약관이므로 기본값을 TERMS 로 둔다.
ALTER TABLE policy_documents
    ADD COLUMN document_kind varchar(20) NOT NULL DEFAULT 'TERMS';

ALTER TABLE policy_documents
    ADD CONSTRAINT policy_documents_document_kind_check
    CHECK (document_kind IN ('CERTIFICATE', 'TERMS'));
