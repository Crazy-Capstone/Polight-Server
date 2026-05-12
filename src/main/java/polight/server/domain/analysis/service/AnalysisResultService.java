package polight.server.domain.analysis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import polight.server.domain.analysis.repository.AnalysisResultRepository;

@Service
@RequiredArgsConstructor
public class AnalysisResultService {

  private final AnalysisResultRepository analysisResultRepository;
}
