package polight.server.domain.concern.dto;

import polight.server.domain.concern.entity.Concern;

/**
 * 선택 화면에 뿌릴 걱정 항목.
 *
 * <p>매칭 키워드는 서버 내부용이라 응답에 넣지 않는다. 프론트가 라벨을 직접 들고 있으면 문구를 바꿀 때 앱을 다시 배포해야 하므로 서버가 내려준다.
 */
public record ConcernResponse(String code, String label) {

  public static ConcernResponse from(Concern concern) {
    return new ConcernResponse(concern.name(), concern.getLabel());
  }
}
