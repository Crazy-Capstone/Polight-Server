# 약관 저장소 분리 — AI 서버 싱크

> 기준: `claude/policy-terms-refactor-v33t5n` @ d03e0d9 · 2026-08-21 · PR #36
> 근거: `V7` ~ `V11` 마이그레이션 (`db/migration/`)
> 아래 스키마 표는 마이그레이션을 빈 DB에 적용한 뒤 `information_schema`에서 그대로 뽑은 값입니다.

`BACKEND_REPLY_2` 1-7에서 요청하신 **`policy_terms` INSERT 권한**을 여는 작업입니다. 요청하신 것보다 테이블이 두 개 더 생겼고, **그쪽에서 쓰기로 하신 값 중 하나가 지금 스키마에서 거부됩니다.** 그것부터 읽어 주세요.

---

## 🔴 지금 바로 확인이 필요한 것 4가지

### ① `source = 'SEEDED'`는 INSERT가 실패합니다

`BACKEND_REPLY_2` 1-7에 "이 8건 전부 `SEEDED`로 넣겠습니다, 크롤링분은 `SOURCED`"라고 쓰셨는데, `policy_terms.source`의 CHECK는 이렇습니다.

```sql
CHECK (source IN ('OFFICIAL', 'USER_UPLOAD'))
```

권한을 드리는 즉시 CHECK 위반으로 8건 전부 실패합니다.

이 컬럼은 "누가 넣었나"가 아니라 **"검수를 거친 공식 약관인가, 사용자가 올린 것인가"**를 구분합니다. 노출 범위를 가르는 `verification_status`와 짝입니다. 그래서 `SEEDED`/`SOURCED`(수집 경로) 구분과는 축이 다릅니다.

**제안**: 8건은 `source='OFFICIAL'`, `verification_status='VERIFIED'`로 넣어 주세요. 수집 경로를 남기실 거면 `SEEDED`/`SOURCED`를 담을 별도 컬럼을 추가하겠습니다 — **필요하다고 알려주시면 넣습니다.** 지금 스키마에는 없습니다.

### ② `coverage_item_sources` 테이블이 없어졌습니다

`policy_terms_coverage_sources`로 이름이 바뀌고 컬럼도 바뀌었습니다.

| 이전 | 지금 |
| --- | --- |
| `coverage_item_sources` | `policy_terms_coverage_sources` |
| `coverage_item_id` → `coverage_items` | `terms_coverage_id` → `policy_terms_coverages` |
| `policy_chunk_id` → `policy_chunks` | `terms_chunk_id` → `policy_terms_chunks` |

근거 조항은 **상품의 사실**이지 가입자의 사실이 아니라서 양쪽 끝을 공용 영역으로 옮겼습니다. 같은 상품을 산 사람 모두에게 같은 조항이 근거가 되는데, 이전 구조는 가입자 수만큼 복제해야 했습니다.

`docker/initdb/01_schema.sql`(로컬 개발용 사본)도 같이 맞춰 주세요.

### ③ 약관 분석 콜백의 자식 배열이 더 이상 저장되지 않습니다

`coverageItems[].exclusions` / `requiredDocuments` / `subLimits` / `detailItems`를 보내시면 **경고 로그만 남고 버려집니다.**

```
WARN  담보 콜백에 약관에서 나온 값이 3건 실려 있어 저장하지 않았습니다: coverageItems[0].title=상해의료비.
      면책·청구서류·세부한도는 policy_terms_coverages 에 적재해야 합니다.
```

이 값들은 이제 사용자의 담보가 아니라 **약관의 보장 규칙**에 매달립니다(아래 4절). 증권 경로는 원래 이 값들을 보내지 않으니(`to_payloads`가 채우지 않습니다) 영향이 없고, **약관 경로만 목적지가 바뀝니다.**

조용히 버리지 않고 경고를 남기는 이유는, 계약이 어긋난 사실이 로그에 드러나게 하기 위해서입니다.

### ④ `policies` 테이블을 없앴습니다 (V11)

**런타임 영향은 없습니다.** 그쪽 `app/`은 이 테이블을 읽지도 쓰지도 않고, `policy_chunks.policy_id` **컬럼은 그대로 남겼습니다.** `pg_mapper`의 `COLUMNS`에 그 이름이 있어서, 지우면 pgvector 저장소로 전환하는 순간 `column does not exist`로 적재가 통째로 실패하거든요. FK 제약만 뗐고 값은 계속 null을 넣으시면 됩니다.

없앤 이유는 그쪽이 겪은 그 버그입니다. `pg_repository`의 스코프 필터가 `policy_id`로 조인하다 "`= NULL`은 아무 행과도 일치하지 않는다"로 검색이 조용히 0건을 반환했던 것 — 근본 원인이 **백엔드가 이 테이블을 영원히 채우지 않을 것**이었습니다. 채우려면 `trip_id` NOT NULL 불일치, `display_name` 생성 규칙, `status` 전이 주체, 증권번호 암호화 수단을 다 정해야 하는데 그게 필요한 기능(만기 알림, "내 보험" 목록)이 로드맵에 없습니다. 그래서 항상 null인 FK를 네 테이블에 남겨두는 대신 지웠습니다.

보험 정보는 `analysis_results`가 갖습니다 — 보험사·상품명(V8), 보험기간(V10).

**그쪽에서 고칠 것 두 개** (둘 다 로컬 개발용):

| 파일 | 할 일 |
| --- | --- |
| `docker/initdb/01_schema.sql` | `policies` 테이블과 `analysis_results`·`policy_documents`·`chat_sessions`의 `policy_id` 제거. `policy_chunks.policy_id`는 FK만 떼고 컬럼 유지 |
| `scripts/verify_pgvector.py` | `INSERT INTO policies` 제거 (FK가 없어져 상위 행이 필요 없습니다) |

`AnalysisStartRequest.policy_id` / `ChunkScope.policy_id`는 그대로 두셔도 됩니다. 백엔드가 원래 안 보냈고 컬럼도 남아 있어 무해합니다. 정리하실 거면 지우셔도 되고요.

---

## 1. 왜 바꿨나

약관 청크가 `policy_chunks`에 있는데 이 테이블은 `analysis_result_id`·`user_id`·`document_id`가 전부 NOT NULL입니다. 그쪽에서 `BACKEND_INTERFACE` 3-2에 지적하신 그대로, **주인 없는 공용 약관을 넣을 자리가 없었습니다.**

여기에 하나가 더 있었습니다. `coverage_items`가 두 가지 뜻으로 쓰이고 있었습니다.

| | 증권 분석에서 온 행 | 약관 분석에서 온 행 |
| --- | --- | --- |
| 뜻 | 사용자가 실제 가입한 담보 | 상품 약관에 있는 보장 규칙 |
| 공유 범위 | 그 사용자 한 명 | 같은 상품 가입자 전원 |
| `limit_amount` | 가입금액 | 약관에 인쇄된 예시값 |
| 면책·서류·세부한도 | 비어 있음 | 채워짐 |

같은 컬럼이 한쪽에서는 "이 사람이 든 3,000만원", 다른 쪽에서는 "약관에 인쇄된 예시 3,000만원"입니다. 그래서 **공용 영역과 사용자 영역을 테이블 단위로 갈랐습니다.**

```
[공용 — 상품 1건당 한 벌]              [사용자 — 그 사람만]

policy_terms                          policy_documents
 ├─ policy_terms_chunks                └─ analysis_results
 │    (원문·RAG 검색)                       │  insurer_name / product_name
 │                                          │  matched_terms_id ──────┐
 └─ policy_terms_coverages                  └─ coverage_items         │
      │  (약관상 보장 규칙)                       가입 여부·가입금액   │
      ├─ exclusion_conditions                    terms_coverage_id ─┐ │
      ├─ required_documents                                         │ │
      ├─ sub_coverage_limits          ◄────────────────────────────┘ │
      ├─ coverage_detail_items                                       │
      └─ policy_terms_coverage_sources ◄──────────────────────────────┘
```

보장 상세 응답은 두 쪽을 합쳐 만듭니다. 가입금액은 `coverage_items`에서, 면책·청구서류·세부한도는 `policy_terms_coverages`에서 가져옵니다.

---

## 2. `policy_terms` — 약관 한 건

| 컬럼 | 타입 | NOT NULL | 비고 |
| --- | --- | --- | --- |
| `id` | uuid | ✓ | **DEFAULT 없음. Python이 생성** |
| `insurer_name` | varchar(200) | ✓ | |
| `product_name` | varchar(200) | ✓ | |
| `revision` | varchar(100) | | 개정판 표기 |
| `effective_date` | date | | 개정판 선택 기준. **아래 5절 참고** |
| `verification_status` | varchar(20) | ✓ | `UNVERIFIED` \| `VERIFIED` |
| `source` | varchar(20) | ✓ | `OFFICIAL` \| `USER_UPLOAD` |
| `source_document_id` | uuid | | → `policy_documents`. 공용 약관은 null |
| `owner_user_id` | uuid | | → `users`. **아래 CHECK 참고** |
| `file_hash` | varchar(64) | | sha-256. 중복 재사용용, UNIQUE 아님 |
| `created_at` / `updated_at` | timestamp | ✓ | **DEFAULT 없음. Python이 채움** |

**제약**

```sql
CHECK (verification_status IN ('UNVERIFIED','VERIFIED'))
CHECK (source IN ('OFFICIAL','USER_UPLOAD'))
-- 주인 없는 UNVERIFIED 금지
CHECK (verification_status = 'VERIFIED' OR owner_user_id IS NOT NULL)
-- VERIFIED 는 (보험사, 상품, 개정판)마다 하나뿐 (부분 유니크)
CREATE UNIQUE INDEX uk_policy_terms_verified_product
  ON policy_terms (insurer_name, product_name, COALESCE(revision, ''))
  WHERE verification_status = 'VERIFIED';
```

공용 약관 8건은 `verification_status='VERIFIED'`, `source='OFFICIAL'`, `owner_user_id=NULL`입니다.

> `COALESCE(revision,'')`인 이유: Postgres는 NULL을 서로 다른 값으로 보아 그냥 두면 개정판 표기 없는 VERIFIED 행이 같은 상품에 몇 개든 들어갑니다. **같은 상품을 개정판 없이 두 번 넣으면 두 번째가 실패합니다.**

---

## 3. `policy_terms_chunks` — 약관 본문 청크

`policy_chunks`에서 `user_id`·`trip_id`·`policy_id`·`analysis_result_id`가 빠지고 `terms_id`가 들어간 형태입니다. 나머지는 같습니다.

| 컬럼 | 타입 | NOT NULL |
| --- | --- | --- |
| `id` | uuid | ✓ (Python 생성) |
| `terms_id` | uuid | ✓ → `policy_terms` |
| `chunk_index` | integer | ✓ |
| `source_content_type` | varchar(30) | ✓ `TEXT`\|`TABLE`\|`OCR_TEXT`\|`IMAGE_CAPTION` |
| `clause_type` | varchar(30) | ✓ `GENERAL`\|`COVERAGE`\|`EXCLUSION`\|`CONDITION`\|`LIMIT`\|`DEFINITION`\|`PROCEDURE`\|`REQUIRED_DOCUMENT` |
| `content` | text | ✓ |
| `char_count` | integer | ✓ |
| `page_start` / `page_end` | integer | |
| `section_title` | varchar(500) | |
| `clause_path` | varchar(300) | |
| `coverage_category` | varchar(100) | |
| `summary` | text | |
| `embedding` | vector(1536) | |
| `created_at` / `updated_at` | timestamp | ✓ (Python이 채움) |

`UNIQUE (terms_id, chunk_index)` — 재적재는 **DELETE 후 INSERT**로 해주세요. `ON CONFLICT DO NOTHING`으로 두면 이전 적재의 잔여 청크가 남습니다.

**HNSW 인덱스는 만들지 않았습니다.** 8건 2225청크 규모에서는 순차 스캔이 수 ms이고, 근사 검색이라 재현율만 떨어집니다. 파라미터를 정할 실제 분포도 아직 없습니다. 청크가 수만 건이 되어 지연이 실측되면 그때 넣겠습니다 — **검색이 느려지면 알려주세요.**

---

## 4. `policy_terms_coverages` — 약관상 보장 규칙 (신규)

`BACKEND_INTERFACE`에는 없던 테이블입니다. 면책·청구서류·세부한도가 매달릴 자리입니다.

| 컬럼 | 타입 | NOT NULL | 비고 |
| --- | --- | --- | --- |
| `id` | uuid | ✓ | Python 생성 |
| `terms_id` | uuid | ✓ | → `policy_terms` |
| `title` | varchar(200) | ✓ | **매칭 키. 아래 6절을 꼭 읽어주세요** |
| `subtitle` | varchar(500) | | |
| `category` | varchar(100) | | |
| `limit_label` | varchar(100) | | 약관에 인쇄된 표기 그대로 |
| `conditions` | text | | |
| `sort_order` | integer | ✓ | |
| `created_at` / `updated_at` | timestamp | ✓ | Python이 채움 |

`UNIQUE (terms_id, sort_order)` — `title`이 아닌 이유는 같은 이름의 보장이 한 약관 안 여러 관에 걸쳐 서술되는 일이 있어서입니다. 그런 경우 INSERT는 통과하지만 **매칭에서 "가릴 수 없음"으로 연결이 끊깁니다**(6절).

> **`limit_amount`(정수) 컬럼을 일부러 두지 않았습니다.** 숫자로 확정된 금액은 가입 사실이고, 그것은 `coverage_items`에만 있어야 합니다. 약관 예시값이 사용자의 가입금액으로 읽히는 것을 막으려는 것입니다. 금액 문구는 `limit_label`에 원문 그대로 넣어 주세요.

### 자식 4종 — 부모만 바뀌었습니다

컬럼 구조는 그대로이고 `coverage_item_id` → **`terms_coverage_id`**로만 바뀌었습니다.

| 테이블 | NOT NULL 필드 | 길이 / 허용값 | timestamps |
| --- | --- | --- | --- |
| `exclusion_conditions` | `terms_coverage_id`, `title`, `severity`, `sort_order` | title 200 / severity `GENERAL`\|`WARNING`\|`CRITICAL` | **있음** |
| `required_documents` | `terms_coverage_id`, `document_name`, `is_mandatory`, `sort_order` | document_name 200 | **없음** |
| `sub_coverage_limits` | `terms_coverage_id`, `label`, `value`, `sort_order` | label 100 / value **200** / description 500 | **없음** |
| `coverage_detail_items` | `terms_coverage_id`, `title`, `is_covered`, `sort_order` | title 200 / subtitle 500 | **없음** |

> ⚠️ `severity`가 `GENERAL`/`WARNING`/`CRITICAL`입니다. 그쪽 스키마의 `HIGH`/`MEDIUM`/`LOW`는 CHECK 위반입니다. (`ai-server-contract-answers.md` 2절 매핑표와 동일)
> ⚠️ 타임스탬프가 있는 테이블은 `exclusion_conditions` 하나뿐입니다. 나머지 셋에 `created_at`을 넣으면 컬럼이 없다고 실패합니다.

### `policy_terms_coverage_sources` — 근거 조항

| 컬럼 | 타입 | NOT NULL |
| --- | --- | --- |
| `id` | uuid | ✓ |
| `terms_coverage_id` | uuid | ✓ → `policy_terms_coverages` |
| `terms_chunk_id` | uuid | ✓ → `policy_terms_chunks` |
| `source_role` | varchar(30) | ✓ |
| `quote_text` | text | |
| `created_at` / `updated_at` | timestamp | ✓ |

```sql
CHECK (source_role IN ('PRIMARY','CONDITION','EXCLUSION','LIMIT','PROCEDURE','REQUIRED_DOCUMENT','DEFINITION'))
UNIQUE (terms_coverage_id, terms_chunk_id, source_role)
```

> ⚠️ 그쪽 `SourceRole`의 `COVERAGE`와 `DOCUMENT`는 허용값이 아닙니다. `COVERAGE` → `PRIMARY`, `DOCUMENT` → `REQUIRED_DOCUMENT`로 보내주세요.
> **규칙과 청크는 같은 약관(`terms_id`)에 속해야 합니다.** 백엔드 엔티티에서도 막고 있습니다.

---

## 5. 부여된 권한

`rag_service`에 아래 8개 테이블 **SELECT / INSERT / DELETE**입니다.

```
policy_terms                     policy_terms_coverages
policy_terms_chunks              policy_terms_coverage_sources
exclusion_conditions             sub_coverage_limits
required_documents               coverage_detail_items
```

**UPDATE는 일부러 뺐습니다.** 적재를 DELETE 후 INSERT로 하면 필요가 없고, 무엇보다 `verification_status` 때문입니다 — UPDATE 권한이 있으면 버그 하나가 `UNVERIFIED`를 `VERIFIED`로 바꿔 검수 안 된 약관이 전 사용자에게 노출됩니다. 승격은 운영자만 하는 일이라 백엔드 계정에만 남겼습니다.

DELETE 순서는 FK 때문에 강제됩니다: 자식 4종 + sources → `policy_terms_coverages` → `policy_terms_chunks` → `policy_terms`.

---

## 6. 🔴 백엔드가 이름으로 매칭합니다 — 적재할 때 영향이 있습니다

AI가 "어느 약관인가"를 보내지 않으므로 백엔드가 두 단계로 찾습니다. **넣으시는 이름이 곧 매칭 키입니다.**

### 6-1. 증권 → 약관 (`analysis_results.matched_terms_id`)

콜백의 `insurerName` / `productName`으로 `policy_terms`를 찾습니다.

```
EXACT     보험사·상품명이 모두 맞는 약관이 하나
REVISION  여럿(개정판) → 여행 시작일에 유효했던 것 (effective_date 기준)
INSURER   상품명은 안 맞지만 그 보험사 약관이 딱 하나
NONE      그 외 전부 — 연결하지 않음
```

표기 차이는 정규화해서 흡수합니다. `삼성화재해상보험(주)` = `삼성화재해상보험 주식회사` = `삼성화재해상보험㈜`. 공백·괄호·대소문자도 무시합니다. **다만 유사도 비교는 하지 않습니다** — `해외여행보험`과 `해외여행보험(실속형)`은 다른 상품으로 봅니다.

기준일은 **증권의 보험 시작일**입니다(V10에서 콜백의 `startDate`를 받기 시작했습니다). 못 읽어 비어 있으면 여행 시작일로 내려가고, 그것도 없으면 최신 개정판을 추측으로 고릅니다.

> **`effective_date`를 꼭 채워 주세요.** 같은 상품의 개정판이 둘 이상인데 `effective_date`가 비어 있으면 어느 것인지 가릴 수 없어 **연결이 통째로 끊깁니다.** 개정판이 하나뿐이면 없어도 됩니다.
>
> 짝이 되는 부탁으로, **증권의 `startDate`를 최대한 읽어 주세요.** 개정판이 쌓이기 시작하면 이 값이 정확도를 가릅니다. 2026-02에 가입해 2026-08에 떠나는 증권은 2026-01 개정판을 적용받는데, 보험 시작일이 없으면 여행 시작일(2026-08)로 판단해 2026-07 개정판을 고르게 됩니다.

### 6-2. 담보 → 보장 규칙 (`coverage_items.terms_coverage_id`)

증권 담보명과 `policy_terms_coverages.title`을 맞춥니다.

```
EXACT      정규화 후 완전히 같음
QUALIFIED  증권 담보명이 규칙명을 통째로 품음
           규칙 "상해의료비"  ←  담보 "해외여행중 상해의료비(3천만원)"
NONE       후보가 둘 이상이거나 없음 — 연결하지 않음
```

**적재 시 부탁드리는 것**

- `title`은 **약관에 인쇄된 담보명 그대로**, 금액이나 적용 범위 수식 없이 넣어 주세요. 증권이 수식을 덧붙이는 쪽이라 약관이 짧아야 `QUALIFIED`가 붙습니다. 반대(규칙명이 더 긴 경우)는 일부러 붙이지 않습니다 — 사용자가 산 것보다 좁은 조건을 씌우게 되기 때문입니다
- **한 약관 안에서 `title`이 겹치지 않게** 해주세요. 겹치면 그 담보는 연결되지 않습니다
- 4글자 미만 `title`(`상해`, `사망` 등)은 `QUALIFIED` 대상에서 제외됩니다. 무관한 담보에 걸리는 것을 막기 위해서입니다

**어느 이름이 안 붙었는지는 로그로 나갑니다.** 운영에서 이걸 보고 적재를 보정하시면 됩니다.

```
INFO  담보-약관규칙 연결: analysisResultId=..., termsId=..., 5/8건 연결(정확 3, 수식 2)
INFO  연결되지 않은 담보: termsId=..., [항공기 지연, 여권 재발급 비용, 특별비용]
INFO  약관에 보장 규칙이 적재되어 있지 않습니다: termsId=..., 보험사=삼성화재, 상품=해외여행보험
```

**연결이 안 되면 화면에서 면책·서류가 비어 나갑니다.** 에러는 아니고 응답 스키마도 그대로입니다.

---

## 7. 그밖에 싱크할 것

### `documentKind` 기본값을 `CERTIFICATE`로 맞췄습니다 (V8)

DB DEFAULT가 `TERMS`였는데 JPA는 항상 명시값을 보내 실제로는 `CERTIFICATE`가 나가고 있었습니다. 어긋난 것을 정렬한 것이라 **동작 변화는 없습니다.** `BACKEND_REPLY_3` 4절의 순서(프론트 명시 전송 → 기본값 제거)에 동의하며, 프론트 배포 일정을 확인하는 대로 알려드리겠습니다.

### `policy_chunks.document_id` 단독 인덱스 — 이미 있습니다

`BACKEND_REPLY_3` 회신 요청 #5로 계속 올려주셨는데 **V6에서 이미 추가됐습니다**(`idx_policy_chunks_document_id`). 확인해 보세요.

### `startDate` / `endDate` — 이제 받습니다 (V10)

보내주고 계신 것을 확인하고(`analysis_service.py:305`, `schemas/analysis.py:163`) 받도록 붙였습니다. `analysis_results.insurance_start_date` / `insurance_end_date`에 저장되고, 약관 개정판을 고르는 기준일로 씁니다(6-1).

형식은 지금 그대로 `YYYY-MM-DD`면 됩니다. 값이 없어도 400은 나지 않습니다 — 기준일이 여행 시작일로 내려갈 뿐입니다.

### `policy_chunks` 존치 — 결정이 필요합니다

`BACKEND_REPLY_2` 1-6에 "`policy_terms_chunks`(공용)와 `policy_chunks`(개인)를 모두 보도록 하겠다"고 하셨는데, 지금 백엔드는 **개인 약관도 `policy_terms`에서 `verification_status='UNVERIFIED'` + `owner_user_id`로 처리**하도록 만들었습니다. 역할이 겹칩니다.

- **A안** 개인 약관도 `policy_terms`(UNVERIFIED) — 검색 코드가 한 테이블만 보면 되고, 승격이 컬럼 하나 바꾸는 일이 됩니다
- **B안** 그쪽 설계대로 두 테이블 — 백엔드가 UNVERIFIED 경로를 걷어냅니다

**A안을 제안**하지만 검색 구현 부담은 그쪽이 더 잘 아실 테니 정해서 알려주세요. `policy_chunks` 테이블 자체는 아직 지우지 않습니다 — 최근 추가된 재시도 게이트가 조각 존재 확인에 쓰고 있고, 무엇보다 그쪽이 INSERT하는 테이블이라 합의 없이 건드리지 않습니다.

---

## 8. 회신 부탁드리는 것

| # | 내용 | 없으면 |
| --- | --- | --- |
| 1 | **`source`를 `OFFICIAL`로 넣는 데 동의하는지** (①) | 8건 적재가 CHECK 위반으로 실패 |
| 2 | 수집 경로(`SEEDED`/`SOURCED`) 컬럼이 필요한지 | 출처 구분 불가 |
| 3 | **`policy_terms_coverages` 적재 일정** (4절) | 보장 상세의 면책·서류가 계속 빈 채로 나감 |
| 4 | 개정판이 둘 이상인 상품에 `effective_date`를 채울 수 있는지 (6-1) | 그 상품은 약관 연결이 끊김 |
| 5 | `policy_chunks` A안 / B안 (7절) | 개인 약관 경로 이중 구현 |
| 6 | 로컬 스키마 사본·`verify_pgvector.py`의 `policies` 정리 (④) | 로컬 DB가 운영과 어긋남 |

1·3번이 급합니다. 나머지는 그쪽 대기 없이 저희가 진행합니다.

권한(`GRANT`)은 마이그레이션 안에 들어 있어 **PR #36이 머지되고 배포되는 시점에 열립니다.** 배포되면 알려드리겠습니다.
