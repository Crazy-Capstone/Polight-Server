package polight.server.domain.trip.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import polight.server.domain.insurance.dto.PolicyDocumentResponse;
import polight.server.domain.insurance.service.PolicyDocumentService;
import polight.server.domain.trip.dto.TripCreateRequest;
import polight.server.domain.trip.dto.TripWithDocumentResponse;
import polight.server.domain.trip.entity.Trip;
import polight.server.domain.trip.mapper.TripMapper;

/**
 * 여행 생성과 약관 업로드를 한 트랜잭션으로 묶는다.
 *
 * <p>사용자는 한 화면에서 약관을 올리고 여행 정보를 입력한 뒤 한 번에 전송한다. 두 작업을 별도 요청으로 나누면 문서 업로드가 실패했을 때 여행만 남는다.
 *
 * <p>{@link PolicyDocumentService}가 이미 {@link TripService}를 의존하므로 TripService에서 문서 업로드를 호출하면 순환 의존이
 * 된다. 두 서비스를 함께 쓰는 조합 책임만 이 클래스가 맡는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripRegistrationService {

  private final TripService tripService;
  private final PolicyDocumentService policyDocumentService;
  private final TripMapper tripMapper;

  /**
   * 여행을 만들고 그 여행에 약관 문서를 저장한다.
   *
   * <p>문서 저장이 실패하면 여행 생성도 함께 롤백된다. 단, 저장소(S3)에 이미 올라간 객체는 롤백 대상이 아니므로 버킷에 남는다. 고아 객체는 라이프사이클 규칙으로
   * 정리하는 것을 전제한다.
   */
  @Transactional
  public TripWithDocumentResponse createTripWithDocument(
      UUID userId, TripCreateRequest tripRequest, MultipartFile file) {
    Trip trip = tripService.createTripEntity(userId, tripRequest);
    PolicyDocumentResponse document =
        policyDocumentService.uploadDocumentTo(trip, file, tripRequest.documentKindOrDefault());

    return new TripWithDocumentResponse(tripMapper.toResponse(trip), document);
  }
}
