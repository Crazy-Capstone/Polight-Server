package polight.server.domain.insurance.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import polight.server.domain.insurance.entity.InsuranceDocument;

public interface InsuranceDocumentRepository extends JpaRepository<InsuranceDocument, UUID> {

  List<InsuranceDocument> findByUserId(UUID userId);
}
