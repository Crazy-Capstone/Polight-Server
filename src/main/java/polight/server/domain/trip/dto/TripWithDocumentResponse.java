package polight.server.domain.trip.dto;

import polight.server.domain.insurance.dto.PolicyDocumentResponse;

/**
 * 여행 생성과 약관 업로드를 한 요청으로 처리한 결과.
 *
 * <p>프론트는 이후 분석 시작 API를 호출할 때 두 식별자가 모두 필요하므로 함께 돌려준다.
 */
public record TripWithDocumentResponse(TripResponse trip, PolicyDocumentResponse document) {}
