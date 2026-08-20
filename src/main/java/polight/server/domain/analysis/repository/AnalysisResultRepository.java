package polight.server.domain.analysis.repository;

import java.time.LocalDateTime;
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

  /**
   * 시작한 지 오래된 채 아직 끝나지 않은 분석. AI 서버가 콜백을 보내지 않은 것들을 찾는다.
   *
   * <p>{@code idx_analysis_results_status}가 status 단독 인덱스라 상태로 먼저 좁힌 뒤 시각을 비교한다.
   */
  List<AnalysisResult> findByStatusAndStartedAtBefore(
      AnalysisStatus status, LocalDateTime startedAt);

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
