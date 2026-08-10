package polight.server.domain.insurance.service;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import polight.server.domain.insurance.dto.PolicyDocumentResponse;
import polight.server.domain.insurance.entity.PolicyDocument;
import polight.server.domain.insurance.mapper.PolicyDocumentMapper;
import polight.server.domain.insurance.repository.PolicyDocumentRepository;
import polight.server.domain.insurance.storage.PolicyDocumentStorage;
import polight.server.domain.trip.entity.Trip;
import polight.server.domain.trip.service.TripService;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyDocumentService {

  private final PolicyDocumentRepository policyDocumentRepository;
  private final PolicyDocumentMapper policyDocumentMapper;
  private final TripService tripService;
  private final PolicyDocumentStorage policyDocumentStorage;

  @Transactional
  public PolicyDocumentResponse uploadDocument(UUID userId, UUID tripId, MultipartFile file) {
    return uploadDocumentTo(tripService.getOwnedTrip(userId, tripId), file);
  }

  /**
   * 소유권이 이미 확인된 여행에 약관 문서를 저장한다.
   *
   * <p>여행과 문서를 한 요청으로 함께 만드는 흐름에서, 방금 생성한 여행 엔티티를 그대로 넘겨 받기 위해 분리했다.
   */
  @Transactional
  public PolicyDocumentResponse uploadDocumentTo(Trip trip, MultipartFile file) {
    if (file.isEmpty()) {
      throw new BaseException(ErrorCode.EMPTY_POLICY_DOCUMENT_FILE);
    }

    String originalFilename = normalizeFilename(file.getOriginalFilename());
    String storedFilePath = policyDocumentStorage.store(file);

    PolicyDocument document =
        policyDocumentMapper.toEntity(trip, file, originalFilename, storedFilePath);
    return policyDocumentMapper.toResponse(policyDocumentRepository.save(document));
  }

  public List<PolicyDocumentResponse> getDocuments(UUID userId, UUID tripId) {
    tripService.getOwnedTrip(userId, tripId);

    return policyDocumentMapper.toResponses(
        policyDocumentRepository.findAllByTripIdAndUserIdOrderByUploadedAtDesc(tripId, userId));
  }

  /**
   * 소유권을 확인한 약관 문서 엔티티를 돌려준다.
   *
   * <p>다른 도메인 서비스가 "이 사용자의, 이 여행에 속한 문서가 맞는지" 확인하면서 엔티티를 함께 얻기 위해 호출한다. 여행 소유권을 먼저 확인해 잘못된 tripId와
   * 잘못된 documentId를 다른 에러로 구분한다.
   */
  public PolicyDocument getOwnedDocument(UUID userId, UUID tripId, UUID documentId) {
    tripService.getOwnedTrip(userId, tripId);

    return policyDocumentRepository
        .findByIdAndTripIdAndUserId(documentId, tripId, userId)
        .orElseThrow(() -> new BaseException(ErrorCode.POLICY_DOCUMENT_NOT_FOUND));
  }

  private String normalizeFilename(String originalFilename) {
    if (originalFilename == null || originalFilename.isBlank()) {
      return "policy-document";
    }
    String filename = Path.of(originalFilename).getFileName().toString();
    return filename.length() <= 255 ? filename : filename.substring(filename.length() - 255);
  }
}
