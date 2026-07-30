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
      WHERE pc.user.id = :userId
        AND pc.analysisResult.status = polight.server.domain.analysis.entity.AnalysisStatus.COMPLETED
      ORDER BY pc.analysisResult.completedAt DESC, pc.chunkIndex ASC
      """)
  List<PolicyChunk> findCompletedChunksByUserId(@Param("userId") UUID userId);

  @Query(
      """
      SELECT pc
      FROM PolicyChunk pc
      WHERE pc.user.id = :userId
        AND pc.trip.id = :tripId
        AND pc.analysisResult.status = polight.server.domain.analysis.entity.AnalysisStatus.COMPLETED
      ORDER BY pc.analysisResult.completedAt DESC, pc.chunkIndex ASC
      """)
  List<PolicyChunk> findCompletedChunksByUserIdAndTripId(
      @Param("userId") UUID userId, @Param("tripId") UUID tripId);

  @Query(
      """
      SELECT pc
      FROM PolicyChunk pc
      WHERE pc.user.id = :userId
        AND pc.policy.id = :policyId
        AND pc.analysisResult.status = polight.server.domain.analysis.entity.AnalysisStatus.COMPLETED
      ORDER BY pc.analysisResult.completedAt DESC, pc.chunkIndex ASC
      """)
  List<PolicyChunk> findCompletedChunksByUserIdAndPolicyId(
      @Param("userId") UUID userId, @Param("policyId") UUID policyId);

  @Query(
      """
      SELECT pc
      FROM PolicyChunk pc
      WHERE pc.user.id = :userId
        AND pc.analysisResult.id = :analysisResultId
        AND pc.analysisResult.status = polight.server.domain.analysis.entity.AnalysisStatus.COMPLETED
      ORDER BY pc.chunkIndex ASC
      """)
  List<PolicyChunk> findCompletedChunksByUserIdAndAnalysisResultId(
      @Param("userId") UUID userId, @Param("analysisResultId") UUID analysisResultId);

  @Query(
      """
      SELECT pc
      FROM PolicyChunk pc
      WHERE pc.user.id = :userId
        AND pc.document.id = :documentId
        AND pc.analysisResult.status = polight.server.domain.analysis.entity.AnalysisStatus.COMPLETED
      ORDER BY pc.chunkIndex ASC
      """)
  List<PolicyChunk> findCompletedChunksByUserIdAndDocumentId(
      @Param("userId") UUID userId, @Param("documentId") UUID documentId);
}
