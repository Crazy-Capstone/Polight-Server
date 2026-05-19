package polight.server.domain.auth.client;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import polight.server.domain.auth.config.KakaoOAuthProperties;
import polight.server.domain.auth.dto.kakao.KakaoTokenResponse;
import polight.server.domain.auth.dto.kakao.KakaoUserInfoResponse;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Component
@RequiredArgsConstructor
public class KakaoOAuthClient {

  private final RestClient restClient;
  private final KakaoOAuthProperties kakaoOAuthProperties;

  public KakaoTokenResponse requestToken(String authorizationCode) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "authorization_code");
    form.add("client_id", kakaoOAuthProperties.clientId());
    form.add("redirect_uri", kakaoOAuthProperties.redirectUri());
    form.add("code", authorizationCode);
    if (kakaoOAuthProperties.clientSecret() != null && !kakaoOAuthProperties.clientSecret().isBlank()) {
      form.add("client_secret", kakaoOAuthProperties.clientSecret());
    }

    try {
      KakaoTokenResponse response =
          restClient
              .post()
              .uri("https://kauth.kakao.com/oauth/token")
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(KakaoTokenResponse.class);

      if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
        throw new ResponseStatusException(UNAUTHORIZED, "카카오 토큰 발급에 실패했습니다.");
      }
      return response;
    } catch (Exception e) {
      throw new ResponseStatusException(UNAUTHORIZED, "카카오 토큰 발급에 실패했습니다.", e);
    }
  }

  public KakaoUserInfoResponse requestUserInfo(String accessToken) {
    try {
      KakaoUserInfoResponse response =
          restClient
              .get()
              .uri("https://kapi.kakao.com/v2/user/me")
              .header("Authorization", "Bearer " + accessToken)
              .retrieve()
              .body(KakaoUserInfoResponse.class);

      if (response == null || Objects.isNull(response.id())) {
        throw new ResponseStatusException(UNAUTHORIZED, "카카오 사용자 정보 조회에 실패했습니다.");
      }
      return response;
    } catch (Exception e) {
      throw new ResponseStatusException(UNAUTHORIZED, "카카오 사용자 정보 조회에 실패했습니다.", e);
    }
  }
}
