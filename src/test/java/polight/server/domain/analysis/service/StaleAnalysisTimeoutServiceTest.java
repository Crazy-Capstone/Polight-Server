package polight.server.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import polight.server.domain.analysis.entity.AnalysisResult;
import polight.server.domain.analysis.entity.AnalysisStatus;
import polight.server.domain.analysis.repository.AnalysisResultRepository;
import polight.server.domain.insurance.entity.DocumentKind;
import polight.server.domain.insurance.entity.DocumentParseStatus;
import polight.server.domain.insurance.entity.PolicyDocument;

@ExtendWith(MockitoExtension.class)
class StaleAnalysisTimeoutServiceTest {

  private static final Duration TIMEOUT = Duration.ofMinutes(10);

  @Mock private AnalysisResultRepository analysisResultRepository;

  @Test
  void 제한_시간을_넘긴_분석을_실패로_내리고_문서도_같이_표시한다() {
    PolicyDocument document = certificate();
    AnalysisResult stale =
        AnalysisResult.builder()
            .document(document)
            .startedAt(LocalDateTime.now().minusMinutes(30))
            .build();
    given(
            analysisResultRepository.findByStatusAndStartedAtBefore(
                eq(AnalysisStatus.PROCESSING), any(LocalDateTime.class)))
        .willReturn(List.of(stale));

    int count = service().failTimedOutAnalyses();

    assertThat(count).isEqualTo(1);
    assertThat(stale.getStatus()).isEqualTo(AnalysisStatus.FAILED);
    assertThat(stale.getFailureReason()).contains("시간 초과");
    assertThat(stale.getCompletedAt()).isNotNull();
    // 문서를 같이 내려야 문서 목록 화면에서도 실패가 보인다.
    assertThat(document.getParseStatus()).isEqualTo(DocumentParseStatus.FAILED);
  }

  @Test
  void 조회_후_완료된_분석은_실패로_덮어쓰지_않는다() {
    // 목록을 읽은 뒤 커밋 전에 완료 콜백이 도착한 상황. 여기서 markFailed 를 하면 담보까지 저장된
    // 성공 결과가 실패로 보이고, 사용자가 이미 성공한 분석을 다시 돌린다.
    PolicyDocument document = certificate();
    AnalysisResult completed =
        AnalysisResult.builder()
            .document(document)
            .startedAt(LocalDateTime.now().minusMinutes(30))
            .build();
    completed.markCompleted(LocalDateTime.now());
    document.markParseCompleted();
    given(
            analysisResultRepository.findByStatusAndStartedAtBefore(
                eq(AnalysisStatus.PROCESSING), any(LocalDateTime.class)))
        .willReturn(List.of(completed));

    int count = service().failTimedOutAnalyses();

    assertThat(count).isZero();
    assertThat(completed.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
    assertThat(completed.getFailureReason()).isNull();
    assertThat(document.getParseStatus()).isEqualTo(DocumentParseStatus.COMPLETED);
  }

  @Test
  void 상태가_바뀐_건은_건너뛰고_남은_건만_실패로_내린다() {
    PolicyDocument completedDocument = certificate();
    AnalysisResult completed =
        AnalysisResult.builder()
            .document(completedDocument)
            .startedAt(LocalDateTime.now().minusMinutes(30))
            .build();
    completed.markCompleted(LocalDateTime.now());

    PolicyDocument staleDocument = certificate();
    AnalysisResult stale =
        AnalysisResult.builder()
            .document(staleDocument)
            .startedAt(LocalDateTime.now().minusMinutes(30))
            .build();

    given(
            analysisResultRepository.findByStatusAndStartedAtBefore(
                eq(AnalysisStatus.PROCESSING), any(LocalDateTime.class)))
        .willReturn(List.of(completed, stale));

    // 건너뛴 건은 세지 않는다. 반환값이 목록 크기면 실제로 내리지 않은 건까지 로그에 잡힌다.
    assertThat(service().failTimedOutAnalyses()).isEqualTo(1);
    assertThat(stale.getStatus()).isEqualTo(AnalysisStatus.FAILED);
    assertThat(completed.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
  }

  @Test
  void 넘긴_분석이_없으면_아무것도_하지_않는다() {
    given(
            analysisResultRepository.findByStatusAndStartedAtBefore(
                eq(AnalysisStatus.PROCESSING), any(LocalDateTime.class)))
        .willReturn(List.of());

    assertThat(service().failTimedOutAnalyses()).isZero();
  }

  @Test
  void 조회_기준_시각은_설정한_제한_시간만큼_과거다() {
    ArgumentCaptor<LocalDateTime> threshold = ArgumentCaptor.forClass(LocalDateTime.class);
    given(
            analysisResultRepository.findByStatusAndStartedAtBefore(
                eq(AnalysisStatus.PROCESSING), threshold.capture()))
        .willReturn(List.of());
    LocalDateTime before = LocalDateTime.now();

    service().failTimedOutAnalyses();

    // 기준 시각은 서비스가 부르는 now() 에서 제한 시간을 뺀 값이다. 그 now() 는 호출 전후 사이에 있으므로
    // 기준 시각도 [before - 10분, after - 10분] 안에 들어와야 한다.
    // 부호가 뒤집히면 미래 시각이 되어 갓 시작한 분석까지 실패로 내리는데, 이 범위가 그것을 잡는다.
    LocalDateTime after = LocalDateTime.now();
    assertThat(threshold.getValue()).isBetween(before.minus(TIMEOUT), after.minus(TIMEOUT));
  }

  @Test
  void 제한_시간이_0이면_기동에_실패한다() {
    assertThatThrownBy(
            () -> new StaleAnalysisTimeoutService(analysisResultRepository, Duration.ZERO))
        .isInstanceOf(IllegalStateException.class);
  }

  private PolicyDocument certificate() {
    return PolicyDocument.builder()
        .originalFilename("증권.pdf")
        .storedFilePath("policy-documents/abc")
        .documentKind(DocumentKind.CERTIFICATE)
        .build();
  }

  private StaleAnalysisTimeoutService service() {
    return new StaleAnalysisTimeoutService(analysisResultRepository, TIMEOUT);
  }
}
