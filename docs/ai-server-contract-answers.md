# AI 서버 협의 항목 — 백엔드 답변

> 기준: `develop` @ e37b58e · 2026-08-10
> 근거 파일: `src/main/resources/db/migration/V1__baseline_schema.sql`, `src/main/java/polight/server/domain/**/entity/*.java`

---

## 먼저 짚어야 할 것 두 가지

### ① enum은 "미정"이 아니라 이미 확정돼 있습니다

문서에 "DDL이 전부 `VARCHAR`라 어떤 값을 넣어야 할지 알 수 없습니다"라고 쓰셨는데, 해당 컬럼들은 **전부 `CHECK` 제약이 걸려 있습니다.** Java 쪽도 `@Enumerated(EnumType.STRING)`이라 **대소문자까지 정확히 일치**해야 합니다.

그래서 제안하신 6개 중 **5개는 그대로 넣으면 제약 위반으로 INSERT가 실패합니다.** 아래 2번에 매핑표를 드렸습니다.

### ② `/internal/*`는 "보호가 없는" 게 아니라 아직 없습니다

`grep -rn "internal" src/main/java` → 결과 0건. 콜백 엔드포인트도, 질의 엔드포인트도 코드에 존재하지 않습니다. `SecurityConfig`의 `anyRequest().authenticated()`에 걸려서 지금 호출하면 전부 401입니다. 즉 **"배포하면 누구나 호출 가능"한 상태가 아니고, 구현 시점에 인증을 같이 넣으면 됩니다.** 5번에 구체안 드렸습니다.

---

# 🔴 1. 콜백 스키마 확장 — 필드 목록 동의

제안하신 필드 목록은 `coverage_items` 및 자식 테이블 컬럼과 **정확히 일치합니다.** 그대로 갑니다.

## 1-1. 길이·NOT NULL 제약 (초과하면 INSERT 실패)

**`coverageItems[]`**

| 필드 | 타입 | NOT NULL | 최대 길이 |
| --- | --- | --- | --- |
| `title` | string | ✓ | **200** |
| `coverageStatus` | enum | ✓ | — |
| `isCovered` | boolean | ✓ | — |
| `sortOrder` | int | ✓ | — |
| `subtitle` | string | | **500** |
| `category` | string | | **100** |
| `limitLabel` | string | | **100** |
| `limitAmount` | **BIGINT** | | 정수만. 소수점·`"1,000만원"` 같은 문자열 불가 |
| `limitCurrency` | string | | **10** (미지정 시 Spring이 `"KRW"`) |
| `conditions` | TEXT | | 제한 없음 |

**자식 배열**

| 배열 | NOT NULL 필드 | 길이 제한 |
| --- | --- | --- |
| `detailItems[]` | `title`, `isCovered`, `sortOrder` | title 200 / subtitle 500 |
| `subLimits[]` | `label`, `value`, `sortOrder` | label 100 / value **200** / description 500 |
| `requiredDocuments[]` | `documentName`, `isMandatory`, `sortOrder` | documentName 200 |
| `exclusions[]` | `title`, `severity`, `sortOrder` | title 200 / description·sourceText TEXT |
| `sources[]` | `chunkId`, `sourceRole` | quoteText TEXT |

> `subLimits[].value`가 200자입니다. "보험가입금액을 한도로 실제 발생한 비용 전액…" 같은 원문을 그대로 넣으면 넘칠 수 있으니 Python 쪽에서 잘라 주세요.

## 1-2. `sources[]`에 걸린 제약 3개 — 여기서 터질 가능성이 높습니다

**(a) UNIQUE `(coverage_item_id, policy_chunk_id, source_role)`**
`V1__baseline_schema.sql:227`. 같은 담보에 같은 chunk를 같은 role로 두 번 실으면 위반입니다. **Python이 보내기 전에 dedupe해 주세요.**

**(b) chunk와 담보가 같은 분석에 속해야 합니다**
`CoverageItemSource` 생성자가 `validateSameAnalysisResult()`로 검증합니다 ([CoverageItemSource.java:79](src/main/java/polight/server/domain/rag/entity/CoverageItemSource.java:79)). 다른 `analysisResultId`의 chunkId를 섞어 보내면 저장 단계에서 예외입니다.

**(c) 존재하지 않는 chunkId → FK 위반**
`fkcepsmnqoc7yukn8633syjgpyq`. 말씀하신 **`policy_chunks` INSERT → 콜백** 순서가 반드시 지켜져야 합니다. 이 순서 확정에 동의합니다.

## 1-3. ⚠️ Python이 `policy_chunks`를 직접 INSERT할 때 놓치기 쉬운 것

### `created_at` / `updated_at`을 Python이 직접 채워야 합니다

`policy_chunks.created_at`, `updated_at`은 **NOT NULL이고 DDL에 DEFAULT가 없습니다** (`V1:206-207`). Spring에서는 `BaseTimeEntity`의 JPA Auditing이 채우지만([BaseTimeEntity.java](src/main/java/polight/server/domain/common/entity/BaseTimeEntity.java)), **Python이 직접 INSERT하면 아무도 안 채웁니다 → NOT NULL 위반.** 두 컬럼 모두 명시적으로 넣어 주세요.

### NOT NULL 컬럼 전체

```
analysis_result_id  ← 분석 요청 시 Spring이 전달 (아래 참고)
user_id             ← 동일
document_id         ← 동일
chunk_index         ← Python
source_content_type ← Python (enum, 2번 참고)
clause_type         ← Python (enum, 2번 참고)
content             ← Python
char_count          ← Python
created_at          ← Python  ⚠️
updated_at          ← Python  ⚠️
(trip_id, policy_id 는 nullable)
```

### 분석 요청 페이로드에 FK 값을 전부 실어 보냅니다

Python이 `analysis_results` / `policy_documents`를 조회하지 않아도 되도록, 분석 요청에 아래를 모두 포함시키겠습니다. **`rag_service` 계정의 SELECT 권한을 줄이는 효과도 있습니다** (7번 참고).

```json
{
  "analysisResultId": "…",
  "userId": "…",
  "documentId": "…",
  "tripId": "…",
  "policyId": null,
  "fileUrl": "…"
}
```

### 재시도 시 UNIQUE 충돌

`uk_policy_chunks_analysis_chunk_index UNIQUE (analysis_result_id, chunk_index)` (`V1:212`). 같은 분석을 다시 돌리면 충돌합니다.

- **콜백 전 재시도** → Python이 해당 `analysis_result_id`의 chunk를 DELETE 후 재삽입. 안전합니다. (DELETE 권한을 요청하신 이유로 이해했습니다.)
- **콜백 후 재시도** → `coverage_item_sources`가 chunk를 FK 참조하므로 **DELETE가 FK 위반**입니다. 이 경우 Spring이 `coverage_items`부터 정리해야 하니, 재분석 API를 만들 때 같이 다루겠습니다. 지금은 **콜백 후 재시도는 하지 않는 것으로** 합의하고 싶습니다.

## 1-4. 콜백에 추가로 담아 주셨으면 하는 것

`analysis_results`에 채울 자리가 있는데 비어 있는 컬럼들입니다.

| 컬럼 | 요청 |
| --- | --- |
| `embedding_model` (varchar 100) | 사용한 임베딩 모델명 |
| `embedding_dimension` (int) | `1536` |
| `summary` (TEXT) | 분석 요약. **현재 프론트에 노출되는 유일한 분석 텍스트입니다** |
| `raw_result_json` (TEXT) | Python 원본 응답. 디버깅·재처리용 |
| `accuracy_score` (real) | 있으면 |

---

# 🔴 2. enum 허용값 — 확정값과 매핑표

**전부 대문자입니다.** 제안값과 다른 부분을 표시했습니다.

### `coverage_items.coverage_status` (`V1:137`)

```
COVERED  |  PARTIALLY_COVERED  |  NOT_COVERED  |  EXCLUDED
```

| 제안값 | 판정 |
| --- | --- |
| `COVERED` | ✅ |
| `NOT_COVERED` | ✅ |
| `PARTIAL` | ❌ → **`PARTIALLY_COVERED`** |
| `UNKNOWN` | ❌ 값 없음. 판단 불가 시 **`NOT_COVERED`** |
| — | `EXCLUDED` 누락. 약관이 명시적으로 면책한 담보에 사용 |

> `NOT_COVERED`(약관에 조항이 없음)와 `EXCLUDED`(약관이 명시적으로 배제)는 화면 문구가 달라질 수 있는 구분입니다. 구분 가능하면 살려 주세요.

### `coverage_item_sources.source_role` (`V1:228`)

```
PRIMARY | CONDITION | EXCLUSION | LIMIT | PROCEDURE | REQUIRED_DOCUMENT | DEFINITION
```

| 제안값 | 판정 |
| --- | --- |
| `COVERAGE` | ❌ → **`PRIMARY`** |
| `EXCLUSION` | ✅ |
| `LIMIT` | ✅ |
| `DOCUMENT` | ❌ → **`REQUIRED_DOCUMENT`** |
| — | `CONDITION`, `PROCEDURE`, `DEFINITION` 추가 사용 가능 |

### `exclusion_conditions.severity` (`V1:182`)

```
GENERAL | WARNING | CRITICAL
```

**제안하신 `HIGH`/`MEDIUM`/`LOW`는 3개 모두 제약 위반입니다.** 매핑:

| 제안값 | → 확정값 |
| --- | --- |
| `LOW` | `GENERAL` |
| `MEDIUM` | `WARNING` |
| `HIGH` | `CRITICAL` |

### `policy_chunks.clause_type` (`V1:214`)

```
GENERAL | COVERAGE | EXCLUSION | CONDITION | LIMIT | DEFINITION | PROCEDURE | REQUIRED_DOCUMENT
```

**소문자 제안값 4개 모두 위반입니다.** 매핑:

| 제안값 | → 확정값 |
| --- | --- |
| `included` | `COVERAGE` |
| `excluded` | `EXCLUSION` |
| `procedure` | `PROCEDURE` |
| `definition` | `DEFINITION` |
| (분류 불가) | `GENERAL` |
| — | `CONDITION`, `LIMIT`, `REQUIRED_DOCUMENT` 추가 사용 가능 |

### `policy_chunks.source_content_type` (`V1:215`)

```
TEXT | TABLE | OCR_TEXT | IMAGE_CAPTION
```

| 제안값 | → 확정값 |
| --- | --- |
| `paragraph` | `TEXT` |
| `table` | `TABLE` |
| `heading` | `TEXT` |
| `list` | `TEXT` |

> 이 컬럼은 "**어떤 방식으로 추출한 텍스트인가**"(원문 텍스트 / 표 / OCR / 이미지 캡션)를 뜻합니다. 제안값은 "**문서 구조상 무엇인가**"(문단/제목/목록)에 가까워서 축이 다릅니다.
> 구조 정보가 필요하면 `section_title`(500) / `clause_path`(300)에 담아 주세요. 그쪽이 원래 그 용도입니다.
> heading/list 구분이 검색 품질에 실제로 기여한다는 측정값이 있으면 enum 확장을 논의합시다. 단 CHECK 제약 + Java enum + Flyway 마이그레이션을 동시에 고쳐야 하므로 근거가 필요합니다.

### `chat_messages.response_type` (`V1:261`) — 이미 확정돼 있습니다

```
TEXT | HOSPITAL_CARDS | COVERAGE_CARDS | EMERGENCY_CONTACTS | POLICY_SUMMARY
```

NOT NULL입니다. **USER 메시지도 값이 필요하며 `TEXT`로 넣습니다.** 카드형 4종은 아직 렌더링하는 화면이 없으니 **당장은 전부 `TEXT`로 보내 주세요.**

---

## 추가 질문 2개에 대한 답

### Q. `is_covered`와 `coverage_status`가 의미 중복인데, `is_covered = (coverage_status == "COVERED")`로 봐도 되나요?

**그 식은 쓰지 마세요.** `PARTIALLY_COVERED`가 `false`가 되어, 부분 보장 담보가 화면에서 "미보장"으로 표시됩니다.

**결론: `isCovered`를 콜백에서 빼시고, `coverageStatus`만 보내 주세요.** Spring이 파생합니다.

```
is_covered = coverage_status IN (COVERED, PARTIALLY_COVERED)
```

두 값이 어긋날 가능성 자체를 없애는 게 낫습니다. `coverage_items.is_covered`는 화면에서 배지 색을 정하는 단순 boolean이고, `coverage_status`가 정밀 분류입니다. 파생 규칙은 Spring 한 곳에만 둡니다.

### Q. `sort_order`를 Python이 정할까요, Spring이 부여할까요?

**Spring이 배열 인덱스로 부여합니다.** Python은 `sortOrder`를 보내지 마시고, **배열 순서만 화면에 보여줄 순서(중요도 순)로 정렬**해 주세요.

이유: 정렬 인덱스(`idx_coverage_items_analysis_sort`)는 DB 소유이고, Python이 보낸 번호와 배열 순서가 어긋나면 원인 추적이 어렵습니다. 자식 배열 4개(`detailItems`, `subLimits`, `requiredDocuments`, `exclusions`)도 동일하게 배열 순서 → `sort_order` 부여합니다.

**정리하면 콜백에서 `isCovered`와 `sortOrder` 두 필드는 불필요합니다.** 나머지는 제안대로 갑니다.

---

# 🔴 3. 챗봇 대화 계약

## (1) 대화 이력 관리 — **A안(Spring이 실어 보냄)에 동의합니다**

제시하신 근거(무상태·확장성) 외에 하나 더 있습니다. **B안은 `rag_service` 계정에 `chat_messages` SELECT 권한을 줘야 하는데, 그건 AI 서버가 전체 사용자 대화 전문에 접근한다는 뜻입니다.** 권한 최소화 원칙에서 A안이 맞습니다. 요청 페이로드만 보면 재현되므로 디버깅도 쉽습니다.

**이력 길이는 Spring이 자릅니다: 최근 6개 메시지(3턴).** TTFT 목표가 있으니 프롬프트 길이를 예측 가능하게 두는 편이 낫습니다. 부족하면 조정합시다.

## (2) 응답 저장 — **Spring이 `chat_messages`에 저장합니다**

`session_id` FK, `response_type` NOT NULL을 Spring이 관리하고 사용자 메시지도 Spring이 먼저 받으므로, 한 트랜잭션에서 USER·ASSISTANT 두 건을 쓰는 게 단순합니다. `chat_sessions.last_active_at` 갱신도 같이 처리합니다.

## (3) `metadata_json` 구조 제안

`sources`를 여기 넣는 구조 맞습니다. `chat_messages`에는 별도 컬럼이 없으므로 이 TEXT 컬럼이 유일한 자리입니다.

```json
{
  "sources": [
    {
      "chunkId": "…",
      "sectionTitle": "제3관 배상책임 특별약관",
      "clausePath": "제3관 > 제12조",
      "pageStart": 12,
      "pageEnd": 12,
      "quote": "…"
    }
  ],
  "model": "…",
  "latencyMs": 1234
}
```

`sectionTitle`/`clausePath`/`pageStart`는 Spring이 `chunkId`로 조회해 채울 수 있으니, **Python 응답에는 `chunkId` + `quote`만 주시면 됩니다.**

## ⚠️ 요청 스키마에서 고쳐야 할 것: `policyId`는 지금 항상 `null`입니다

제안하신 요청 예시에 `policyId`가 있는데, **`policies` 테이블에 행을 만드는 경로가 코드에 전혀 없습니다.**

- `PolicyController` / `PolicyService` 없음 (`PolicyRepository`만 존재)
- `policy_documents.policy_id`, `analysis_results.policy_id` 모두 nullable이고 채우는 코드 없음
- `AnalysisMapper.toEntity()`가 `document.getPolicy()`를 쓰는데 이 값이 `null`
- 따라서 `PolicyChunk.resolvePolicy()`도 `null` → **`policy_chunks.policy_id`가 전부 `null`로 들어갑니다**

`RagSearchScopeService`에 policy 스코프 조회가 있지만([RagSearchScopeService.java:31](src/main/java/polight/server/domain/rag/service/RagSearchScopeService.java:31)) 데이터가 없어 항상 빈 결과입니다.

**→ 스코프 키를 `documentId`(단일 약관 질의) 또는 `tripId`(여행 내 전체 약관)로 바꾸는 걸 제안합니다.** `policyId`는 필드만 남겨 두고 지금은 항상 `null`로 보내주세요.

## ⚠️ 검색 결과가 0건으로 보이는 조건

`findCompletedChunks*` 쿼리 전체가 `analysisResult.status = COMPLETED`로 필터합니다 ([PolicyChunkRepository.java:20](src/main/java/polight/server/domain/rag/repository/PolicyChunkRepository.java:20)).

```
policy_chunks INSERT  →  아직 검색 안 됨 (status=PROCESSING)
콜백 수신 → Spring이 markCompleted()  →  이때부터 검색 가능
```

**콜백이 실패하면 chunk는 DB에 있는데 검색은 0건인 상태로 남습니다.** 콜백 재시도(지수 백오프 3회 정도)를 Python 쪽에 넣어 주세요. 콜백 수신은 멱등으로 만들겠습니다.

## 확정 요청 스키마

```json
{
  "userId": "…",
  "tripId": "…",
  "documentId": "…",
  "policyId": null,
  "sessionId": "…",
  "question": "그럼 얼마까지요?",
  "history": [
    { "sender": "USER",      "content": "항공편 지연되면 보상돼요?" },
    { "sender": "ASSISTANT", "content": "4시간 이상 지연 시…" }
  ]
}
```

`sender`는 `USER` / `ASSISTANT` / `SYSTEM`만 허용됩니다 (`V1:260`).

**응답**

```json
{
  "answer": "…",
  "responseType": "TEXT",
  "sources": [{ "chunkId": "…", "quote": "…" }]
}
```

---

# 🟡 4. 가입 담보 정보 — 스키마 구조상의 답과, 제품 결정이 필요한 부분

## `coverage_status`의 의미 = "약관상 보장 조항이 존재하는가"

스키마 구조가 이미 그렇게 말하고 있습니다. `coverage_items.analysis_result_id`는 **NOT NULL**이고 `analysis_results`를 참조합니다 — 즉 `coverage_items`는 **문서 1건을 분석한 결과의 자식**입니다. 사용자의 가입 사실을 표현하는 자리가 아닙니다. 그건 `policies`(+`coverage_count`, `coverage_score`)가 담당하도록 설계돼 있습니다.

**→ Python은 "약관에 뭐라고 쓰여 있는가"만 판단해 주세요.** 가입 여부 판정은 넣지 마세요.

## 문제 1(금액) — 지적이 맞습니다. MVP는 이렇게 갑니다

`limit_amount`가 nullable인 건 이 상황을 이미 전제한 스키마입니다. 나오는 것만 채우고 나머지는 `null`로 두세요.

대신 **`limit_label`(varchar 100)을 활용해 주세요.** 금액이 없어도 원문 표현을 화면에 그대로 보여줄 수 있습니다.

```
limitAmount: null
limitLabel:  "보험가입금액 한도"
```

`sub_coverage_limits`의 `label`/`value` 쌍도 같은 용도입니다 (`value`는 varchar 200이라 문자열 표현 가능).

## 문제 2(미가입 담보) — MVP 범위 밖입니다. 단 챗봇에 안전장치가 필요합니다

증권 파싱도, 가입 정보 입력 화면도 없고 `policies`에 데이터를 만드는 경로 자체가 없습니다. 이번 스프린트에 해결할 수 없습니다.

**대응: 챗봇 답변에 고정 문구를 붙입니다.** "약관 조항이 있으니 보상됩니다"라고 단정하면 실제로 미가입인 사용자에게 잘못된 안내가 되므로, 이건 그냥 두면 안 됩니다.

> 이 답변은 **약관 기준**이며, 실제 보상 여부는 가입하신 담보와 가입금액에 따라 달라집니다. 보험증권을 확인해 주세요.

**⚠️ 이 문구의 최종 표현과 노출 위치는 제품 결정입니다.** 보험 관련 안내라 문구에 따라 책임 소재가 달라질 수 있으니, 백엔드/AI 임의로 정하지 말고 팀에서 확정하는 게 좋겠습니다.

향후(증권 정보가 들어오면) 붙일 자리는 `policies` 하위에 가입담보 테이블을 새로 두고 `coverage_items`와 카테고리로 매칭하는 형태가 될 것 같습니다. 지금 스키마를 미리 바꾸지는 않겠습니다.

---

# 🟡 5. 내부 API 인증 — 구현안

앞서 적었듯 `/internal/*`는 아직 없으므로, 만들 때 아래를 함께 넣겠습니다.

**공유 시크릿 헤더 방식에 동의합니다.**

| 항목 | 값 |
| --- | --- |
| 헤더 | `X-Internal-Api-Key` |
| 환경변수 | `INTERNAL_API_KEY` (양쪽 동일) |
| 생성 | `openssl rand -base64 32` |
| 비교 | 상수 시간 비교 (`MessageDigest.isEqual`) |
| 적용 방향 | Spring→Python, Python→Spring(콜백) **양방향 동일 키** |

Spring 쪽 구현: `SecurityConfig`에서 `/internal/**`을 `permitAll`로 열고, 그 앞에 키 검증 필터를 둡니다. 실패 시 기존 `ErrorResponse` 형식으로 401을 반환합니다(`AUTHENTICATION_REQUIRED`).

**시크릿 값은 이 문서나 커밋에 넣지 않습니다.** 별도 채널로 전달하겠습니다.

여기에 보안그룹 제한(8000 인바운드를 Spring EC2에서만)까지 더하면 2중 방어가 됩니다. 그 구성에 동의합니다.

---

# 🟡 6. 스트리밍(SSE)

**"지금 결정해야 재작업이 없다"는 판단에 동의합니다.** 다만 범위를 나눠 봅시다.

- **분석 콜백**: 스트리밍 무관. 지금 형태 유지.
- **챗봇 질의**: 여기만 해당.

**제안: 엔드포인트를 2개 두고 병행합니다.**

| 엔드포인트 | 형식 | 용도 |
| --- | --- | --- |
| `POST /internal/rag/query` | JSON 1회 | 초기 개발·통합 테스트·자동화 테스트 |
| `POST /internal/rag/query/stream` | SSE | 실제 사용자 트래픽, TTFT 측정 |

두 엔드포인트가 같은 파이프라인을 공유하면 유지 비용이 크지 않고, 테스트 코드를 SSE로 짜는 부담을 피할 수 있습니다. JSON만 만들었다가 나중에 SSE를 얹는 것보다, **처음부터 둘 다 두는 쪽**이 재작업이 적습니다.

Spring 쪽은 Python SSE를 그대로 프록시(passthrough)하고, **스트림 종료 시 누적 텍스트를 `chat_messages`에 1건으로 저장**합니다. 프론트는 SSE를 직접 구독합니다.

> 참고: 현재 `build.gradle`에 WebFlux가 없습니다. MVC의 `SseEmitter`로 처리 가능하지만, 프록시 구간이 스레드를 점유합니다. 동시 사용자가 많아지면 WebFlux 도입을 검토해야 하니, **예상 동시 대화 수를 알려주시면** 지금 판단하겠습니다.

---

# 🟡 7. 인프라 — ⚠️ 전제를 정정합니다: RDS가 없습니다

문서 전체가 **RDS + VPC + 보안그룹**을 전제로 쓰여 있는데, 실제 구성이 다릅니다. 요청 4개 중 3개가 성립하지 않습니다.

## 실제 구성

`docker-compose.prod.yml` 기준입니다.

```
[EC2 1대]
 ├─ polight-postgres   (pgvector/pgvector:pg16 컨테이너)
 └─ polight-backend    (ECR 이미지, 80:8080 노출)
    └─ 두 컨테이너가 polight-network 도커 내부망으로 통신
```

- **RDS를 쓰지 않습니다.** PostgreSQL이 Spring과 **같은 EC2 안의 컨테이너**입니다
- 배포는 GitHub Actions가 ECR에 이미지를 푸시하고(`.github/workflows/deploy.yml`), EC2에서 pull·재시작하는 구조입니다
- 약관 파일만 S3에 저장합니다 (`STORAGE_TYPE: s3`)

## 요청 4건에 대한 답

| 요청 | 답 |
| --- | --- |
| RDS가 속한 VPC ID·서브넷 | **RDS가 없어 해당 없음.** 아래 배포 구성 참고 |
| RDS 보안그룹 5432 인바운드 | **RDS가 없어 해당 없음.** 아래 ⚠️ 참고 |
| `rag_service` 계정 + RDS 엔드포인트 | 컨테이너에 `CREATE USER`로 생성. 엔드포인트는 내부망 `postgres:5432` |
| pgvector 설치 여부 | **✅ 이미 설치되어 있습니다** |

### ✅ pgvector — 확인 불필요

이미지 자체가 `pgvector/pgvector:pg16`이라 확장이 포함되어 있고, `V1__baseline_schema.sql:13`의 `CREATE EXTENSION IF NOT EXISTS vector;`가 Flyway 실행 시 활성화합니다.

단 확장 생성 권한이 필요하므로 **Flyway는 마스터 계정(`POSTGRES_USER`)으로 돌아야 합니다.** `rag_service`로는 안 됩니다.

## ⚠️ 별도 EC2로 가면 DB에 접속할 방법이 없습니다

운영 compose의 postgres 서비스에는 **`ports:` 선언이 없습니다.**

```yaml
postgres:
  image: pgvector/pgvector:pg16
  # ports 없음 → 호스트/외부에 5432가 열려 있지 않음
  networks:
    - polight-network
```

로컬 개발용(`docker-compose.yml`)에는 `5432:5432`가 있지만 운영에는 의도적으로 없습니다. **같은 도커망 안의 backend만 접속할 수 있습니다.**

따라서 AI 서버를 별도 EC2에 두면 5432를 EC2 밖으로 노출해야 하는데, 컨테이너 PostgreSQL은 RDS와 달리 자체 보호 장치가 없어 **비밀번호가 사실상 유일한 방어**가 됩니다.

## 배포 구성 결정: 같은 EC2, 같은 도커망

```
[EC2 1대]
 ├─ polight-postgres
 ├─ polight-backend
 └─ polight-ai        ← 추가
```

| 항목 | 값 |
| --- | --- |
| AI → DB | `postgres:5432` (도커 내부망, 포트 노출 없음) |
| AI → Spring 콜백 | `http://backend:8080` (내부망) |
| Spring → AI | `http://ai:8000` (내부망) |
| AI 8000 포트 외부 노출 | **하지 않습니다** |

**이 구성의 장점: VPC·보안그룹 협의가 통째로 사라집니다.** "VPC 정보를 못 받아 EC2를 만들 수 없다"던 블로커가 없어지고, AWS 콘솔 작업 없이 바로 시작할 수 있습니다. `ngrok` 우회도 필요 없습니다.

**CPU 경합 우려는 타당합니다.** 다만 별도 EC2가 유일한 해법은 아니고, 컨테이너 자원 제한으로 다룹니다.

```yaml
ai:
  image: <ECR>/polight/ai:latest
  cpus: 1.0
  mem_limit: 1536m
  networks: [polight-network]
```

## ⚠️ 인스턴스 스펙 — 현재 t3.small(2vCPU/2GB)로는 부족합니다

컨테이너 3개의 메모리 합이 한계를 넘습니다.

| 컨테이너 | 필요치 |
| --- | --- |
| postgres | 512 MB |
| backend (JVM) | 768 MB |
| ai (Python) | 512 MB |
| 합계 | **1,792 MB** + OS 200~300 MB |

**t3.medium(2vCPU/4GB)으로 올린 뒤 운영 트래픽을 받는 것을 전제로 합니다.** t3.small에서는 소규모 문서로 계약 연동 테스트까지만 가능합니다.

> 참고로 EC2 2대(t3.small×2)보다 t3.medium 1대가 저렴하므로, 비용 면에서도 같은 EC2 구성이 유리합니다.

## ⚠️ 별개 이슈: JVM 메모리 설정이 컨테이너 수를 고려하지 않습니다

`Dockerfile`이 `-XX:MaxRAMPercentage=75.0`인데 `docker-compose.prod.yml`의 backend에 **`mem_limit`이 없습니다.** 컨테이너 제한이 없으면 JVM은 **호스트 전체의 75%**를 자기 몫으로 계산합니다.

즉 컨테이너를 추가하기 전에도 Spring은 이미 "2GB 전부가 내 것"이라고 판단하고 있습니다. AI 컨테이너를 올리면 OOM Killer가 개입합니다.

**AI 서버와 무관하게 백엔드가 먼저 고칠 사항입니다.** 세 컨테이너 모두에 `mem_limit`을 명시하겠습니다.

## ⚠️ 별개 이슈: DB 백업이 없습니다

PostgreSQL이 컨테이너 + 도커 볼륨(`postgres_data`)이라 **EC2가 소실되면 DB도 함께 소실됩니다.** RDS의 자동 백업에 해당하는 장치가 없습니다.

색인해 두신 약관 7개·1,915조각도 함께 사라집니다. MVP 범위에서 감수하고 갈 수는 있지만, **알고 감수하는 것과 모르고 있는 것은 다르므로** 공유합니다. `pg_dump` + S3 업로드를 cron으로 도는 정도가 최소 대응입니다.

## ✅ 임베딩 차원 1536 — 스키마 변경 불필요

`policy_chunks.embedding vector(1536)` (`V1:203`), `PolicyChunk.EMBEDDING_DIMENSION = 1536`. Upstage 임베딩을 1536으로 축소해도 품질이 유지된다고 확인해 주신 덕에 **양쪽 다 손댈 게 없습니다.** 이 검증 감사합니다.

## ⚠️ 벡터 인덱스가 없습니다

`embedding` 컬럼에 HNSW/IVFFlat 인덱스가 **없습니다.** 1,915조각 규모에서는 순차 스캔으로도 동작하지만, 약관이 늘면 검색 지연이 선형으로 증가합니다.

TTFT 목표가 있으니 인덱스를 추가하는 게 맞다고 봅니다. **다만 어떤 거리 연산자를 쓰시는지(cosine / L2 / inner product)에 따라 인덱스 opclass가 달라집니다.** 알려주시면 Flyway 마이그레이션으로 추가하겠습니다.

```sql
-- 예: cosine이면
CREATE INDEX idx_policy_chunks_embedding_hnsw
  ON policy_chunks USING hnsw (embedding vector_cosine_ops);
```

## `rag_service` 권한 — 제안 검토 결과

| 테이블 | 제안 | 백엔드 의견 |
| --- | --- | --- |
| `policy_chunks` | SELECT/INSERT/UPDATE/DELETE | ✅ 그대로 |
| `analysis_results` | SELECT (제한적) | **불필요.** FK 값을 요청 페이로드로 전달합니다(1-3 참고) |
| `policy_documents` | SELECT (제한적) | **불필요.** 동일 |
| `users`, `policies` | 접근 불필요 | ✅ |
| `chat_messages` | — | **부여 안 함** (3번 A안) |
| DDL 권한 | 없음 | ✅ 스키마는 Flyway로만 |

읽기 권한 2개를 줄일 수 있습니다. 다만 **디버깅 편의상 `analysis_results` SELECT가 실제로 필요하다면 말씀해 주세요** — 반대하지는 않습니다.

계정은 컨테이너 PostgreSQL에 아래 형태로 생성합니다. **비밀번호가 git에 올라가면 안 되므로 Flyway 마이그레이션에는 넣지 않고**, 환경변수를 받는 초기화 스크립트 또는 수동 `psql`로 처리합니다.

```sql
CREATE USER rag_service WITH PASSWORD :'pw';
GRANT SELECT, INSERT, UPDATE, DELETE ON policy_chunks TO rag_service;
```

## 백엔드가 준비해서 전달할 것

AWS 콘솔 작업은 필요하지 않습니다. 아래 3개만 전달하면 됩니다.

1. `rag_service` 계정 비밀번호 (별도 채널)
2. `INTERNAL_API_KEY` 값 (별도 채널, 5번)
3. `docker-compose.prod.yml`에 추가할 `ai` 서비스 블록 — 이미지 태그와 환경변수 목록을 알려주시면 백엔드가 작성합니다

AI 서버 이미지도 같은 ECR 레지스트리(`054422645032.dkr.ecr.ap-northeast-2.amazonaws.com`)에 `polight/ai` 리포지토리를 만들어 올리는 방식을 제안합니다. 백엔드가 쓰는 GitHub Actions OIDC 방식(`.github/workflows/deploy.yml`)을 그대로 복사해 쓰실 수 있습니다.

> **`ngrok` 우회는 필요 없어졌습니다.** VPC 대기가 없으니 로컬 `docker-compose.yml`(5432 노출됨)로 바로 개발하시고, 준비되면 운영 compose에 컨테이너를 추가하면 됩니다.

---

# 결정 사항 요약

## Python 쪽에서 고쳐야 할 것

| # | 내용 |
| --- | --- |
| 1 | **enum 값 5종 전면 수정** — 2번 매핑표. 현재 제안값은 CHECK 제약 위반 |
| 2 | 콜백에서 **`isCovered`, `sortOrder` 제거** (Spring이 파생/부여) |
| 3 | `policy_chunks` 직접 INSERT 시 **`created_at`/`updated_at` 명시적으로 채우기** |
| 4 | `sources[]` **dedupe** (item+chunk+role UNIQUE) |
| 5 | 문자열 길이 컷 — 특히 `subLimits[].value` 200자 |
| 6 | 요청 스코프 키를 `documentId`/`tripId`로 (`policyId`는 항상 null) |
| 7 | **콜백 재시도** (실패 시 검색 0건으로 남음) |
| 8 | 콜백에 `embeddingModel`, `embeddingDimension`, `summary`, `rawResultJson` 추가 |

## Spring 쪽에서 구현할 것

| # | 내용 |
| --- | --- |
| 1 | `/internal/*` 콜백·질의 엔드포인트 (현재 없음) |
| 2 | `X-Internal-Api-Key` 검증 필터 |
| 3 | 콜백 → `coverage_items` + 자식 4종 + `coverage_item_sources` 저장, `markCompleted()` |
| 4 | `isCovered` 파생, `sort_order` 배열 인덱스 부여 |
| 5 | 챗봇 API — 세션·이력 조립(최근 6메시지), `chat_messages` 저장 |
| 6 | SSE 프록시 + 스트림 종료 시 메시지 저장 |
| 7 | 벡터 인덱스 마이그레이션 (거리 연산자 확인 후) |
| 8 | `docker-compose.prod.yml`에 세 컨테이너 `mem_limit` 명시 — **AI 서버와 무관하게 선행 필요** (7번) |
| 9 | `ai` 서비스 블록 추가 + `rag_service` 계정 생성 |

## AI 팀에 회신 요청

| # | 내용 |
| --- | --- |
| 1 | 벡터 거리 연산자 (cosine / L2 / inner product) |
| 2 | 예상 동시 대화 수 (WebFlux 도입 판단용) |
| 3 | `analysis_results` SELECT 권한이 실제로 필요한지 |
| 4 | `NOT_COVERED` / `EXCLUDED` 구분 가능 여부 |
| 5 | AI 컨테이너의 예상 메모리 사용량 (분석 피크 기준) — 인스턴스 스펙 산정용 |
| 6 | AI 컨테이너에 필요한 환경변수 목록 (compose 작성용) |

## 팀 논의 필요 (백엔드 단독 결정 불가)

| # | 내용 |
| --- | --- |
| 1 | **미가입 담보 안내 문구** — 보험 안내 책임 소재와 직결 (4번) |
| 2 | 증권 기반 가입 정보 수집을 어느 스프린트에 넣을지 |
| 3 | **EC2를 t3.medium으로 올릴지** — t3.small에서는 컨테이너 3개가 OOM (7번) |
| 4 | DB 백업 전략 — 현재 EC2 소실 시 데이터 전량 유실 (7번) |
