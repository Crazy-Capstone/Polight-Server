# 약관 버전 저장 구조

> 기준: `develop` @ 8eb3287 · 2026-09-07
> 근거: `V7__add_policy_terms.sql`, `V8__link_analysis_to_policy_terms.sql`, `V9__separate_terms_coverages_from_coverage_items.sql`, `V11__add_analysis_insurance_period.sql`
> 코드: `domain/terms/entity/PolicyTerms.java`, `domain/terms/service/PolicyTermsMatchingService.java`

## 한 줄 요약

약관 버전은 **별도 버전 테이블 없이 `policy_terms` 행 자체로** 표현합니다. 개정판 하나 = 행 하나이고, `revision`(표기)과 `effective_date`(효력 시작일) 두 컬럼이 그 행이 어느 판인지를 말합니다. **어느 판을 적용할지는 `effective_date`와 증권의 보험 시작일을 비교해 고릅니다.**

**⚠️ 지금 이 구조는 스키마만 서 있고 채워지지 않습니다.** 백엔드에는 `policy_terms` 행을 만드는 코드 경로가 없습니다(6절). 개정판이 2건 이상 들어오는 순간 무엇이 깨지는지 5절에 적었습니다.

---

## 1. 왜 행으로 두었나

같은 상품이라도 약관은 개정됩니다. 2024년 가입자와 2026년 가입자는 **다른 조항을 적용받습니다.** 그래서 개정판을 같은 행의 "최신 값"으로 덮어쓰면 안 됩니다 — 덮어쓰는 순간 2024년 가입자에게 2026년 면책 조항을 근거로 답하게 됩니다.

버전 이력 테이블(`policy_terms_versions` 같은)을 따로 두지 않은 이유는, 약관 본문과 보장 규칙이 **개정판마다 통째로 다르기** 때문입니다. 변경분만 저장할 이유가 없어서 개정판 자체를 독립된 약관 한 건으로 둡니다. `policy_terms_chunks`(본문)와 `policy_terms_coverages`(보장 규칙)가 `terms_id`로 그 행에 매달립니다.

---

## 2. `policy_terms` — 약관 한 건 = 보험사 + 상품 + 개정판

| 컬럼 | 타입 | Null | 설명 |
| --- | --- | --- | --- |
| `id` | uuid | ✕ | PK |
| `insurer_name` | varchar(200) | ✕ | 보험사명 |
| `product_name` | varchar(200) | ✕ | 상품명 |
| **`revision`** | varchar(100) | ✓ | **개정판 표기.** "2026.01 개정" 같은 사람이 읽는 라벨. 형식 제약 없음 |
| **`effective_date`** | date | ✓ | **이 개정판이 효력을 갖기 시작한 날.** 개정판 선택의 유일한 기준 |
| `verification_status` | varchar(20) | ✕ | `VERIFIED`(공용) / `UNVERIFIED`(올린 사람만) |
| `source` | varchar(20) | ✕ | `OFFICIAL`(운영자 등록) / `USER_UPLOAD`(사용자 업로드) |
| `source_document_id` | uuid | ✓ | 사용자 업로드일 때 원본 문서(`policy_documents`) |
| `owner_user_id` | uuid | ✓ | `UNVERIFIED` 약관의 주인. 접근 범위를 가름 |
| `file_hash` | varchar(64) | ✓ | sha-256. 같은 파일 재업로드 시 재사용용 |
| `created_at` / `updated_at` | timestamp | ✕ | |

### 두 컬럼의 역할이 다릅니다

- **`revision`은 표시용입니다.** 화면에 "2026.01 개정"이라고 보여주기 위한 라벨이고, 선택 로직은 이 값을 보지 않습니다.
- **`effective_date`가 실제 기준입니다.** 개정판이 여럿일 때 어느 것을 고를지는 오직 이 값으로 정합니다.
- 따라서 **`effective_date`가 비어 있으면 그 개정판은 선택 대상에서 제외됩니다.** `revision`만 채우고 `effective_date`를 비우면, 그 행은 매칭에서 없는 것과 같습니다.

### 중복 방지: 부분 UNIQUE 인덱스

```sql
CREATE UNIQUE INDEX uk_policy_terms_verified_product
    ON policy_terms (insurer_name, product_name, coalesce(revision, ''))
    WHERE verification_status = 'VERIFIED';
```

- **`VERIFIED`에만 걸립니다.** 사용자 열 명이 같은 상품 약관을 각자 올리면 열 행이 생기는 것이 정상이라, `UNVERIFIED`에 걸면 두 번째 사용자의 업로드가 실패합니다.
- **`revision`을 `coalesce(revision, '')`로 감쌉니다.** Postgres는 NULL을 서로 다른 값으로 보기 때문에, 그냥 두면 `revision`이 없는 `VERIFIED` 행이 같은 상품에 몇 개든 들어갑니다.
- ⚠️ **그래서 공용 약관의 개정판 유일성은 `revision`이 지킵니다.** `effective_date`는 이 제약에 들어 있지 않습니다. `revision`을 비운 채 `effective_date`만 다르게 두 건을 넣으면 **두 번째 INSERT가 유니크 위반으로 실패합니다.**

---

## 3. 버전에 매달리는 것들

```
policy_terms  (개정판 1건 = 1행)
  │
  ├── policy_terms_chunks         약관 본문 청크 + 임베딩 (uk: terms_id + chunk_index)
  │     └── policy_terms_coverage_sources   보장 규칙의 근거 조항
  │
  ├── policy_terms_coverages      약관상 보장 규칙 (uk: terms_id + sort_order)
  │     ├── coverage_detail_items
  │     ├── sub_coverage_limits
  │     ├── required_documents
  │     └── exclusion_conditions
  │
  └── analysis_results.matched_terms_id   "이 증권은 이 개정판을 적용받는다"
        └── coverage_items.terms_coverage_id   사용자 담보 → 약관 보장 규칙
```

- 개정판마다 본문·보장 규칙을 **통째로 한 벌씩** 갖습니다. 개정판 간 diff는 저장하지 않습니다.
- **재적재는 `DELETE` 후 `INSERT`입니다.** AI 서버 계정(`rag_service`)에 `UPDATE` 권한을 주지 않아서, 부분적으로 수정되다 만 상태가 남을 수 없습니다.
- `coverage_items`(사용자 가입 담보)와 `policy_terms_coverages`(약관 보장 규칙)가 나뉜 이유는 공유 범위가 다르기 때문입니다. 가입금액은 그 사람만의 사실, 면책·청구서류는 같은 상품 가입자 전원에게 같은 사실입니다.

---

## 4. 어느 개정판을 적용하나 — 선택 로직

`PolicyTermsMatchingService`가 증권 분석 완료 시점에 판단해 `analysis_results.matched_terms_id`에 씁니다. AI 서버는 보험사명·상품명만 보내고, "어느 약관인가"는 백엔드가 정합니다.

### 4단계

| 단계 | 조건 | 신뢰도 |
| --- | --- | --- |
| `EXACT` | 보험사·상품명이 (표기 차이 제거 후) 맞는 약관이 **하나** | 가장 높음 |
| `REVISION` | 그런 약관이 **여럿** → 개정판 선택으로 넘어감 | 높음 |
| `INSURER` | 상품명은 안 맞지만 그 보험사 약관이 **딱 하나** | 낮음 |
| `NONE` | 그 외 전부 (연결하지 않음) | — |

> 보험사·상품명 비교는 글자 그대로 하지 않고 `InsuranceNameNormalizer`로 표기를 다듬은 뒤 합니다. `삼성화재해상보험(주)` / `삼성화재해상보험 주식회사` / `삼성화재해상보험㈜`가 모두 같아집니다. **오타 교정이나 유사도 비교는 하지 않습니다** — 그러면 "해외여행보험"과 "해외여행보험(실속형)"이 붙어 다른 상품 약관을 근거로 답하게 됩니다.

### `REVISION` 단계의 선택 규칙

**"기준일에 이미 효력이 있던 것 중 가장 최근 것."** 2026-03에 가입한 증권은 2026-01 개정판을 적용받지, 2026-07 개정판을 적용받지 않습니다.

```
후보 = 같은 보험사·상품의 약관들
  ↓ effective_date 가 없는 것은 제외        ← 전부 제외되면 NONE
  ↓ effective_date <= 기준일 인 것만 남김   ← 하나도 없으면 NONE
  ↓ 그중 effective_date 가 가장 늦은 것 선택
```

기준일이 `null`이면(아래) 최신 개정판을 고르되, 추측이라는 사실을 `reason` 문구에 남깁니다.

### 기준일은 무엇인가

1. `analysis_results.insurance_start_date` — 증권의 보험 시작일 (V11). **이것이 정답입니다.**
2. 없으면 `trips.start_date` — 여행 시작일. 여행자보험은 보험기간이 여행 기간을 덮도록 가입하므로 같은 개정판을 가리킬 가능성이 높습니다.
3. 둘 다 없으면 `null` → 최신 개정판 추측.

### VERIFIED 우선

같은 상품에 사용자가 올린 `UNVERIFIED`와 운영자 `VERIFIED`가 함께 있으면, **`VERIFIED`만 남기고 개정판 판정에 들어갑니다.** 이 둘은 개정판 관계가 아니라 같은 것의 사본이기 때문입니다(`preferVerified`).

### 애매하면 연결하지 않습니다

후보가 둘 이상 남아 가릴 수 없으면 아무거나 고르지 않고 `NONE`으로 둡니다. **잘못 연결된 약관은 연결이 없는 것보다 나쁩니다** — 화면에는 그럴듯한 조항이 인용되지만 사용자가 가입한 상품과 무관한 문장이고, 그것을 보고 청구를 포기합니다.

---

## 5. ⚠️ 개정판이 2건 이상 들어오면 지금 무슨 일이 생기나

현재 등록된 공용 약관 8건은 전부 개정판이 하나씩이라 이 경로를 타지 않습니다. **2건째 개정판이 들어오는 순간** 다음이 걸립니다.

| 상황 | 결과 |
| --- | --- |
| 두 개정판 모두 `effective_date`가 비어 있음 | **`NONE`.** 약관이 등록돼 있는데도 연결이 안 되고, 사용자는 면책·청구서류를 못 봅니다 |
| 한쪽만 `effective_date`가 있음 | 그 한쪽만 후보가 됩니다. 기준일보다 이르면 그것이 선택되고, 늦으면 `NONE` |
| 둘 다 있는데 `revision`이 둘 다 비어 있음 | **두 번째 INSERT가 유니크 위반으로 실패합니다** (2절) |
| 증권에서 보험 시작일을 못 읽음 | 여행 시작일로 대체, 그것도 없으면 최신 개정판 추측 |
| 등록된 개정판이 전부 기준일 이후 판 | **`NONE`.** 과거 가입자가 볼 약관이 없습니다 |

**정리하면: 개정판을 여러 건 등록하려면 `revision`과 `effective_date`를 둘 다 채워야 합니다.** 하나만 채우면 위 표 중 하나에 걸립니다.

---

## 6. ⚠️ 지금 이 값을 채우는 주체가 정해져 있지 않습니다

백엔드 코드를 전수 확인한 결과입니다.

- `PolicyTerms.official(insurerName, productName, revision, effectiveDate)` — **테스트에서만 호출됩니다.**
- `PolicyTerms.fromUserUpload(...)` — **테스트에서만 호출됩니다.** 게다가 `revision`·`effective_date`를 인자로 받지 않아, 이 경로로 만들어지는 약관은 **두 값이 항상 null**입니다.
- `markVerified()` (UNVERIFIED → VERIFIED 승격) — **테스트에서만 호출됩니다.**
- `policyTermsRepository.save(...)`를 부르는 프로덕션 코드가 **없습니다.**

즉 **백엔드에는 `policy_terms` 행을 만드는 API도, 관리자 화면도, 배치도 없습니다.** 지금 DB에 있는 행은 전부 AI 서버가 `rag_service` 계정으로 직접 INSERT한 것입니다(V7에서 `SELECT, INSERT, DELETE` 권한을 부여).

그래서 **`revision`·`effective_date`를 누가 어떤 규칙으로 채울지가 아직 합의된 적이 없습니다.** 약관 적재(파싱·청킹·임베딩)가 AI 쪽에 있으므로, 실질적으로 AI 서버가 약관 PDF에서 개정일을 읽어 채워야 합니다. 그 합의가 없으면 5절 첫 줄 상황(둘 다 null → `NONE`)이 기본값이 됩니다.

### 정해야 할 것

1. **`effective_date`를 누가 채우는가** — AI가 약관 PDF에서 읽나, 운영자가 등록 시 입력하나?
2. **`revision` 표기 규칙** — 유니크 제약의 일부라 형식이 흔들리면 중복 행이 생깁니다 (`"2026.01"` vs `"2026년 1월 개정"`).
3. **UNVERIFIED → VERIFIED 승격을 누가 하는가** — 승격 코드 경로가 없습니다. AI 계정에는 `UPDATE` 권한이 없어(의도적) 백엔드나 DB 직접 조작으로만 가능합니다.
4. **개정판 등록 후 기존 분석 재연결** — `POST /internal/terms/backfill?mode=REMATCH`가 이미 있습니다. 새 개정판을 넣은 뒤 이걸 돌려야 기존 분석의 연결이 갱신됩니다.

---

## 7. 파일 위치

| 무엇 | 어디 |
| --- | --- |
| 스키마 | `src/main/resources/db/migration/V7__add_policy_terms.sql` |
| 분석↔약관 연결 | `.../V8__link_analysis_to_policy_terms.sql` |
| 보장 규칙 분리 | `.../V9__separate_terms_coverages_from_coverage_items.sql` |
| 보험기간 컬럼 | `.../V11__add_analysis_insurance_period.sql` |
| 엔티티 | `domain/terms/entity/PolicyTerms.java` |
| 개정판 선택 로직 | `domain/terms/service/PolicyTermsMatchingService.java` (`selectRevision`) |
| 이름 정규화 | `domain/terms/service/InsuranceNameNormalizer.java` |
| 재연결 백필 | `domain/terms/service/TermsBackfillService.java`, `POST /internal/terms/backfill` |
| AI 서버 계약 | `docs/ai-sync-policy-terms.md` |
