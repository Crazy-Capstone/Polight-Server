package polight.server.domain.auth.dto.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record KakaoUserInfoResponse(Long id, KakaoAccount kakaoAccount) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record KakaoAccount(String email, Profile profile) {}

  // profile_image_url 처럼 snake_case 필드가 있어 중첩 레코드에도 네이밍 전략을 따로 지정한다.
  // @JsonNaming 은 선언된 클래스에만 적용되고 중첩 레코드로 상속되지 않는다.
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record Profile(String nickname, String profileImageUrl) {}
}
