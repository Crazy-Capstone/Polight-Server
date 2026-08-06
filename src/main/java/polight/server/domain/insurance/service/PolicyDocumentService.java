package polight.server.domain.insurance.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import polight.server.domain.insurance.dto.PolicyDocumentResponse;
import polight.server.domain.insurance.entity.PolicyDocument;
import polight.server.domain.insurance.repository.PolicyDocumentRepository;
import polight.server.domain.trip.entity.Trip;
import polight.server.domain.trip.service.TripService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyDocumentService {

  private final PolicyDocumentRepository policyDocumentRepository;
  private final TripService tripService;

  @Value("${storage.policy-documents-directory:uploads/policy-documents}")
  private String storageDirectory;

  @Transactional
  public PolicyDocumentResponse upload(UUID userId, UUID tripId, MultipartFile file) {
    if (file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "업로드할 약관 파일은 비어 있을 수 없습니다.");
    }

    Trip trip = tripService.getOwnedTrip(userId, tripId);
    String originalFilename = normalizeFilename(file.getOriginalFilename());
    Path target = Path.of(storageDirectory).toAbsolutePath().normalize().resolve(UUID.randomUUID().toString());

    try {
      Files.createDirectories(target.getParent());
      Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException exception) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "약관 파일을 저장하지 못했습니다.", exception);
    }

    PolicyDocument document =
        PolicyDocument.builder()
            .user(trip.getUser())
            .trip(trip)
            .originalFilename(originalFilename)
            .storedFilePath(target.toString())
            .contentType(file.getContentType())
            .fileSize(file.getSize())
            .build();
    return PolicyDocumentResponse.from(policyDocumentRepository.save(document));
  }

  public List<PolicyDocumentResponse> findAll(UUID userId, UUID tripId) {
    tripService.getOwnedTrip(userId, tripId);
    return policyDocumentRepository
        .findAllByTripIdAndUserIdOrderByUploadedAtDesc(tripId, userId)
        .stream()
        .map(PolicyDocumentResponse::from)
        .toList();
  }

  private String normalizeFilename(String originalFilename) {
    if (originalFilename == null || originalFilename.isBlank()) {
      return "policy-document";
    }
    String filename = Path.of(originalFilename).getFileName().toString();
    return filename.length() <= 255 ? filename : filename.substring(filename.length() - 255);
  }
}
