package polight.server.domain.rag.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import polight.server.domain.rag.entity.CoverageItemSource;
import polight.server.domain.rag.entity.CoverageItemSourceRole;

public interface CoverageItemSourceRepository extends JpaRepository<CoverageItemSource, UUID> {

  List<CoverageItemSource> findByCoverageItemId(UUID coverageItemId);

  List<CoverageItemSource> findByCoverageItemIdOrderBySourceRoleAscCreatedAtAsc(UUID coverageItemId);

  List<CoverageItemSource> findByTermsChunkId(UUID termsChunkId);

  boolean existsByCoverageItemIdAndTermsChunkIdAndSourceRole(
      UUID coverageItemId, UUID termsChunkId, CoverageItemSourceRole sourceRole);

  /** 담보를 지우기 전에 이 테이블부터 비워야 한다. coverage_item_id 쪽 FK 때문에 순서가 강제된다. */
  void deleteByCoverageItemIdIn(Collection<UUID> coverageItemIds);
}
