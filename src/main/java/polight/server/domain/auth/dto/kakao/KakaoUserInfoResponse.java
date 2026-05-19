package polight.server.domain.auth.dto.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoUserInfoResponse(Long id, KakaoAccount kakaoAccount) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record KakaoAccount(String email, Profile profile) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Profile(String nickname) {}
}
