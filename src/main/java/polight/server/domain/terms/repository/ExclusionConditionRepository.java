package polight.server.domain.terms.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import polight.server.domain.terms.entity.ExclusionCondition;
import polight.server.domain.terms.entity.ExclusionConditionSeverity;

public interface ExclusionConditionRepository extends JpaRepository<ExclusionCondition, UUID> {

  List<ExclusionCondition> findByTermsCoverageIdOrderBySortOrderAsc(UUID termsCoverageId);


  List<ExclusionCondition> findByTermsCoverageIdAndSeverityOrderBySortOrderAsc(
      UUID termsCoverageId, ExclusionConditionSeverity severity);

  /** 규칙별로 한 번씩 조회하면 규칙 수만큼 쿼리가 나간다. 화면에 필요한 것을 한 번에 가져온다. */
  List<ExclusionCondition> findByTermsCoverageIdInOrderBySortOrderAsc(
      Collection<UUID> termsCoverageIds);

  void deleteByTermsCoverageIdIn(Collection<UUID> termsCoverageIds);
}
