package polight.server.domain.analysis.mapper;

import org.springframework.stereotype.Component;
import polight.server.domain.analysis.dto.AnalysisResponse;
import polight.server.domain.analysis.entity.AnalysisResult;
import polight.server.domain.insurance.entity.PolicyDocument;

/** AnalysisResult 엔티티와 DTO 사이의 변환을 담당한다. */
@Component
public class AnalysisMapper {

  /** 문서에 대한 새 분석 작업을 만든다. 분석에 연결할 보험 계약은 문서가 이미 알고 있다. */
  public AnalysisResult toEntity(PolicyDocument document) {
    return AnalysisResult.builder().document(document).build();
  }

  public AnalysisResponse toResponse(AnalysisResult result) {
    return new AnalysisResponse(
        result.getId(),
        result.getDocument().getId(),
        result.getStatus(),
        result.getSummary(),
        result.getFailureReason(),
        result.getStartedAt(),
        result.getCompletedAt());
  }
}
