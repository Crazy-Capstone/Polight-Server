package polight.server.domain.rag.repository;

import java.util.Collection;
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

  /** 이 분석으로 색인된 조각이 하나라도 있는지. 조각을 만드는 주체는 AI 서버다. */
  boolean existsByAnalysisResultId(UUID analysisResultId);

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

  /** 주어진 id 중 해당 사용자 소유인 청크만. 소유자 조건이 있어야 남의 청크 조회를 막을 수 있다. */
  List<PolicyChunk> findByIdInAndUserId(Collection<UUID> ids, UUID userId);
}
