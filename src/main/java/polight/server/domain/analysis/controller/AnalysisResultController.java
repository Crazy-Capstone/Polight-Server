package polight.server.domain.analysis.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import polight.server.domain.analysis.service.AnalysisResultService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/analysis-results")
public class AnalysisResultController {

  private final AnalysisResultService analysisResultService;
}
