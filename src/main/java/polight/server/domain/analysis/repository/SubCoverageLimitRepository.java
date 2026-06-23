package polight.server.domain.analysis.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import polight.server.domain.analysis.entity.SubCoverageLimit;

public interface SubCoverageLimitRepository extends JpaRepository<SubCoverageLimit, UUID> {

  List<SubCoverageLimit> findByCoverageItemIdOrderBySortOrderAsc(UUID coverageItemId);
}
