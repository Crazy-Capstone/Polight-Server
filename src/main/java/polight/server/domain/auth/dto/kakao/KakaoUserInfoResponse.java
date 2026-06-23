package polight.server.domain.auth.dto.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record KakaoUserInfoResponse(Long id, KakaoAccount kakaoAccount) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record KakaoAccount(String email, Profile profile) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Profile(String nickname) {}
}
