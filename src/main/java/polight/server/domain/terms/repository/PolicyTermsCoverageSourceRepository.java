package polight.server.domain.terms.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import polight.server.domain.terms.entity.PolicyTermsCoverageSource;
import polight.server.domain.terms.entity.TermsCoverageSourceRole;

public interface PolicyTermsCoverageSourceRepository
    extends JpaRepository<PolicyTermsCoverageSource, UUID> {

  List<PolicyTermsCoverageSource> findByTermsCoverageIdOrderBySourceRoleAscCreatedAtAsc(
      UUID termsCoverageId);

  List<PolicyTermsCoverageSource> findByTermsCoverageIdInOrderBySourceRoleAscCreatedAtAsc(
      Collection<UUID> termsCoverageIds);

  List<PolicyTermsCoverageSource> findByTermsChunkId(UUID termsChunkId);

  boolean existsByTermsCoverageIdAndTermsChunkIdAndSourceRole(
      UUID termsCoverageId, UUID termsChunkId, TermsCoverageSourceRole sourceRole);

  /** 보장 규칙을 지우기 전에 이 테이블부터 비워야 한다. terms_coverage_id 쪽 FK 때문에 순서가 강제된다. */
  void deleteByTermsCoverageIdIn(Collection<UUID> termsCoverageIds);
}
