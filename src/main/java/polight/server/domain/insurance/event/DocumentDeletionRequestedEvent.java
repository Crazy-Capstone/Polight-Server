package polight.server.domain.insurance.event;

import java.util.List;

public record DocumentDeletionRequestedEvent(List<String> storedFilePaths) {

  public DocumentDeletionRequestedEvent {
    storedFilePaths = List.copyOf(storedFilePaths);
  }
}
