package polight.server.domain.terms.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 보험사명·상품명을 비교할 수 있는 형태로 다듬는다.
 *
 * <p>필요한 이유는 양쪽 값의 출처가 다르기 때문이다. 한쪽({@code analysis_results})은 AI가 증권 이미지에서 읽은 것이고, 다른
 * 쪽({@code policy_terms})은 운영자가 공시자료를 보고 입력한 것이다. 같은 회사를 두고도 이렇게 갈린다.
 *
 * <pre>
 *   삼성화재해상보험(주)  /  삼성화재해상보험 주식회사  /  삼성화재해상보험㈜
 *   무배당 다이렉트 해외여행보험  /  무배당다이렉트해외여행보험
 * </pre>
 *
 * <p>글자 그대로 비교하면 이 중 어느 것도 서로 맞지 않아 매칭이 거의 항상 실패한다. 그러면 약관이 등록되어 있는데도 "약관을 찾지 못했습니다"가 뜬다.
 *
 * <p>다듬는 것은 <b>표기 차이뿐</b>이다. 오타 교정이나 유사도 비교는 하지 않는다. 그런 것까지 맞추기 시작하면 "해외여행보험"과
 * "해외여행보험(실속형)"이 같은 것으로 붙어, 다른 상품의 약관을 근거로 답하게 된다. 애매하면 맞추지 않고 비워 두는 편이 낫다.
 */
public final class InsuranceNameNormalizer {

  /**
   * 법인격 표기. 회사 이름의 일부가 아니라 형태 표시라 빼고 비교한다.
   *
   * <p>구두점을 지우기 <b>전에</b> 지워야 한다. 순서가 바뀌면 "(주)"가 "주"로 남아 "삼성화재주"가 되고, "삼성화재"와도 "삼성화재주식회사"와도
   * 어긋난다.
   */
  private static final Pattern LEGAL_ENTITY = Pattern.compile("주식회사|\\(주\\)|㈜|\\(株\\)|株式会社");

  /** 한글·영문·숫자만 남긴다. 공백, 괄호, 하이픈, 가운뎃점 등 표기 흔들림의 대부분이 여기서 사라진다. */
  private static final Pattern NON_NAME_CHARS = Pattern.compile("[^0-9a-zA-Z가-힣ㄱ-ㅎㅏ-ㅣ]");

  private InsuranceNameNormalizer() {}

  /**
   * 비교용 형태로 다듬는다.
   *
   * @return 다듬은 문자열. 입력이 {@code null}이거나 다듬고 나서 아무것도 남지 않으면 {@code null}
   */
  public static String normalize(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }

    // NFKC 를 먼저 거는 이유: 전각 문자와 조합 문자를 표준형으로 모은다. "㈜"는 여기서 "(주)"가 되어
    // 아래 LEGAL_ENTITY 에 걸린다.
    String normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC);
    normalized = LEGAL_ENTITY.matcher(normalized).replaceAll("");
    normalized = NON_NAME_CHARS.matcher(normalized).replaceAll("");

    if (normalized.isEmpty()) {
      return null;
    }
    return normalized.toLowerCase(Locale.ROOT);
  }

  /** 다듬은 결과가 같은지. 어느 한쪽이라도 비어 있으면 같다고 보지 않는다. */
  public static boolean matches(String left, String right) {
    String normalizedLeft = normalize(left);
    String normalizedRight = normalize(right);
    return normalizedLeft != null && normalizedLeft.equals(normalizedRight);
  }
}
