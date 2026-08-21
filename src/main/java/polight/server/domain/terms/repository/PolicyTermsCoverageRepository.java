package polight.server.domain.terms.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import polight.server.domain.terms.entity.PolicyTermsCoverage;

public interface PolicyTermsCoverageRepository extends JpaRepository<PolicyTermsCoverage, UUID> {

  List<PolicyTermsCoverage> findByTermsIdOrderBySortOrderAsc(UUID termsId);

  /**
   * 증권 담보명으로 약관의 보장 규칙을 찾는다.
   *
   * <p>여러 건이 나올 수 있다 -- 같은 이름의 보장이 한 약관 안 여러 관에 걸쳐 서술되기도 한다. 그때 어느 것인지 가릴 수 없으므로 호출부는 1건일 때만
   * 연결해야 한다. 아무거나 고르면 다른 조항의 면책을 근거로 보여주게 된다.
   */
  List<PolicyTermsCoverage> findByTermsIdAndTitle(UUID termsId, String title);

  /** 재적재는 지우고 다시 넣는 방식이다. UNIQUE(terms_id, sort_order) 때문에 순서가 강제된다. */
  void deleteByTermsId(UUID termsId);

  long countByTermsId(UUID termsId);

  List<PolicyTermsCoverage> findByIdIn(Collection<UUID> ids);
}
