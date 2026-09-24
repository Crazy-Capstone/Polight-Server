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
- 리프레시 토큰: 로그인 응답의 `refreshToken`. 만료 기본 **14일**. `POST /api/auth/refresh`로 access token을 재발급합니다.
  - **재발급 때마다 리프레시 토큰도 새 값으로 바뀝니다(회전).** 응답에 실려 온 `refreshToken`으로 저장해 둔 값을 반드시 덮어쓰세요. 예전 값은 그 즉시 무효입니다.
  - 같은 리프레시 토큰으로 두 번 재발급하면 두 번째는 401(`INVALID_REFRESH_TOKEN`)입니다. 재발급 요청을 동시에 두 번 보내지 않도록 클라이언트에서 직렬화하세요.
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
| 1 | POST | `/api/auth/kakao/login` | ✕ | 카카오 로그인 → access + refresh 토큰 발급 |
| 2 | POST | `/api/auth/refresh` | ✕ | 리프레시 토큰으로 access token 재발급 (토큰 회전) |
| 3 | POST | `/api/auth/logout` | ✕ | 리프레시 토큰 무효화 |
| 4 | POST | `/api/v1/trips` | ✓ | 여행 세션 생성 + 보험 문서 업로드 (multipart) |
| 5 | GET | `/api/v1/trips` | ✓ | 내 여행 목록 |
| 6 | GET | `/api/v1/trips/{tripId}` | ✓ | 여행 단건 조회 |
| 7 | PATCH | `/api/v1/trips/{tripId}` | ✓ | 여행 정보 수정 |
| 8 | POST | `/api/v1/trips/{tripId}/documents` | ✓ | 보험 문서 추가 업로드 |
| 9 | GET | `/api/v1/trips/{tripId}/documents` | ✓ | 보험 문서 목록 |
| 10 | POST | `/api/v1/trips/{tripId}/documents/{documentId}/analysis` | ✓ | 분석 시작 |
| 11 | GET | `/api/v1/trips/{tripId}/documents/{documentId}/analysis` | ✓ | 분석 상태/결과 조회 |
| 12 | POST | `/api/v1/trips/{tripId}/chat/messages` | ✓ | 챗봇에 질문하기 |
| 13 | GET | `/api/v1/trips/{tripId}/chat/messages` | ✓ | 대화 이력 조회 |

> `/api/auth/refresh`와 `/api/auth/logout`이 인증 불필요인 이유: 둘 다 access token이 이미 만료된 상황에서 불리는 API입니다. 신원 확인은 요청 본문의 리프레시 토큰이 대신합니다.

> `/api/users` 컨트롤러는 클래스만 존재하고 **엔드포인트가 아직 없습니다.**
>
> `GET /api/v1/trips/{tripId}/documents/{documentId}/analysis/coverages`(보장 내역 조회)는 구현돼 있으나 이 문서에 아직 정리되지 않았습니다.

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
| `refreshToken` | string | access token 만료 시 3.2에 그대로 실어 보냄. **안전한 곳에 보관** |
| `expiresInSeconds` | number | access token 만료까지 남은 초 (기본 3600) |
| `nickname` | string | 사용자 닉네임. 카카오가 닉네임을 주지 않으면 `"카카오사용자"` |
| `profileImageUrl` | string \| null | 카카오 프로필 이미지 URL. 동의 항목 미동의/미설정이면 `null` |

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "9qL3kT7xR2mVb8nZ5wQ1yF6cH0dJ4sA-eG7uP2iO3rE",
  "expiresInSeconds": 3600,
  "nickname": "홍길동",
  "profileImageUrl": "https://k.kakaocdn.net/dn/abc/img_640x640.jpg"
}
```

**에러**: `INVALID_INPUT`(400), `KAKAO_TOKEN_REQUEST_FAILED`(401), `KAKAO_USER_INFO_REQUEST_FAILED`(401), `KAKAO_USER_ID_NOT_FOUND`(401)

> ⚠️ 인가 코드는 **일회용**입니다. 같은 코드로 재요청하면 `KAKAO_TOKEN_REQUEST_FAILED`가 납니다 (React StrictMode 이중 호출 주의).

---

### 3.2 토큰 재발급

```
POST /api/auth/refresh
```

인증 **불필요** (access token이 이미 만료된 상황에서 호출하는 API이므로).

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `refreshToken` | string | ✓ | NotBlank | 로그인(3.1) 또는 직전 재발급 응답에서 받은 값 |

```json
{ "refreshToken": "9qL3kT7xR2mVb8nZ5wQ1yF6cH0dJ4sA-eG7uP2iO3rE" }
```

**200 OK** — 3.1과 같은 형식입니다.

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `accessToken` | string | 새 access token |
| `refreshToken` | string | **새 리프레시 토큰.** 저장해 둔 값을 이 값으로 덮어쓰세요 |
| `expiresInSeconds` | number | 새 access token 만료까지 남은 초 |
| `nickname` | string | 사용자 닉네임 |
| `profileImageUrl` | `null` | **항상 null입니다.** 카카오에서만 오는 값이라 서버가 보관하지 않습니다. 로그인 때 받은 값을 클라이언트가 계속 들고 있어야 합니다 |

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "aB3xY9zQ1wE5rT7yU2iO4pA-sD6fG8hJ0kL",
  "expiresInSeconds": 3600,
  "nickname": "홍길동",
  "profileImageUrl": null
}
```

**에러**: `INVALID_INPUT`(400), `INVALID_REFRESH_TOKEN`(401), `USER_NOT_FOUND`(404)

> ⚠️ **리프레시 토큰은 일회용입니다.** 재발급에 성공하는 순간 보낸 토큰은 무효가 되고 새 토큰이 발급됩니다. 이미 쓴 토큰으로 다시 요청하면 `INVALID_REFRESH_TOKEN`(401)이고, 이때는 로그인 화면으로 보내야 합니다.
>
> 401을 받아 재발급하는 인터셉터를 만든다면, **동시에 여러 요청이 401을 받아도 재발급은 한 번만** 나가도록 묶어 주세요. 두 번 나가면 늦은 쪽이 무효 토큰으로 요청해 사용자가 이유 없이 로그아웃됩니다.

---

### 3.3 로그아웃

```
POST /api/auth/logout
```

인증 **불필요**.

**Request Body**

| 필드 | 타입 | 필수 | 제약 | 설명 |
| --- | --- | --- | --- | --- |
| `refreshToken` | string | ✓ | NotBlank | 무효화할 리프레시 토큰 |

**204 No Content** — 본문 없음. 이미 무효한 토큰을 보내도 204입니다(목적이 이미 달성된 상태이므로).

**에러**: `INVALID_INPUT`(400) — `refreshToken`이 비어 있을 때만.

> ⚠️ **이미 발급된 access token은 만료(기본 1시간) 전까지 계속 유효합니다.** 서버가 access token을 즉시 차단하지는 않으므로, 클라이언트도 저장해 둔 access token을 반드시 함께 지워야 합니다.

---

### 3.4 여행 세션 생성 + 보험 문서 업로드

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

### 3.5 내 여행 목록

```
GET /api/v1/trips
```

**200 OK** — `TripResponse` 배열. **생성일 내림차순(최신순)**. 페이징 없음. 결과 없으면 `[]`.

**에러**: `AUTHENTICATION_REQUIRED`(401)

---

### 3.6 여행 단건 조회

```
GET /api/v1/trips/{tripId}
```

| 파라미터 | 타입 | 설명 |
| --- | --- | --- |
| `tripId` | UUID | 여행 ID |

**200 OK** — `TripResponse`

**에러**: `INVALID_INPUT`(400, UUID 형식 오류), `AUTHENTICATION_REQUIRED`(401), `TRIP_NOT_FOUND`(404)

---

### 3.7 여행 정보 수정

```
PATCH /api/v1/trips/{tripId}
```

> ⚠️ 메서드는 PATCH지만 **부분 수정이 아닙니다.** `name`, `startDate`, `endDate`를 **모두 보내야** 합니다(전부 필수). 일부만 보내면 `INVALID_INPUT`이 납니다.
> `status`는 이 API로 변경할 수 없습니다.

**Request Body** — 3.4 생성과 동일한 필드/제약

**200 OK** — `TripResponse` (`updatedAt` 갱신)

**에러**: `INVALID_INPUT`(400), `INVALID_TRIP_PERIOD`(400), `AUTHENTICATION_REQUIRED`(401), `TRIP_NOT_FOUND`(404)

---

### 3.8 보험 문서 추가 업로드

```
POST /api/v1/trips/{tripId}/documents
Content-Type: multipart/form-data
```

**Request Parts**

| 파트명 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `file` | binary | ✓ | 업로드할 PDF 파일 |
| `documentKind` | string | ✕ | `CERTIFICATE` \| `TERMS`. **생략하면 `CERTIFICATE`(증권)** |

- 파일 크기 제한: **파일당 30MB, 요청당 35MB** (`application.yaml` 기본값. 배포 환경에서 `MULTIPART_MAX_FILE_SIZE` / `MULTIPART_MAX_REQUEST_SIZE`로 조정). 초과 시 `POLICY_DOCUMENT_TOO_LARGE`(413).
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

**에러**: `INVALID_INPUT`(400), `EMPTY_POLICY_DOCUMENT_FILE`(400), `AUTHENTICATION_REQUIRED`(401), `TRIP_NOT_FOUND`(404), `POLICY_DOCUMENT_TOO_LARGE`(413), `POLICY_DOCUMENT_STORAGE_FAILED`(500)

> 📌 `multipart/form-data` 요청 시 `Content-Type` 헤더를 **직접 지정하지 마세요.** boundary가 깨집니다. `FormData`만 넘기면 브라우저가 알아서 설정합니다.

---

### 3.9 보험 문서 목록

```
GET /api/v1/trips/{tripId}/documents
```

**200 OK** — `PolicyDocumentResponse` 배열. **업로드 시각 내림차순(최신순)**. 페이징 없음.

**에러**: `AUTHENTICATION_REQUIRED`(401), `TRIP_NOT_FOUND`(404)

> 단건 조회(`GET /documents/{documentId}`)와 삭제 API는 **아직 없습니다.**

---

### 3.10 보험 문서 분석 시작 (약관용)

```
POST /api/v1/trips/{tripId}/documents/{documentId}/analysis
```

**요청 본문 없음.**

**상태에 따라 동작이 다릅니다** (항상 201).

| 기존 분석 상태 | 동작 |
| --- | --- |
| 없음 | 새 분석을 만들고 AI 서버에 요청 |
| `PROCESSING` | 아무것도 하지 않고 진행 중인 분석을 그대로 반환 |
| `COMPLETED` | 아무것도 하지 않고 완료된 분석을 그대로 반환 |
| `FAILED` | **같은 분석을 다시 시작합니다.** `status`가 `PROCESSING`으로, `failureReason`이 `null`로 돌아갑니다. 단 아래 제약이 있습니다 |

> **분석이 실패했을 때 문서를 다시 업로드하지 마세요.** 원문 파일은 S3에 그대로 있으므로 같은 `documentId`로 이 API를 다시 호출하면 재시도됩니다. 재업로드는 쓰지 않는 문서 레코드만 늘립니다.
>
> ⚠️ **예외 — 재시도할 수 없는 경우가 있습니다.** 약관 분석이 색인(청킹·임베딩)까지 진행된 뒤 실패했다면 재시도가 `409 ANALYSIS_RETRY_NOT_SUPPORTED`로 거절됩니다. AI 서버가 이전 색인을 지우는 기능이 아직 없어, 재시도하면 매번 같은 제약 위반으로 실패하기 때문입니다. 이 응답을 받으면 **문서를 새로 업로드**하도록 안내하세요.
>
> 증권은 색인을 만들지 않으므로 이 제약에 걸리지 않습니다. 증권 재시도는 항상 가능합니다.

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

**에러**: `AUTHENTICATION_REQUIRED`(401), `TRIP_NOT_FOUND`(404), `POLICY_DOCUMENT_NOT_FOUND`(404), `ANALYSIS_RETRY_NOT_SUPPORTED`(409)

---

### 3.11 분석 상태/결과 조회

```
GET /api/v1/trips/{tripId}/documents/{documentId}/analysis
```

**200 OK** — 3.10과 동일한 `AnalysisResponse`

**에러**: `AUTHENTICATION_REQUIRED`(401), `TRIP_NOT_FOUND`(404), `POLICY_DOCUMENT_NOT_FOUND`(404), `ANALYSIS_RESULT_NOT_FOUND`(404)

> ⚠️ **분석을 시작하기 전에 조회하면 `ANALYSIS_RESULT_NOT_FOUND`(404)** 입니다. 반드시 POST를 먼저 호출하세요.
>
> **폴링 방식**: 분석 완료 알림(WebSocket/SSE)은 없습니다. POST 후 이 엔드포인트를 폴링(예: 3~5초 간격)하며 `status`가 `COMPLETED` 또는 `FAILED`가 될 때까지 기다리는 방식으로 구현하세요.
>
> **폴링은 반드시 끝납니다.** AI 서버가 콜백을 보내지 않아도 서버가 제한 시간(기본 10분, `ANALYSIS_TIMEOUT_AFTER`) 이 지난 분석을 `FAILED`로 내립니다. `failureReason`은 `AI 서버 응답 시간 초과 (10분)`입니다.
>
> 단, 이 보장은 서버의 타임아웃 처리가 켜져 있을 때만 성립합니다(`ANALYSIS_TIMEOUT_ENABLED`, 기본값 `true`). 껐다면 콜백이 오지 않는 분석은 `PROCESSING`에 그대로 남으므로, 그 환경을 대상으로 개발한다면 프론트엔드에도 자체 타임아웃이 필요합니다.
>
> `FAILED`를 받으면 사용자에게 재시도 버튼을 노출하고, 누르면 **3.10을 같은 `documentId`로 다시 호출**하세요.

---

### 3.12 챗봇에 질문하기

```
POST /api/v1/trips/{tripId}/chat/messages
Content-Type: application/json
```

여행에 올린 약관을 근거로 답변합니다. 질문과 답변을 **서버가 저장하므로 프론트가 대화 이력을 들고 있을 필요가 없습니다.**

**요청**

```json
{
  "question": "항공편이 지연되면 보상되나요?"
}
```

| 필드 | 타입 | 필수 | 비고 |
| --- | --- | --- | --- |
| `question` | string | ✓ | 1~2000자. 공백만 보내면 `INVALID_INPUT`(400) |

**200 OK**

```json
{
  "sessionId": "9f1c...",
  "messageId": "3ab7...",
  "answer": "4시간 이상 지연 시 지연비용 특약으로 보상됩니다. 다만 …",
  "responseType": "TEXT",
  "suggestedContacts": [],
  "sources": [
    {
      "chunkId": "11111111-…",
      "documentId": "22222222-…",
      "index": 1,
      "sectionTitle": "제3관 배상책임 특별약관",
      "clausePath": "제3관 > 제12조",
      "pageStart": 12,
      "pageEnd": 12,
      "clauseType": "COVERAGE",
      "quote": "항공기 지연으로 인하여 …",
      "text": "제12조(보상하는 손해) ① 회사는 피보험자가 해외여행 도중에 … (조항 전문)",
      "cited": true
    }
  ]
}
```

**에러**: `AUTHENTICATION_REQUIRED`(401), `INVALID_INPUT`(400), `TRIP_NOT_FOUND`(404), `AI_CHAT_REQUEST_FAILED`(502)

#### 프론트가 알아야 할 것

- **세션을 만들거나 고르는 호출이 없습니다.** 대화 세션은 여행당 하나이고, 첫 질문에 서버가 자동으로 만들어 이후 재사용합니다. 응답의 `sessionId`는 참고용이며 다음 요청에 실어 보내지 않아도 됩니다.
- **대화 이력을 보낼 필요가 없습니다.** 서버가 직전 6개(3턴)를 잘라 AI에 전달합니다. 화면에 이전 대화를 그릴 때는 3.13로 받아 오세요.
- **검색 범위는 여행 전체**입니다. 그 여행에 올린 약관이 모두 대상이며, 문서를 지정하는 파라미터는 없습니다.
- `responseType`은 **현재 항상 `TEXT`** 입니다. 병원 카드 같은 카드형 응답은 표시할 데이터 출처가 아직 없어 내려가지 않습니다.
- `suggestedContacts`는 **이 답변과 함께 띄우면 좋은 현지 연락처 종류**입니다. 번호가 아니라 종류만 옵니다 — 실제 번호는 프론트가 가진 연락처 화면에서 보여주세요.
  - 값은 `HOSPITAL`(부상·질병·치료) / `POLICE`(도난·분실·폭행) / `EMBASSY`(여권 분실, 체포·구금, 사망·실종) 중 **0개 이상**입니다. 단순 약관·보장 문의면 빈 배열입니다.
  - **여러 개가 올 수 있습니다.** "여권을 도난당했어요"는 `["POLICE", "EMBASSY"]` 입니다.
  - `responseType`은 이때도 **`TEXT` 그대로**입니다. 약관 답변과 연락처 안내가 함께 필요한 경우라, 카드로 바꾸면 보상 설명이 사라집니다. 답변 말풍선은 평소처럼 그리고 연락처는 **곁들여** 띄우세요.
  - 목록에 없는 값이 올 수 있습니다(AI가 종류를 늘리는 경우). **모르는 값은 무시**하세요 — 서버는 막지 않고 그대로 내려줍니다.
- `sources`는 답변의 근거가 된 약관 조항입니다. 빈 배열일 수 있습니다.
  - **`quote`와 `text`는 다릅니다.** `quote`는 AI가 고른 **짧은 발췌**로 말풍선 옆에 붙이는 용도이고, `text`는 그 조항의 **원문 전체**입니다. "약관 원문" 화면처럼 잘리지 않은 본문을 보여줄 때는 `text`를 쓰세요 — 수천 자일 수 있으니 스크롤을 두세요.
  - `index`는 답변 안에서 이 근거가 몇 번째인지입니다. 각주 번호를 붙일 때 씁니다.
  - `cited`는 이 근거가 답변 문장에 실제로 인용됐는지입니다. **`null`이면 "모름"이므로 걸러내지 말고 그대로 그리세요** — 이 필드가 붙기 전에 저장된 메시지는 `null`입니다.
  - `clauseType`은 조항의 성격입니다(`GENERAL` `COVERAGE` `EXCLUSION` `CONDITION` `LIMIT` `DEFINITION` `PROCEDURE` `REQUIRED_DOCUMENT`). 목록에 없는 값이 올 수 있으니 **모르는 값은 무시**하세요.
- `sectionTitle`·`clausePath`·`pageStart`·`pageEnd`·`clauseType`·`text`는 **비어 있을 수 있습니다.** 그때도 `quote`는 남습니다.
  - 이 값들은 서버가 `policy_terms_chunks`에서 채웁니다. 질의에 지목한 약관에 속한 청크를 찾지 못하면 AI가 함께 보낸 값으로 채우고, 그것도 없으면 빕니다. `clausePath`는 AI가 보내지 않으므로 이 경우 항상 빕니다.

> ⚠️ **응답까지 수 초 걸립니다.** 검색과 답변 생성을 기다리는 동기 호출이라 클라이언트 타임아웃을 넉넉히(60초 이상) 두세요. 완료 알림(WebSocket/SSE)은 없습니다.
>
> ⚠️ **502를 받아도 사용자가 보낸 질문은 서버에 저장돼 있습니다.** 화면에서 질문 말풍선을 지우지 말고, 재시도 버튼을 붙이는 쪽이 자연스럽습니다.
>
> ⚠️ **증권 분석이 끝나 있으면 답변 품질이 올라갑니다.** 가입 담보와 한도를 프롬프트에 함께 실어 보내기 때문입니다. 증권 분석이 없거나 진행 중이면 약관만 보고 답하므로, 가입하지 않은 담보를 물었을 때 "보상됩니다"라고 답할 수 있습니다.

---

### 3.13 대화 이력 조회

```
GET /api/v1/trips/{tripId}/chat/messages?limit=50
```

이 여행의 대화를 **오래된 것부터** 돌려줍니다. 화면에 위에서 아래로 그대로 그리면 됩니다.

| 쿼리 | 타입 | 필수 | 비고 |
| --- | --- | --- | --- |
| `limit` | int | ✕ | 최근 몇 개를 받을지. 기본 50, 최대 200. 범위를 벗어나면 서버가 맞춥니다 |

**200 OK**

```json
{
  "sessionId": "9f1c…",
  "messages": [
    {
      "messageId": "1aaa…",
      "sender": "USER",
      "content": "항공편이 지연되면 보상되나요?",
      "responseType": "TEXT",
      "suggestedContacts": [],
      "sources": [],
      "createdAt": "2026-08-21T14:14:02"
    },
    {
      "messageId": "2bbb…",
      "sender": "ASSISTANT",
      "content": "4시간 이상 지연 시 …",
      "responseType": "TEXT",
      "suggestedContacts": [],
      "sources": [
        {
          "chunkId": "11111111-…",
          "documentId": "22222222-…",
          "index": 1,
          "sectionTitle": "제3관 배상책임 특별약관",
          "clausePath": "제3관 > 제12조",
          "pageStart": 12,
          "pageEnd": 12,
          "clauseType": "COVERAGE",
          "quote": "항공기 지연으로 인하여 …",
          "text": "제12조(보상하는 손해) ① 회사는 피보험자가 해외여행 도중에 … (조항 전문)",
          "cited": true
        }
      ],
      "createdAt": "2026-08-21T14:14:09"
    }
  ]
}
```

**대화가 아직 없을 때 (200 OK)**

```json
{ "sessionId": null, "messages": [] }
```

**에러**: `AUTHENTICATION_REQUIRED`(401), `TRIP_NOT_FOUND`(404)

#### 프론트가 알아야 할 것

- **대화가 없어도 200입니다.** `sessionId`가 `null`, `messages`가 빈 배열로 옵니다. 여행을 만들고 챗봇을 아직 열지 않은 상태가 정상이라 오류로 두지 않았습니다.
- `sender`가 `USER`면 사용자 말풍선, `ASSISTANT`면 챗봇 말풍선입니다. `SYSTEM`은 현재 생성되지 않습니다.
- `messages[]` 항목은 **3.12 응답과 같은 모양**입니다(`messageId`·`content`·`responseType`·`sources`). 질문을 보낸 직후 화면에 붙이는 객체와 이력에서 받은 객체를 다르게 다룰 필요가 없습니다.
- `createdAt`은 말풍선 시각 표시용입니다. 타임존 없는 로컬 시각(서버 TZ = UTC)으로 내려갑니다.
- 사용자 메시지의 `sources`와 `suggestedContacts`는 **항상 빈 배열**입니다.
- 이 API가 붙기 전에 저장된 챗봇 메시지는 `suggestedContacts`가 빈 배열로 나옵니다. 그때 받은 연락처를 되살릴 방법은 없습니다.
- `sources`는 **답변받은 그 시점의 약관 본문 그대로** 저장해 되돌려줍니다. 약관이 개정돼도 과거 상담 기록에는 사용자가 그때 본 문장이 남습니다. 다만 `text`·`clauseType`·`index`·`cited`가 붙기 전에 저장된 메시지는 그 필드들이 `null`입니다.

> ⚠️ **더 오래된 대화를 이어서 받는 방법(커서 페이지네이션)은 아직 없습니다.** 세션이 여행당 하나이고 닫는 시점이 없어 대화가 계속 쌓이므로, 응답 크기를 막기 위해 최근 N개만 내려줍니다. "더 보기"가 필요해지면 그때 추가합니다.

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
2) POST /api/auth/kakao/login          → accessToken + refreshToken 저장
   ↓
3) POST /api/v1/trips  (multipart: trip + 증권 file)
      → tripId, documentId, analysis.status = PROCESSING
      ※ 분석은 여기서 자동 시작. 분석 시작 API를 따로 부르지 않는다
   ↓
4) GET  .../documents/{documentId}/analysis 폴링  → COMPLETED / FAILED
   ↓
5) GET  .../documents/{documentId}/analysis/coverages  → 담보 목록 + 걱정 매칭

[access token 만료 — 어느 단계에서든 401(AUTHENTICATION_REQUIRED)을 받았을 때]
   POST /api/auth/refresh  { refreshToken }
     → 200: accessToken/refreshToken 둘 다 새 값으로 교체하고 원래 요청 재시도
     → 401(INVALID_REFRESH_TOKEN): 저장된 토큰 모두 폐기하고 로그인 화면으로

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
| 리프레시 토큰 | **있음**(3.2). 401(`AUTHENTICATION_REQUIRED`) 수신 시 재발급을 먼저 시도하고, 그것도 401이면 로그인 화면으로 |
| 로그아웃 API | **있음**(3.3). 리프레시 토큰만 무효화하며, 이미 발급된 access token은 만료 전까지 유효하므로 클라이언트도 함께 폐기해야 함 |
| 다른 기기 로그아웃 | 없음. 기기별 리프레시 토큰이 각각 살아 있으며, 한 기기의 로그아웃이 다른 기기에 영향을 주지 않음 |
| 내 정보 조회(`/api/users/me`) | 없음. 사용자 정보가 필요하면 JWT payload의 `email`, `name`, `provider` 클레임을 디코딩해 사용 |
| 여행/문서 삭제 | 없음 |
| 문서 단건 조회·다운로드 | 없음 |
| 페이지네이션 | 목록 API 모두 전체 반환 |
| 분석 상세 결과(보장 항목·면책 조건) | `GET .../analysis/coverages` 로 제공. `GET .../analysis` 응답에는 `summary` 문자열만 들어감 |
| 실제 분석 파이프라인 | **구현됨.** 증권 업로드 → AI 서버 요청 → 콜백 수신까지 동작하며 `status`가 자동으로 전이함 |
| 분석 재시도 | `FAILED` 상태에서 3.10을 다시 호출하면 재시도됨. 재업로드 불필요 |
| 분석 타임아웃 | 제한 시간(기본 10분)을 넘긴 `PROCESSING` 분석은 서버가 `FAILED`로 내림 |
| 채팅 API | 미구현 |
| 파일 타입 검증 | 미구현 (서버가 모든 확장자 허용) |
