package polight.server.domain.insurance.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import polight.server.domain.insurance.entity.PolicyDocument;

public interface PolicyDocumentRepository extends JpaRepository<PolicyDocument, UUID> {

  List<PolicyDocument> findByUserId(UUID userId);

  List<PolicyDocument> findByPolicyId(UUID policyId);

  List<PolicyDocument> findAllByTripIdAndUserIdOrderByUploadedAtDesc(UUID tripId, UUID userId);

  Optional<PolicyDocument> findByIdAndTripIdAndUserId(UUID id, UUID tripId, UUID userId);
}
