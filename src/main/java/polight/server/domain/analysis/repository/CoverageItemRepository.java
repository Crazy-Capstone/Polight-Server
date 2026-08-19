package polight.server.domain.analysis.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import polight.server.domain.analysis.entity.CoverageItem;

public interface CoverageItemRepository extends JpaRepository<CoverageItem, UUID> {

  List<CoverageItem> findByAnalysisResultIdOrderBySortOrderAsc(UUID analysisResultId);
}
