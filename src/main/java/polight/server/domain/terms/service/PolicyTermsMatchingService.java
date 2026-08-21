package polight.server.domain.terms.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import polight.server.domain.analysis.entity.AnalysisResult;
import polight.server.domain.insurance.entity.DocumentKind;
import polight.server.domain.insurance.entity.PolicyDocument;
import polight.server.domain.terms.entity.PolicyTerms;
import polight.server.domain.terms.repository.PolicyTermsRepository;
import polight.server.domain.trip.entity.Trip;

/**
 * 증권 분석 결과를 약관에 연결한다.
 *
 * <p>AI 서버는 증권에서 읽은 것만 콜백으로 보낸다 -- 요약, 담보 목록, 보험사명, 상품명. "그래서 어느 약관인가"는 보내지 않는다. 그 판단은 약관 저장소를
 * 가진 쪽만 할 수 있어 백엔드 몫이다.
 *
 * <p>이 연결이 서야 담보의 근거 조항을 인용하고 챗봇이 약관 본문을 검색할 수 있다. 연결이 없으면 사용자는 증권에 적힌 금액만 보게 된다.
 *
 * <h2>단계</h2>
 *
 * <ol>
 *   <li><b>EXACT</b> -- 보험사·상품명이 모두 맞는 약관이 하나
 *   <li><b>REVISION</b> -- 그런 약관이 여럿(개정판). 증권이 적용받았을 시점으로 하나를 고른다
 *   <li><b>INSURER</b> -- 상품명은 안 맞지만 그 보험사 약관이 딱 하나
 *   <li><b>NONE</b> -- 그 외 전부
 * </ol>
 *
 * <p>단계를 내려갈수록 근거가 약해지므로, <b>애매하면 연결하지 않는다</b>. 후보가 둘 이상 남아 가릴 수 없을 때 아무거나 고르지 않고 NONE 으로 둔다.
 * 잘못 연결된 약관은 연결이 없는 것보다 나쁘다 -- 화면에는 그럴듯한 조항이 인용되지만 사용자가 가입한 상품과 무관한 문장이고, 그것을 보고 청구를 포기한다.
 * 연결이 없으면 최소한 "약관을 찾지 못했다"는 사실이 드러나 약관 업로드를 요청할 수 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyTermsMatchingService {

  private final PolicyTermsRepository policyTermsRepository;

  /**
   * 약관을 찾아 분석에 연결한다.
   *
   * <p>찾지 못하면 연결을 {@code null}로 비운다. 재분석으로 보험사/상품명이 달라졌는데 이전 연결이 남아 있으면, 그 약관은 더 이상 이 증권의 것이
   * 아니다.
   */
  @Transactional
  public TermsMatch matchAndLink(AnalysisResult analysisResult) {
    TermsMatch match = match(analysisResult);
    analysisResult.linkTerms(match.terms());

    if (match.isMatched()) {
      log.info(
          "약관 연결: analysisResultId={}, termsId={}, 단계={}, 근거={}",
          analysisResult.getId(),
          match.terms().getId(),
          match.stage(),
          match.reason());
    } else {
      // 실패가 아니라 정상 갈래이므로 info 로 남긴다. 다만 어떤 이름으로 못 찾았는지는 남겨야
      // 어느 상품 약관을 등록해야 하는지 알 수 있다.
      log.info(
          "약관을 연결하지 않음: analysisResultId={}, 보험사={}, 상품={}, 근거={}",
          analysisResult.getId(),
          analysisResult.getInsurerName(),
          analysisResult.getProductName(),
          match.reason());
    }
    return match;
  }

  /** 연결하지 않고 판단만 한다. */
  @Transactional(readOnly = true)
  public TermsMatch match(AnalysisResult analysisResult) {
    PolicyDocument document = analysisResult.getDocument();

    // 약관 문서를 분석한 결과에는 약관을 붙이지 않는다. 그 분석의 산출물이 곧 약관 자체이고,
    // 여기서 자기 자신이나 남의 약관을 가리키게 하면 근거 연결이 뒤엉킨다.
    if (document == null || document.getDocumentKind() != DocumentKind.CERTIFICATE) {
      return TermsMatch.none("증권 분석이 아니라 약관을 찾지 않습니다.");
    }

    String insurerName = analysisResult.getInsurerName();
    String productName = analysisResult.getProductName();

    // 보험사명이 없으면 시작할 수 없다. 상품명만으로 찾으면 다른 보험사의 같은 이름 상품에 붙는다
    // ("해외여행보험"은 어느 보험사에나 있다).
    if (InsuranceNameNormalizer.normalize(insurerName) == null) {
      return TermsMatch.none("증권에서 보험사명을 읽지 못했습니다.");
    }

    UUID userId = document.getUser() == null ? null : document.getUser().getId();
    List<PolicyTerms> usable =
        policyTermsRepository.findUsableBy(userId).stream()
            // 쿼리가 이미 같은 범위로 좁히지만, 접근 판정의 정본은 엔티티에 둔다. 쿼리 조건과
            // 판정이 어긋나면 남의 UNVERIFIED 약관이 후보로 들어오므로 여기서 한 번 더 거른다.
            .filter(terms -> terms.isUsableBy(userId))
            .toList();

    List<PolicyTerms> insurerMatches =
        usable.stream()
            .filter(terms -> InsuranceNameNormalizer.matches(terms.getInsurerName(), insurerName))
            .toList();

    if (insurerMatches.isEmpty()) {
      return TermsMatch.none("'" + insurerName + "' 보험사로 등록된 약관이 없습니다.");
    }

    List<PolicyTerms> productMatches =
        preferVerified(
            insurerMatches.stream()
                .filter(
                    terms -> InsuranceNameNormalizer.matches(terms.getProductName(), productName))
                .toList());

    if (productMatches.size() == 1) {
      return TermsMatch.found(
          TermsMatchStage.EXACT, productMatches.get(0), "보험사·상품명이 일치하는 약관이 하나입니다.");
    }
    if (productMatches.size() > 1) {
      return selectRevision(productMatches, referenceDate(document));
    }

    return matchByInsurerOnly(preferVerified(insurerMatches), insurerName, productName);
  }

  /**
   * 개정판이 여럿일 때 증권이 적용받았을 하나를 고른다.
   *
   * <p>고르는 규칙은 "기준일에 이미 효력이 있던 것 중 가장 최근 것"이다. 2026-03 에 가입한 증권은 2026-01 개정판을 적용받지, 2026-07
   * 개정판을 적용받지 않는다.
   *
   * <p>{@code effectiveDate}가 없는 개정판은 고르지 않는다. 언제부터 유효했는지 모르면 적용 여부를 판단할 수 없고, 그 상태로 고르는 것은
   * 추측이다.
   */
  private TermsMatch selectRevision(List<PolicyTerms> revisions, LocalDate referenceDate) {
    List<PolicyTerms> dated =
        revisions.stream().filter(terms -> terms.getEffectiveDate() != null).toList();

    if (dated.isEmpty()) {
      return TermsMatch.none(
          "같은 상품 약관이 " + revisions.size() + "건인데 개정일(effectiveDate)이 없어 어느 것인지 가릴 수 없습니다.");
    }

    if (referenceDate == null) {
      // 기준일을 모르는 경우다. 지금 분석하는 증권은 최근에 가입한 것일 가능성이 높아 최신
      // 개정판을 고르되, 추측이라는 사실을 근거 문구에 남긴다.
      PolicyTerms latest = latestOf(dated);
      return TermsMatch.found(
          TermsMatchStage.REVISION, latest, "기준일을 알 수 없어 최신 개정판(" + latest.getEffectiveDate() + ")을 골랐습니다.");
    }

    List<PolicyTerms> applicable =
        dated.stream()
            .filter(terms -> !terms.getEffectiveDate().isAfter(referenceDate))
            .toList();

    if (applicable.isEmpty()) {
      return TermsMatch.none(
          "기준일(" + referenceDate + ") 시점에 유효했던 개정판이 등록되어 있지 않습니다. 등록된 것은 모두 그 이후 개정판입니다.");
    }

    PolicyTerms selected = latestOf(applicable);
    return TermsMatch.found(
        TermsMatchStage.REVISION,
        selected,
        "기준일 " + referenceDate + " 에 유효했던 개정판(" + selected.getEffectiveDate() + ")을 골랐습니다.");
  }

  /**
   * 상품명이 맞지 않을 때의 마지막 갈래.
   *
   * <p>그 보험사 약관이 하나뿐일 때만 연결한다. 둘 이상이면 상품명으로 가릴 수 없었던 것이므로 고르지 않는다.
   *
   * <p>하나뿐이어도 확실하지는 않다 -- 그 보험사의 다른 상품 약관이 아직 등록되지 않았을 뿐일 수 있다. 그래서 단계를 {@link
   * TermsMatchStage#INSURER}로 남겨 호출부가 신뢰도를 알 수 있게 한다.
   */
  private TermsMatch matchByInsurerOnly(
      List<PolicyTerms> insurerMatches, String insurerName, String productName) {
    if (insurerMatches.size() == 1) {
      PolicyTerms only = insurerMatches.get(0);
      return TermsMatch.found(
          TermsMatchStage.INSURER,
          only,
          "상품명('" + productName + "')은 맞지 않지만 '" + insurerName + "' 약관이 '" + only.getProductName() + "' 하나뿐입니다.");
    }

    return TermsMatch.none(
        "'"
            + insurerName
            + "' 약관이 "
            + insurerMatches.size()
            + "건 있으나 상품명('"
            + productName
            + "')이 어느 것과도 맞지 않습니다.");
  }

  /**
   * VERIFIED 가 하나라도 있으면 그것만 남긴다.
   *
   * <p>사용자가 올린 UNVERIFIED 약관과 운영자가 확인한 VERIFIED 약관이 같은 상품에 함께 있을 수 있다. 그대로 두면 "후보 2건"이 되어 개정판
   * 판정으로 넘어가는데, 둘은 개정판 관계가 아니라 같은 것의 사본이다.
   *
   * <p>확인된 쪽을 쓴다. 사용자가 올린 파일은 다른 상품이거나 일부만 담긴 것일 수 있다.
   */
  private List<PolicyTerms> preferVerified(List<PolicyTerms> candidates) {
    List<PolicyTerms> verified = candidates.stream().filter(PolicyTerms::isVerified).toList();
    return verified.isEmpty() ? candidates : verified;
  }

  private PolicyTerms latestOf(List<PolicyTerms> candidates) {
    return candidates.stream()
        .max(Comparator.comparing(PolicyTerms::getEffectiveDate))
        .orElseThrow();
  }

  /**
   * 어느 시점의 약관을 적용할지 판단하는 기준일. 여행 시작일을 쓴다.
   *
   * <p>원래 기준이 되어야 할 것은 증권의 보험 시작일이다. 그런데 그 값이 지금 어디에도 없다 -- {@code policies} 행을 만드는 코드가 없고,
   * 콜백에도 보험기간이 오지 않는다.
   *
   * <p>여행 시작일은 그 대용으로 쓸 수 있다. 여행자보험의 보험기간은 여행 기간을 덮도록 가입하므로 여행 시작일은 보험기간 안에 있고, 그 날 유효했던
   * 개정판이 곧 이 증권이 적용받는 개정판이다.
   *
   * <p>여행이 연결되지 않은 문서면 {@code null}이다. 그때는 {@link #selectRevision}이 최신 개정판을 고른다.
   */
  private LocalDate referenceDate(PolicyDocument document) {
    Trip trip = document.getTrip();
    return trip == null ? null : trip.getStartDate();
  }
}
