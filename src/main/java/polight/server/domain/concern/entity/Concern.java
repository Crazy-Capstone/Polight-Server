package polight.server.domain.concern.entity;

import java.util.List;

/**
 * 사용자가 여행 등록 시 고르는 "걱정되는 상황".
 *
 * <p>항목이 11개로 고정돼 있어 테이블 대신 enum으로 둔다. 선언 순서가 곧 화면 노출 순서이므로 정렬 값이 필요 없다. 화면의 대분류(건강·의료 / 분실·도난 /
 * 항공·기타)와 사용자에게 보이는 문구는 프론트가 들고 있다. 문구를 양쪽에 두면 서로 어긋날 수 있어 서버는 코드만 다룬다.
 *
 * <p><b>상수를 지우면 안 된다.</b> 이미 저장된 여행의 {@code trips.concerns}를 읽을 때 값을 해석할 수 없어 조회 자체가 실패한다. 항목을 내려야
 * 하면 지우는 대신 프론트 목록에서 빼야 한다.
 *
 * <p>{@code coverageKeywords}는 이 걱정이 어떤 담보에 해당하는지 찾는 데 쓴다. AI가 증권에서 읽어 채우는 {@code
 * coverage_items.title}/{@code category}가 자유 문자열이라 코드로 직접 이어붙일 수 없어 부분 문자열로 매칭한다. 실제 증권 표현을 보면서
 * 늘려가야 하는 값이다.
 */
public enum Concern {
  // 다치거나 아플까 봐
  INJURY_OR_ILLNESS("상해", "의료비", "치료", "입원"),
  // 질병에 걸릴까 봐
  ILLNESS("질병", "의료비"),
  // 식중독에 걸릴까 봐
  FOOD_POISONING("식중독", "질병"),
  // 특정 전염병 걸릴까 봐
  INFECTIOUS_DISEASE("전염병", "감염"),
  // 짐이 분실·파손될까 봐
  BAGGAGE_DAMAGE("휴대품", "수하물", "파손"),
  // 여권을 잃어버릴까 봐
  PASSPORT_LOSS("여권", "재발급"),
  // 소매치기 당할까 봐
  PICKPOCKET("도난", "휴대품"),
  // 비행기 지연·결항될까 봐
  FLIGHT_DELAY("지연", "결항", "항공"),
  // 여행을 중단해야 할까 봐
  TRIP_INTERRUPTION("중단", "취소"),
  // 남에게 피해줄까 봐
  LIABILITY("배상책임", "배상"),
  // 큰 사고로 후유증 남을까 봐
  AFTEREFFECTS("후유장해", "장해");

  private final List<String> coverageKeywords;

  Concern(String... coverageKeywords) {
    this.coverageKeywords = List.of(coverageKeywords);
  }

  public List<String> getCoverageKeywords() {
    return coverageKeywords;
  }
}
