# Polight API 명세서 (v1)

> 프론트엔드 연동용 문서. 2026-08-10 기준 `develop` 브랜치 구현 상태를 그대로 반영합니다.
> Swagger UI: `GET /swagger-ui.html` · OpenAPI JSON: `GET /v3/api-docs`

---

## 1. 공통 규약

### Base URL

| 환경 | URL |
| --- | --- |
| 로컬 | `http://localhost:8080` |

### 인증

- 방식: **Bearer JWT** (`Authorization: Bearer {accessToken}`)
- 토큰 발급: `POST /api/auth/kakao/login`
- 토큰 만료: 기본 **3600초(1시간)**, 응답의 `expiresInSeconds`로 내려감
- 리프레시 토큰은 **아직 없습니다.** 만료 시 카카오 로그인을 다시 수행해야 합니다.
- 인증 불필요(permitAll) 경로: `/api/auth/**`, `/swagger-ui/**`, `/v3/api-docs/**`, `/auth/login/kakao`, `/kakao-login-test.html`, `/error`, `/`, `/favicon.ico`
- 그 외 **모든 경로는 인증 필수**입니다.

### CORS

- 허용 Origin(기본값): `http://localhost:3000`, `http://localhost:5173` (환경변수 `CORS_ALLOWED_ORIGINS`로 변경)
- 허용 메서드: `GET, POST, PATCH, PUT, DELETE, OPTIONS`
- `Access-Control-Allow-Credentials: true`
- **노출 헤더: `Location`** — 201 응답의 리소스 위치를 JS에서 읽을 수 있습니다.

### 공통 포맷

- 요청/응답 Content-Type: `application/json` (파일 업로드만 `multipart/form-data`)
- 날짜: `LocalDate` → `"2026-09-01"`
- 일시: `LocalDateTime` → `"2026-08-10T14:23:11.123456"` (**타임존 오프셋 없음**, 서버 로컬 시각)
- 식별자: 모두 **UUID** 문자열

### 공통 에러 응답

모든 에러는 아래 형태로 통일되어 있습니다.

```json
{
  "code": "TRIP_NOT_FOUND",
  "message": "여행을 찾을 수 없습니다.",
  "fieldErrors": {
    "name": "여행 이름은 필수입니다."
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `code` | string | 에러 코드. **분기는 반드시 이 값으로** 하세요 (message는 문구가 바뀔 수 있음) |
| `message` | string | 사용자에게 그대로 노출 가능한 한국어 메시지 |
| `fieldErrors` | object\|없음 | 검증 실패한 필드별 사유. 해당 없으면 **키 자체가 생략**됩니다 |

#### 전체 에러 코드표

| code | HTTP | message | 발생 상황 |
| --- | --- | --- | --- |
| `INVALID_INPUT` | 400 | 입력값이 올바르지 않습니다. | `@Valid` 검증 실패, PathVariable 타입 불일치(UUID 형식 오류 등). `fieldErrors` 포함 |
| `AUTHENTICATION_REQUIRED` | 401 | 인증이 필요합니다. | 토큰 없음 / 만료 / 서명 불일치 |
| `ACCESS_DENIED` | 403 | 접근 권한이 없습니다. | 인가 실패 |
| `KAKAO_TOKEN_REQUEST_FAILED` | 401 | 카카오 토큰 발급에 실패했습니다. | 인가 코드가 잘못됨/재사용됨/만료 |
| `KAKAO_USER_INFO_REQUEST_FAILED` | 401 | 카카오 사용자 정보 조회에 실패했습니다. | 카카오 사용자 정보 API 실패 |
| `KAKAO_USER_ID_NOT_FOUND` | 401 | 카카오 사용자 식별자(providerId)가 없습니다. | 카카오 응답에 id 없음 |
| `USER_NOT_FOUND` | 404 | 사용자를 찾을 수 없습니다. | 토큰의 사용자가 DB에 없음 |
| `TRIP_NOT_FOUND` | 404 | 여행을 찾을 수 없습니다. | 존재하지 않거나 **내 소유가 아닌** 여행 |
| `INVALID_TRIP_PERIOD` | 400 | 여행 종료일은 시작일보다 빠를 수 없습니다. | `endDate < startDate` |
| `POLICY_DOCUMENT_NOT_FOUND` | 404 | 보험 문서를 찾을 수 없습니다. | 존재하지 않거나 내 소유가 아닌 문서 |
| `EMPTY_POLICY_DOCUMENT_FILE` | 400 | 업로드할 파일은 비어 있을 수 없습니다. | 0바이트 파일 업로드 |
| `POLICY_DOCUMENT_TOO_LARGE` | 413 | 업로드할 수 있는 파일 크기를 초과했습니다. | multipart 용량 초과 |
| `POLICY_DOCUMENT_STORAGE_FAILED` | 500 | 파일을 저장하지 못했습니다. | 로컬/S3 저장 실패 |
| `ANALYSIS_RESULT_NOT_FOUND` | 404 | 분석 결과를 찾을 수 없습니다. | 분석을 시작하지 않은 문서를 조회 |
| `INTERNAL_ERROR` | 500 | 서버 내부 오류가 발생했습니다. | 예상치 못한 예외 |

> **소유권 정책**: 남의 여행/문서에 접근하면 403이 아니라 **404(`TRIP_NOT_FOUND` / `POLICY_DOCUMENT_NOT_FOUND`)** 가 내려갑니다. 리소스 존재 여부를 숨기기 위한 의도된 동작입니다.

---

## 2. 전체 엔드포인트 요약

| # | 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- | --- |
| 1 | POST | `/api/auth/kakao/login` | ✕ | 카카오 로그인 → JWT 발급 |
| 2 | POST | `/api/v1/trips` | ✓ | 여행 세션 생성 + 보험 문서 업로드 (multipart) |
| 3 | GET | `/api/v1/trips` | ✓ | 내 여행 목록 |
| 4 | GET | `/api/v1/trips/{tripId}` | ✓ | 여행 단건 조회 |
| 5 | PATCH | `/api/v1/trips/{tripId}` | ✓ | 여행 정보 수정 |
| 6 | POST | `/api/v1/trips/{tripId}/documents` | ✓ | 보험 문서 추가 업로드 |
| 7 | GET | `/api/v1/trips/{tripId}/documents` | ✓ | 보험 문서 목록 |
| 8 | POST | `/api/v1/trips/{tripId}/documents/{documentId}/analysis` | ✓ | 분석 시작 |
| 9 | GET | `/api/v1/trips/{tripId}/documents/{documentId}/analysis` | ✓ | 분석 상태/결과 조회 |

> `/api/users`, `/api/chat-messages` 컨트롤러는 클래스만 존재하고 **엔드포인트가 아직 없습니다.**

---

## 3. 엔드포인트 상세

### 3.1 카카오 로그인

```
POST /api/auth/kakao/login
```

인증 **불필요**.

프론트엔드가 카카오 인가 코드를 받아 전달하면, 서버가 카카오 토큰/사용자 정보를 조회하고 서비스 JWT를 발급합니다. 신규 사용자는 이 시점에 자동 가입됩니다.

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `authorizationCode` | string | ✓ | NotBlank | 카카오 OAuth redirect URI로 전달된 인가 코드 |

```json
{ "authorizationCode": "abc123-kakao-code" }
```

**200 OK**

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `accessToken` | string | 이후 모든 요청의 `Authorization: Bearer {값}` |
| `expiresInSeconds` | number | 만료까지 남은 초 (기본 3600) |
| `nickname` | string | 사용자 닉네임. 카카오가 닉네임을 주지 않으면 `"카카오사용자"` |
| `profileImageUrl` | string \| null | 카카오 프로필 이미지 URL. 동의 항목 미동의/미설정이면 `null` |

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresInSeconds": 3600,
  "nickname": "홍길동",
  "profileImageUrl": "https://k.kakaocdn.net/dn/abc/img_640x640.jpg"
}
```

**에러**: `INVALID_INPUT`(400), `KAKAO_TOKEN_REQUEST_FAILED`(401), `KAKAO_USER_INFO_REQUEST_FAILED`(401), `KAKAO_USER_ID_NOT_FOUND`(401)

> ⚠️ 인가 코드는 **일회용**입니다. 같은 코드로 재요청하면 `KAKAO_TOKEN_REQUEST_FAILED`가 납니다 (React StrictMode 이중 호출 주의).

---

### 3.2 여행 세션 생성 + 보험 문서 업로드

```
POST /api/v1/trips
Content-Type: multipart/form-data
```

여행 생성 API는 이것 하나입니다. **여행만 만드는 JSON 엔드포인트는 없습니다** — 증권 없는 여행은 분석할 대상이 없기 때문입니다. 여행 생성과 문서 저장은 같은 트랜잭션이라, 문서 저장이 실패하면 여행도 만들어지지 않습니다.

> ⚡ **증권(`CERTIFICATE`)을 올리면 분석이 자동으로 시작됩니다.** 별도로 분석 시작 API를 호출하지 마세요. 응답의 `analysis`에 시작된 작업이 담겨 오고, 바로 `GET .../analysis` 폴링으로 넘어가면 됩니다.

**Request Parts**

| 파트명 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `trip` | JSON | ✓ | 여행 정보. **이 파트의 `Content-Type`을 `application/json`으로 지정해야 합니다.** |
| `file` | binary | ✓ | 업로드할 PDF 파일 |

`trip` 파트 필드:

| 필드 | 타입 | 필수 | 제약 | 실패 메시지 |
| --- | --- | --- | --- | --- |
| `name` | string | ✓ | NotBlank, 최대 100자 | "여행 이름은 필수입니다." / "여행 이름은 100자 이하여야 합니다." |
| `startDate` | date | ✓ | NotNull | "여행 시작일은 필수입니다." |
| `endDate` | date | ✓ | NotNull, `>= startDate` | "여행 종료일은 필수입니다." |
| `concerns` | string[] | ✕ | `Concern` 코드값. 생략/`null`이면 빈 목록 | 허용되지 않는 코드는 `INVALID_INPUT`(400) |
| `documentKind` | enum | ✕ | `CERTIFICATE` \| `TERMS`. **생략하면 `CERTIFICATE`(증권)** | |

```json
{
  "name": "오사카 3박 4일",
  "startDate": "2026-09-01",
  "endDate": "2026-09-04",
  "concerns": ["INJURY_OR_ILLNESS", "BAGGAGE_DAMAGE", "FLIGHT_DELAY"],
  "documentKind": "CERTIFICATE"
}
```

**201 Created** — 응답 헤더 `Location: /api/v1/trips/{tripId}`

`trip`과 `document`를 함께 돌려줍니다. 이어서 분석 시작 API를 호출할 때 두 식별자가 모두 필요합니다.

```json
{
  "trip": {
    "id": "8f2c1e0a-6a4b-4d5e-9f01-2b3c4d5e6f70",
    "name": "오사카 3박 4일",
    "startDate": "2026-09-01",
    "endDate": "2026-09-04",
    "status": "PLANNED",
    "concerns": ["INJURY_OR_ILLNESS", "BAGGAGE_DAMAGE", "FLIGHT_DELAY"],
    "createdAt": "2026-08-10T14:23:11.123456",
    "updatedAt": "2026-08-10T14:23:11.123456"
  },
  "document": {
    "id": "1a2b3c4d-5e6f-4071-8293-a4b5c6d7e8f9",
    "tripId": "8f2c1e0a-6a4b-4d5e-9f01-2b3c4d5e6f70",
    "originalFilename": "여행자보험_증권.pdf",
    "contentType": "application/pdf",
    "fileSize": 482913,
    "documentKind": "CERTIFICATE",
    "parseStatus": "UPLOADED",
    "uploadedAt": "2026-08-10T14:23:11.456789"
  },
  "analysis": {
    "id": "9c8b7a65-4321-4fed-8ba9-876543210fed",
    "documentId": "1a2b3c4d-5e6f-4071-8293-a4b5c6d7e8f9",
    "status": "PROCESSING",
    "summary": null,
    "failureReason": null,
    "startedAt": "2026-08-10T14:23:11.501234",
    "completedAt": null
  }
}
```

`analysis`는 **증권을 올렸을 때만** 채워집니다. `documentKind`를 `TERMS`로 지정해 약관을 올린 경우 `null`이며, 이때는 `POST .../analysis`로 분석을 직접 시작해야 합니다.

`name`은 서버에서 앞뒤 공백이 제거되어 저장됩니다. 생성 직후 `status`는 항상 `PLANNED`입니다.

**에러**: `INVALID_INPUT`(400), `INVALID_TRIP_PERIOD`(400), `EMPTY_POLICY_DOCUMENT_FILE`(400), `AUTHENTICATION_REQUIRED`(401), `USER_NOT_FOUND`(404), `POLICY_DOCUMENT_TOO_LARGE`(413), `POLICY_DOCUMENT_STORAGE_FAILED`(500)

> 📌 `multipart/form-data` 요청 시 **요청 전체의 `Content-Type` 헤더를 직접 지정하지 마세요.** boundary가 깨집니다. 단 `trip` 파트에는 `application/json`을 반드시 붙여야 합니다 — 안 붙이면 415입니다.

---

### 3.3 내 여행 목록

```
GET /api/v1/trips
```

**200 OK** — `TripResponse` 배열. **생성일 내림차순(최신순)**. 페이징 없음. 결과 없으면 `[]`.

**에러**: `AUTHENTICATION_REQUIRED`(401)

---

### 3.4 여행 단건 조회

```
GET /api/v1/trips/{tripId}
```

| 파라미터 | 타입 | 설명 |
| --- | --- | --- |
| `tripId` | UUID | 여행 ID |

**200 OK** — `TripResponse`

**에러**: `INVALID_INPUT`(400, UUID 형식 오류), `AUTHENTICATION_REQUIRED`(401), `TRIP_NOT_FOUND`(404)

---

### 3.5 여행 정보 수정

```
PATCH /api/v1/trips/{tripId}
```

> ⚠️ 메서드는 PATCH지만 **부분 수정이 아닙니다.** `name`, `startDate`, `endDate`를 **모두 보내야** 합니다(전부 필수). 일부만 보내면 `INVALID_INPUT`이 납니다.
> `status`는 이 API로 변경할 수 없습니다.

**Request Body** — 3.2 생성과 동일한 필드/제약

**200 OK** — `TripResponse` (`updatedAt` 갱신)

**에러**: `INVALID_INPUT`(400), `INVALID_TRIP_PERIOD`(400), `AUTHENTICATION_REQUIRED`(401), `TRIP_NOT_FOUND`(404)

---

### 3.6 보험 문서 추가 업로드

```
POST /api/v1/trips/{tripId}/documents
Content-Type: multipart/form-data
```

**Request Parts**

| 파트명 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `file` | binary | ✓ | 업로드할 PDF 파일 |
| `documentKind` | string | ✕ | `CERTIFICATE` \| `TERMS`. **생략하면 `CERTIFICATE`(증권)** |

- 파일 크기 제한: **Spring 기본값 — 파일당 1MB, 요청당 10MB** (별도 설정 없음). 초과 시 `POLICY_DOCUMENT_TOO_LARGE`(413).
- **확장자/MIME 타입 검증은 현재 없습니다.** 어떤 파일이든 업로드됩니다. 프론트에서 `accept="application/pdf"` 등으로 1차 제한을 걸어 주세요.
- 원본 파일명은 255자를 넘으면 뒤 255자만 저장되고, 비어 있으면 `"policy-document"`로 대체됩니다.
- 같은 파일을 여러 번 올리면 **매번 별개의 문서로 생성**됩니다(중복 검사 없음).
- `documentKind`가 `CERTIFICATE`면 **업로드 즉시 분석이 시작됩니다.** 응답 본문에는 분석 정보가 없으므로 `GET .../analysis`로 조회하세요.
- `TERMS`는 분석이 시작되지 않습니다. 주 용도가 이쪽입니다 — 증권 분석 결과에 필요한 약관을 DB에서 찾지 못해 사용자에게 받아올 때 씁니다.

**201 Created** — 응답 헤더 `Location: /api/v1/trips/{tripId}/documents/{id}`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `id` | UUID | 문서 ID |
| `tripId` | UUID | 소속 여행 ID |
| `originalFilename` | string | 업로드한 원본 파일명 |
| `contentType` | string\|null | 브라우저가 보낸 MIME 타입 |
| `fileSize` | number | 바이트 |
| `parseStatus` | enum | 업로드 직후 항상 `UPLOADED` |
| `uploadedAt` | datetime | 업로드 시각 |

```json
{
  "id": "1a2b3c4d-5e6f-4071-8293-a4b5c6d7e8f9",
  "tripId": "8f2c1e0a-6a4b-4d5e-9f01-2b3c4d5e6f70",
  "originalFilename": "여행자보험_증권.pdf",
  "contentType": "application/pdf",
  "fileSize": 482913,
  "parseStatus": "UPLOADED",
  "uploadedAt": "2026-08-10T14:31:02.004512"
}
```

**에러**: `EMPTY_POLICY_DOCUMENT_FILE`(400), `AUTHENTICATION_REQUIRED`(401), `TRIP_NOT_FOUND`(404), `POLICY_DOCUMENT_TOO_LARGE`(413), `POLICY_DOCUMENT_STORAGE_FAILED`(500)

> 📌 `multipart/form-data` 요청 시 `Content-Type` 헤더를 **직접 지정하지 마세요.** boundary가 깨집니다. `FormData`만 넘기면 브라우저가 알아서 설정합니다.

---

### 3.7 보험 문서 목록

```
GET /api/v1/trips/{tripId}/documents
```

**200 OK** — `PolicyDocumentResponse` 배열. **업로드 시각 내림차순(최신순)**. 페이징 없음.

**에러**: `AUTHENTICATION_REQUIRED`(401), `TRIP_NOT_FOUND`(404)

> 단건 조회(`GET /documents/{documentId}`)와 삭제 API는 **아직 없습니다.**

---

### 3.8 보험 문서 분석 시작 (약관용)

```
POST /api/v1/trips/{tripId}/documents/{documentId}/analysis
```

**요청 본문 없음.**

**멱등적입니다** — 같은 문서로 다시 호출하면 새 분석을 만들지 않고 기존 분석 작업을 그대로 돌려줍니다(항상 201).

**201 Created** — 응답 헤더 `Location: /api/v1/trips/{tripId}/documents/{documentId}/analysis`

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `id` | UUID | 분석 ID |
| `documentId` | UUID | 대상 문서 ID |
| `status` | enum | `PROCESSING` / `COMPLETED` / `FAILED`. 생성 직후 `PROCESSING` |
| `summary` | string\|null | 분석 요약. 완료 전에는 `null` |
| `failureReason` | string\|null | 실패 사유. `FAILED`가 아니면 `null` |
| `startedAt` | datetime | 분석 시작 시각 |
| `completedAt` | datetime\|null | 완료 시각. 완료 전에는 `null` |

```json
{
  "id": "c0ffee00-1111-4222-8333-444455556666",
  "documentId": "1a2b3c4d-5e6f-4071-8293-a4b5c6d7e8f9",
  "status": "PROCESSING",
  "summary": null,
  "failureReason": null,
  "startedAt": "2026-08-10T14:35:20.881003",
  "completedAt": null
}
```

**에러**: `AUTHENTICATION_REQUIRED`(401), `TRIP_NOT_FOUND`(404), `POLICY_DOCUMENT_NOT_FOUND`(404)

---

### 3.9 분석 상태/결과 조회

```
GET /api/v1/trips/{tripId}/documents/{documentId}/analysis
```

**200 OK** — 3.8과 동일한 `AnalysisResponse`

**에러**: `AUTHENTICATION_REQUIRED`(401), `TRIP_NOT_FOUND`(404), `POLICY_DOCUMENT_NOT_FOUND`(404), `ANALYSIS_RESULT_NOT_FOUND`(404)

> ⚠️ **분석을 시작하기 전에 조회하면 `ANALYSIS_RESULT_NOT_FOUND`(404)** 입니다. 반드시 POST를 먼저 호출하세요.
>
> **폴링 방식**: 분석 완료 알림(WebSocket/SSE)은 없습니다. POST 후 이 엔드포인트를 폴링(예: 3~5초 간격)하며 `status`가 `COMPLETED` 또는 `FAILED`가 될 때까지 기다리는 방식으로 구현하세요.

---

## 4. Enum 정의

| Enum | 값 | 비고 |
| --- | --- | --- |
| `TripStatus` | `PLANNED`, `ACTIVE`, `COMPLETED`, `CANCELLED` | 기본값 `PLANNED`. **변경 API 없음** |
| `DocumentParseStatus` | `UPLOADED`, `PROCESSING`, `COMPLETED`, `FAILED` | 기본값 `UPLOADED` |
| `AnalysisStatus` | `PROCESSING`, `COMPLETED`, `FAILED` | 기본값 `PROCESSING` |
| `CoverageStatus` | `COVERED`, `PARTIALLY_COVERED`, `NOT_COVERED`, `EXCLUDED` | 엔티티에만 존재, **응답 미노출** |
| `ExclusionConditionSeverity` | `GENERAL`, `WARNING`, `CRITICAL` | 엔티티에만 존재, **응답 미노출** |

---

## 5. 전형적인 플로우

```
1) 카카오 인가 코드 획득 (프론트)
   ↓
2) POST /api/auth/kakao/login          → accessToken 저장
   ↓
3) POST /api/v1/trips  (multipart: trip + 증권 file)
      → tripId, documentId, analysis.status = PROCESSING
      ※ 분석은 여기서 자동 시작. 분석 시작 API를 따로 부르지 않는다
   ↓
4) GET  .../documents/{documentId}/analysis 폴링  → COMPLETED / FAILED
   ↓
5) GET  .../documents/{documentId}/analysis/coverages  → 담보 목록 + 걱정 매칭

[약관이 추가로 필요한 경우 — 증권 분석 결과의 약관을 DB에서 못 찾았을 때]
   a) POST /api/v1/trips/{tripId}/documents  (file + documentKind=TERMS)  → documentId
   ↓
   b) POST .../documents/{documentId}/analysis   ← 약관은 이때 명시적으로 시작
   ↓
   c) GET  .../documents/{documentId}/analysis 폴링
```

---

## 6. 프론트엔드가 미리 알아둘 제약 (현재 구현 기준)

| 항목 | 현황 |
| --- | --- |
| 리프레시 토큰 | 없음. 401(`AUTHENTICATION_REQUIRED`) 수신 시 로그인 화면으로 보내야 함 |
| 로그아웃 API | 없음. 클라이언트에서 토큰 폐기로 처리 |
| 내 정보 조회(`/api/users/me`) | 없음. 사용자 정보가 필요하면 JWT payload의 `email`, `name`, `provider` 클레임을 디코딩해 사용 |
| 여행/문서 삭제 | 없음 |
| 문서 단건 조회·다운로드 | 없음 |
| 페이지네이션 | 목록 API 모두 전체 반환 |
| 분석 상세 결과(보장 항목·면책 조건) | 엔티티는 있으나 **응답 DTO에 미포함**. 현재 노출은 `summary` 문자열뿐 |
| 실제 분석 파이프라인 | 미구현. `status`는 `PROCESSING`에서 자동으로 변하지 않음 |
| 채팅 API | 미구현 |
| 파일 타입 검증 | 미구현 (서버가 모든 확장자 허용) |
