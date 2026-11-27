# 테스트 품질 검토 및 리팩터링 결과

검토일: 2026-11-27. 대상은 `src/test/java`의 테스트 클래스 55개와 대응하는 프로덕션 코드다. 테스트 보조 서버인 `AppleTestServer`는 별도로 확인했다. 기존 기능의 동작을 바꾸거나 테스트 기대값을 낮추지 않았으며, 프로덕션 코드·빌드 의존성·마이그레이션은 변경하지 않았다.

## 1. 판단 기준과 개선 순서

SUT가 검증할 규칙을 기준으로 입력·출력·도메인 상태·영속 상태·외부 부수 효과를 구분했다. 단위 테스트의 저장소 Stub으로 DB 잠금이나 롤백을 검증했다고 판단하지 않았다. API 인증과 역직렬화, DB 정합성은 기존 HTTP/JPA 테스트를 함께 확인했다.

[강남언니의 테스트 안정성 사례](https://blog.gangnamunni.com/post/how-we-improved-test-stability)는 테스트 대역이 Redis TTL 등의 실제 동작과 달라질 수 있다는 점을, [토스의 테스트 전략](https://toss.tech/article/test-strategy-server)은 핵심 정책·유스케이스·직렬화의 목적을 구분하고 실제 객체를 활용하는 접근을 참고했다. 이를 이 프로젝트의 구조와 기존 검증 범위에 맞게 적용했다.

| 순서 | 대상 | 개선 가치와 결정 |
| --- | --- | --- |
| 1 | Consent 서비스 | POST/PATCH의 잘못된 입력 검증이 비대칭이고, 부분 갱신을 일부 키와 `flush()` 호출로만 확인했다. 실제 User/Consent와 전체 상태 비교로 보강했다. 기존 HTTP·영속성·동시성 테스트는 유지했다. |
| 2 | Terms 로컬 매칭 | 기존 실제 도메인 객체 기반 테스트의 품질은 좋다. 시행일 전날·당일·다음날과 개정일 미상 후보가 섞인 경우만 추가했다. |
| 3 | Analysis 콜백 | 재수신 멱등성 테스트는 있으나, 기존 담보 삭제 후 새 담보를 일부 저장하다 실패하는 경로의 롤백 검증이 부족했다. 기존 HTTP/JPA 테스트에 해당 시나리오를 추가했다. |
| 3 | Chat 질의 | 순수 매퍼·세션까지 Mock으로 대체하고 전역 LENIENT로 불필요한 준비를 허용했다. 매퍼 호출 검증을 실제 전송 이력·저장 JSON 검증으로 바꿨다. |
| 4 | Trip 삭제 / User 탈퇴 | 실제 DB 그래프 삭제·롤백·커밋 후 파일 삭제·타 사용자 데이터 보존을 이미 검증한다. 중복 테스트를 추가하거나 구조를 전면 변경하지 않았다. PostgreSQL 기반 재검증을 후속 우선 과제로 남겼다. |

## 2. 파일별 검토 결과

아래 파일 경로는 `src/test/java/polight/server/` 기준이다. `유지`는 모든 위험이 검증되었다는 뜻이 아니라, 현재 검증 목적을 보존하고 이번 수정 범위에서 제외했다는 뜻이다. 정상·예외·경계 시나리오와 대역의 한계는 각 행과 후속 통합 테스트 항목에 함께 기록했다.

### Consent

| 파일 | SUT와 검증 규칙 | 판단 |
| --- | --- | --- |
| `domain/consent/service/ConsentServiceTest.java` | ConsentService: 최초 생성, 부분 병합, 잘못된 입력, 미인증·없는 사용자, 저장 실패 | 수정. 실제 User/Consent, 저장소만 Mock. DB 접근 금지는 요구사항이므로 상호작용 검증 유지. |
| `domain/consent/ConsentIntegrationTest.java` | 서비스 + JPA: 미수집 null, POST/PATCH 병합, 동일 값 재전송 시 수정 시각 유지, 이력 보존, 삭제 cascade, UNIQUE, 동시 생성·갱신, 롤백 | 유지. H2에서 실제 트랜잭션을 실행한다. PostgreSQL의 잠금·마이그레이션 보장은 별도 필요. |
| `domain/consent/ConsentHttpTest.java` | 인증된 소유자 범위, POST 201/200, PATCH, JSON 오류, 계정 자동 가입과 동의의 독립성, Swagger | 유지. OAuth/refresh만 대역으로 격리. 서비스 테스트와의 중복은 인증·JSON·DB 경계를 확인하는 목적이 있다. |

### Terms

| 파일 | SUT와 검증 규칙 | 판단 |
| --- | --- | --- |
| `domain/terms/service/InsuranceNameNormalizerTest.java` | 법인격·공백·구두점·대소문자 정규화, 다른 상품·빈 값 오매칭 방지 | 유지. 외부 의존성이 없는 실제 함수 테스트다. |
| `domain/terms/service/LocalTermsMatcherTest.java` | 정확 매칭, 개정판 기준일, 보험 시작일 우선, 미매칭, 사유·안내, 소유권, 공용 약관 우선 | 경계 시나리오만 추가. 후보 목록만 저장소 Stub으로 공급하고 실제 도메인 정책을 실행한다. |
| `domain/terms/service/AiTermsMatcherTest.java` | AI 약관 ID 반영, 미매칭과 장애 구분, 증권 이외 분석 제외 | 유지. AI 판단은 대역으로 격리. 없는 약관 ID의 FK 실패는 실제 DB로 추가 검증해야 한다. |
| `domain/terms/service/PolicyTermsMatchingServiceTest.java` | 약관·단계·안내 반영, 미매칭 시 연결 해제, 판단 실패 시 이전 연결 보존 | 유지. 매칭 정책과 결과 반영 책임을 분리한 적절한 테스트다. 직접 호출 테스트가 실제 트랜잭션 롤백까지 입증하지는 않는다. |
| `domain/terms/service/CoverageTermsLinkerTest.java` | 명칭·수식·분류에 따른 담보 규칙 연결, 모호한 후보 거부, 이전 연결 해제 | 유지. 실제 담보/규칙의 연결 상태와 요약을 확인한다. 각 정책의 목적이 구체적이다. |
| `domain/terms/service/AnalysisTermsRelinkerTest.java` | 기존 연결 유지·재매칭·분석 없음, 담보 재연결 | 후속 개선. 호출 중심 검증과 공통 lenient Stub이 있다. REQUIRES_NEW와 실제 연결 상태 검증은 통합 테스트가 필요하다. |
| `domain/terms/service/TermsBackfillServiceTest.java` | 대상 모드, 미매칭/실패 구분, 한 건 실패 후 계속 진행, 집계 | 유지. 정책 분기는 명확하다. Mock의 예외만으로 건별 트랜잭션 격리를 보장하지 않는다. |
| `domain/terms/entity/PolicyTermsTest.java` | 증권의 약관 등록 금지, 미검증 소유권, 공용 사용, 승격 후 출처 보존, 필수 이름 | 유지. 실제 엔티티를 사용하며 상태와 예외를 검증한다. |
| `domain/terms/entity/PolicyTermsCoverageSourceTest.java` | 같은 약관의 청크만 근거로 연결, 기본 역할 | 유지. 잘못된 근거 연결을 실제 생성자로 차단하는 정책 테스트다. |
| `domain/terms/client/FastApiTermsMatchClientTest.java` | 응답 역직렬화, null ID/404 미매칭, 서버 장애 구분, 새 필드 호환성 | 유지. 실제 RestClient + HTTP 응답 Stub. 실제 소켓 타임아웃·요청 계약은 추가 여지가 있다. |

### Analysis / Chat

| 파일 | SUT와 검증 규칙 | 판단 |
| --- | --- | --- |
| `domain/analysis/CoverageDetailHttpTest.java` | 콜백 HTTP + DB + 보장 조회: 지급 유형, 이전 형식 호환, 재수신, 공용 약관 보존, 소유권 | 수정. 보장 상태별 가입 여부, 중간 실패 롤백, 실패 후 담보 보존과 조회 결과를 추가했다. |
| `domain/analysis/service/AnalysisResultServiceTest.java` | 분석 생성·재시도·기존 결과 초기화, 처리 중/완료 시 중복 요청 방지, 청크가 남은 재시도 거부 | 유지. 실제 결과 상태와 발행 이벤트를 확인한다. 이벤트 발행 자체는 외부 작업 시작 계약이라 verify가 타당하다. 행 잠금은 별도 DB 검증 대상이다. |
| `domain/analysis/service/CoverageAnalysisServiceTest.java` | 증권 가입 정보와 약관 규칙 결합, 공유 면책, 규칙 없는 경우 빈 결과, 불필요 조회 방지 | 후속 개선. 결과 검증은 좋지만 전역 LENIENT와 공통 빈 자식 Stub은 정리할 여지가 있다. |
| `domain/analysis/service/StaleAnalysisTimeoutServiceTest.java` | 타임아웃 상태 전이, 이미 완료된 분석 보호, 집계, 제한 시간 설정 | 유지. 실제 엔티티 상태와 시각 범위를 확인한다. 조회 결과 Stub은 PostgreSQL의 잠금 후 조건 재평가를 재현하지 않는다. |
| `domain/analysis/event/AnalysisRequestEventListenerTest.java` | 처리 시점의 다운로드 URL, AI 요청 식별자, 요청 준비 실패 시 실패 처리 | 유지. 네트워크를 대역으로 격리. 메서드 직접 호출은 AFTER_COMMIT 발동 조건까지 검증하지 않는다. |
| `domain/analysis/event/AnalysisTermsLinkEventListenerTest.java` | 완료 후 재매칭, 연결 실패가 이미 완료된 분석을 실패 응답으로 바꾸지 않음 | 유지. 예외 격리 목적은 명확하다. 별도 트랜잭션의 실제 커밋 결과는 후속 검증 필요. |
| `domain/analysis/client/FastApiAnalysisClientTest.java` | 5xx 재시도, 400 비재시도, 최종 실패 | 유지. HTTP 응답 Stub과 0 지연으로 빠르고 독립적이다. 429·연결 실패·요청 본문/헤더 계약은 추가 여지가 있다. |
| `domain/chat/service/ChatQueryServiceTest.java` | 사용자/여행 범위, AI 전송 이력, 질문 선저장, 상태별 질의, 근거 범위, 연락처·청구 안내, AI 실패 | 수정. 매퍼/세션 Mock과 전역 LENIENT를 없애고 실제 요청·메타데이터를 검증한다. |
| `domain/chat/service/ChatHistoryServiceTest.java` | 빈 이력, 세션/메시지, 기본·상하한 limit | 후속 개선. 매퍼 Mock으로 만든 응답을 재검증하고 limit는 호출 횟수 중심이다. 실제 매퍼와 결과 순서·내용 중심으로 정리할 수 있다. |
| `domain/chat/service/CertificateContextProviderTest.java` | 증권 없음, 문서/분석 상태 우선순위, 완료 시 약관·담보·완전성 전달 | 유지. 저장소만 대역으로 격리하며 실제 결과 객체를 확인한다. 최신 증권 SQL 정렬은 HTTP 테스트가 보완한다. |
| `domain/chat/mapper/ChatMessageMapperTest.java` | 이력 시간순 변환, 근거 정보 우선순위, 메타데이터 왕복·구버전·손상 호환성 | 유지. 실제 매퍼의 순수 변환을 검증한다. 직렬화 실패를 유발하는 ObjectMapper 대역은 실패 경로 검증에 적절하다. |
| `domain/chat/client/FastApiRagQueryClientTest.java` | AI 요청 직렬화, 상태·미등록 범위, 응답 호환성, 연결 실패 재시도와 5xx 비재시도 | 유지. 실제 RestClient/직렬화와 HTTP 응답 Stub을 함께 쓴다. |
| `domain/chat/ChatMetadataHttpTest.java` | 로컬 AI 서버 + 실제 HTTP/JPA: 응답·저장·이력 일치, 최신 증권 선택, 사용자 범위, AI 실패 시 질문 보존 | 유지. 고가치 유스케이스/계약 테스트다. 정적 서버 상태를 사용하므로 병렬 실행 전 격리가 필요하다. |
| `domain/chat/UnregisteredChatHttpTest.java` | 미등록 상담 세션, 인증·입력·사용자 범위, 여행 등록 후 전환, 이력 제한, 동시 최초 질문, 탈퇴 정리 | 유지. AI만 대역으로 격리하고 DB와 응답을 검증한다. 부분 UNIQUE와 경합은 PostgreSQL에서도 확인해야 한다. |

### Trip / User

| 파일 | SUT와 검증 규칙 | 판단 |
| --- | --- | --- |
| `domain/trip/TripCountriesTest.java` | 국가 코드 검증, 중복 제거·순서 보존, 이전 JSON 호환, DTO 변환 | 유지. 실제 엔티티·매퍼·Validator를 사용한다. |
| `domain/trip/TripUpdateTest.java` | null/생략은 국가 유지, 빈 목록은 삭제, 다른 필드 보존, 입력 검증 | 유지. 생성 규칙 테스트와 달리 갱신 의미를 검증한다. |
| `domain/trip/TripCreationHttpTest.java` | 여행/문서/분석 동시 생성, 잘못된 국가·기간·빈 파일 거부, 인증 | 유지. DB와 실제 임시 파일 상태를 확인한다. S3 저장 후 DB 실패의 객체 잔존은 별도 한계다. |
| `domain/trip/TripQueryHttpTest.java` | 국가 순서, 소유 여행만 목록/단건 조회, 401/404, API 스키마 | 유지. 실제 HTTP 응답과 사용자 범위를 확인한다. |
| `domain/trip/TripUpdateHttpTest.java` | 부분 갱신 의미, 국가·기간 경계, 실패 시 원래 값 보존, 타 사용자 거부 | 유지. 실제 DB 재조회와 HTTP 결과를 함께 확인한다. |
| `domain/trip/TripDeletionIntegrationTest.java` | 연관 그래프·공용 데이터 분리 삭제, 커밋 후 파일 삭제, 롤백, 늦은 콜백, 업로드/삭제 경합 | 유지. 서비스·JPA·트랜잭션은 실제 구현, 저장소 실패만 대역으로 제어한다. H2 잠금 및 150ms 대기 판정의 한계는 남는다. |
| `domain/trip/TripDeletionHttpTest.java` | 204/400/401/404, 소유권, DB 실패 롤백, 파일 일부 실패, 삭제 후 콜백 | 유지. 실제 HTTP·임시 파일의 삭제 결과를 확인한다. 통합 테스트와 경계가 다르므로 삭제하지 않았다. |
| `domain/user/service/UserWithdrawalServiceTest.java` | 사용자 그래프 삭제, 다른 사용자·공식 약관 보호, DB 롤백, 커밋 후 파일 정리, Redis 실패 시 중단 | 유지. 이름은 ServiceTest지만 실제 JPA 통합 테스트다. Redis/S3 대역의 실패와 DB 상태를 구분한다. |
| `domain/user/UserWithdrawalHttpTest.java` | 탈퇴 HTTP, DB·실제 파일 정리, 다른 사용자 보존, 토큰 재사용 거부 | 유지. 서비스 Mock만으로 대체하면 잃는 유스케이스 보장이 있다. |
| `domain/user/controller/UserControllerTest.java` | 인증된 사용자 탈퇴 위임, 미인증·삭제 사용자 거부 | 유지. 얇은 컨트롤러/보안 경계이므로 서비스 verify가 적절하다. |

### Auth / Security / Storage / 기동

| 파일 | SUT와 검증 규칙 | 판단 |
| --- | --- | --- |
| `domain/auth/AppleLoginIntegrationTest.java` | 로컬 Apple 서버·실제 암호화/JPA: 계정 생성/갱신, nonce·코드 재사용 차단, 경합, 탈퇴 후 폐기 | 유지. 실제 OAuth 제공자 대신 로컬 서버 사용이 적절하다. refresh 저장은 대역이므로 Redis 원자성은 보장하지 않는다. |
| `domain/auth/client/AppleOAuthClientTest.java` | 코드 교환 폼·서명·비재시도, 오류 분류, 반복 폐기 | 유지. 로컬 서버로 실제 HTTP 계약을 검증한다. |
| `domain/auth/client/KakaoOAuthClientTest.java` | 토큰 교환 폼, 사용자 정보 Bearer/역직렬화 | 유지. MockRestServiceServer가 네트워크를 대체한다. 전체 form 문자열 비교는 파라미터 순서 변경에 민감하며 오류 응답 테스트는 부족하다. |
| `domain/auth/controller/AuthControllerTest.java` | 로그인·재발급·로그아웃의 HTTP 입력/출력과 오류 | 유지. `@MockBean` 폐기 예정 경고가 있어 별도 정리 대상이다. 비즈니스 정책은 서비스/통합 테스트가 담당한다. |
| `domain/auth/service/AuthServiceTest.java` | 기존 계정 로그인, 프로필 기본값, 서비스 토큰 발급·회전·폐기 | 유지. OAuth/저장 서비스를 대역으로 격리한다. provider 조회 호출 검증은 통합 계정 재사용 결과 검증으로 더 단순화할 수 있다. |
| `domain/auth/service/AppleAuthServiceTest.java` | 잘못된 신원/코드/설정 시 부수 효과 차단, 중복 계정 재조회, 암호화 실패, 해시 refresh 저장 | 유지. 보안상 호출 금지는 타당하다. 전역 LENIENT/공통 성공 Stub은 후속 정리 대상이다. |
| `domain/auth/service/AppleSecurityTest.java` | JWT 서명·claim·nonce, JWKS 회전, client secret, 암호화 무작위성·변조·주체 결합, 민감 값 비노출 | 유지. 실제 암호화와 로컬 서버를 활용한다. 별도 외부 Apple 서비스에 의존할 필요가 없다. |
| `domain/auth/service/RefreshTokenStoreTest.java` | 해시 키·TTL 인자, GETDEL 사용, 토큰 회전·폐기·사용자 범위 | 후속 인프라 우선순위 높음. Redis Mock 검증은 만료와 동시 한 번 소비를 입증하지 못한다. |
| `global/security/JwtAuthenticationFilterTest.java` | 탈퇴 사용자 인증 거부, 기존 사용자 principal, SecurityContext 정리 | 유지. 필터 경계를 격리하고 관찰 가능한 인증 상태를 확인한다. |
| `global/security/JwtConfigurationTest.java` | 대문자 환경값 매핑, 직접 설정 우선, 빈 비밀키로 기동 실패 | 유지. 실제 설정 바인딩과 기동 실패를 검증한다. |
| `global/security/JwtTokenProviderTest.java` | 실제 서명 토큰의 사용자 ID 왕복, 잘못된 토큰 거부 | 유지. 잘못된 토큰 테스트의 포괄적 Exception 검증과 만료/다른 키 경계는 후속 개선 대상이다. |
| `domain/insurance/event/DocumentDeletionListenerTest.java` | 모든 경로 삭제 시도, 일부 실패 후 계속 진행, 이벤트 경로 복사 | 유지. 저장소 삭제는 외부 부수 효과이므로 verify가 요구사항에 맞다. 실패의 수동 정리 로그도 확인한다. |
| `domain/insurance/storage/LocalPolicyDocumentStorageTest.java` | 실제 파일 저장·고유 경로·디렉터리 생성·반복 삭제·경로 범위 거부 | 유지. 임시 디렉터리에서 실제 파일시스템을 검증한다. |
| `domain/insurance/storage/S3PolicyDocumentStorageTest.java` | bucket/key/prefix, 저장 실패 변환, 입력 스트림 실패 | 유지. SDK 요청·예외 처리 범위다. 실제 업로드 바이트·endpoint·저장소 동작은 통합 테스트가 필요하다. |
| `domain/insurance/storage/S3PolicyDocumentUrlProviderTest.java` | 실제 SDK presigner로 key·서명·만료 인자 생성, 빈 key 거부 | 유지. 고정 테스트 자격증명으로 오프라인 서명한다. 실제 다운로드 가능성은 검증하지 않는다. |
| `domain/rag/entity/VectorColumnMappingTest.java` | 미지원 float[] 벡터 매핑 재도입 방지 | 유지. 의도적인 구조 회귀 방지다. pgvector 행의 실제 조회 검증을 대신하지는 못한다. |
| `AiTermsMatchingContextTest.java` | provider=ai일 때 조건부 빈 선택과 기동 | 유지. 실제 외부 AI 응답 검증과 목적이 다르다. 로컬 .env와 스케줄러 영향을 더 명시적으로 격리할 수 있다. |
| `PolightServerApplicationTests.java` | 기본 Spring 컨텍스트 기동 | 유지. assert가 없는 것은 smoke test 목적상 문제가 아니다. .env·storage·scheduler 설정을 테스트 전용으로 명시하면 이식성이 좋아진다. |

## 3. 실제 수정과 기술적 근거

### ConsentServiceTest

- 도메인 `User`의 Mock 4곳을 실제 User로 대체하고 MockitoExtension의 기본 strict stubbing을 사용한다. Repository만 격리하므로 검증 대상은 입력 검증과 상태 병합이다.
- 잘못된 요청을 공통 파라미터로 정리했다. null 요청, null/빈 맵, null 키, 명시적 null 값을 POST와 PATCH 모두에 적용한다. 이전에는 POST의 null 키와 PATCH의 null 값이 직접 서비스 테스트에서 빠져 있었다.
- 응답의 일부 키만 검사하던 테스트를 전체 7개 상태 비교로 보강했다. false와 미수집 null을 구분하고 기존 true 값과 생략된 항목이 보존되는지 확인한다.
- `flush()` 호출 확인을 제거하고 실제 Consent 상태를 확인한다. 영속화는 기존 통합 테스트가 담당한다. 미인증·잘못된 입력 시 DB 접근 금지 검증은 유지했다.
- 모든 ConsentType의 단일 PATCH와 없는 사용자에 대한 POST/PATCH 오류를 추가했다. 기존 저장 예외 전파와 미인증 검증은 유지했다.

### LocalTermsMatcherTest

- 기존 한글 메서드명·헬퍼·실제 도메인 객체 구조를 유지한다.
- 보험 시작일이 시행일 전날이면 이전 개정판, 시행일 당일/다음날이면 새 개정판을 선택하는 경계를 추가했다. 여행일을 다른 개정판 적용 시점으로 두어 보험 시작일 우선 규칙도 함께 보호한다.
- 후보 반환 순서를 최신순으로 배치해 저장소 순서에 의존하지 않는 선택을 확인했다. 날짜가 없는 후보가 섞여 있어도 날짜가 확인된 유효 개정판을 선택하는 경우를 추가했다.
- 날짜가 같은 두 개정판 중 무엇을 고를지와 같은 미정의 규칙을 임의로 추가하지 않았다.

### CoverageDetailHttpTest

- 기존 재수신 멱등성·공용 약관 보존·인증·소유권 검증을 유지했다.
- COVERED/PARTIALLY_COVERED는 가입 여부 true, NOT_COVERED/EXCLUDED는 false인지 저장 상태와 HTTP 결과를 확인한다. 부분 보장을 미가입으로 오인하지 않도록 한다.
- 먼저 정상 콜백으로 담보를 저장한 뒤, 재수신의 첫 담보는 유효하고 두 번째 담보는 제목 누락/공백/잘못된 상태인 경우를 검증한다. HTTP 400뿐 아니라 기존 담보 ID·제목·금액, 분석 요약·원문·완료/수정 시각, 문서 완료 상태가 유지되는지 확인한다. 실제 삭제·INSERT가 수행된 후 실패해야 하므로 Repository Mock 대신 기존 Spring/JPA 경로를 이용한다.
- 실패 콜백은 분석/문서를 FAILED로 바꾸고 이미 저장된 담보는 보존하는 현재 구현을 검증한다. 동시에 보장 조회가 실패 상태에서 빈 담보·걱정 판정 목록을 반환하는지 확인한다. 완료 후 실패 콜백을 무시한다는 새로운 정책은 가정하지 않았다.

### ChatQueryServiceTest

- 순수 `ChatMessageMapper`와 `ChatSession` Mock을 실제 객체로 대체했다. 전역 LENIENT를 제거하고 성공 경로의 준비를 필요한 시나리오에서만 호출한다.
- 답변 저장 Stub은 입력받은 답변과 메타데이터를 반환한다. 이전 고정 답변 Stub은 AI 응답과 다른 내용을 반환해도 일부 테스트가 통과할 수 있었다. 이 Stub은 DB 저장을 재현하는 Fake가 아니며, 실제 커밋은 기존 HTTP 테스트의 책임이다.
- 이력 조회/질문 저장의 호출 순서 검증을 실제 AI 요청 데이터 검증으로 바꿨다. 새 질문 저장 시 조회 가능한 목록이 바뀌도록 대역을 구성하고, 전송 이력에는 이전 질문/답변만 시간순으로 남는지 확인한다.
- 매퍼의 `toMetadataJson()` 호출 여부 대신 저장 서비스에 전달한 JSON을 역직렬화해 추천 연락처, 공백을 포함한 청구 안내, URL, 음수가 아닌 소요 시간을 확인한다. 근거 청크가 없을 때도 실제 매퍼가 인용문을 보존하는지 확인한다.
- AI 질의와 저장 서비스는 외부/트랜잭션 경계이므로 대역을 유지했다. 질문 선저장과 실패 시 답변 저장 금지, 약관 범위 제한 같은 요구사항은 상호작용 검증도 유지했다.

영문 이름이 명확한 수정 테스트에는 한글 `@DisplayName`을 추가했다. 이미 한글 이름인 Terms는 그대로 유지했다. 준비·행위·검증은 공백으로 구분하고 단순 테스트에 GWT 주석을 일괄 추가하지 않았다.

## 4. 실행과 회귀 결과

Java 17과 저장소의 Gradle Wrapper를 사용했다. 최초 샌드박스 실행은 사용자 Gradle 캐시의 잠금 파일 쓰기 제한으로 시작하지 못했다. 허용된 실행으로 재시도했다. 최초 `test`는 UP-TO-DATE였으므로 기준 결과로 사용하지 않고 `--rerun-tasks`로 실제 재실행했다.

각 파일을 수정한 뒤 해당 테스트, 관련 테스트, 전체 테스트 순으로 실행했다. 모든 수치는 JUnit XML의 실행 사례 수이며 파라미터화된 테스트의 각 입력을 포함한다. 이 수치를 품질 지표로 해석하지 않는다.

| 단계 | 실행 명령 | 실제 결과 |
| --- | --- | --- |
| 변경 전 기준 | `./gradlew test --rerun-tasks --console=plain` | 495개 성공, 실패/오류/스킵 0 |
| Consent 수정 후 관련 전체 | `./gradlew test --tests 'polight.server.domain.consent.*' --console=plain` | 49개 성공, 실패/오류/스킵 0 |
| Consent 후 전체 | `./gradlew test --console=plain` | 506개 성공, 실패/오류/스킵 0 |
| Terms 수정 후 관련 전체 | `./gradlew test --tests 'polight.server.domain.terms.*' --console=plain` | 81개 성공, 실패/오류/스킵 0 |
| Terms 후 전체 | `./gradlew test --console=plain` | 510개 성공, 실패/오류/스킵 0 |
| 콜백 수정 파일 | `./gradlew test --tests 'polight.server.domain.analysis.CoverageDetailHttpTest' --console=plain` | 22개 성공, 실패/오류/스킵 0 |
| Analysis/Chat/여행 삭제 관련 | `./gradlew test --tests 'polight.server.domain.analysis.*' --tests 'polight.server.domain.chat.*' --tests 'polight.server.domain.trip.TripDeletion*' --console=plain` | 177개 성공, 실패/오류/스킵 0 |
| 콜백 후 전체 | `./gradlew test --console=plain` | 518개 성공, 실패/오류/스킵 0 |
| Chat 수정 파일 | `./gradlew test --tests 'polight.server.domain.chat.service.ChatQueryServiceTest' --console=plain` | 18개 성공, 실패/오류/스킵 0 |
| Chat 관련 전체 | `./gradlew test --tests 'polight.server.domain.chat.*' --console=plain` | 107개 성공, 실패/오류/스킵 0 |
| 최종 전체 재실행 | `./gradlew test --rerun-tasks --console=plain` | 55개 클래스, 518개 성공, 실패/오류/스킵 0. BUILD SUCCESSFUL, 40초 |

최종 HTML 보고서는 `build/reports/tests/test/index.html`, 원본 실행 결과는 `build/test-results/test/TEST-*.xml`이다. 기본 빌드에서 테스트를 skip하지 않았다. 기존 `@MockBean` 3곳과 S3 SDK 사용에 관한 폐기 예정 경고는 테스트 실패와 구분했다.

## 5. 아직 필요한 통합 테스트와 권장 순서

1. **독립적인 PostgreSQL/pgvector 테스트 환경**: 기존 compose는 영속 볼륨을 쓰는 개발 환경이고 테스트 전용 Testcontainers 설정은 없다. 이를 테스트 DB로 자동 사용하지 않았다. 기존 `POLIGHT_*_TEST_DB_*` 설정 경로를 활용하거나, 별도 Gradle 태스크에 일회용 pgvector PostgreSQL 컨테이너를 연결하는 방식을 권장한다. Flyway를 켜고 Hibernate는 validate로 두어 실제 마이그레이션의 UNIQUE/FK/CHECK/부분 인덱스를 검증한다. H2의 PostgreSQL 모드는 대체 보장이 아니다.
2. **PostgreSQL 동시성**: Consent 최초 POST/POST+PATCH, Trip 삭제/콜백/업로드, 미등록 Chat 세션 생성, 분석 완료/타임아웃을 실제 행 잠금과 커밋 경계에서 재검증한다. 기존 CountDownLatch로 시작 시점을 맞추는 테스트가 항상 충돌 시점을 보장하지는 않는다. 150ms Future 대기 판정도 스레드 스케줄링만으로 통과할 수 있으므로 잠금 진입을 관찰하는 동기화가 필요하다.
3. **Redis TTL과 원자적 소비**: 일회용 Redis에 실제 RefreshTokenStore를 연결해 TTL 범위·만료 후 거부, 같은 토큰의 동시 소비 중 정확히 한 번 성공, revoke/revokeAll의 사용자 범위를 검증한다. 현재 Mock의 GETDEL 호출/TTL 인자는 이 동작을 입증하지 못한다. 음수/0 TTL의 정책은 현재 명세를 확인한 뒤 정해야 한다.
4. **커밋 후 약관 연결 실패 격리**: 실제 Spring 이벤트 + REQUIRES_NEW 경계에서 약관 FK 오류나 매칭 장애를 유발하고, 이미 완료된 분석/담보는 남고 실패한 연결만 롤백되는지 확인한다. 백필에서도 한 건의 롤백이 앞뒤 건의 커밋을 취소하지 않는지 확인한다.
5. **S3 어댑터 계약**: 운영 AWS 대신 LocalStack 또는 로컬 S3 호환 저장소에서 업로드 바이트·Content-Type·key 범위·반복 삭제를 확인한다. 에뮬레이터가 실제 IAM·서명·네트워크 정책을 완전히 대체하지는 않으므로 검증 범위를 명시한다. 실제 AWS/운영 데이터에 접근하는 테스트는 추가하지 않았다.
6. **테스트 환경 및 CI**: 현재 배포 워크플로의 Dockerfile은 `bootJar`를 수행하며 테스트를 실행하는 독립 단계가 없다. PR용 테스트 job에서 빠른 테스트와 인프라 테스트를 분리해 실행하는 것이 다음 개선이다. `.env`/스케줄러 격리와 정적 로컬 서버 상태의 초기화도 병렬 실행 도입 전에 정리한다.
7. **추가 리팩터링**: ChatHistory의 실제 매퍼 사용, CoverageAnalysis/AppleAuth/RefreshTokenStore의 전역 LENIENT 정리, Kakao form의 의미 기반 비교와 실패 응답 보강을 작은 변경으로 진행한다. 이미 상태를 잘 검증하는 Terms 정책·Trip/User 유스케이스 테스트를 일괄 재작성하거나 공통 거대 Fixture로 숨기지 않는다.

동일 규칙을 단위·통합·HTTP에서 검증하더라도 경계 목적이 다르면 유지했다. 이번에는 새로운 커버리지 목표나 테스트 라이브러리를 도입하지 않고, 놓치기 쉬운 상태 보존·롤백·날짜 경계와 실제 변환 결과의 검증력을 높이는 데 집중했다.
