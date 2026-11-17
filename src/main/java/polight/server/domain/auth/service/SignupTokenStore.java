package polight.server.domain.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

@Component
@RequiredArgsConstructor
public class SignupTokenStore {

  static final Duration EXPIRY = Duration.ofMinutes(5);
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Pattern TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");
  private static final String KEY_PREFIX = "auth:signup:";

  private final StringRedisTemplate redis;
  private final ObjectMapper mapper;

  String issue(KakaoSignupProfile profile) {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    try {
      redis.opsForValue().set(key(token), mapper.writeValueAsString(profile), EXPIRY);
    } catch (JsonProcessingException e) {
      throw new BaseException(ErrorCode.INTERNAL_ERROR, e);
    }
    return token;
  }

  KakaoSignupProfile consume(String token) {
    if (token == null || !TOKEN_FORMAT.matcher(token).matches()) {
      throw new BaseException(ErrorCode.INVALID_SIGNUP_TOKEN);
    }
    String profile = redis.opsForValue().getAndDelete(key(token));
    if (profile == null) {
      throw new BaseException(ErrorCode.INVALID_SIGNUP_TOKEN);
    }
    try {
      return mapper.readValue(profile, KakaoSignupProfile.class);
    } catch (JsonProcessingException e) {
      throw new BaseException(ErrorCode.INTERNAL_ERROR, e);
    }
  }

  private static String key(String token) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(token.getBytes(StandardCharsets.UTF_8));
      return KEY_PREFIX + HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
