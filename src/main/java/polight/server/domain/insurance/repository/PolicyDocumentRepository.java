package polight.server.domain.insurance.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import polight.server.domain.insurance.entity.PolicyDocument;
import polight.server.domain.insurance.entity.DocumentKind;

public interface PolicyDocumentRepository extends JpaRepository<PolicyDocument, UUID> {

  Optional<PolicyDocument> findFirstByUserIdAndDocumentKindOrderByUploadedAtDescIdDesc(
      UUID userId, DocumentKind documentKind);

  Optional<PolicyDocument> findFirstByUserIdAndTripIdAndDocumentKindOrderByUploadedAtDescIdDesc(
      UUID userId, UUID tripId, DocumentKind documentKind);

  List<PolicyDocument> findByUserId(UUID userId);

  List<PolicyDocument> findAllByTripIdAndUserIdOrderByUploadedAtDesc(UUID tripId, UUID userId);

  Optional<PolicyDocument> findByIdAndTripIdAndUserId(UUID id, UUID tripId, UUID userId);
}
