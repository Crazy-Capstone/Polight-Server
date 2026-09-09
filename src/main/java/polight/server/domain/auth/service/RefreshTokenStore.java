package polight.server.domain.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

/**
 * 리프레시 토큰 저장소. Redis 에 둔다.
 *
 * <p>access token 은 서명만 검증하면 되어 서버가 아무것도 기억하지 않지만(STATELESS), 리프레시 토큰은 그럴 수 없다. 로그아웃과 재발급 회전이
 * "이 토큰은 이제 못 쓴다"를 서버가 기억해야 성립하기 때문이다. 그 기억을 JWT 안에 넣을 방법은 없다 -- 이미 발급된 서명을 취소할 수 없다.
 *
 * <p>DB 가 아니라 Redis 인 이유는 두 가지다. 만료된 토큰을 지우는 일을 TTL 이 대신해 정리 배치가 필요 없고, 이 값들은 잃어도 사용자가 다시
 * 로그인하면 그만이라 관계형 저장소의 보장이 필요 없다.
 *
 * <h2>토큰의 형태</h2>
 *
 * <p>JWT 가 아니라 난수 문자열이다. 리프레시 토큰이 담아야 할 정보는 "누구의 것인가" 하나뿐인데, 그것은 Redis 값으로 두면 된다. JWT 로 만들면
 * 토큰 자체가 사용자 정보를 들고 다녀 유출 시 노출면이 넓어지고, 정작 필요한 무효화는 여전히 Redis 로 해야 한다.
 *
 * <h2>키를 해시로 두는 이유</h2>
 *
 * <p>Redis 에 토큰 원문을 그대로 키로 쓰면, Redis 를 읽을 수 있는 사람(운영 중 {@code KEYS} 한 번, 덤프 파일 하나)이 살아 있는 토큰을
 * 전부 손에 넣고 그대로 남의 계정으로 재발급받을 수 있다. sha-256 만 저장하면 저장소에 있는 값으로는 토큰을 되만들 수 없다. 토큰이 128비트 이상의
 * 난수라 사전 공격도 성립하지 않는다.
 */
@Slf4j
@Component
public class RefreshTokenStore {

  /** 값이 사용자 id 하나뿐이라 문자열 템플릿으로 충분하다. 직렬화 설정을 따로 둘 이유가 없다. */
  private final StringRedisTemplate redisTemplate;

  private final Duration expiry;

  private static final String KEY_PREFIX = "auth:refresh:";

  /** 32바이트(256비트). 추측으로 남의 토큰을 맞히는 경로를 닫는다. */
  private static final int TOKEN_BYTES = 32;

  private static final SecureRandom RANDOM = new SecureRandom();

  public RefreshTokenStore(
      StringRedisTemplate redisTemplate,
      @Value("${security.refresh-token.expiry-seconds:1209600}") long expirySeconds) {
    this.redisTemplate = redisTemplate;
    this.expiry = Duration.ofSeconds(expirySeconds);
  }

  /** 새 리프레시 토큰을 발급해 저장한다. 돌려주는 값이 원문이고, 서버에는 해시만 남는다. */
  public String issue(UUID userId) {
    byte[] bytes = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

    redisTemplate.opsForValue().set(key(token), userId.toString(), expiry);
    return token;
  }

  /**
   * 토큰을 쓰고 즉시 버린다. 재발급 회전의 핵심이다.
   *
   * <p>조회와 삭제를 {@code GETDEL} 한 번으로 하는 이유: 나눠 하면 두 요청이 같은 토큰으로 동시에 들어왔을 때 둘 다 조회에 성공해 access
   * token 을 두 벌 받아 간다. 유출된 토큰으로 공격자와 사용자가 동시에 재발급받는 상황이 정확히 그 모양이다. 원자적으로 지우면 둘 중 하나만
   * 성공하고, 실패한 쪽은 다시 로그인하게 되어 이상이 드러난다.
   *
   * @return 토큰 주인의 사용자 id
   * @throws BaseException 토큰이 없거나 이미 쓰였거나 만료됐을 때
   */
  public UUID consume(String token) {
    if (token == null || token.isBlank()) {
      throw new BaseException(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    String userId = redisTemplate.opsForValue().getAndDelete(key(token));
    if (userId == null) {
      throw new BaseException(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    try {
      return UUID.fromString(userId);
    } catch (IllegalArgumentException e) {
      // 우리가 넣은 값은 항상 UUID 라 여기 오면 저장소가 오염된 것이다. 그 사실을 남기되
      // 사용자에게는 "다시 로그인하세요"와 같은 응답을 준다.
      log.error("리프레시 토큰에 사용자 id 가 아닌 값이 저장되어 있습니다: value={}", userId, e);
      throw new BaseException(ErrorCode.INVALID_REFRESH_TOKEN, e);
    }
  }

  /**
   * 토큰을 무효화한다. 로그아웃이 하는 일이다.
   *
   * <p>없는 토큰이어도 조용히 넘어간다. 로그아웃은 "이 토큰이 더는 안 통하게 하라"는 요청이고, 이미 안 통하면 목적은 이미 달성되어 있다. 여기서
   * 404 를 주면 클라이언트는 이미 끝난 로그아웃을 실패로 다루게 된다.
   */
  public void revoke(String token) {
    if (token == null || token.isBlank()) {
      return;
    }
    redisTemplate.delete(key(token));
  }

  public long getExpirySeconds() {
    return expiry.toSeconds();
  }

  private static String key(String token) {
    return KEY_PREFIX + sha256Hex(token);
  }

  private static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 은 모든 JVM 이 제공해야 하는 알고리즘이라 실제로는 도달하지 않는다.
      throw new IllegalStateException("SHA-256 을 사용할 수 없습니다.", e);
    }
  }
}
