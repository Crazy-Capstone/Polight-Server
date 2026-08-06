package polight.server.domain.analysis.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import polight.server.domain.analysis.dto.AnalysisResponse;
import polight.server.domain.analysis.entity.AnalysisResult;
import polight.server.domain.analysis.repository.AnalysisResultRepository;
import polight.server.domain.insurance.entity.PolicyDocument;
import polight.server.domain.insurance.repository.PolicyDocumentRepository;
import polight.server.domain.trip.service.TripService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisResultService {

  private final AnalysisResultRepository analysisResultRepository;
  private final PolicyDocumentRepository policyDocumentRepository;
  private final TripService tripService;

  @Transactional
  public AnalysisResponse start(UUID userId, UUID tripId, UUID documentId) {
    PolicyDocument document = getOwnedDocument(userId, tripId, documentId);
    AnalysisResult result =
        analysisResultRepository
            .findOneByDocumentId(documentId)
            .orElseGet(
                () ->
                    analysisResultRepository.save(
                        AnalysisResult.builder().document(document).policy(document.getPolicy()).build()));
    return AnalysisResponse.from(result);
  }

  public AnalysisResponse find(UUID userId, UUID tripId, UUID documentId) {
    getOwnedDocument(userId, tripId, documentId);
    return analysisResultRepository
        .findOneByDocumentId(documentId)
        .map(AnalysisResponse::from)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "분석 결과를 찾을 수 없습니다."));
  }

  private PolicyDocument getOwnedDocument(UUID userId, UUID tripId, UUID documentId) {
    tripService.getOwnedTrip(userId, tripId);
    return policyDocumentRepository
        .findByIdAndTripIdAndUserId(documentId, tripId, userId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "약관 문서를 찾을 수 없습니다."));
  }
}
