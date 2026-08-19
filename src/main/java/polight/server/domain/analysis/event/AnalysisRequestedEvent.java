package polight.server.domain.analysis.event;

import java.util.UUID;
import polight.server.domain.insurance.entity.DocumentKind;

/**
 * DB 커밋 후 AI 분석 요청을 시작하기 위한 이벤트.
 *
 * <p>Presigned URL이 아니라 영구 object key를 담는다. URL은 유효기간이 짧아 이벤트에 실어 두면 발급 시점과 사용 시점이 벌어질수록 만료 위험이 커진다.
 * 커밋 이후 리스너가 그때 발급한다.
 */
public record AnalysisRequestedEvent(
    UUID analysisResultId,
    UUID userId,
    UUID tripId,
    UUID documentId,
    DocumentKind documentKind,
    String objectKey) {}
