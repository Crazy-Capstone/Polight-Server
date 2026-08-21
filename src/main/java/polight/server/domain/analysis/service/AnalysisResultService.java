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
import polight.server.domain.rag.service.PolicyChunkQueryService;
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
  private final PolicyChunkQueryService policyChunkQueryService;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * 분석을 시작한다.
   *
   * <p>진행 중이거나 완료된 분석이 있으면 그것을 그대로 돌려준다. 같은 문서를 두 번 분석할 이유가 없다.
   *
   * <p><b>실패한 분석은 다시 시작한다.</b> 원문 파일은 S3에 그대로 있으므로 사용자가 문서를 다시 올릴 필요가 없다. 이 호출이 재시도 수단이 아니면
   * 사용자에게는 재업로드밖에 남지 않고, 그러면 쓰지 않는 S3 객체와 문서 행이 실패할 때마다 쌓인다.
   *
   * <p>단, 색인된 조각이 남아 있는 분석은 재시도할 수 없다({@link #requireRetryable}). 이 경우
   * {@code ANALYSIS_RETRY_NOT_SUPPORTED}로 거절한다.
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
      requireRetryable(result);
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

  /**
   * 이 분석을 다시 시작해도 되는지 확인한다.
   *
   * <p>색인된 조각이 남아 있으면 재시도가 무조건 실패한다. AI 서버는 재분석 때 조각을 같은 {@code analysis_result_id}로 {@code
   * chunk_index} 0부터 다시 넣는데, {@code uk_policy_chunks_analysis_chunk_index}가 이를 막는다. 이전 조각을 지우는
   * 코드는 AI 서버에 아직 없다(계약 문서에는 예정으로 적혀 있다).
   *
   * <p>그대로 두면 재시도가 매번 같은 제약 위반으로 실패하고, 사용자에게는 원인을 알 수 없는 실패가 반복된다. 재업로드하라고 명확히 알려주는 편이 낫다.
   *
   * <p>증권은 조각을 만들지 않으므로 항상 통과한다. 즉 이 제한은 약관 분석이 조각을 넣은 뒤 실패한 경우에만 걸린다.
   */
  private void requireRetryable(AnalysisResult result) {
    if (policyChunkQueryService.hasChunksFor(result.getId())) {
      log.warn(
          "색인된 조각이 남아 재시도를 거부합니다: analysisResultId={}, documentKind={}",
          result.getId(),
          result.getDocument().getDocumentKind());
      throw new BaseException(ErrorCode.ANALYSIS_RETRY_NOT_SUPPORTED);
    }
  }

  public AnalysisResponse getAnalysis(UUID userId, UUID tripId, UUID documentId) {
    policyDocumentService.getOwnedDocument(userId, tripId, documentId);

    return analysisResultRepository
        .findOneByDocumentId(documentId)
        .map(analysisMapper::toResponse)
        .orElseThrow(() -> new BaseException(ErrorCode.ANALYSIS_RESULT_NOT_FOUND));
  }
}
