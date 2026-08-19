package polight.server.domain.analysis.dto;

import java.util.UUID;

/**
 * Spring 서버가 AI 서버에 새 분석 작업을 전달할 때 쓰는 내부 계약.
 *
 * <p>FK 값을 전부 실어 보낸다. AI 서버가 {@code policy_chunks}에 청크를 넣을 때 {@code user_id}와 {@code document_id}가
 * NOT NULL이라 반드시 필요하고, 이 값을 요청에 담으면 AI 계정에 {@code analysis_results}·{@code policy_documents} 조회
 * 권한을 주지 않아도 된다.
 *
 * @param downloadUrl Presigned GET URL. AI 서버는 {@code downloadUrl} 또는 {@code fileUrl} 이름만 인식한다
 * @param documentType {@code CERTIFICATE} / {@code TERMS}. 보내지 않으면 AI가 페이지 수로 추측하는데, 조용히
 *     오분류되면 증권이 약관 파이프라인으로 넘어간다
 */
public record AiAnalysisRequest(
    UUID analysisResultId,
    UUID userId,
    UUID tripId,
    UUID documentId,
    String downloadUrl,
    String documentType) {}
