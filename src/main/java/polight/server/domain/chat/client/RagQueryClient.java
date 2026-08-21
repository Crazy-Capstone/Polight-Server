package polight.server.domain.chat.client;

import polight.server.domain.chat.dto.RagQueryRequest;
import polight.server.domain.chat.dto.RagQueryResponse;

public interface RagQueryClient {

  RagQueryResponse query(RagQueryRequest request);
}
