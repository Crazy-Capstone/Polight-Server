package polight.server.domain.terms.controller;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import polight.server.domain.terms.service.TermsBackfillMode;
import polight.server.domain.terms.service.TermsBackfillService;
import polight.server.domain.terms.service.TermsBackfillSummary;

/**
 * 약관 백필을 손으로 돌리는 창구.
 *
 * <p>스케줄러로 두지 않았다. 백필은 약관을 적재한 <b>뒤</b>에 한 번 돌리는 작업이고, 주기적으로 돌 이유가 없다. 자동으로 돌면 약관을 잘못 적재한
 * 직후에도 그대로 돌아 잘못된 연결이 조용히 퍼진다.
 *
 * <p>인증은 {@code InternalApiKeyFilter}가 {@code X-Internal-Api-Key} 헤더로 처리한다({@code /internal/**}).
 * 사용자 토큰으로는 부를 수 없다.
 *
 * <p>{@link Hidden}으로 공개 API 문서에서 제외한다. 프론트가 쓸 API가 아니다.
 */
@Hidden
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/terms")
public class InternalTermsBackfillController {

  private final TermsBackfillService termsBackfillService;

  /**
   * 약관 연결이 비어 있는 분석에 약관과 보장 규칙을 붙인다.
   *
   * <p>동기로 처리하고 결과를 돌려준다. 대상이 수십 건 규모라 몇 초면 끝나고, 무엇이 붙었는지 바로 봐야 한 번 더 돌릴지 판단할 수 있다. 대상이
   * 크게 늘면 그때 비동기로 바꾸면 된다.
   *
   * @param mode {@code MISSING_TERMS}(기본) / {@code RELINK_COVERAGES} / {@code REMATCH}. 규칙을 새로
   *     적재했거나 담보 매칭 단계를 늘린 뒤라면 {@code RELINK_COVERAGES}다 -- 약관은 그대로 두고 담보 규칙만 다시 붙인다
   */
  @PostMapping("/backfill")
  public ResponseEntity<TermsBackfillSummary> backfill(
      @RequestParam(defaultValue = "MISSING_TERMS") TermsBackfillMode mode) {
    return ResponseEntity.ok(termsBackfillService.backfill(mode));
  }
}
