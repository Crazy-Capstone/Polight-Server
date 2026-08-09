package polight.server.domain.insurance.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import polight.server.domain.insurance.dto.PolicyDocumentResponse;
import polight.server.domain.insurance.service.PolicyDocumentService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/trips/{tripId}/documents")
@Tag(name = "Policy Document", description = "여행별 보험 약관 API")
public class PolicyDocumentController {

  private final PolicyDocumentService policyDocumentService;

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "여행에 약관 업로드", description = "인증 사용자가 소유한 여행 세션에 약관 파일을 연결합니다.")
  public ResponseEntity<PolicyDocumentResponse> uploadDocument(
      @AuthenticationPrincipal UUID userId,
      @PathVariable UUID tripId,
      @RequestPart("file") MultipartFile file) {
    PolicyDocumentResponse response = policyDocumentService.uploadDocument(userId, tripId, file);
    return ResponseEntity.created(
            URI.create("/api/v1/trips/" + tripId + "/documents/" + response.id()))
        .body(response);
  }

  @GetMapping
  @Operation(summary = "여행의 약관 목록")
  public List<PolicyDocumentResponse> getDocuments(
      @AuthenticationPrincipal UUID userId, @PathVariable UUID tripId) {
    return policyDocumentService.getDocuments(userId, tripId);
  }
}
