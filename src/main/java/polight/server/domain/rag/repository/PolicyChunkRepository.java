package polight.server.domain.rag.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import polight.server.domain.rag.entity.PolicyChunk;

public interface PolicyChunkRepository extends JpaRepository<PolicyChunk, UUID> {

  List<PolicyChunk> findByAnalysisResultIdOrderByChunkIndexAsc(UUID analysisResultId);

  Optional<PolicyChunk> findByAnalysisResultIdAndChunkIndex(UUID analysisResultId, int chunkIndex);

  //TODO : 쿼리 검토 필요
  @Query(
      """
      SELECT pc
      FROM PolicyChunk pc
      JOIN pc.analysisResult ar
      JOIN ar.document document
      WHERE document.user.id = :userId
        AND ar.active = true
        AND ar.status = polight.server.domain.analysis.entity.AnalysisStatus.COMPLETED
      ORDER BY ar.completedAt DESC, pc.chunkIndex ASC
      """)
  List<PolicyChunk> findActiveCompletedChunksByUserId(@Param("userId") UUID userId);

  @Query(
      """
      SELECT pc
      FROM PolicyChunk pc
      JOIN pc.analysisResult ar
      JOIN ar.document document
      LEFT JOIN document.trip documentTrip
      LEFT JOIN document.policy documentPolicy
      LEFT JOIN documentPolicy.trip documentPolicyTrip
      LEFT JOIN ar.policy analysisPolicy
      LEFT JOIN analysisPolicy.trip analysisPolicyTrip
      WHERE document.user.id = :userId
        AND ar.active = true
        AND ar.status = polight.server.domain.analysis.entity.AnalysisStatus.COMPLETED
        AND (
          documentTrip.id = :tripId
          OR documentPolicyTrip.id = :tripId
          OR analysisPolicyTrip.id = :tripId
        )
      ORDER BY ar.completedAt DESC, pc.chunkIndex ASC
      """)
  List<PolicyChunk> findActiveCompletedChunksByUserIdAndTripId(
      @Param("userId") UUID userId, @Param("tripId") UUID tripId);

  @Query(
      """
      SELECT pc
      FROM PolicyChunk pc
      JOIN pc.analysisResult ar
      JOIN ar.document document
      LEFT JOIN document.policy documentPolicy
      LEFT JOIN ar.policy analysisPolicy
      WHERE document.user.id = :userId
        AND ar.active = true
        AND ar.status = polight.server.domain.analysis.entity.AnalysisStatus.COMPLETED
        AND (
          documentPolicy.id = :policyId
          OR analysisPolicy.id = :policyId
        )
      ORDER BY ar.completedAt DESC, pc.chunkIndex ASC
      """)
  List<PolicyChunk> findActiveCompletedChunksByUserIdAndPolicyId(
      @Param("userId") UUID userId, @Param("policyId") UUID policyId);

  @Query(
      """
      SELECT pc
      FROM PolicyChunk pc
      JOIN pc.analysisResult ar
      JOIN ar.document document
      WHERE document.user.id = :userId
        AND ar.id = :analysisResultId
        AND ar.active = true
        AND ar.status = polight.server.domain.analysis.entity.AnalysisStatus.COMPLETED
      ORDER BY pc.chunkIndex ASC
      """)
  List<PolicyChunk> findActiveCompletedChunksByUserIdAndAnalysisResultId(
      @Param("userId") UUID userId, @Param("analysisResultId") UUID analysisResultId);
}
