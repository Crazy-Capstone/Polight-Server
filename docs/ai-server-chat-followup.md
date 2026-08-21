# 챗봇 연동 관련 회신 — 백엔드 → AI

> 약관 RAG 기반 챗봇 질의 API를 붙였습니다([PR #38](https://github.com/Crazy-Capstone/Polight-Server/pull/38)). `POST /internal/rag/query`를 그대로 쓰고, 계약 문서 3번의 확정 스키마에 맞췄습니다.
>
> 그 과정에서 **양쪽이 서로 다른 전제로 코드를 짜 둔 지점 2건**을 발견했습니다. 지금 무언가 고장 나 있는 것은 아니고, 기록을 맞춰 두려는 회신입니다.

---

## 🔴 1. `coveragesComplete` — 받는 쪽만 만들어져 있습니다

같은 값이 두 방향으로 흐르는데, 한쪽에만 통로가 있습니다.

| 방향 | 필드 | 상태 |
| --- | --- | --- |
| AI → 백엔드 (분석 완료 콜백) | `coverages_complete` | **필드 자체가 없습니다** (`app/schemas/analysis.py:122-135`) |
| 백엔드 → AI (챗봇 질의) | `coverages_complete` | 있습니다 (`app/schemas/rag.py:86`) |

`prompt_builder.py:141-163`이 이 값으로 프롬프트 머리말을 갈라 씁니다. 받을 준비는 이미 되어 있는데, 그 값의 유일한 출처인 완료 콜백에 필드가 없습니다. 그래서:

```
AI가 안 보냄  →  백엔드 DB에 항상 false  →  백엔드가 AI에 false만 보냄
              →  prompt_builder 의 분기가 영원히 한쪽만 탐
```

결과적으로 챗봇은 계속 "목록에 없는 담보를 미가입으로 단정하지 않는" 안전 모드로만 동작합니다. AI 쪽에서 만들어 둔 기능이 스스로에게 닿지 않는 상태입니다.

### 백엔드는 준비되어 있습니다

- `AnalysisCallbackRequest.coveragesComplete`로 받고, `Boolean.TRUE.equals(...)`로 null-safe 처리합니다
- `analysis_results.coverages_complete`에 저장합니다 (`V5`)
- `GET .../analysis/coverages` 응답으로 그대로 내려줍니다
- 챗봇 질의 시 AI에 실어 보냅니다

**값이 켜지는 시점에 백엔드 코드는 바뀌지 않습니다.** 받아서 저장하고 되돌려주기만 합니다.

### 요청 — 필드 한 줄 + 보수적 규칙

`AnalysisCompleteCallback`에 `coverages_complete: bool = False` 추가를 부탁드립니다.

그리고 **지금 이미 계산할 수 있는 근거가 있어 보입니다.** `to_payloads`(`app/services/certificate_adapter.py:207`)가 에이전트 출력의 표를 순회하면서, 담보명이 비어 있는 행을 건너뜁니다.

```python
for row in certificate.get("coverage_by_age_table", []):
    title = _title(row)
    if not title:
        continue        # ← 여기서 버려진 행이 있는지 알 수 있습니다
```

그렇다면 이 비교가 가능합니다.

```python
rows = certificate.get("coverage_by_age_table", [])
complete = bool(rows) and len(payloads) == len(rows)
```

이 값이 뜻하는 것은 **"에이전트가 뽑아 준 표를 한 행도 버리지 않고 담보로 옮겼다"** 까지입니다. 에이전트가 증권에 인쇄된 표 전체를 읽었는지는 별개의 질문이고, 그건 판단할 수 없다면 `false`로 두셔도 됩니다.

백엔드가 원하는 것은 정확한 `true`가 아니라, **`false`의 이유가 "필드가 없어서"가 아닌 상태**입니다. 지금은 값이 없는 것과 "전체가 아니다"가 구분되지 않습니다.

### 대안 B — 권하지 않습니다

`_process_certificate`가 `rawResultJson`에 `certificate` dict 전체를 담아 보내 주므로, 백엔드가 그걸 파싱해 위 비교를 직접 할 수도 있습니다. 다만 그러면 백엔드가 AI 내부 출력 형태(`coverage_by_age_table` 키 이름과 행 구조)에 의존하게 되고, 에이전트 출력이 바뀌면 조용히 틀린 값을 내기 시작합니다. 계약 문서에서 "판단은 추출한 쪽만 할 수 있다"고 정리한 이유이기도 해서, A안을 권합니다.

### 우선순위 — 낮습니다

챗봇 질의 경로가 아직 실 호출 전이고, `false`는 안전한 쪽입니다. 급하지 않습니다. 다만 코드만 보면 백엔드 버그처럼 보이는 값이라, 리뷰나 인수인계 때 오해가 생기기 전에 맞춰 두고 싶습니다.

---

## 🟡 2. 카드형 응답 — 전제를 정정합니다

`app/schemas/rag.py:91-92`에 이렇게 적혀 있습니다.

> `responseType`은 `chat_messages.response_type`(NOT NULL)에 그대로 들어간다.
> 카드형 4종(`HOSPITAL_CARDS` 등)은 아직 **렌더링하는 화면이 없어** 항상 `TEXT`를 보낸다.

**화면은 있습니다.** 프론트(`Crazy-Capstone/Polight-frontend`, Flutter)를 확인했습니다.

- `lib/screens/chatbot_screen.dart`의 `_BotMessageWithCards` — 병원 카드를 그리는 위젯이 있고, 목업 데이터로 도쿄 병원 2곳이 렌더링됩니다
- 퀵리플라이 첫 항목이 `'🏥 병원 찾기'` 입니다
- `pubspec.yaml`에 `geolocator`, `geocoding`이 들어 있습니다 — 위치 기반 조회를 상정한 의존성입니다

즉 막고 있는 것은 화면이 아니라 **데이터 출처**입니다. 카드형 4종을 갈라 보면 이렇습니다.

| 응답 유형 | 데이터 출처 | 실제로 막는 것 |
| --- | --- | --- |
| `HOSPITAL_CARDS` | 없음 | 제휴병원·위치 데이터가 어디에도 없습니다. 약관 청크에는 해외 병원 정보가 없어 RAG로는 답할 수 없습니다 |
| `EMERGENCY_CONTACTS` | `emergency_contacts` 테이블 (`V1`) | 테이블·enum·인덱스는 있는데 **시드 데이터가 0건**이고, 조회 코드도 엔티티뿐입니다 |
| `COVERAGE_CARDS` | `coverage_items` | 데이터는 있습니다. 챗봇 경로에 연결되어 있지 않을 뿐입니다 |
| `POLICY_SUMMARY` | `analysis_results.summary` | 동일합니다 |

이걸 구분해야 하는 이유는 해결 순서가 달라지기 때문입니다. "화면이 없다"로 두면 프론트 대기 항목이 되지만, 실제 선행 작업은 **데이터 확보**이고 그건 백엔드·기획 몫입니다.

### 지금 백엔드 동작

`ChatQueryService`가 `TEXT` 외의 `responseType`을 받으면 **`TEXT`로 저장하고 `log.warn`을 남깁니다.** 조용히 바꾸면 나중에 "카드가 왜 안 나오지"를 추적하기 어려워서 흔적은 남기게 했습니다.

그래서 지금은 AI가 `HOSPITAL_CARDS`를 보내 주셔도 화면에 카드가 뜨지 않습니다. 반대로 데이터가 준비되면 백엔드의 이 강제 저장을 풀어야 하므로, **켜는 시점을 양쪽이 같이 알아야 합니다.**

### 요청

1. 주석과 계획의 근거를 "렌더링 화면 없음" → **"데이터 출처 없음"** 으로 정정 부탁드립니다
2. 켤 순서에 대한 의견을 듣고 싶습니다. 백엔드가 보기에 `EMERGENCY_CONTACTS`가 제일 가깝습니다 — 국가별 공용 데이터라 개인정보 문제가 없고, 테이블과 인덱스(`idx_emergency_contacts_country_type`)가 이미 있어 **값을 채워 넣기만** 하면 됩니다. 그 작업은 백엔드·기획 몫이고, AI 쪽 작업은 `responseType` 분기뿐입니다
3. **MVP는 `TEXT` 고정으로 갑니다** — 이건 백엔드 확정입니다. 카드형은 MVP 이후로 미뤄 주세요

---

## 참고 — 조치 필요 없는 것 2건

### 분석 요청 페이로드가 계약 문서와 달라졌습니다

`docs/ai-server-contract-answers.md` 1-3의 요청 예시는 `fileUrl` + `policyId`인데, 실제 구현은 `downloadUrl` + `documentType`을 보내고 `policyId`는 보내지 않습니다. AI 쪽에서 `AliasChoices("downloadUrl", "fileUrl", "download_url")`로 방어해 두신 덕에 깨지지 않았습니다. 감사합니다.

**백엔드가 문서를 갱신하겠습니다.** AI 쪽 조치는 필요 없습니다.

### 챗봇 질의 계약은 코드를 읽어 맞췄습니다

`app/schemas/rag.py`를 보고 필드명을 맞췄고, 모킹 테스트까지만 확인한 상태입니다. **실 호출은 아직 한 번도 하지 않았습니다.** 실 연동 때 아래 두 가지를 같이 봤으면 합니다.

- `HistoryTurn.sender`(`USER`/`ASSISTANT`)가 AI 쪽 `role`(`user`/`assistant`) 변환을 타는지 — `rag_service.py:154-157`을 보고 맞다고 판단했습니다
- 2턴 이상 대화에서 `query_rewriter`가 실제로 도는지 (`"그럼 얼마까지요?"` 같은 후속 질문)

---

## 회신 요청 정리

| # | 내용 | 필요한 것 | 우선순위 |
| --- | --- | --- | --- |
| 1 | `AnalysisCompleteCallback`에 `coverages_complete` 추가 | 필드 한 줄 + 위 보수적 규칙 적용 여부 회신 | 낮음 (실 연동 이후) |
| 2 | 카드형 4종을 막고 있는 것이 데이터 출처라는 점 확인 | 주석·계획 정정, 켤 순서 의견 | 낮음 (MVP 밖) |
| 3 | 실 연동 일정 | AI 서버 기동 가능한 시점 | **높음** |

3번이 가장 급합니다. 백엔드 쪽은 준비가 끝나 있고, 로컬 기준 `AI_SERVER_BASE_URL` 기본값이 `http://localhost:8000`이라 별도 설정 없이 붙습니다. `INTERNAL_API_KEY` 없이도 AI 쪽이 경고만 남기고 통과시켜 주시니, 로컬 통합 테스트는 키 교환 전에 바로 가능합니다.
