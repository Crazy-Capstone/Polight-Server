package polight.server.domain.auth.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import polight.server.domain.auth.config.AppleOAuthProperties;
import polight.server.domain.auth.dto.apple.AppleTokenResponse;
import polight.server.domain.auth.service.AppleClientSecretGenerator;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

@Component
public class AppleOAuthClient {

  private final AppleOAuthProperties properties;
  private final AppleClientSecretGenerator secrets;
  private final RestClient client;
  private final ObjectMapper mapper;

  public AppleOAuthClient(AppleOAuthProperties properties, AppleClientSecretGenerator secrets,
      RestClient.Builder builder, ObjectMapper mapper) {
    this.properties = properties;
    this.secrets = secrets;
    this.mapper = mapper;
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(properties.connectTimeout());
    factory.setReadTimeout(properties.readTimeout());
    this.client = builder.clone().requestFactory(factory).build();
  }

  public AppleTokenResponse exchangeCode(String code) {
    var form = credentials();
    form.add("grant_type", "authorization_code");
    form.add("code", code);
    try {
      var response = client.post().uri(properties.tokenUri())
          .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
          .retrieve().body(AppleTokenResponse.class);
      if (response == null || !StringUtils.hasText(response.accessToken())) {
        throw new BaseException(ErrorCode.APPLE_TOKEN_EXCHANGE_FAILED);
      }
      return response;
    } catch (HttpStatusCodeException exception) {
      throw classify(exception, true);
    } catch (ResourceAccessException exception) {
      throw new BaseException(ErrorCode.APPLE_SERVICE_UNAVAILABLE);
    } catch (BaseException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new BaseException(ErrorCode.APPLE_TOKEN_EXCHANGE_FAILED);
    }
  }

  public void revoke(String refreshToken) {
    var form = credentials();
    form.add("token", refreshToken);
    form.add("token_type_hint", "refresh_token");
    try {
      client.post().uri(properties.revokeUri()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(form).retrieve().toBodilessEntity();
    } catch (HttpStatusCodeException exception) {
      if ("invalid_grant".equals(error(exception)) || "invalid_token".equals(error(exception))) {
        return;
      }
      throw classify(exception, false);
    } catch (ResourceAccessException exception) {
      throw new BaseException(ErrorCode.APPLE_SERVICE_UNAVAILABLE);
    } catch (BaseException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new BaseException(ErrorCode.APPLE_TOKEN_REVOCATION_FAILED);
    }
  }

  private MultiValueMap<String, String> credentials() {
    var form = new CredentialForm();
    form.add("client_id", properties.clientId());
    form.add("client_secret", secrets.generate());
    return form;
  }

  private BaseException classify(HttpStatusCodeException exception, boolean exchanging) {
    if (exception.getStatusCode().is5xxServerError() || exception.getStatusCode().value() == 429) {
      return new BaseException(ErrorCode.APPLE_SERVICE_UNAVAILABLE);
    }
    String error = error(exception);
    if ("invalid_client".equals(error) || "unauthorized_client".equals(error)) {
      return new BaseException(ErrorCode.APPLE_CONFIGURATION_ERROR);
    }
    if (exchanging && ("invalid_grant".equals(error) || "invalid_request".equals(error))) {
      return new BaseException(ErrorCode.APPLE_AUTHORIZATION_CODE_INVALID);
    }
    return new BaseException(exchanging ? ErrorCode.APPLE_TOKEN_EXCHANGE_FAILED : ErrorCode.APPLE_TOKEN_REVOCATION_FAILED);
  }

  private String error(HttpStatusCodeException exception) {
    try {
      return mapper.readTree(exception.getResponseBodyAsByteArray()).path("error").asText();
    } catch (Exception ignored) {
      return "";
    }
  }

  private static final class CredentialForm extends LinkedMultiValueMap<String, String> {
    @Override
    public String toString() {
      return "AppleOAuthForm[redacted]";
    }
  }
}
