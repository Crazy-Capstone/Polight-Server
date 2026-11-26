package polight.server.domain.auth.dto.apple;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AppleTokenResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("refresh_token") String refreshToken,
    @JsonProperty("id_token") String identityToken) {

  @Override
  public String toString() {
    return "AppleTokenResponse[redacted]";
  }
}
