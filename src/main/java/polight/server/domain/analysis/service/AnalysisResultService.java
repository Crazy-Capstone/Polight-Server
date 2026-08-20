package polight.server.domain.analysis.service;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.analysis.dto.AnalysisResponse;
import polight.server.domain.analysis.entity.AnalysisResult;
import polight.server.domain.analysis.entity.AnalysisStatus;
import polight.server.domain.analysis.event.AnalysisRequestedEvent;
import polight.server.domain.analysis.mapper.AnalysisMapper;
import polight.server.domain.analysis.repository.AnalysisResultRepository;
import polight.server.domain.insurance.entity.PolicyDocument;
import polight.server.domain.insurance.service.PolicyDocumentService;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisResultService {

  private final AnalysisResultRepository analysisResultRepository;
  private final AnalysisMapper analysisMapper;
  private final PolicyDocumentService policyDocumentService;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * 분석을 시작한다.
   *
   * <p>진행 중이거나 완료된 분석이 있으면 그것을 그대로 돌려준다. 같은 문서를 두 번 분석할 이유가 없다.
   *
   * <p><b>실패한 분석은 다시 시작한다.</b> 원문 파일은 S3에 그대로 있으므로 사용자가 문서를 다시 올릴 필요가 없다. 이 호출이 재시도 수단이 아니면
   * 사용자에게는 재업로드밖에 남지 않고, 그러면 쓰지 않는 S3 객체와 문서 행이 실패할 때마다 쌓인다.
   */
  @Transactional
  public AnalysisResponse startAnalysis(UUID userId, UUID tripId, UUID documentId) {
    PolicyDocument document = policyDocumentService.getOwnedDocument(userId, tripId, documentId);

    // 잠그고 읽는다. 동시 재시도와 타임아웃 처리가 같은 분석에 겹치지 않게 한다.
    AnalysisResult result =
        analysisResultRepository.findOneByDocumentIdForUpdate(documentId).orElse(null);
    if (result == null) {
      result = analysisResultRepository.save(analysisMapper.toEntity(document));
    } else if (result.getStatus() == AnalysisStatus.FAILED) {
      log.info(
          "실패한 분석을 다시 시작합니다: analysisResultId={}, 이전 사유={}",
          result.getId(),
          result.getFailureReason());
      result.restart(LocalDateTime.now());
      document.markParseUploaded();
    } else {
      return analysisMapper.toResponse(result);
    }

    eventPublisher.publishEvent(
        new AnalysisRequestedEvent(
            result.getId(),
            userId,
            tripId,
            document.getId(),
            document.getDocumentKind(),
            document.getStoredFilePath()));

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
