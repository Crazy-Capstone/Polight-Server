package polight.server.domain.analysis.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import polight.server.domain.analysis.entity.AnalysisStatus;
import polight.server.domain.analysis.entity.AnalysisResult;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, UUID> {

  List<AnalysisResult> findByDocumentId(UUID documentId);

  List<AnalysisResult> findByPolicyId(UUID policyId);

  Optional<AnalysisResult> findOneByDocumentId(UUID documentId);

  Optional<AnalysisResult> findOneByDocumentIdAndStatus(UUID documentId, AnalysisStatus status);

  @Query(
      """
      SELECT ar
      FROM AnalysisResult ar
      WHERE ar.document.id = :documentId
        AND ar.status = polight.server.domain.analysis.entity.AnalysisStatus.COMPLETED
      """)
  Optional<AnalysisResult> findCompletedByDocumentId(@Param("documentId") UUID documentId);

  @Query(
      """
      SELECT ar
      FROM AnalysisResult ar
      WHERE ar.policy.id = :policyId
        AND ar.status = polight.server.domain.analysis.entity.AnalysisStatus.COMPLETED
      """)
  List<AnalysisResult> findCompletedByPolicyId(@Param("policyId") UUID policyId);
}
