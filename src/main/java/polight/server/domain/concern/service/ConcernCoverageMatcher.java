package polight.server.domain.concern.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import polight.server.domain.analysis.entity.CoverageItem;
import polight.server.domain.concern.entity.Concern;

/**
 * 사용자가 고른 걱정이 어떤 담보에 해당하는지 찾는다.
 *
 * <p>키워드 부분 문자열 매칭이다. 담보의 {@code title}/{@code category}는 AI가 증권에서 읽어 채우는 자유 문자열이라({@code
 * "해외의료비 보장 상해"}, {@code "해외여행중 휴대품손해(분실제외)"}) 걱정 코드와 직접 이어붙일 수 없다.
 *
 * <p><b>이 매칭은 완전하지 않다.</b> 보험사마다 표현이 달라 못 잡는 경우가 생긴다. 그래서 매칭 실패를 "미가입"으로 단정하면 안 된다. 실제로는 보장되는데 안
 * 된다고 안내하면 사용자가 청구를 포기하는데, 그게 반대 방향 오류보다 나쁘다. 근본 해결은 AI가 담보에 표준 코드를 붙여주는 것이고 그건 계약 변경이 필요하다.
 */
@Component
public class ConcernCoverageMatcher {

  /** 이 담보에 해당하는 걱정들. 사용자가 고른 걱정 중에서만 찾는다. */
  public Set<Concern> match(CoverageItem item, List<Concern> selectedConcerns) {
    String haystack = haystack(item);

    Set<Concern> matched = new LinkedHashSet<>();
    for (Concern concern : selectedConcerns) {
      if (containsAnyKeyword(haystack, concern)) {
        matched.add(concern);
      }
    }
    return matched;
  }

  /** 담보명과 카테고리를 함께 본다. 카테고리에만 단서가 있는 경우가 있다. */
  private String haystack(CoverageItem item) {
    StringBuilder builder = new StringBuilder();
    if (item.getTitle() != null) {
      builder.append(item.getTitle());
    }
    if (item.getCategory() != null) {
      builder.append(' ').append(item.getCategory());
    }
    return builder.toString();
  }

  private boolean containsAnyKeyword(String haystack, Concern concern) {
    return concern.getCoverageKeywords().stream().anyMatch(haystack::contains);
  }
}
