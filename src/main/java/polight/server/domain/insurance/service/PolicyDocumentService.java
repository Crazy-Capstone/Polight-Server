package polight.server.domain.insurance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import polight.server.domain.insurance.repository.PolicyDocumentRepository;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import polight.server.domain.analysis.entity.AnalysisResult;
import polight.server.domain.analysis.repository.AnalysisResultRepository;
import polight.server.domain.analysis.repository.CoverageItemRepository;
import polight.server.domain.insurance.analysis.PolicyAnalysisRequestPort;
import polight.server.domain.insurance.dto.PolicyDocumentDto;
import polight.server.domain.insurance.entity.DocumentParseStatus;
import polight.server.domain.insurance.entity.PolicyDocument;
import polight.server.domain.insurance.storage.PolicyDocumentStorage;
import polight.server.domain.policy.entity.Policy;
import polight.server.domain.policy.service.PolicyService;
import polight.server.domain.trip.entity.Trip;
import polight.server.domain.trip.repository.TripRepository;
import polight.server.domain.user.service.UserService;
import polight.server.global.exception.BusinessException;
import polight.server.global.exception.ErrorCode;

@Service
@RequiredArgsConstructor
public class PolicyDocumentService {

  private final PolicyDocumentRepository policyDocumentRepository;
  private final AnalysisResultRepository analysisResultRepository;
  private final CoverageItemRepository coverageItemRepository;
  private final TripRepository tripRepository;
  private final PolicyService policyService;
  private final UserService userService;
  private final PolicyDocumentStorage storage;
  private final PolicyAnalysisRequestPort analysisRequestPort;

  @Transactional
  public PolicyDocumentDto.Response upload(UUID userId, MultipartFile file, UUID tripId, UUID policyId) {
    Trip trip = tripId == null ? null : tripRepository.findByIdAndUserId(tripId, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.TRIP_NOT_FOUND));
    Policy policy = policyId == null ? null : policyService.requireOwnedPolicy(userId, policyId);
    if (policy != null && trip == null) trip = policy.getTrip();
    if (policy != null && trip != null && !policy.getTrip().getId().equals(trip.getId())) {
      throw new BusinessException(ErrorCode.VALIDATION_ERROR, "보험과 여행 정보가 일치하지 않습니다.");
    }
    String path = storage.store(file);
    PolicyDocument document = policyDocumentRepository.save(PolicyDocument.builder()
        .user(userService.findUser(userId)).trip(trip).policy(policy)
        .originalFilename(file.getOriginalFilename()).storedFilePath(path)
        .contentType(file.getContentType()).fileSize(file.getSize()).build());
    return toResponse(document);
  }

  @Transactional(readOnly = true)
  public PolicyDocumentDto.Response get(UUID userId, UUID documentId) {
    return toResponse(requireOwnedDocument(userId, documentId));
  }

  @Transactional
  public PolicyDocumentDto.AnalysisRequest requestAnalysis(UUID userId, UUID documentId) {
    PolicyDocument document = requireOwnedDocument(userId, documentId);
    if (document.getParseStatus() == DocumentParseStatus.PROCESSING
        || document.getParseStatus() == DocumentParseStatus.COMPLETED) {
      throw new BusinessException(ErrorCode.ANALYSIS_STATE_CONFLICT);
    }
    document.markProcessing();
    AnalysisResult analysis = analysisResultRepository.save(AnalysisResult.builder()
        .document(document).policy(document.getPolicy()).parseStatus(DocumentParseStatus.PROCESSING).build());
    analysisRequestPort.request(analysis.getId(), document.getId());
    return new PolicyDocumentDto.AnalysisRequest(document.getId(), analysis.getId(), document.getParseStatus());
  }

  @Transactional(readOnly = true)
  public PolicyDocumentDto.AnalysisResponse getAnalysis(UUID userId, UUID documentId) {
    PolicyDocument document = requireOwnedDocument(userId, documentId);
    AnalysisResult analysis = analysisResultRepository.findFirstByDocumentIdOrderByCreatedAtDesc(documentId)
        .orElse(null);
    Policy policy = document.getPolicy();
    Long count = policy == null ? null : coverageItemRepository.countByPolicyId(policy.getId());
    return new PolicyDocumentDto.AnalysisResponse(document.getId(), analysis == null ? null : analysis.getId(),
        analysis == null ? document.getParseStatus() : analysis.getParseStatus(),
        policy == null ? null : policy.getId(), count, policy == null ? null : policy.getCoverageScore(),
        analysis == null ? null : analysis.getErrorMessage());
  }

  private PolicyDocument requireOwnedDocument(UUID userId, UUID documentId) {
    return policyDocumentRepository.findByIdAndUserId(documentId, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
  }

  private PolicyDocumentDto.Response toResponse(PolicyDocument item) {
    return new PolicyDocumentDto.Response(item.getId(), item.getOriginalFilename(), item.getContentType(),
        item.getFileSize(), item.getParseStatus(), item.getTrip() == null ? null : item.getTrip().getId(),
        item.getPolicy() == null ? null : item.getPolicy().getId(), item.getUploadedAt());
  }
}
