package polight.server.domain.terms.service;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.terms.entity.PolicyTermsChunk;
import polight.server.domain.terms.repository.PolicyTermsChunkRepository;

/**
 * 약관 청크를 다른 도메인에 읽어 준다.
 *
 * <p>챗봇이 쓴다. AI 서버는 답변 근거로 {@code chunkId}와 인용문만 돌려주고, 조항 제목·조항 경로·페이지는 {@code
 * policy_terms_chunks}에 있으므로 서버가 붙인다.
 *
 * <p>{@code rag} 패키지의 {@code RagSearchScopeService}와 갈라져 있다. 그쪽은 {@code policy_chunks} -- 사용자별로
 * 색인된 개인 청크를 다루고 접근 판정을 {@code user_id}로 한다. 약관 청크는 상품 공용이라 주인이 없고, 판정 기준이 "그 사용자의 것인가"가 아니라
 * "질의에 지목한 그 약관의 것인가"다. 두 판정을 한 클래스에 두면 어느 것을 쓸지가 호출부마다 헷갈린다.
 *
 * <p>적재는 AI 서버가 직접 한다. 백엔드는 읽기만 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TermsChunkQueryService {

  private final PolicyTermsChunkRepository policyTermsChunkRepository;

  /**
   * 주어진 id 중 이 약관에 속한 청크만 돌려준다.
   *
   * <p>찾지 못한 id는 결과에서 빠진다. 오류가 아니다 -- 호출한 쪽이 인용문만 남기고 위치를 비우면 된다.
   *
   * @param termsId 질의에 실어 보낸 약관. 이 약관 밖의 청크는 걸러진다
   */
  public List<PolicyTermsChunk> findChunksIn(UUID termsId, Collection<UUID> chunkIds) {
    if (termsId == null || chunkIds.isEmpty()) {
      return List.of();
    }

    return policyTermsChunkRepository.findByIdInAndTermsId(chunkIds, termsId);
  }
}
