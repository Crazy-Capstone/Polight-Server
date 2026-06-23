package polight.server.domain.analysis.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import polight.server.domain.analysis.entity.RequiredDocument;

public interface RequiredDocumentRepository extends JpaRepository<RequiredDocument, UUID> {
  List<RequiredDocument> findByCoverageItemIdOrderBySortOrderAsc(UUID coverageItemId);
}
