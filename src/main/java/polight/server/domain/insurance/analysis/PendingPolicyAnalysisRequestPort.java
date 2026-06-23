package polight.server.domain.insurance.analysis;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PendingPolicyAnalysisRequestPort implements PolicyAnalysisRequestPort {
  @Override
  public void request(UUID analysisId, UUID documentId) {
    // 실제 OCR/AI 작업 큐 연동 지점. MVP에서는 PROCESSING 상태만 유지한다.
  }
}
