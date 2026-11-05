package polight.server.domain.trip.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.insurance.entity.PolicyDocument;
import polight.server.domain.insurance.event.DocumentDeletionRequestedEvent;
import polight.server.domain.insurance.repository.PolicyDocumentRepository;
import polight.server.domain.trip.repository.TripDeletionRepository;
import polight.server.domain.trip.repository.TripRepository;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

@Service
@RequiredArgsConstructor
public class TripDeletionService {

  private final TripRepository tripRepository;
  private final PolicyDocumentRepository policyDocumentRepository;
  private final TripDeletionRepository tripDeletionRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public void deleteTrip(UUID userId, UUID tripId) {
    if (userId == null) {
      throw new BaseException(ErrorCode.AUTHENTICATION_REQUIRED);
    }
    tripRepository.findOwnedByIdForUpdate(tripId, userId)
        .orElseThrow(() -> new BaseException(ErrorCode.TRIP_NOT_FOUND));
    var paths = policyDocumentRepository.findAllByTripIdAndUserIdOrderByUploadedAtDesc(tripId, userId)
        .stream().map(PolicyDocument::getStoredFilePath).distinct().toList();
    tripDeletionRepository.deleteTripData(tripId, userId);
    eventPublisher.publishEvent(new DocumentDeletionRequestedEvent(paths));
  }
}
