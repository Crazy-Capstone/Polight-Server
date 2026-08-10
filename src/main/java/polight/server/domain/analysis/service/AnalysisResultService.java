package polight.server.domain.analysis.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.analysis.dto.AnalysisResponse;
import polight.server.domain.analysis.entity.AnalysisResult;
import polight.server.domain.analysis.mapper.AnalysisMapper;
import polight.server.domain.analysis.repository.AnalysisResultRepository;
import polight.server.domain.insurance.entity.PolicyDocument;
import polight.server.domain.insurance.service.PolicyDocumentService;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisResultService {

  private final AnalysisResultRepository analysisResultRepository;
  private final AnalysisMapper analysisMapper;
  private final PolicyDocumentService policyDocumentService;

  /** 분석을 시작한다. 같은 문서로 다시 요청하면 이미 만들어진 분석 작업을 그대로 돌려준다. */
  @Transactional
  public AnalysisResponse startAnalysis(UUID userId, UUID tripId, UUID documentId) {
    PolicyDocument document = policyDocumentService.getOwnedDocument(userId, tripId, documentId);

    AnalysisResult result =
        analysisResultRepository
            .findOneByDocumentId(documentId)
            .orElseGet(() -> analysisResultRepository.save(analysisMapper.toEntity(document)));

    return analysisMapper.toResponse(result);
  }

  public AnalysisResponse getAnalysis(UUID userId, UUID tripId, UUID documentId) {
    policyDocumentService.getOwnedDocument(userId, tripId, documentId);

    return analysisResultRepository
        .findOneByDocumentId(documentId)
        .map(analysisMapper::toResponse)
        .orElseThrow(() -> new BaseException(ErrorCode.ANALYSIS_RESULT_NOT_FOUND));
  }
}
