package polight.server.domain.chat.client;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import polight.server.domain.chat.dto.RagQueryRequest;
import polight.server.domain.chat.dto.RagQueryResponse;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

@Component
public class UnregisteredChatClient {

  public enum Mode { DISABLED, MOCK, AI }

  private final RagQueryClient client;
  private final Mode mode;

  public UnregisteredChatClient(
      RagQueryClient client,
      @Value("${ai.server.unregistered-chat-mode:DISABLED}") Mode mode,
      Environment environment) {
    if (mode == Mode.MOCK && (!environment.matchesProfiles("local", "dev", "test")
        || environment.matchesProfiles("prod", "production"))) {
      throw new IllegalStateException("Unregistered chat mock requires a local, dev or test profile without a production profile");
    }
    this.client = client;
    this.mode = mode;
  }

  public void verifyAvailable() {
    if (mode == Mode.DISABLED) {
      throw new BaseException(ErrorCode.UNREGISTERED_CHAT_NOT_READY);
    }
  }

  public RagQueryResponse query(RagQueryRequest request) {
    verifyAvailable();
    if (mode == Mode.MOCK) {
      return new RagQueryResponse(
          "개발용 테스트 응답입니다. AI 연동 전까지 실제 상담 답변을 제공하지 않습니다.",
          "TEXT", List.of(), List.of(), List.of(), null, null);
    }
    return client.query(request);
  }
}
