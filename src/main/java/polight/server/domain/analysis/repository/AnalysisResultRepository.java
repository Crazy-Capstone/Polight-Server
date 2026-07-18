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
      value =
          """
          SELECT *
          FROM analysis_results
          WHERE document_id = :documentId
            AND is_active = true
            AND status = 'COMPLETED'
          ORDER BY completed_at DESC
          LIMIT 1
          """,
      nativeQuery = true)
  Optional<AnalysisResult> findActiveCompletedByDocumentId(@Param("documentId") UUID documentId);

  // 같은 보험이여도 여러번 분석될 수 있음 , 그 분석된 버전을 관리함
  @Query(
      value =
          """
          SELECT *
          FROM analysis_results
          WHERE policy_id = :policyId
            AND is_active = true
            AND status = 'COMPLETED'
          ORDER BY completed_at DESC
          LIMIT 1
          """,
      nativeQuery = true)
  Optional<AnalysisResult> findActiveCompletedByPolicyId(@Param("policyId") UUID policyId);
}
