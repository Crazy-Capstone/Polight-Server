package polight.server.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefreshTokenStoreTest {

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private RefreshTokenStore store;

  private static final long EXPIRY_SECONDS = 1_209_600L;

  @BeforeEach
  void setUp() {
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    store = new RefreshTokenStore(redisTemplate, EXPIRY_SECONDS);
  }

  @Test
  void issue_storesUserIdUnderHashedKeyWithTtl() {
    UUID userId = UUID.randomUUID();

    String token = store.issue(userId);

    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    verify(valueOperations)
        .set(keyCaptor.capture(), eq(userId.toString()), eq(Duration.ofSeconds(EXPIRY_SECONDS)));

    String key = keyCaptor.getValue();
    assertThat(key).startsWith("auth:refresh:");

    // 저장된 키에 토큰 원문이 들어 있으면 안 된다. Redis 를 읽을 수 있는 사람이 살아 있는
    // 토큰을 그대로 손에 넣게 된다.
    assertThat(key).doesNotContain(token);
    // sha-256 16진수 64자.
    assertThat(key.substring("auth:refresh:".length())).hasSize(64).matches("[0-9a-f]{64}");
  }

  @Test
  void issue_returnsADifferentTokenEveryTime() {
    UUID userId = UUID.randomUUID();

    assertThat(store.issue(userId)).isNotEqualTo(store.issue(userId));
  }

  @Test
  void consume_returnsUserIdAndDeletesInOneOperation() {
    UUID userId = UUID.randomUUID();
    given(valueOperations.getAndDelete(anyString())).willReturn(userId.toString());

    assertThat(store.consume("some-token")).isEqualTo(userId);

    // GETDEL 하나로 끝나야 한다. get 과 delete 로 나뉘면 같은 토큰으로 동시에 들어온 두
    // 요청이 모두 재발급에 성공한다.
    verify(valueOperations).getAndDelete(anyString());
    verify(redisTemplate, org.mockito.Mockito.never()).delete(anyString());
  }

  @Test
  void consume_readsBackTheTokenIssuedForTheSameUser() {
    UUID userId = UUID.randomUUID();

    String token = store.issue(userId);

    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    verify(valueOperations).set(keyCaptor.capture(), anyString(), any(Duration.class));

    // 발급이 만든 키와 소비가 찾는 키가 같아야 한다. 해싱 규칙이 한쪽에서만 바뀌면
    // 모든 재발급이 조용히 401 이 된다.
    given(valueOperations.getAndDelete(keyCaptor.getValue())).willReturn(userId.toString());
    assertThat(store.consume(token)).isEqualTo(userId);
  }

  @Test
  void consume_rejectsUnknownToken() {
    given(valueOperations.getAndDelete(anyString())).willReturn(null);

    assertThatThrownBy(() -> store.consume("dead"))
        .isInstanceOf(BaseException.class)
        .extracting(e -> ((BaseException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
  }

  @Test
  void consume_rejectsBlankTokenWithoutTouchingRedis() {
    assertThatThrownBy(() -> store.consume("  "))
        .isInstanceOf(BaseException.class)
        .extracting(e -> ((BaseException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);

    verify(valueOperations, org.mockito.Mockito.never()).getAndDelete(anyString());
  }

  @Test
  void revoke_deletesTheHashedKey() {
    store.revoke("some-token");

    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    verify(redisTemplate).delete(keyCaptor.capture());
    assertThat(keyCaptor.getValue()).startsWith("auth:refresh:").doesNotContain("some-token");
  }

  @Test
  void revoke_ignoresBlankToken() {
    store.revoke(null);
    store.revoke("");

    verify(redisTemplate, org.mockito.Mockito.never()).delete(anyString());
  }
}
