---
id: T-073
owner: codex
status: done    # todo | doing | blocked | done
scope:
  - backend/src/test/java/kr/needteacher/account/**
  - docs/tasks/T-073-auth-acceptance.md
---

> **계약 v2 (확정).** v1 → Codex 리뷰(T-074: P0 4·P1 5) · agy 리뷰(T-075: P0 1·P1 3·P2 1) → v2 → agy 반영 확인(T-076: 확정 가능, 새 지적 없음). 두 리뷰가 갈린 만료 삭제는 아래 "코드 저장·삭제"에서 합쳤다. T-075·T-076의 구현 메모(CSRF 지연 핸들러, 원자적 SQL)는 구현 카드로 간다. 테스트를 쓰다 계약이 틀렸다고 판단되면 고치지 말고 결과에 적고 멈춘다.

## 목표

MVP 구현 순서 2번(F1a 이메일 코드 로그인 + 탈퇴, `docs/proposals/mvp.md` F1·F7)의 인수 테스트 원본을 쓴다. 운영 코드는 쓰지 않는다. 구현 전이므로 red여야 한다.

## 계약 (테스트가 고정할 것)

이 카드의 내용이 `mvp.md`보다 우선한다. 바꿔야 할 이유가 있으면 테스트를 쓰기 전에 결과에 적고 멈춘다.

**범위:** 이메일 코드 로그인·가입, 로그아웃, 내 계정 조회, 탈퇴. 탈퇴 시 이번 조각에 있는 데이터(계정·선생님 프로필)만 처리한다. 카카오(F1b)·대학 이메일(F3)·처리방침 페이지·IP별 요청 제한은 뺀다(아래 "뺀 것").

**슬라이스 1과의 경계:** 1의 원본(`TutorProfileAcceptanceIT`)은 그대로 둔다. 그 테스트는 계정 행 없이 `user("<임의 ID>")`로 프로필을 만든다. 그래서 프로필 → 계정 FK는 걸지 않고, 계정 행 없이 주입한 테스트 주체의 인증 규칙은 바꾸지 않는다. 실제 로그인한 계정의 `id`가 1의 `Authentication.getName()`(= `owner_id`)이 된다.

### 엔드포인트

| 엔드포인트 | 권한 | 성공 | 실패 (위에서부터 판정) |
|---|---|---|---|
| `POST /api/v1/auth/email-codes` `{email}` | 누구나 | `202`, 본문 없음. 계정 유무와 무관하게 같은 응답. 그 주소로 코드 메일 | 형식 `400 VALIDATION_FAILED` → 발송 제한 `429 TOO_MANY_REQUESTS` + `Retry-After` |
| `POST /api/v1/auth/email-codes/verify` `{email, code}` | 누구나 | 활성 계정 있음: `200 {result:"LOGGED_IN", account}` + 로그인. 없음: `200 {result:"SIGNUP_REQUIRED"}` + 세션에 확인된 이메일 보관 | 형식(이메일 형식, `code`가 6자리 숫자 아님) `400 VALIDATION_FAILED`, 시도 횟수에 안 셈 → 틀림·만료·무효·사용됨·요청 없음 모두 같은 `400 INVALID_CODE`(같은 메시지) |
| `POST /api/v1/auth/signup` `{role, ageOver14}` | 확인된 이메일이 세션에 있음 | `201`, `Location: /api/v1/me`, 본문 `account` + 로그인 | 이미 로그인한 세션 `409 ALREADY_SIGNED_UP` → 확인된 이메일 없음·만료·사용됨 `401 UNAUTHORIZED` → `role`이 `TEACHER`·`PARENT` 아님, `ageOver14` 누락·`false` `400 VALIDATION_FAILED`(계정 안 만듦) → 그 이메일의 활성 계정이 이미 있음 `409 ALREADY_SIGNED_UP` |
| `POST /api/v1/auth/logout` | 누구나 | `204`. 세션 무효화. 익명이어도 `204`(멱등) | — |
| `GET /api/v1/me` | 로그인 | `200 account` | `401` |
| `DELETE /api/v1/me` | 로그인 | `204`. 탈퇴 처리(아래), 세션 무효화 | `401` |

- `account` = `{id, email, role, createdAt}`. `id`는 UUID 문자열, `email`은 정규화된 값.
- 모든 POST·DELETE는 CSRF 토큰이 없거나 틀리면 `403 FORBIDDEN`(기존 `csrf.spa()`, 로그인 전 요청과 익명 로그아웃 포함 — 로그인 CSRF·메일 폭탄 방지). CSRF 판정이 위 표의 실패보다 먼저다.
- 오류 형식은 기존 `{code, message, fieldErrors?}`. 401·403은 기존 보안 핸들러 본문 그대로. `VALIDATION_FAILED`의 `fieldErrors[].field`는 요청 JSON 필드명(`email`, `code`, `role`, `ageOver14`).

### 세션·쿠키

- 서버 세션. 쿠키 이름은 **`SESSION`**으로 고정한다(나중에 Spring Session JDBC로 바꿔도 원본이 안 깨지게).
- 세션 쿠키: `HttpOnly`, `SameSite=Lax`, `Path=/`, `Max-Age=1209600`(14일). `Secure`는 운영 HTTPS 설정에서 켜며 테스트하지 않는다.
- 세션 유휴 만료 14일: 실제로 기다리지 않는다. IT는 적용된 설정값(`server.servlet.session.timeout`을 `Duration`으로 읽어 14일)을 단언한다.
- **세션 고정 방지:** 코드 확인 성공(두 결과 모두)과 가입 성공 응답은 `Set-Cookie`로 새 `SESSION`을 준다. 기존 세션이 있었다면 ID가 바뀐다. 바뀌기 전 `SESSION` 쿠키로는:
  - `SIGNUP_REQUIRED` 뒤: 유효한 CSRF로 `POST /api/v1/auth/signup`이 `401`. 새 쿠키만 가입할 수 있다.
  - `LOGGED_IN`·가입 성공 뒤: `GET /api/v1/me`가 `401`.
  - 검증하려면 확인 전에 세션을 먼저 만들어 둔다(예: 다른 이메일로 `SIGNUP_REQUIRED`를 받아 둔 세션에서 확인).
- **CSRF 토큰 교체:** 코드 확인 성공(두 결과 모두)과 가입 성공 응답은 이전과 다른 새 `XSRF-TOKEN` 쿠키를 준다. **새 `XSRF-TOKEN` 쿠키 + 이전 토큰 값의 `X-XSRF-TOKEN` 헤더**로 보낸 POST는 `403`. (`csrf.spa()`는 쿠키와 헤더를 비교하는 방식이라, 쿠키·헤더가 둘 다 이전 값이면 일치해서 통과한다 — T-075 P0-1.)
- 첫 CSRF 토큰은 별도 엔드포인트 없이 아무 GET(예: `GET /api/ping`, 기존 `SecurityIT`가 보장) 응답의 `XSRF-TOKEN` 쿠키로 받는다.
- 로그아웃·탈퇴 후 이전 세션 쿠키로는 `GET /api/v1/me`가 `401`.

### 확인된 이메일 (가입 자격)

- `SIGNUP_REQUIRED`의 확인된 이메일은 새 세션에만 묶인다.
- 확인 시각부터 **10분 미만** 동안 가입 1회에만 쓸 수 있다(9분 59초 가입 가능, 10분 0초 `401`).
- 같은 세션에서 다른 이메일을 확인하면 이전 확인 상태는 버린다(가입은 마지막에 확인한 이메일로).
- 로그아웃·가입 성공·만료 뒤에는 다시 쓸 수 없다(`401`).

### 코드 규칙

- 이메일은 앞뒤 공백 제거 + 소문자(`Locale.ROOT`)로 정규화한다. ` A@X.com`과 `a@x.com`은 같은 계정이고 발송 기록·실패 횟수도 같이 센다. 254자 초과는 `400`.
- 메일은 정규화된 주소로 가고, 본문에 6자리 숫자는 코드 하나만 있다.
- 코드는 발급 후 **10분 미만**까지 유효하다(9분 59초 수락, 10분 0초 `INVALID_CODE`).
- 새 코드를 발급하면 이전 코드는 무효다.
- 한 코드에 **5번** 틀리면 그 코드는 무효다. 4번 틀린 뒤 맞으면 성공, 5번 틀린 뒤에는 맞는 코드도 `400 INVALID_CODE`.
- 실패 횟수는 코드 발급 건별로 **모든 세션에서 합쳐** 센다(쿠키 저장소 두 개로 나눠 틀려도 합계 5회면 무효).
- 성공한 코드는 다시 쓸 수 없다. 같은 코드로 **동시에** 확인하면 최대 한 요청만 성공한다(맞는 코드 동시 2요청 → `200` 하나, `400 INVALID_CODE` 하나).
- **발송 제한 (정규화된 이메일별, 모든 세션 공유, 코드 상태와 무관하게 성공 발송 기록 기준):**
  - 마지막 성공 발송 후 **60초 미만**이면 `429`(59초 `429`, 60초 `202`). 코드가 5회 실패로 무효가 됐거나 성공으로 지워져도 이 제한은 그대로다.
  - 현재 시각 직전 24시간 안의 성공 발송이 **10회**면 `429`(60초 간격으로 10번째 `202`, 11번째 `429`). 첫 발송 후 정확히 24시간이 되면 그 발송은 구간에서 빠져 다시 허용된다.
  - `429` 요청은 기존 코드·발송 기록을 바꾸지 않는다(거부된 요청은 세지 않는다).
  - `Retry-After`는 다음 발송 가능 시각(마지막 발송 + 60초, 24시간 상한에 걸렸으면 구간 안 가장 오래된 발송 + 24시간 중 늦은 쪽)까지 남은 초를 올림한 양의 정수(최소 `1`).

### 코드 저장·삭제 (F7 "인증 코드는 만료 즉시 삭제")

- 만료 시점(발급 후 10분)부터 코드는 검증되지 않는다. 저장된 행은 만료 후 **60초 안에** 지운다. (`mvp.md` F7 문구를 이 뜻으로 맞춘다.)
- 코드는 `email_codes` 테이블에 둔다. 정규화된 이메일은 `email` 컬럼. 평문 코드는 저장하지 않는다.
- 성공·5회 실패 무효·새 코드로 교체된 코드 행은 그 시점에 지운다.
- 만료된 코드 행은 코드 발송·코드 확인 요청(어느 이메일이든)을 처리할 때 함께 지운다. 그와 별도로 1분 주기 정리가 요청이 없을 때의 60초 상한을 지킨다(주기 정리는 이미 만료된 행만 지우므로 IT 단언을 흔들지 않는다. 구현자 단위 테스트로 검증).
- IT 관찰 (원본은 삭제·평문 확인에 `email_codes.email`, 소유자 확인에 `tutor_profiles.owner_id`만 읽고, DB 값을 고치지 않는다 — T-079 P1-6):
  - 서로 다른 이메일 A·B의 코드를 발급하고 시계를 10분 넘긴 뒤, 이메일 C로 코드를 요청하면 A·B 행이 각각 0개.
  - 성공 뒤, 5회 실패 뒤 해당 이메일 행 0개.
  - 발급 직후 행의 모든 문자 값에 메일로 받은 코드가 들어 있지 않다.

### 탈퇴 (F7)

- 계정은 다시 쓸 수 없게 처리하고 이메일 연결을 끊는다(같은 이메일 재가입 가능, 복원 없음). 구현 방식은 구현자가 정한다.
- 선생님 프로필은 즉시 삭제: 공개 상세 `404`, 검색 결과에서 빠짐.
- **그 계정의 다른 세션도 더 이상 인증되지 않는다.** 같은 계정으로 실제 세션 두 개를 만들고(첫 로그인 후 시계를 60초 넘겨 새 코드를 받아 둘째 쿠키 저장소에서 확인), 한쪽에서 탈퇴하면 다른 쪽의 `GET /api/v1/me`와 `POST /api/v1/tutor-profiles`가 `401`.
- 같은 이메일로 다시 확인하면 `SIGNUP_REQUIRED`, 가입하면 **새 `id`**, 그 세션의 `GET /api/v1/me/tutor-profile`은 `404`.

### 슬라이스 1 연결 (끝에서 끝)

- 가입한 TEACHER는 실제 세션으로 프로필을 만들고 공개할 수 있다(소유자 = `account.id`, 이메일이 아니다. IT는 `tutor_profiles.owner_id`로 확인). 가입한 PARENT의 프로필 생성은 `403`.
- 로그아웃은 `XSRF-TOKEN`을 지울 수 있다(Spring Security 기본 `CsrfLogoutHandler`). 테스트는 지우는 구현과 남기는 구현을 모두 허용하고, 로그아웃 뒤 POST 전에는 GET으로 토큰을 다시 받는다(T-079 P0-1·P2-N3).
- 역할은 바뀌지 않는다: PARENT로 가입한 이메일로 다시 로그인하면 `role`은 `PARENT`.

## 테스트 방식 (T-071·T-072·T-074 반영)

- **HTTP:** 새 IT는 `@SpringBootTest(webEnvironment = RANDOM_PORT)` + 실제 쿠키. `RestTestClient`(`@AutoConfigureRestTestClient`, T-071 5번: 기존 의존성에 포함)나 JDK `HttpClient` + `CookieManager`(기존 `BackendApplicationIT`) 중 고른다. MockMvc는 실제 `Set-Cookie`·세션 ID 교체를 보증하지 않는다. 슬라이스 1의 MockMvc 원본은 그대로.
- **시간:** 운영 코드가 `java.time.Clock` 빈을 주입받는다. 새 IT의 `@TestConfiguration`이 `@Primary` 조작 가능 `Clock`을 제공한다. 테스트마다 고유 이메일과 기준 시각을 쓰고, 위 경계값(직전·정각)을 HTTP로 본다.
- **메일:** `GenericContainer`로 `axllent/mailpit:v1.31.6`(SMTP 1025, API 8025)을 띄우고 `DynamicPropertyRegistrar`로 `spring.mail.host`·`spring.mail.port`만 연결한다(T-071 4번). 메일은 그 테스트의 수신인 앞으로 새로 온 것만 기다려 읽는다.
- **헬퍼:** 시계·메일 설정 같은 헬퍼도 원본에 포함한다(원본 전용 PR에는 원본과 카드만 들어간다). IT는 `@Import({TestcontainersConfiguration.class, ...})`.
- **새 경로의 허용·거부 쌍:** 위 표의 성공·실패를 경로마다 실제 필터 체인으로 고정한다(`backend/AGENTS.md` 보안 규칙).

## 뺀 것 (기록)

- IP별 발송·확인 제한: 프록시 뒤 실제 클라이언트 IP를 정하는 배포 구성이 없어서 지금 넣으면 잘못 판정한다(T-075 동의). `mvp.md` 출시 전 확인 목록에 넣는다.
- 코드 용도(`purpose`) 분리: F3(대학 이메일)에서 같은 모듈을 쓸 때 추가하고, 그 조각의 계약에 "로그인 코드로 대학 이메일 확인 불가"를 넣는다.
- 운영 메일 발송 서비스 선택·SPF/DKIM/DMARC·세션 쿠키 `Secure`: 출시 전 확인 목록(T-071 6번).

## 완료 조건

- [x] 새 테스트 파일만 추가(헬퍼 포함). 전부 red이고, 실패 이유가 "엔드포인트 없음/기능 없음"이지 컴파일 오류·컨테이너 기동 실패가 아니다.
- [x] `spotlessJavaCheck` 통과.
- [x] 결과에 테스트 목록(메서드 이름)과 red 확인 방법.

## 참고

`docs/proposals/mvp.md` F1·F7·구현 순서, `docs/tasks/T-045-tutor-profile-acceptance.md`(형식), `docs/research/auth.md`(T-071), T-072·T-074·T-075 `## 리뷰`.

## 결과

- 새 파일: `AuthAcceptanceIT.java`(20개), `AdjustableClock.java`, `AuthTestConfiguration.java`, `AuthHttpClient.java`, `AuthMailInbox.java` (`backend/src/test/java/kr/needteacher/account/`). 운영 코드·기존 테스트 미수정.
- 테스트 1–5: `requestIsGenericForExistingAndNewEmailAndNormalizesAddress`, `emailAndCodeShapesAreValidatedBeforeAttemptCounting`, `codeExpiresAtTenMinutesAndResendingInvalidatesOldCode`, `fourWrongCodesStillAllowSuccessButFiveAcrossClientsInvalidateCode`, `missingExpiredConsumedAndWrongCodesHaveTheSamePublicError`.
- 테스트 6–10: `simultaneousCorrectVerificationsConsumeCodeOnlyOnce`, `sixtySecondLimitPersistsAfterCodeConsumptionAndDoesNotReplaceItOnRejection`, `dailyLimitUsesSuccessfulSendsAndOpensAtTwentyFourHourBoundary`, `consumedAndInvalidatedCodesAreRemovedAndPlaintextIsNeverStored`, `pendingSignupProofUsesRotatedSessionAndCsrfToken`.
- 테스트 11–15: `pendingSignupProofExpiresAtTenMinutesAndCanBeReplacedByAnotherEmail`, `signupRejectsInvalidRoleAndAgeWithoutCreatingAccount`, `signupRequiresVerifiedEmailAndDetectsAccountCreatedByAnotherSession`, `existingAccountLoginRotatesCookiesAndPreservesRole`, `sessionCookieHasRequiredAttributesAndIdleTimeoutIsFourteenDays`.
- 테스트 16–20: `logoutIsIdempotentButCsrfProtectedAndInvalidatesSession`, `logoutDiscardsPendingSignupProof`, `allStateChangingEndpointsRejectMissingOrWrongCsrfBeforeOtherErrors`, `teacherCanUseProfileApisAndWithdrawalRevokesOtherSessionAndDeletesProfile`, `parentCannotCreateProfileAndRoleCannotChangeAfterLogin`.
- 형식·컴파일: `cd backend; .\gradlew.bat spotlessJavaCheck compileTestJava` 통과. 아키텍처 규칙: `.\gradlew.bat test --tests 'kr.needteacher.architecture.*'` 통과.
- Red: `cd backend; .\gradlew.bat integrationTest --tests '*AuthAcceptanceIT'` → PostgreSQL·Mailpit 기동, 20개 실행·20개 실패. 모두 아직 없는 인증 경로에서 기대 `202`/`400` 대신 `401 UNAUTHORIZED`; 컴파일·컨텍스트 오류 없음.
- 계약과 다르게 정한 것 없음. 구현 후 동일 원본을 복사해 green 확인 필요.
