package polight.server.domain.concern.entity;

import java.util.List;

/**
 * 사용자가 여행 등록 시 고르는 "걱정되는 상황".
 *
 * <p>항목이 11개로 고정돼 있어 테이블 대신 enum으로 둔다. 선언 순서가 곧 화면 노출 순서이므로 정렬 컬럼이 필요 없다. 화면의 대분류(건강·의료 / 분실·도난 /
 * 항공·기타)는 프론트에서만 묶는 것이라 여기에 두지 않는다.
 *
 * <p><b>상수를 지우면 안 된다.</b> 이미 저장된 여행의 {@code trips.concerns}를 읽을 때 값을 해석할 수 없어 조회 자체가 실패한다. 항목을 내려야
 * 하면 지우는 대신 노출 목록에서만 빼야 한다.
 *
 * <p>{@code coverageKeywords}는 이 걱정이 어떤 담보에 해당하는지 찾는 데 쓴다. AI가 증권에서 읽어 채우는 {@code
 * coverage_items.title}/{@code category}가 자유 문자열이라 코드로 직접 이어붙일 수 없어, 부분 문자열로 매칭한다. 실제 증권 표현을 보면서
 * 늘려가야 하는 값이다.
 */
public enum Concern {
  INJURY_OR_ILLNESS("다치거나 아플까 봐", "상해", "의료비", "치료", "입원"),
  ILLNESS("질병에 걸릴까 봐", "질병", "의료비"),
  FOOD_POISONING("식중독에 걸릴까 봐", "식중독", "질병"),
  INFECTIOUS_DISEASE("특정 전염병 걸릴까 봐", "전염병", "감염"),
  BAGGAGE_DAMAGE("짐이 분실·파손될까 봐", "휴대품", "수하물", "파손"),
  PASSPORT_LOSS("여권을 잃어버릴까 봐", "여권", "재발급"),
  PICKPOCKET("소매치기 당할까 봐", "도난", "휴대품"),
  FLIGHT_DELAY("비행기 지연·결항될까 봐", "지연", "결항", "항공"),
  TRIP_INTERRUPTION("여행을 중단해야 할까 봐", "중단", "취소"),
  LIABILITY("남에게 피해줄까 봐", "배상책임", "배상"),
  AFTEREFFECTS("큰 사고로 후유증 남을까 봐", "후유장해", "장해");

  private final String label;
  private final List<String> coverageKeywords;

  Concern(String label, String... coverageKeywords) {
    this.label = label;
    this.coverageKeywords = List.of(coverageKeywords);
  }

  public String getLabel() {
    return label;
  }

  public List<String> getCoverageKeywords() {
    return coverageKeywords;
  }
}
