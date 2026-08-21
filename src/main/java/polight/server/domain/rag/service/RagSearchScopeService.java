package polight.server.domain.rag.service;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.rag.entity.PolicyChunk;
import polight.server.domain.rag.repository.PolicyChunkRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RagSearchScopeService {

  // 사용자의 질문을 검색할 수 있는 PolicyChunk 범위를 제한하는 서비스

  private final PolicyChunkRepository policyChunkRepository;

  // 1. 특정 사용자가 보유한 모든 보험 문서의 활성 청크를 조회한다.
  public List<PolicyChunk> findUserScopedChunks(UUID userId) {
    return policyChunkRepository.findCompletedChunksByUserId(userId);
  }

  // 2. 특정 사용자의 특정 여행에 연결된 보험 문서 청크만 조회한다.
  public List<PolicyChunk> findTripScopedChunks(UUID userId, UUID tripId) {
    return policyChunkRepository.findCompletedChunksByUserIdAndTripId(userId, tripId);
  }


  // 4. 특정 AnalysisResult에서 생성된 청크만 조회한다.
  public List<PolicyChunk> findAnalysisScopedChunks(UUID userId, UUID analysisResultId) {
    return policyChunkRepository.findCompletedChunksByUserIdAndAnalysisResultId(userId, analysisResultId);
  }

  // 5. 특정 사용자의 특정 보험 문서 청크만 조회한다.
  public List<PolicyChunk> findDocumentScopedChunks(UUID userId, UUID documentId) {
    return policyChunkRepository.findCompletedChunksByUserIdAndDocumentId(userId, documentId);
  }

  /**
   * 6. 주어진 id 중 이 사용자 소유인 청크만 조회한다.
   *
   * <p>챗봇 답변의 근거에 조항 위치를 채울 때 쓴다. id를 AI 서버가 돌려주므로 소유자 조건 없이 조회하면 남의 약관 조항 제목이 응답에 실릴 수 있다.
   * 검색 범위는 이미 요청에서 사용자별로 좁혀 보내지만, 응답을 그대로 믿지 않고 한 번 더 거른다.
   */
  public List<PolicyChunk> findOwnedChunks(UUID userId, Collection<UUID> chunkIds) {
    if (chunkIds.isEmpty()) {
      return List.of();
    }

    return policyChunkRepository.findByIdInAndUserId(chunkIds, userId);
  }
}
