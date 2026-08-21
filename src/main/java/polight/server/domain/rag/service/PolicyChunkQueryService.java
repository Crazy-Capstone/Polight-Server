package polight.server.domain.rag.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.rag.repository.PolicyChunkRepository;

/**
 * 색인된 약관 조각의 상태를 다른 도메인에 알려준다.
 *
 * <p>{@link RagSearchScopeService}와 나눠 둔 이유: 그쪽은 "사용자 질문을 어느 조각 범위에서 검색할지"를 정하고, 이쪽은 "이 분석에 조각이
 * 있는지"라는 분석 수명주기 질문에 답한다. 쓰는 쪽과 시점이 다르다.
 *
 * <p>리포지토리를 직접 주입하지 않고 이 서비스를 거치게 하는 것은 저장소 규칙이다. 다른 도메인이 리포지토리를 직접 쓰면 도메인 규칙을 각자 재현해야 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyChunkQueryService {

  private final PolicyChunkRepository policyChunkRepository;

  /**
   * 이 분석으로 색인된 조각이 하나라도 있는지.
   *
   * <p>조각을 만들고 지우는 주체는 AI 서버다. 백엔드는 읽기만 한다.
   */
  public boolean hasChunksFor(UUID analysisResultId) {
    return policyChunkRepository.existsByAnalysisResultId(analysisResultId);
  }
}
