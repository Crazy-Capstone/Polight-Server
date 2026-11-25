package polight.server.domain.chat.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import polight.server.domain.chat.client.UnregisteredChatClient.Mode;
import polight.server.domain.chat.dto.CertificateStatus;
import polight.server.domain.chat.dto.RagQueryRequest;
import polight.server.domain.chat.dto.RagQueryResponse;
import polight.server.global.exception.BaseException;
import polight.server.global.exception.ErrorCode;

class UnregisteredChatClientTest {

  private final RagQueryClient ai = mock(RagQueryClient.class);
  private final RagQueryRequest request = new RagQueryRequest(UUID.randomUUID(), null, null, null, null,
      UUID.randomUUID(), "Help", List.of(), List.of(), false, List.of(), CertificateStatus.UNREGISTERED);

  @Test
  void disabledModeRejectsWithoutCallingAi() {
    var client = new UnregisteredChatClient(ai, Mode.DISABLED, new MockEnvironment());
    assertThatExceptionOfType(BaseException.class).isThrownBy(() -> client.query(request))
        .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.UNREGISTERED_CHAT_NOT_READY));
    verifyNoInteractions(ai);
  }

  @ParameterizedTest
  @ValueSource(strings = {"local", "dev", "test"})
  void mockModeUsesOnlyClearlyMarkedDevelopmentText(String profile) {
    var environment = new MockEnvironment();
    environment.setActiveProfiles(profile);
    var response = new UnregisteredChatClient(ai, Mode.MOCK, environment).query(request);
    assertThat(response.answer()).contains("개발용 테스트 응답", "실제 상담 답변을 제공하지 않습니다");
    assertThat(response.responseType()).isEqualTo("TEXT");
    assertThat(response.sources()).isEmpty();
    assertThat(response.claimDocuments()).isEmpty();
    verifyNoInteractions(ai);
  }

  @ParameterizedTest
  @ValueSource(strings = {"prod", "production", "staging", "default"})
  void mockModeCannotStartOutsideDevelopmentProfiles(String profile) {
    var environment = new MockEnvironment();
    environment.setActiveProfiles(profile);
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> new UnregisteredChatClient(ai, Mode.MOCK, environment));
    verifyNoInteractions(ai);
  }

  @Test
  void productionProfileCannotBeBypassedByAlsoEnablingLocal() {
    var environment = new MockEnvironment();
    environment.setActiveProfiles("local", "prod");
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> new UnregisteredChatClient(ai, Mode.MOCK, environment));
  }

  @Test
  void aiModeUsesExistingAiClientAndResponseContract() {
    var expected = new RagQueryResponse("Guidance", "TEXT", List.of(), List.of(), List.of(), null, null);
    when(ai.query(request)).thenReturn(expected);
    assertThat(new UnregisteredChatClient(ai, Mode.AI, new MockEnvironment()).query(request)).isSameAs(expected);
  }
}
