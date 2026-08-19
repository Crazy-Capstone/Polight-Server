package polight.server.domain.analysis.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import polight.server.domain.analysis.entity.SubCoverageLimit;

public interface SubCoverageLimitRepository extends JpaRepository<SubCoverageLimit, UUID> {

  List<SubCoverageLimit> findByCoverageItemIdOrderBySortOrderAsc(UUID coverageItemId);

  /** 담보별로 한 번씩 조회하면 담보 수만큼 쿼리가 나간다. 한 분석의 자식을 한 번에 가져온다. */
  List<SubCoverageLimit> findByCoverageItemIdInOrderBySortOrderAsc(Collection<UUID> coverageItemIds);

  void deleteByCoverageItemIdIn(Collection<UUID> coverageItemIds);
}
