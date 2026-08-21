package polight.server.domain.terms.service;

import java.util.Locale;
import java.util.Set;

/**
 * AI와 합의한 표준 담보 분류 어휘.
 *
 * <p>증권 담보({@code coverage_items.category})와 약관 규칙({@code policy_terms_coverages.category}) 양쪽에
 * 같은 값이 들어온다는 것이 계약이고, 그 값을 키로 마지막 단계 연결이 선다.
 *
 * <h2>왜 enum이 아니라 문자열 + Set인가</h2>
 *
 * <p><b>어휘는 늘어난다.</b> 실제로 7종으로 시작해 {@code dental_emergency}가 붙어 8종이 됐다. enum으로 받아 검증하면 9번째가
 * 추가되는 순간 콜백이 깨지거나 값이 통째로 비고, 그러면 <b>그 담보들의 연결이 전부 끊긴다</b> -- 어휘가 하나 늘었을 뿐인데 화면에서 면책·서류가
 * 사라진다.
 *
 * <p>그래서 이 목록은 <b>차단용이 아니라 감지용</b>이다. 모르는 값이 와도 저장하고 비교에 그대로 쓰되(양쪽이 같은 값이면 붙는 것이 맞다), 로그로
 * 드러내 계약이 어긋난 사실을 사람이 알게 한다.
 */
public final class StandardCoverageCategories {

  private static final Set<String> VALUES =
      Set.of(
          "medical_expense",
          "dental_emergency",
          "flight_delay",
          "baggage",
          "emergency_transport",
          "liability",
          "trip_cancellation",
          "death_disability");

  private StandardCoverageCategories() {}

  /**
   * 비교에 쓸 형태로 다듬는다. 값이 없거나 공백뿐이면 {@code null}이다.
   *
   * <p>담보명에 쓰는 {@code InsuranceNameNormalizer}를 쓰지 않는다. 그쪽은 한국어 상품명의 괄호·공백을 걷어내는 규칙이고, 여기 오는
   * 것은 {@code medical_expense} 같은 코드다. 코드 정규화를 상품명 규칙에 묶어 두면 그쪽을 고칠 때 이쪽이 조용히 따라 바뀐다.
   */
  public static String normalize(String category) {
    if (category == null) {
      return null;
    }
    String trimmed = category.strip().toLowerCase(Locale.ROOT);
    return trimmed.isEmpty() ? null : trimmed;
  }

  /** 합의한 어휘 안의 값인지. 아니어도 연결은 막지 않는다 -- 로그로만 드러낸다. */
  public static boolean isStandard(String normalizedCategory) {
    return normalizedCategory != null && VALUES.contains(normalizedCategory);
  }
}
