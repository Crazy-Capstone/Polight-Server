package polight.server.domain.insurance.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import polight.server.domain.insurance.entity.PolicyDocument;

public interface PolicyDocumentRepository extends JpaRepository<PolicyDocument, UUID> {

  List<PolicyDocument> findByUserId(UUID userId);

  List<PolicyDocument> findByPolicyId(UUID policyId);
}
