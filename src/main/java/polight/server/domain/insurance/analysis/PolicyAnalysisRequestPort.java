package polight.server.domain.insurance.analysis;

import java.util.UUID;

public interface PolicyAnalysisRequestPort {
  void request(UUID analysisId, UUID documentId);
}
