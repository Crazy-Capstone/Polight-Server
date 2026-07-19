package polight.server.domain.analysis.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import polight.server.domain.analysis.entity.AnalysisResult;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, UUID> {

  List<AnalysisResult> findByDocumentId(UUID documentId);

  List<AnalysisResult> findByPolicyId(UUID policyId);

  List<AnalysisResult> findByDocumentIdOrderByCreatedAtDesc(UUID documentId);

  @Query(
      """
      SELECT ar
      FROM AnalysisResult ar
      WHERE ar.document.id = :documentId
        AND ar.active = true
        AND ar.status = polight.server.domain.analysis.entity.AnalysisStatus.COMPLETED
      """)
  Optional<AnalysisResult> findActiveCompletedByDocumentId(@Param("documentId") UUID documentId);

  // 같은 보험이여도 여러번 분석될 수 있음 , 그 분석된 버전을 관리함
  @Query(
      """
      SELECT ar
      FROM AnalysisResult ar
      WHERE ar.policy.id = :policyId
        AND ar.active = true
        AND ar.status = polight.server.domain.analysis.entity.AnalysisStatus.COMPLETED
      """)
  Optional<AnalysisResult> findActiveCompletedByPolicyId(@Param("policyId") UUID policyId);
}
