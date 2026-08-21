package polight.server.domain.terms.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.analysis.entity.AnalysisResult;
import polight.server.domain.analysis.entity.CoverageItem;
import polight.server.domain.terms.entity.PolicyTerms;
import polight.server.domain.terms.entity.PolicyTermsCoverage;
import polight.server.domain.terms.repository.PolicyTermsCoverageRepository;
import polight.server.domain.terms.service.CoverageTermsLinkSummary.UnlinkedCoverage;

/**
 * 사용자가 가입한 담보를 약관의 보장 규칙에 붙인다.
 *
 * <p>{@code PolicyTermsMatchingService}가 "이 증권은 어느 약관인가"를 정했다면, 여기는 그 한 단계 아래다 -- "이 담보는 그 약관의
 * 어느 조항인가".
 *
 * <p>이 연결이 서야 보장 상세가 두 영역을 합쳐 보여줄 수 있다. 가입금액은 담보에서, 면책·청구서류·세부한도는 규칙에서 온다. 붙지 않으면 가입 정보만
 * 나가고 약관 쪽 필드는 빈 목록이다 -- 지금과 같은 응답이다.
 *
 * <h2>이름을 맞추는 방법</h2>
 *
 * <p>증권과 약관은 같은 보장을 다르게 적는다. 증권은 보장내용 표에 적용 범위와 가입금액을 함께 인쇄한다.
 *
 * <pre>
 *   약관 규칙   상해의료비
 *   증권 담보   해외여행중 상해의료비(3천만원)
 * </pre>
 *
 * <p>그래서 세 단계로 본다. 표기를 다듬어 완전히 같으면({@link CoverageTermsMatchStage#EXACT}) 붙이고, 담보명이 규칙명을
 * 통째로 품고 있으면({@link CoverageTermsMatchStage#QUALIFIED}) 붙인다. 이름이 아예 겹치지 않는 경우를 위해 마지막으로 표준
 * 분류({@link CoverageTermsMatchStage#CATEGORY})를 본다 -- 증권 "해외의료실비보장"과 약관 "기본형 해외여행 실손의료비"는 같은
 * 보장인데 글자가 하나도 겹치지 않는다.
 *
 * <p><b>어느 단계든 후보가 둘 이상이면 붙이지 않는다.</b> 잘못 붙은 규칙은 붙지 않은 것보다 나쁘다 -- 화면에는 다른 담보의 면책 조항이 이 담보의
 * 것으로 표시되고, 사용자는 그것을 보고 받을 수 있는 보험금을 포기한다. 붙지 않으면 최소한 약관 정보가 없다는 사실이 드러난다.
 *
 * <p><b>이름이 모호해서 못 붙은 담보는 category 단계로 내려보내지 않는다.</b> 이름으로 가리지 못한 것을 그보다 거친 분류로 가를 수는 없다.
 * 그렇게 고른 하나는 근거가 아니라 동전 던지기이고, 그 결과로 다른 조항의 면책이 이 담보의 것으로 화면에 나간다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoverageTermsLinker {

  /**
   * 품고 있는지 볼 때 요구하는 최소 길이.
   *
   * <p>없으면 "상해", "사망" 같은 두 글자 규칙이 그 글자를 포함한 모든 담보에 걸린다. 후보가 여럿이면 어차피 붙이지 않지만, 그런 규칙 하나만 등록된
   * 약관에서는 무관한 담보에 붙어버린다.
   */
  private static final int MIN_CONTAINED_LENGTH = 4;

  private final PolicyTermsCoverageRepository policyTermsCoverageRepository;

  /**
   * 담보 각각에 약관 규칙을 붙인다.
   *
   * <p>연결된 약관이 없으면 아무것도 하지 않는다. 찾을 규칙의 범위 자체가 없기 때문이다.
   *
   * @param items 이 분석의 담보. 저장이 끝난 것이어야 한다
   */
  @Transactional
  public CoverageTermsLinkSummary link(AnalysisResult analysisResult, List<CoverageItem> items) {
    if (items == null || items.isEmpty()) {
      return CoverageTermsLinkSummary.empty();
    }

    PolicyTerms terms = analysisResult.getMatchedTerms();
    if (terms == null) {
      // 이전 시도에서 붙은 규칙이 남아 있을 수 있다. 약관 연결이 끊겼으면 규칙도 끊는다.
      items.forEach(item -> item.linkTermsCoverage(null));
      log.info(
          "약관이 연결되지 않아 담보 규칙 연결을 건너뜁니다: analysisResultId={}, 담보 {}건",
          analysisResult.getId(),
          items.size());
      return notLinked(items);
    }

    List<PolicyTermsCoverage> rules =
        policyTermsCoverageRepository.findByTermsIdOrderBySortOrderAsc(terms.getId());

    if (rules.isEmpty()) {
      items.forEach(item -> item.linkTermsCoverage(null));
      // 약관은 찾았는데 규칙이 없다 -- 아직 적재되지 않은 약관이다. 운영에서 이 로그가 보이면
      // 그 약관의 보장 규칙을 넣어야 한다는 뜻이다.
      log.info(
          "약관에 보장 규칙이 적재되어 있지 않습니다: termsId={}, 보험사={}, 상품={}",
          terms.getId(),
          terms.getInsurerName(),
          terms.getProductName());
      return notLinked(items);
    }

    warnOnNonStandardCategories(terms, items, rules);

    int exact = 0;
    int qualified = 0;
    int category = 0;
    List<UnlinkedCoverage> unlinked = new ArrayList<>();

    for (CoverageItem item : items) {
      LinkOutcome outcome = linkOne(item, rules);
      switch (outcome.stage()) {
        case EXACT -> exact++;
        case QUALIFIED -> qualified++;
        case CATEGORY -> category++;
        case NONE -> unlinked.add(new UnlinkedCoverage(item.getTitle(), outcome.reason()));
      }
    }

    CoverageTermsLinkSummary summary =
        new CoverageTermsLinkSummary(
            items.size(), exact, qualified, category, List.copyOf(unlinked));
    logSummary(analysisResult.getId(), terms, summary);
    return summary;
  }

  private LinkOutcome linkOne(CoverageItem item, List<PolicyTermsCoverage> rules) {
    String itemTitle = InsuranceNameNormalizer.normalize(item.getTitle());
    if (itemTitle == null) {
      return unlink(item, CoverageTermsUnlinkReason.NOT_FOUND);
    }

    List<PolicyTermsCoverage> exact =
        rules.stream().filter(rule -> itemTitle.equals(normalizedTitleOf(rule))).toList();
    if (exact.size() == 1) {
      item.linkTermsCoverage(exact.get(0));
      return LinkOutcome.linked(CoverageTermsMatchStage.EXACT);
    }
    if (exact.size() > 1) {
      // 같은 이름의 규칙이 여럿이다. 어느 조항을 말하는지 가릴 수 없으므로 붙이지 않고,
      // category 단계로도 내려보내지 않는다.
      return unlink(item, CoverageTermsUnlinkReason.AMBIGUOUS_TITLE);
    }

    List<PolicyTermsCoverage> qualified =
        rules.stream().filter(rule -> qualifies(itemTitle, normalizedTitleOf(rule))).toList();
    if (qualified.size() == 1) {
      item.linkTermsCoverage(qualified.get(0));
      return LinkOutcome.linked(CoverageTermsMatchStage.QUALIFIED);
    }
    if (qualified.size() > 1) {
      // 담보명이 여러 규칙명을 품은 경우다(예: "휴대품손해 및 배상책임"). 어느 쪽 조항을 붙여도
      // 나머지 절반이 빠지므로 가릴 수 없는 것으로 본다.
      return unlink(item, CoverageTermsUnlinkReason.AMBIGUOUS_TITLE);
    }

    return linkByCategory(item, rules);
  }

  /**
   * 마지막 단계. 표준 분류가 같은 규칙이 딱 하나일 때만 붙인다.
   *
   * <p>모르는 어휘가 와도 비교에서 빼지 않는다. 양쪽이 같은 값을 쓰고 있다면 그 연결은 맞고, 어휘가 어긋난 사실은 따로 로그로 드러난다. 여기서
   * 걸러버리면 어휘가 하나 늘어난 날 그 담보들의 연결이 통째로 사라진다.
   */
  private LinkOutcome linkByCategory(CoverageItem item, List<PolicyTermsCoverage> rules) {
    String itemCategory = StandardCoverageCategories.normalize(item.getCategory());
    if (itemCategory == null) {
      // 약관 저장소가 생기기 전에 분석된 담보는 category 가 비어 있다. 이름으로 못 붙었으면 거기까지다.
      return unlink(item, CoverageTermsUnlinkReason.NOT_FOUND);
    }

    List<PolicyTermsCoverage> candidates =
        rules.stream()
            .filter(
                rule ->
                    itemCategory.equals(StandardCoverageCategories.normalize(rule.getCategory())))
            .toList();

    if (candidates.size() == 1) {
      item.linkTermsCoverage(candidates.get(0));
      return LinkOutcome.linked(CoverageTermsMatchStage.CATEGORY);
    }
    if (candidates.size() > 1) {
      return unlink(item, CoverageTermsUnlinkReason.AMBIGUOUS_CATEGORY);
    }
    return unlink(item, CoverageTermsUnlinkReason.NOT_FOUND);
  }

  /**
   * 증권 담보명이 규칙명을 통째로 품고 있는지.
   *
   * <p>방향이 한쪽뿐인 것이 중요하다. 담보명이 규칙명보다 길 때만 붙인다 -- 증권이 "해외여행중", "(3천만원)" 같은 수식을 덧붙인 경우다.
   *
   * <p>반대는 붙이지 않는다. 규칙명이 더 길다는 것은 약관이 더 좁은 보장을 말한다는 뜻이고(예: 담보 "휴대품손해" ← 규칙 "휴대품손해(도난에 한함)"),
   * 그것을 붙이면 사용자가 산 것보다 좁은 조건을 씌우게 된다. 실제로는 보장되는데 화면에는 "도난만 보장"으로 나간다.
   */
  private boolean qualifies(String itemTitle, String ruleTitle) {
    if (ruleTitle == null || ruleTitle.length() < MIN_CONTAINED_LENGTH) {
      return false;
    }
    return itemTitle.length() > ruleTitle.length() && itemTitle.contains(ruleTitle);
  }

  /**
   * 규칙명을 담보명과 같은 방식으로 다듬는다.
   *
   * <p>보험사명에 쓰는 정규화를 그대로 쓴다. 하는 일이 공백·괄호·대소문자 차이를 걷어내는 것뿐이라 담보명에도 그대로 맞고, 규칙을 두 벌 두면 한쪽만
   * 고쳐져 어긋난다.
   */
  private String normalizedTitleOf(PolicyTermsCoverage rule) {
    return InsuranceNameNormalizer.normalize(rule.getTitle());
  }

  private LinkOutcome unlink(CoverageItem item, CoverageTermsUnlinkReason reason) {
    item.linkTermsCoverage(null);
    return LinkOutcome.unlinked(reason);
  }

  /** 담보 전부를 끊은 결과. 사유는 하나뿐이고 로그가 앞줄에 이미 남았다. */
  private CoverageTermsLinkSummary notLinked(List<CoverageItem> items) {
    List<UnlinkedCoverage> unlinked =
        items.stream()
            .map(item -> new UnlinkedCoverage(item.getTitle(), CoverageTermsUnlinkReason.NOT_FOUND))
            .toList();
    return new CoverageTermsLinkSummary(items.size(), 0, 0, 0, unlinked);
  }

  /**
   * 합의한 어휘 밖의 category 를 로그로 드러낸다. 연결을 막지는 않는다.
   *
   * <p>어휘는 계약이지 런타임 제약이 아니다. 값을 거부하면 어휘가 하나 늘어난 날 연결이 통째로 끊긴다. 대신 어긋난 사실이 사람에게 보여야 하므로
   * 여기서 한 번 모아 남긴다 -- 담보마다 남기면 로그가 같은 값으로 도배된다.
   */
  private void warnOnNonStandardCategories(
      PolicyTerms terms, List<CoverageItem> items, List<PolicyTermsCoverage> rules) {
    Set<String> unknown = new LinkedHashSet<>();
    items.forEach(item -> collectIfNonStandard(item.getCategory(), unknown));
    rules.forEach(rule -> collectIfNonStandard(rule.getCategory(), unknown));

    if (!unknown.isEmpty()) {
      log.warn("합의한 어휘 밖의 category 입니다(연결은 그대로 진행): termsId={}, {}", terms.getId(), unknown);
    }
  }

  private void collectIfNonStandard(String category, Set<String> unknown) {
    String normalized = StandardCoverageCategories.normalize(category);
    if (normalized != null && !StandardCoverageCategories.isStandard(normalized)) {
      unknown.add(normalized);
    }
  }

  private void logSummary(UUID analysisResultId, PolicyTerms terms, CoverageTermsLinkSummary s) {
    log.info(
        "담보-약관규칙 연결: analysisResultId={}, termsId={}, {}/{}건 연결(정확 {}, 수식 {}, 분류 {})",
        analysisResultId,
        terms.getId(),
        s.linked(),
        s.total(),
        s.exact(),
        s.qualified(),
        s.category());

    if (!s.unlinked().isEmpty()) {
      // 붙지 않은 이름은 약관 규칙 적재가 무엇을 빠뜨렸는지 알려주는 유일한 단서다. 사유를 함께
      // 남기는 이유는 고쳐야 할 곳이 다르기 때문이다 -- 후보 없음은 적재를 더 하면 되고,
      // 가릴 수 없음은 이미 적재된 데이터가 어긋났다는 뜻이다.
      log.info("연결되지 않은 담보: termsId={}, {}", terms.getId(), s.unlinked());
    }
  }

  /** @param reason 붙인 경우에는 {@code null}이다 */
  private record LinkOutcome(CoverageTermsMatchStage stage, CoverageTermsUnlinkReason reason) {

    static LinkOutcome linked(CoverageTermsMatchStage stage) {
      return new LinkOutcome(stage, null);
    }

    static LinkOutcome unlinked(CoverageTermsUnlinkReason reason) {
      return new LinkOutcome(CoverageTermsMatchStage.NONE, reason);
    }
  }
}
