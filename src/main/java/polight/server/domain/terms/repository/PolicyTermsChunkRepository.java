package polight.server.domain.terms.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import polight.server.domain.terms.entity.PolicyTermsChunk;

public interface PolicyTermsChunkRepository extends JpaRepository<PolicyTermsChunk, UUID> {

  List<PolicyTermsChunk> findByTermsIdOrderByChunkIndexAsc(UUID termsId);

  /** 약관에 청크가 적재되어 있는지. 적재 전 약관에 챗봇 질의를 보내지 않기 위해 확인한다. */
  long countByTermsId(UUID termsId);

  /**
   * 주어진 id 중 이 약관에 속한 청크만.
   *
   * <p>챗봇 답변의 근거에 조항 위치를 채울 때 쓴다. id를 돌려주는 주체가 AI 서버라 범위 조건 없이 조회하면, 질의에 실어 보낸 약관이 아닌 다른
   * 약관의 조항 제목이 응답에 실릴 수 있다.
   *
   * <p>소유자가 아니라 {@code termsId}로 거르는 이유: 약관은 상품 공용 문서라 청크에 주인이 없다. "이 사용자가 이 약관을 볼 수 있는가"는
   * 증권-약관 매칭 시점에 이미 끝났고 그 결과가 {@code analysis_results.matched_terms_id}다. 여기서 확인할 것은 "AI가 돌려준
   * 청크가 우리가 지목한 그 약관의 것인가" 하나다.
   */
  List<PolicyTermsChunk> findByIdInAndTermsId(Collection<UUID> ids, UUID termsId);

  /** 특정 조항 위치의 청크만. 담보의 근거 조항을 찾아 인용할 때 쓴다. */
  List<PolicyTermsChunk> findByTermsIdAndClausePathIn(UUID termsId, Collection<String> clausePaths);

  /** 재적재는 지우고 다시 넣는 방식이다. UNIQUE(terms_id, chunk_index) 때문에 순서가 강제된다. */
  void deleteByTermsId(UUID termsId);
}
