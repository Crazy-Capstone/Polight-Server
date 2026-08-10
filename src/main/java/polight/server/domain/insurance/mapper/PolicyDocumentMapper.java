package polight.server.domain.insurance.mapper;

import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import polight.server.domain.insurance.dto.PolicyDocumentResponse;
import polight.server.domain.insurance.entity.PolicyDocument;
import polight.server.domain.trip.entity.Trip;

/** PolicyDocument 엔티티와 DTO 사이의 변환을 담당한다. */
@Component
public class PolicyDocumentMapper {

  /**
   * 업로드된 파일과 저장 결과로부터 문서 엔티티를 만든다.
   *
   * @param storedFilePath 저장소가 돌려준 위치. 로컬 경로일 수도, S3 키일 수도 있다.
   */
  public PolicyDocument toEntity(
      Trip trip, MultipartFile file, String originalFilename, String storedFilePath) {
    return PolicyDocument.builder()
        .user(trip.getUser())
        .trip(trip)
        .originalFilename(originalFilename)
        .storedFilePath(storedFilePath)
        .contentType(file.getContentType())
        .fileSize(file.getSize())
        .build();
  }

  public PolicyDocumentResponse toResponse(PolicyDocument document) {
    return new PolicyDocumentResponse(
        document.getId(),
        document.getTrip().getId(),
        document.getOriginalFilename(),
        document.getContentType(),
        document.getFileSize(),
        document.getParseStatus(),
        document.getUploadedAt());
  }

  public List<PolicyDocumentResponse> toResponses(List<PolicyDocument> documents) {
    return documents.stream().map(this::toResponse).toList();
  }
}
