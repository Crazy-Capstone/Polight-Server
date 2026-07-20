package polight.server.domain.rag.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import polight.server.domain.rag.entity.CoverageItemSource;
import polight.server.domain.rag.entity.CoverageItemSourceRole;

public interface CoverageItemSourceRepository extends JpaRepository<CoverageItemSource, UUID> {

  List<CoverageItemSource> findByCoverageItemId(UUID coverageItemId);

  List<CoverageItemSource> findByCoverageItemIdOrderBySourceRoleAscCreatedAtAsc(UUID coverageItemId);

  List<CoverageItemSource> findByPolicyChunkId(UUID policyChunkId);

  boolean existsByCoverageItemIdAndPolicyChunkIdAndSourceRole(
      UUID coverageItemId, UUID policyChunkId, CoverageItemSourceRole sourceRole);
}
