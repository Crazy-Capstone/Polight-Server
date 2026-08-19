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
import polight.server.domain.analysis.service.CertificateAnalysisStarter;
import polight.server.domain.insurance.dto.PolicyDocumentResponse;
import polight.server.domain.insurance.entity.DocumentKind;
import polight.server.domain.insurance.service.PolicyDocumentService;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/trips/{tripId}/documents")
@Tag(name = "Policy Document", description = "여행별 보험 문서(증권·약관) API")
public class PolicyDocumentController {

  private final PolicyDocumentService policyDocumentService;
  private final CertificateAnalysisStarter certificateAnalysisStarter;

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "여행에 보험 문서 추가 업로드",
      description =
          """
          이미 만들어진 여행에 문서를 추가로 올립니다. 증권은 여행 생성 API(`POST /api/v1/trips`)에서 함께 올라오므로,
          이 API는 주로 **증권 분석 결과에 필요한 약관을 DB에서 찾지 못해 사용자에게 받아올 때** 씁니다.

          multipart 파트 구성:
          - `file` : 업로드할 PDF 파일
          - `documentKind` : `CERTIFICATE`(증권) 또는 `TERMS`(약관). 생략하면 `CERTIFICATE` 입니다.

          `CERTIFICATE` 를 올리면 업로드 즉시 분석이 시작됩니다. `TERMS` 는 시작되지 않으므로
          `POST .../documents/{documentId}/analysis` 로 분석을 시작해야 합니다.
          """)
  public ResponseEntity<PolicyDocumentResponse> uploadDocument(
      @AuthenticationPrincipal UUID userId,
      @PathVariable UUID tripId,
      @RequestPart("file") MultipartFile file,
      @RequestPart(name = "documentKind", required = false) String documentKind) {
    PolicyDocumentResponse response =
        policyDocumentService.uploadDocument(userId, tripId, file, parseDocumentKind(documentKind));
    // 증권은 업로드가 곧 분석 요청이다. 시작된 분석은 GET .../analysis 로 조회한다.
    certificateAnalysisStarter.startIfCertificate(userId, tripId, response);

    return ResponseEntity.created(
            URI.create("/api/v1/trips/" + tripId + "/documents/" + response.id()))
        .body(response);
  }

  /** 미지정이면 증권으로 본다. 약관을 올릴 때는 TERMS 를 명시해야 한다. */
  private DocumentKind parseDocumentKind(String documentKind) {
    if (documentKind == null || documentKind.isBlank()) {
      return DocumentKind.CERTIFICATE;
    }
    try {
      return DocumentKind.valueOf(documentKind.strip());
    } catch (IllegalArgumentException exception) {
      throw new BaseException(ErrorCode.INVALID_INPUT, exception);
    }
  }

  @GetMapping
  @Operation(summary = "여행의 보험 문서 목록")
  public List<PolicyDocumentResponse> getDocuments(
      @AuthenticationPrincipal UUID userId, @PathVariable UUID tripId) {
    return policyDocumentService.getDocuments(userId, tripId);
  }
}
