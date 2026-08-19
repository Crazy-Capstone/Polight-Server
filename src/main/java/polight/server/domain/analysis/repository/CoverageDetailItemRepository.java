package polight.server.domain.analysis.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import polight.server.domain.analysis.entity.CoverageDetailItem;

public interface CoverageDetailItemRepository extends JpaRepository<CoverageDetailItem, UUID> {

  List<CoverageDetailItem> findByCoverageItemIdOrderBySortOrderAsc(UUID coverageItemId);

  void deleteByCoverageItemIdIn(Collection<UUID> coverageItemIds);
}
