package polight.server.domain.analysis.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import polight.server.domain.analysis.dto.AnalysisResponse;
import polight.server.domain.insurance.dto.PolicyDocumentResponse;
import polight.server.domain.insurance.entity.DocumentKind;

/**
 * 증권이 올라오면 분석을 바로 시작한다.
 *
 * <p>증권 업로드와 분석 시작을 사용자가 두 번 나눠 호출할 이유가 없다. 증권은 곧 분석 대상이므로 올라온 순간 시작한다.
 *
 * <p>약관({@link DocumentKind#TERMS})은 다르다. 증권 분석 결과에서 필요한 약관을 DB에서 찾지 못했을 때만 사용자에게 요청해 받는 문서이고, 그
 * 시점과 분석 시점이 같지 않을 수 있다. 그래서 약관은 {@code POST .../analysis}로 명시 호출한다.
 *
 * <p>{@link AnalysisResultService}가 {@code PolicyDocumentService}를 의존하므로 업로드 쪽 서비스에서 분석 시작을 호출하면 순환
 * 의존이 된다. 두 서비스를 잇는 조합 책임만 이 클래스가 맡는다.
 */
@Service
@RequiredArgsConstructor
public class CertificateAnalysisStarter {

  private final AnalysisResultService analysisResultService;

  /**
   * 업로드된 문서가 증권이면 분석을 시작하고 그 작업을 돌려준다.
   *
   * @return 증권이 아니면 {@code null}. 약관은 분석을 시작하지 않는다
   */
  public AnalysisResponse startIfCertificate(
      UUID userId, UUID tripId, PolicyDocumentResponse document) {
    if (document.documentKind() != DocumentKind.CERTIFICATE) {
      return null;
    }

    return analysisResultService.startAnalysis(userId, tripId, document.id());
  }
}
