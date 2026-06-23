package polight.server.domain.policy.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import polight.server.domain.policy.entity.Policy;
import polight.server.domain.policy.entity.PolicyStatus;

public interface PolicyRepository extends JpaRepository<Policy, UUID> {

  List<Policy> findByUserId(UUID userId);

  List<Policy> findByUserIdAndStatus(UUID userId, PolicyStatus status);
}
