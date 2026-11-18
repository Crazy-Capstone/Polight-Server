# AI 요청 증권 상태 필드 추가

이번 브랜치(`feat/unregistered-chat`)는 AI 요청의 `certificateStatus` 필드와 상태 계산만 추가합니다. **백엔드가 제안하는 필드명이며 AI 팀과 합의 또는 AI 서버 반영 완료를 의미하지 않습니다.** 프론트 응답, 공개 API, 세션 구조, DB 마이그레이션은 변경하지 않습니다.

## 요청 계약

기존 AI 경로를 유지합니다. `application.yaml`의 `ai.server.rag-path` 기본값은 `POST /internal/rag/query`이며, `AI_SERVER_RAG_PATH` 환경변수로 재정의할 수 있습니다. 기존 내부 API 키 헤더와 요청 필드는 유지합니다.

추가 필드: `certificateStatus` (JSON 문자열).

| 값 | 의미 |
| --- | --- |
| `UNREGISTERED` | 사용자에게 현재 등록된 증권(CERTIFICATE)이 없음 |
| `PROCESSING` | 증권 업로드 후 분석 대기 또는 진행 중 |
| `FAILED` | 분석 실패 |
| `COMPLETED` | 분석 완료. 약관 매칭 여부와 별개 |

`ANALYZING`으로 변경하지 않습니다. `termsId == null`을 미등록 판정으로 사용하면 안 됩니다. 분석 중·실패·완료 후 약관 매칭 실패에서도 null일 수 있습니다. TERMS 문서만 있으면 증권 미등록입니다.

가짜 증권/분석 행을 저장하거나 기존 DB enum을 변경하지 않습니다. 문서의 분석 행이 아직 없으면 PROCESSING으로, 문서 자체가 실패 상태이면 FAILED로 계산합니다. 삭제한 증권의 최초 등록 이력은 영구 추적하지 않습니다.

여행 채팅은 해당 여행의 최신 업로드 증권(`uploadedAt DESC, id DESC`)을 선택합니다. 최신 증권이 분석 중 또는 실패했다고 이전 완료 증권으로 대체하지 않습니다. 해당 여행에 증권이 없어도 사용자에게 다른 증권이 있으면 그 상태만 사용합니다. 다른 여행의 약관과 담보는 현재 여행 요청에 섞지 않습니다.

## 요청 JSON 예시

아래는 전송 계약 예시입니다. UNREGISTERED 및 약관 없는 요청의 실제 서비스 호출 연결은 후속 일반 채팅 브랜치에서 처리합니다.

```json
{
  "userId": "11111111-1111-4111-8111-111111111111",
  "tripId": "22222222-2222-4222-8222-222222222222",
  "termsId": null,
  "documentId": null,
  "policyId": null,
  "sessionId": "33333333-3333-4333-8333-333333333333",
  "question": "여행 보험에서 무엇을 확인해야 하나요?",
  "history": [],
  "coverages": [],
  "coveragesComplete": false,
  "clausePaths": [],
  "certificateStatus": "UNREGISTERED"
}
```

분석 완료 후 약관 매칭 실패의 경우 `certificateStatus`는 `COMPLETED`, `termsId`는 null입니다. 읽어낸 담보가 있으면 기존 형식으로 `coverages`에 포함될 수 있습니다. 약관 유무와 상태를 별도로 판단해야 합니다.

## 이번 변경과 후속 작업 경계

- 이번 변경: 상태 enum, 실제 증권·분석 데이터에 따른 계산, RagQueryRequest 필드 추가, 기존 AI 호출에 상태 포함, 전송 로그 및 검증.
- 기존 채팅 경로 `/api/v1/trips/{tripId}/chat/messages`와 프론트 응답은 그대로입니다.
- **약관이 없으면 AI를 호출하지 않는 기존 동작도 유지합니다.** 따라서 현재 서비스에서 UNREGISTERED/PROCESSING/FAILED 또는 COMPLETED + termsId null 요청은 아직 AI까지 전달되지 않습니다. 클라이언트 직렬화 계약은 네 상태를 모두 지원합니다.
- 여행 없는 GET·POST `/api/v1/chat/messages`, 일반 상담 세션, 중복 방지 마이그레이션, 약관 없는 AI 호출은 다른 브랜치에서 연결합니다.
- 횟수 제한과 AI 프롬프트·고정 등록 문구는 이번 범위에 없습니다.

AI 팀은 수신 스키마에 `certificateStatus` 네 상태를 추가하면 됩니다. 후속 일반 채팅 연동 시에는 `tripId` null 허용과 `termsId`가 없을 때 RAG 없이 일반 답변을 생성하는 분기가 필요합니다. 이때 `COMPLETED`라도 termsId가 null이면 전체 약관 검색으로 확장하면 안 됩니다. 구조화된 일반 답변과 고정 등록 안내 문구는 AI 서버가 담당합니다. 응답 JSON 구조는 기존 계약을 유지합니다.
