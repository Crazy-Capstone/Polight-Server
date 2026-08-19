package polight.server.domain.trip.dto;

import polight.server.domain.analysis.dto.AnalysisResponse;
import polight.server.domain.insurance.dto.PolicyDocumentResponse;

/**
 * 여행 생성과 보험 문서 업로드를 한 요청으로 처리한 결과.
 *
 * <p>프론트는 이후 분석 상태를 폴링할 때 두 식별자가 모두 필요하므로 함께 돌려준다.
 *
 * @param analysis 증권을 올렸을 때 바로 시작된 분석 작업. 약관을 올린 경우에는 {@code null}이며, 이때는 {@code POST .../analysis}
 *     로 분석을 시작해야 한다
 */
public record TripWithDocumentResponse(
    TripResponse trip, PolicyDocumentResponse document, AnalysisResponse analysis) {}
