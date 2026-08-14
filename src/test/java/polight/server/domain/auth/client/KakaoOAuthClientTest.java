package polight.server.domain.auth.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import polight.server.domain.auth.config.KakaoOAuthProperties;
import polight.server.domain.auth.dto.kakao.KakaoUserInfoResponse;

class KakaoOAuthClientTest {

  @Test
  void requestToken_sendsRequiredFormParams() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    KakaoOAuthClient client =
        new KakaoOAuthClient(builder.build(), new KakaoOAuthProperties("client-id", "redirect", ""));

    server
        .expect(requestTo("https://kauth.kakao.com/oauth/token"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(
            content()
                .string(
                    "grant_type=authorization_code&client_id=client-id&redirect_uri=redirect&code=auth-code"))
        .andRespond(
            withSuccess(
                """
            {"access_token":"kakao-token","token_type":"bearer","expires_in":3600}
            """,
                MediaType.APPLICATION_JSON));

    client.requestToken("auth-code");
    server.verify();
  }

  @Test
  void requestUserInfo_sendsKakaoAccessTokenAsBearerToken() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    KakaoOAuthClient client =
        new KakaoOAuthClient(builder.build(), new KakaoOAuthProperties("client-id", "redirect", ""));

    server
        .expect(requestTo("https://kapi.kakao.com/v2/user/me"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("Authorization", "Bearer kakao-access-token"))
        .andRespond(
            withSuccess(
                """
            {"id":12345,"kakao_account":{"email":"user@example.com","profile":{"nickname":"tester","profile_image_url":"https://img.kakao/profile.jpg"}}}
            """,
                MediaType.APPLICATION_JSON));

    KakaoUserInfoResponse response = client.requestUserInfo("kakao-access-token");
    server.verify();

    assertThat(response.kakaoAccount().profile().nickname()).isEqualTo("tester");
    assertThat(response.kakaoAccount().profile().profileImageUrl())
        .isEqualTo("https://img.kakao/profile.jpg");
  }
}
