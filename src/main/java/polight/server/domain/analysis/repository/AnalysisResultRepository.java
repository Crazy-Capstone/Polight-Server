package polight.server.domain.analysis.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import polight.server.domain.analysis.entity.AnalysisStatus;
import polight.server.domain.analysis.entity.AnalysisResult;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, UUID> {

  List<AnalysisResult> findByDocumentId(UUID documentId);

  Optional<AnalysisResult> findOneByDocumentId(UUID documentId);

  Optional<AnalysisResult> findOneByDocumentIdAndStatus(UUID documentId, AnalysisStatus status);

  /**
   * 분석을 시작·재시작하기 위해 문서의 분석을 잠그고 읽는다.
   *
   * <p>락이 필요한 이유가 둘이다.
   *
   * <ul>
   *   <li>같은 문서로 재시도 요청이 동시에 들어오면, 둘 다 {@code FAILED}를 보고 각자 {@code restart()}해 AI 요청이
   *       두 번 나간다. 뒤에 온 요청은 이 락에서 기다렸다가 {@code PROCESSING}을 보고 돌아간다
   *   <li>타임아웃 처리가 같은 행을 잠그므로, 재시작과 타임아웃이 같은 분석에 겹쳐 일어나지 않는다
   * </ul>
   *
   * <p>읽기 전용 조회({@link #findOneByDocumentId})와 분리해 둔다. 상태를 조회하는 GET 요청까지 행을 잠글 이유가 없다.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT ar FROM AnalysisResult ar WHERE ar.document.id = :documentId")
  Optional<AnalysisResult> findOneByDocumentIdForUpdate(@Param("documentId") UUID documentId);

  /**
   * 시작한 지 오래된 채 아직 끝나지 않은 분석을 잠그고 읽는다. AI 서버가 콜백을 보내지 않은 것들이다.
   *
   * <p>{@code idx_analysis_results_status}가 status 단독 인덱스라 상태로 먼저 좁힌 뒤 시각을 비교한다.
   *
   * <p><b>락이 없으면 완료된 분석을 실패로 덮어쓴다.</b> 이 목록을 읽은 뒤 커밋하기 전에 완료 콜백이 도착해 {@code
   * COMPLETED}로 커밋되면, 뒤이은 {@code markFailed()}가 그것을 지운다. 담보까지 다 저장된 성공한 분석이 실패로 보이고, 사용자는 이미 성공한
   * 분석을 다시 돌리게 된다.
   *
   * <p>PostgreSQL은 READ COMMITTED에서 {@code SELECT ... FOR UPDATE}가 락을 얻은 뒤 조건을 다시 평가하므로, 그
   * 사이 {@code COMPLETED}가 된 행은 결과에서 빠진다. 다른 DB나 다른 순서를 위해 호출부에서 상태를 한 번 더 확인한다.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
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

  /**
   * 여행에 올린 증권의 완료된 분석을 최근 순으로 돌려준다.
   *
   * <p>챗봇이 프롬프트에 실을 가입 담보를 여기서 찾는다. 약관 분석을 제외하는 이유는 담보와 가입금액이 증권에만 있기 때문이다. 같은 여행에 증권을 여러 번
   * 올릴 수 있어 목록으로 받고 호출한 쪽이 가장 최근 것을 쓴다.
   */
  @Query(
      """
      SELECT ar
      FROM AnalysisResult ar
      WHERE ar.document.trip.id = :tripId
        AND ar.document.user.id = :userId
        AND ar.document.documentKind = polight.server.domain.insurance.entity.DocumentKind.CERTIFICATE
        AND ar.status = polight.server.domain.analysis.entity.AnalysisStatus.COMPLETED
      ORDER BY ar.completedAt DESC
      """)
  List<AnalysisResult> findCompletedCertificateAnalyses(
      @Param("userId") UUID userId, @Param("tripId") UUID tripId);
}
