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

  /** 특정 조항 위치의 청크만. 담보의 근거 조항을 찾아 인용할 때 쓴다. */
  List<PolicyTermsChunk> findByTermsIdAndClausePathIn(UUID termsId, Collection<String> clausePaths);

  /** 재적재는 지우고 다시 넣는 방식이다. UNIQUE(terms_id, chunk_index) 때문에 순서가 강제된다. */
  void deleteByTermsId(UUID termsId);
}
