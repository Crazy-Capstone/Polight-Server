package polight.server.domain.trip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import polight.server.domain.concern.entity.Concern;
import polight.server.domain.insurance.entity.DocumentKind;

/**
 * 여행 등록 요청.
 *
 * <p>사용자는 파일 선택 · 여행 정보 · 기간 · 걱정 선택을 화면에서 모두 마친 뒤 "분석 시작"을 누른다. 그 시점에 이 요청 하나로 전부 전달된다.
 *
 * @param concerns 걱정되는 상황. 없으면 빈 목록으로 저장된다. 허용되지 않는 코드가 오면 400이다
 * @param documentKind 함께 올리는 파일의 종류. 파일 없이 여행만 만들 때는 의미가 없다. 미지정 시 {@code TERMS}
 */
public record TripCreateRequest(
    @NotBlank(message = "여행 이름은 필수입니다.")
        @Size(max = 100, message = "여행 이름은 100자 이하여야 합니다.")
        String name,
    @NotNull(message = "여행 시작일은 필수입니다.") LocalDate startDate,
    @NotNull(message = "여행 종료일은 필수입니다.") LocalDate endDate,
    List<Concern> concerns,
    DocumentKind documentKind) {

  public List<Concern> concernsOrEmpty() {
    return concerns == null ? List.of() : concerns;
  }

  public DocumentKind documentKindOrDefault() {
    return documentKind == null ? DocumentKind.TERMS : documentKind;
  }
}
