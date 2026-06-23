package polight.server.domain.analysis.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import polight.server.domain.analysis.entity.CoverageItem;

public interface CoverageItemRepository extends JpaRepository<CoverageItem, UUID> {

  List<CoverageItem> findByPolicyIdOrderBySortOrderAsc(UUID policyId);

  List<CoverageItem> findTop4ByPolicyIdOrderBySortOrderAsc(UUID policyId);

  Optional<CoverageItem> findByIdAndPolicyId(UUID id, UUID policyId);

  long countByPolicyId(UUID policyId);
}
