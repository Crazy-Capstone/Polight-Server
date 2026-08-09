package polight.server.domain.insurance.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import polight.server.domain.insurance.dto.PolicyDocumentResponse;
import polight.server.domain.insurance.entity.PolicyDocument;
import polight.server.domain.insurance.mapper.PolicyDocumentMapper;
import polight.server.domain.insurance.repository.PolicyDocumentRepository;
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

  @Value("${storage.policy-documents-directory:uploads/policy-documents}")
  private String storageDirectory;

  @Transactional
  public PolicyDocumentResponse uploadDocument(UUID userId, UUID tripId, MultipartFile file) {
    if (file.isEmpty()) {
      throw new BaseException(ErrorCode.EMPTY_POLICY_DOCUMENT_FILE);
    }

    Trip trip = tripService.getOwnedTrip(userId, tripId);
    String originalFilename = normalizeFilename(file.getOriginalFilename());
    String storedFilePath = storeFile(file);

    PolicyDocument document =
        policyDocumentMapper.toEntity(trip, file, originalFilename, storedFilePath);
    return policyDocumentMapper.toResponse(policyDocumentRepository.save(document));
  }

  public List<PolicyDocumentResponse> getDocuments(UUID userId, UUID tripId) {
    tripService.getOwnedTrip(userId, tripId);

    return policyDocumentMapper.toResponses(
        policyDocumentRepository.findAllByTripIdAndUserIdOrderByUploadedAtDesc(tripId, userId));
  }

  /** 파일을 저장하고 저장 위치를 돌려준다. */
  private String storeFile(MultipartFile file) {
    Path target =
        Path.of(storageDirectory).toAbsolutePath().normalize().resolve(UUID.randomUUID().toString());

    try {
      Files.createDirectories(target.getParent());
      Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException exception) {
      throw new BaseException(ErrorCode.POLICY_DOCUMENT_STORAGE_FAILED, exception);
    }
    return target.toString();
  }

  private String normalizeFilename(String originalFilename) {
    if (originalFilename == null || originalFilename.isBlank()) {
      return "policy-document";
    }
    String filename = Path.of(originalFilename).getFileName().toString();
    return filename.length() <= 255 ? filename : filename.substring(filename.length() - 255);
  }
}
