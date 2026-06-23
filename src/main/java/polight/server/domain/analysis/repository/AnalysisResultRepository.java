package polight.server.domain.analysis.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import polight.server.domain.analysis.entity.AnalysisResult;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, UUID> {

  List<AnalysisResult> findByDocumentId(UUID documentId);

  List<AnalysisResult> findByPolicyId(UUID policyId);

  Optional<AnalysisResult> findFirstByDocumentIdOrderByCreatedAtDesc(UUID documentId);
}
