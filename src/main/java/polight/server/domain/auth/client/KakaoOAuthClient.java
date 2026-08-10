package polight.server.domain.auth.client;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import polight.server.domain.auth.config.KakaoOAuthProperties;
import polight.server.domain.auth.dto.kakao.KakaoTokenResponse;
import polight.server.domain.auth.dto.kakao.KakaoUserInfoResponse;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

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

    // 응답 검증은 try 밖에서 한다. try 안에서 던지면 아래 catch가 다시 잡아 예외를 이중으로 감싼다.
    KakaoTokenResponse response;
    try {
      response =
          restClient
              .post()
              .uri("https://kauth.kakao.com/oauth/token")
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(KakaoTokenResponse.class);
    } catch (Exception e) {
      throw new BaseException(ErrorCode.KAKAO_TOKEN_REQUEST_FAILED, e);
    }

    if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
      throw new BaseException(ErrorCode.KAKAO_TOKEN_REQUEST_FAILED);
    }
    return response;
  }

  public KakaoUserInfoResponse requestUserInfo(String accessToken) {
    KakaoUserInfoResponse response;
    try {
      response =
          restClient
              .get()
              .uri("https://kapi.kakao.com/v2/user/me")
              .header("Authorization", "Bearer " + accessToken)
              .retrieve()
              .body(KakaoUserInfoResponse.class);
    } catch (Exception e) {
      throw new BaseException(ErrorCode.KAKAO_USER_INFO_REQUEST_FAILED, e);
    }

    if (response == null || Objects.isNull(response.id())) {
      throw new BaseException(ErrorCode.KAKAO_USER_INFO_REQUEST_FAILED);
    }
    return response;
  }
}
