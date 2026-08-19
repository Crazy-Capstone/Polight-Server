package polight.server.domain.insurance.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 보험 문서 파일의 실제 바이트를 보관하는 저장소.
 *
 * <p>구현체는 {@code storage.type} 설정으로 선택된다: 로컬 파일시스템({@code local}, 기본값) 또는 S3({@code s3}).
 */
public interface PolicyDocumentStorage {

  /**
   * 업로드된 파일을 저장하고, 나중에 다시 찾아갈 수 있는 식별자를 반환한다. 반환값은 {@code
   * PolicyDocument.storedFilePath} 에 그대로 저장된다 (로컬은 절대 경로, S3 는 오브젝트 키).
   */
  String store(MultipartFile file);
}
