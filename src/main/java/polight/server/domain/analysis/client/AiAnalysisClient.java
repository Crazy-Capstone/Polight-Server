package polight.server.domain.analysis.client;

import polight.server.domain.analysis.dto.AiAnalysisRequest;

public interface AiAnalysisClient {

  void requestAnalysis(AiAnalysisRequest request);
}
