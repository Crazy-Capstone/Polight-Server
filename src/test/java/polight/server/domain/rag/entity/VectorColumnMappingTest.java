package polight.server.domain.rag.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import polight.server.domain.terms.entity.PolicyTermsChunk;

/**
 * 청크 엔티티가 pgvector 컬럼을 매핑하지 않는지 지킨다.
 *
 * <p>이 프로젝트에는 {@code hibernate-vector} 가 없어 {@code @JdbcTypeCode(SqlTypes.VECTOR)} 가
 * 해석되지 않는다. {@code float[]} 를 매핑하면 Hibernate 가 VARBINARY(자바 직렬화)로 폴백해 읽고,
 * 실제로 오는 값은 {@code "[0.2,..."} 라는 pgvector 텍스트라 <b>엔티티를 만드는 순간</b> {@code
 * SerializationException: invalid stream header: 5B302E32} 로 터진다.
 *
 * <p>이 고장은 보통의 단위 테스트에 잡히지 않는다. 엔티티를 직접 만들어 쓰는 테스트는 DB 를 거치지
 * 않고, 실제로 터지려면 pgvector 가 값을 채워 둔 행을 읽어야 한다. 그래서 운영에서만 드러났다 --
 * 답변 근거를 조회하는 챗봇 질문이 전부 500 이 됐다.
 *
 * <p>벡터 검색은 AI 서버가 한다. 백엔드는 이 값을 읽지도 쓰지도 않으므로 매핑할 이유가 없다.
 * 되살려야 한다면 {@code hibernate-vector} 를 먼저 넣고 실제 pgvector 행으로 확인한 뒤에 한다.
 */
class VectorColumnMappingTest {

  @Test
  void chunkEntitiesDoNotMapTheVectorColumn() {
    assertThat(floatArrayFieldsOf(PolicyTermsChunk.class)).isEmpty();
    assertThat(floatArrayFieldsOf(PolicyChunk.class)).isEmpty();
  }

  private static List<String> floatArrayFieldsOf(Class<?> type) {
    return Arrays.stream(type.getDeclaredFields())
        .filter(field -> field.getType() == float[].class)
        .map(Field::getName)
        .toList();
  }
}
