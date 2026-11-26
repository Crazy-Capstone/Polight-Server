package polight.server.domain.auth.event;

import java.util.UUID;

public record AppleTokenRevocationRequestedEvent(UUID userId, String subject, String encryptedRefreshToken) {

  @Override
  public String toString() {
    return "AppleTokenRevocationRequestedEvent[redacted]";
  }
}
