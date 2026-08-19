package polight.server.domain.insurance.storage;

import java.net.URI;

/** 저장된 보험 문서를 제한된 시간 동안 내려받을 수 있는 URL을 만든다. */
public interface PolicyDocumentUrlProvider {

  URI createDownloadUrl(String objectKey);
}
