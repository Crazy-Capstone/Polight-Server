package polight.server.domain.analysis.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import polight.server.domain.analysis.entity.ExclusionCondition;
import polight.server.domain.analysis.entity.ExclusionConditionSeverity;

public interface ExclusionConditionRepository extends JpaRepository<ExclusionCondition, UUID> {

  List<ExclusionCondition> findByCoverageItemIdOrderBySortOrderAsc(UUID coverageItemId);

  List<ExclusionCondition> findByCoverageItemAnalysisResultIdOrderByCoverageItemSortOrderAscSortOrderAsc(
      UUID analysisResultId);

  List<ExclusionCondition> findByCoverageItemIdAndSeverityOrderBySortOrderAsc(
      UUID coverageItemId, ExclusionConditionSeverity severity);
}
