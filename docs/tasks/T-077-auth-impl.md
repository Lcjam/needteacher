---
id: T-077
owner: agy
status: blocked    # todo | doing | blocked | done
scope:
  - backend/src/main/java/kr/needteacher/account/**
  - backend/src/main/java/kr/needteacher/security/**
  - backend/src/main/java/kr/needteacher/common/**
  - backend/src/main/java/kr/needteacher/tutorprofile/**     # 소유자 ID로 프로필 삭제만
  - backend/src/main/resources/application.properties
  - backend/src/main/resources/db/migration/**              # 새 V2__ 파일만
  - backend/src/test/java/kr/needteacher/account/**          # 원본 사본 + 새 파일
  - backend/src/test/java/kr/needteacher/security/**         # 새 파일만 (기존 파일 불변)
  - backend/src/test/java/kr/needteacher/tutorprofile/**     # 새 파일만 (기존 파일 불변)
  - backend/src/test/java/kr/needteacher/common/**           # 새 파일만 (기존 파일 불변)
  - docs/tasks/T-077-auth-impl.md
---

> **선행 구현으로 배정 (원본 PR·의존성 PR 병합 전).** 테스트는 확정됐고(T-073·T-081, 리뷰 T-079·T-080), `backend/src/test/java/kr/needteacher/account/`의 5개 파일이 이미 아래 sha256과 같은 원본 바이트다. 원본·의존성 PR은 사용자 잠금 해제 뒤 오케스트레이터가 낸다. 그때까지 아래 "선행 구현 규칙"을 지킨다.
>
> **선행 구현 규칙**
> - `git add`·`commit`·`switch`·`stash`·`checkout` 금지. 오케스트레이터가 잠금 해제 중 브랜치를 바꾸는데, 네 커밋 안 된 변경은 그대로 따라온다.
> - 시작 전에 5개 테스트 파일의 sha256을 확인하고, 한 글자도 고치지 않는다.
> - `backend/build.gradle.kts`, `backend/src/acceptance/`, 다른 카드는 건드리지 않는다.
> - **메일 발송은 마지막에.** `spring-boot-starter-mail`은 의존성 PR이 병합돼야 들어온다. 그 전에는 메일 발송 부분만 남기고 나머지(스키마·계정·코드 저장·세션·보안·탈퇴)를 먼저 끝낸다. 메일 때문에 막히면 `status: blocked` + "의존성 대기"로 두고 멈춘다. 오케스트레이터가 병합 후 알린다.
> - 중간 점검은 단위 테스트와 `integrationTest --tests '*AuthAcceptanceIT'`로 한다. 메일이 없는 동안 메일이 필요한 테스트는 red가 정상이다.

## 목표

MVP 2번 조각(F1a 이메일 코드 로그인 + 탈퇴)을 구현해 T-073 인수 테스트를 green으로 만든다 (하네스 ④).

## 시작

1. 인수 테스트 원본 5개(`backend/src/acceptance/java/kr/needteacher/account/`)를 `backend/src/test/java/kr/needteacher/account/` 같은 이름으로 **바이트 그대로** 복사한다. sha256:
   ```
   ae4a32776382f971a7b6a07596f6713f4d66aecced2c9c85d056154804379b8f  AdjustableClock.java
   ad0d1ced5d2b73475bb8ff3412a4bb9a920b60b257d5e963fbba268e827ba41f  AuthAcceptanceIT.java
   52cfc2eadcb240881d8c287a0c22ac7c617f1da15003c667aa179b55367bd0c0  AuthHttpClient.java
   4b3f54e957cb6e86b098544b3428821f391a3a647ea177a4fd5968f747a280b5  AuthMailInbox.java
   d15a7c8adbe4c7ad98426663892e1625316928fff3032cdfe2f06e4f3b1bcfe4  AuthTestConfiguration.java
   ```
   - 이 파일들은 **한 글자도 고치지 않는다.** 형식 도구도 돌리지 않는다.
   - 계약이 이상하다고 판단되면 고치지 말고 결과에 적고 멈춘다.
2. 계약은 `docs/tasks/T-073-auth-acceptance.md`의 `## 계약`이다. 근거와 함정은 `docs/research/auth.md`, T-074·T-075·T-076 `## 리뷰`.
3. `spring-boot-starter-mail`은 오케스트레이터가 별도 PR로 `build.gradle.kts`에 넣는다(보호 파일). 직접 고치지 않는다.

## 구현 조건

- **구조:** 기능 패키지 `kr.needteacher.account`. `Controller → Service → Repository`. 기능 패키지 사이 순환 금지(`LayerArchitectureTest`). 의존은 `security → account → tutorprofile → common` 한 방향이다(T-078 P1): 전용 `Authentication` 타입과 세션 검증 필터는 `account`에 두고 `SecurityConfig`가 주입받는다. `account`는 `kr.needteacher.security`를 import하지 않는다. 탈퇴 시 프로필 삭제는 `TutorProfileRepository`에 `deleteByOwnerId`만 추가해 부른다(하위 테이블은 `ON DELETE CASCADE`).
- **시간·스케줄링 설정 (T-078 P0):** `BackendApplication`은 scope 밖이므로 `account` 아래 `@Configuration`에 `@EnableScheduling`과 `@Bean Clock systemClock()`을 둔다. **빈 이름을 `clock`으로 하지 않는다** — 인수 테스트의 `@Primary` 시계 빈과 이름이 겹치면 `BeanDefinitionOverrideException`이고, 덮어쓰기 허용은 `quality_gate.py`가 막는다. 설정 클래스도 단위 테스트로 반환값을 단언한다(PIT).
- **스키마:** Flyway `V2__...sql`. 계정, 코드(`email_codes`, 정규화된 `email` 컬럼 — 계약이 이 이름을 읽는다), 발송 기록(코드 행이 지워져도 60초·24시간 제한이 유지되게 별도). `tutor_profiles.owner_id`에 FK를 걸지 않는다(슬라이스 1 원본).
- **시간:** 모든 시각은 주입한 `java.time.Clock`으로. `Instant.now()` 직접 호출 금지(새 코드).
- **코드:** `SecureRandom` 6자리. 평문 저장 금지 — 서버 키 HMAC-SHA256 해시, 비교는 `MessageDigest.isEqual`. 키 없는 SHA-256은 6자리(100만 가지)라 DB만 새도 바로 되돌려지므로 쓰지 않는다(T-078 P2에 대한 오케스트레이터 판단). 키는 기동 시 무작위 생성으로 시작한다(`ponytail:` 주석 — 재시작하면 발급된 코드가 무효, 다중 인스턴스면 환경 변수 키로).
- **원자성 (T-074 P0-2, 방법은 T-076 2절):** 실패 횟수 누적과 성공 소비는 원자적으로(`SELECT ... FOR UPDATE` 행 잠금, 조건부 `UPDATE`/`DELETE`의 영향 행 수, `RETURNING` 중 택일). 같은 코드 동시 확인에서 한 요청만 성공해야 한다. 5회째 실패의 삭제는 `400` 응답과 함께 롤백되지 않게 커밋한다. 발송 기록은 이메일별 직렬화(예: `pg_advisory_xact_lock`) 뒤 60초·24시간 판정.
- **확인된 이메일 (T-076 2절):** 세션 속성 하나에 `(email, verifiedAt)`을 두어 다른 이메일 확인 시 덮어쓰고, 가입 성공·로그아웃 시 지운다. 10분 판정은 같은 `Clock`.
- **만료 삭제:** 코드 발송·확인 요청 처리 때 만료 행 전체 삭제 + 서비스 안의 1분 주기 정리 메서드(`@Scheduled(fixedDelay = 60_000, initialDelay = 60_000)`, 같은 `Clock`). `initialDelay`는 짧게 떴다 닫히는 IT 컨텍스트와의 경합을 막는다(T-078 P1). 정리 메서드는 단위 테스트에서 직접 불러 검증한다.
- **세션 ID·CSRF 교체와 로그인 (T-071 1절, T-075 P0-1, T-078 P1):**
  - 코드 확인 성공(**`LOGGED_IN`·`SIGNUP_REQUIRED` 둘 다**)과 가입 성공, 세 경로 모두: ① 기존 세션이 있으면 `changeSessionId`, 없으면 새로 만든다. ② `CsrfAuthenticationStrategy` 기본 핸들러는 지연 방식이라 새 쿠키가 안 나가므로, `CookieCsrfTokenRepository.withHttpOnlyFalse()`로 `generateToken` + `saveToken`을 직접 부르거나 즉시 로딩 핸들러를 쓴다.
  - `LOGGED_IN`·가입 성공: 전용 `Authentication`을 담은 `SecurityContext`를 `HttpSessionSecurityContextRepository#saveContext`로 명시 저장. `getName()`은 `account.id`, 권한은 `ROLE_TEACHER`/`ROLE_PARENT`(슬라이스 1의 `hasRole`과 연결).
  - 세션에 넣는 주체·인증 객체는 `Serializable`(나중 Spring Session JDBC 대비).
- **판정 순서·입력 (T-078 P1):** `/signup`은 계약 표의 순서(이미 로그인 `409` → 확인된 이메일 없음·만료·사용됨 `401` → 형식 `400` → 활성 계정 있음 `409`). `/auth/**`는 `permitAll`이라 `/signup`의 `401`은 애플리케이션이 직접 낸다. `/verify`의 형식 오류는 실패 횟수에 넣지 않는다. `JsonNode`는 `isString()`·`isBoolean()`을 먼저 확인한다(T-051 교훈).
- **메일:** 발신 주소를 설정으로 명시한다(비어 있으면 SMTP가 거부할 수 있다).
- **탈퇴한 계정의 다른 세션 거부 (T-074 P1-5, T-075 3-4):** 실제 이메일 로그인 세션만 계정 ID에 결속하고(예: 전용 `Authentication` 타입), 요청마다 그 타입일 때만 계정 상태를 확인해 탈퇴면 세션을 버리고 `401`. `user()`로 주입한 슬라이스 1 테스트 주체는 건드리지 않는다. 세션 저장소를 바꿔도 동작하는 방식을 고른다(메모리 세션 목록에 의존하지 않기). 탈퇴 요청 자신의 세션도 즉시 무효화한다.
- **보안 설정:** `SecurityConfig` 구조 규칙(필드 0개·무인자 생성자·`@Bean securityFilterChain` 하나, 같은 파일에 익명·중첩 클래스 금지, `ConfigurationIntegrityTest`). 필터·핸들러는 `securityFilterChain` 파라미터로 주입. 로그아웃은 `.logout(...)` + `HttpStatusReturningLogoutSuccessHandler(NO_CONTENT)`. 새 matcher: `/api/v1/auth/**` 공개(CSRF 유지), `/api/v1/me` 인증.
- **설정 (`application.properties`):** `server.servlet.session.cookie.name=SESSION`, `http-only=true`, `same-site=lax`, `max-age=14d`, `server.servlet.session.timeout=14d`. 로컬 기본 `spring.mail.host=localhost`, `spring.mail.port=1025`. 비밀값은 넣지 않는다. (T-082 바이트코드 확인)
  - 기간은 반드시 단위를 붙인다(`14d`). 단위 없는 숫자는 테스트가 읽는 `Environment#getProperty(..., Duration.class)`에서 밀리초가 된다.
  - `spring.mail.host` 기본값은 필수다. 테스트의 `DynamicPropertyRegistrar`는 자동 설정 조건(`@ConditionalOnProperty`) 평가 뒤에 돌아서, 기본값이 없으면 `JavaMailSender` 빈이 안 생긴다. 실제 접속은 레지스트라 값이 이긴다.
  - 로그아웃은 `.logout(...)` 그대로 둔다. `CsrfConfigurer`가 붙이는 `CsrfLogoutHandler`가 `XSRF-TOKEN`도 지우는 것이 계약이다(T-073, T-079 P0-1).
- **오류:** `INVALID_CODE`(400), `TOO_MANY_REQUESTS`(429 + `Retry-After`), `ALREADY_SIGNED_UP`(409)를 기존 `{code, message, fieldErrors?}` 형식으로.
- **품질 (`backend/AGENTS.md` 테스트 절):** 새 운영 파일마다 line 90 / branch 85 / mutation 80. PIT는 `*Test` 단위 테스트만 본다. 시간 경계·해시·Retry-After 계산은 Spring 없는 단위 테스트로 뮤턴트를 죽인다. IT에서 빈 바꿔치기·DB 설정 덮어쓰기 금지. 기존 테스트 파일 불변.

## 완료 조건

- [ ] T-073 인수 테스트 전부 green. 실행 사본 sha256이 원본과 같다.
- [x] 슬라이스 1 `TutorProfileAcceptanceIT` 포함 기존 테스트 전부 green.
- [ ] `bash scripts/verify.sh full --worktree` 통과.
- [ ] 결과에 바꾼 파일, 스키마 요약, 다른 세션 거부 방식, verify 수치.

## 참고

T-073, T-074, T-075, T-076, `docs/research/auth.md`, `docs/tasks/T-050-tutor-profile-impl.md`(슬라이스 1 구현 카드).

## 결과

- 바꾼 파일: V2 flyway 스키마, application.properties, SecurityConfig, TutorProfileRepository, TutorProfileService, OwnerAccessValidator, GlobalExceptionHandler, TooManyRequestsException, UnauthorizedException, kr.needteacher.account/**, AuthConcurrencyIT
- 동시성 배리어 보완: AuthConcurrencyIT 3건 모두 DB 락(advisory xact lock, SHARE ROW EXCLUSIVE, EXCLUSIVE) 배리어로 두 요청이 위험 구간에 동시 도달(pg_locks 대기 2건/1건)함을 확인 후 해제하도록 개선. 탈퇴-프로필 경합은 60초 후 새 코드로 발급한 독립 로그인 세션 2개로 검증.
- 결함 되돌림 확인(3/3 실패 확인 후 원복):
  1) sendCode @Transactional 제거 시: 동시 발송 모두 202 반환으로 실패 확인.
  2) createAccount UNIQUE catch 제거 시: 500 에러 반환으로 실패 확인 (409 기대).
  3) OwnerAccessValidator FOR SHARE 제거 시: 탈퇴 UPDATE가 대기하지 않아 pg_locks 튜플락 타임아웃 실패 확인.
- 테스트 상태: 3개 결함 재현 후 정상 원복 완료, AuthConcurrencyIT 3/3 통과, 단위 테스트 153건 전건 green, 원본 5개 sha256 유지, 메일 의존성 대기(blocked).

## 재작업 1 (오케스트레이터, 진행 중 개입)

`SmtpEmailSender`가 `java.net.Socket`으로 SMTP를 직접 구현했다. **반려한다.**

- TLS(STARTTLS)·AUTH가 없어 운영 메일 서비스(AWS SES 등, `docs/research/auth.md` 6절: 587 STARTTLS + 인증)로 보낼 수 없다. Mailpit 테스트만 통과하는 운영 코드다(슬라이스 1 T-050 "통과용 코드" 반려와 같은 유형).
- 제목·본문 인코딩, 헤더 주입(CRLF), 응답 코드 처리, 타임아웃을 손으로 다시 만드는 셈이다. 표준(Jakarta Mail)을 쓴다.

할 일:
1. `SmtpEmailSender`와 그 테스트를 지운다. 메일 발송은 `JavaMailSender`(`SimpleMailMessage`, 발신 주소는 설정값)로 쓴다. 단위 테스트는 `JavaMailSender`를 목으로 둬도 된다(단위 테스트의 목은 허용, IT 빈 바꿔치기만 금지).
2. `spring-boot-starter-mail`은 아직 없다(의존성 PR은 사용자 잠금 해제 대기). 메일을 뺀 나머지를 끝낸 상태로 `status: blocked` + "의존성 대기"로 두고 멈춘다. 의존성이 들어오면 오케스트레이터가 알린다.
3. 결과에 지금까지 한 것(바꾼 파일, 스키마 요약, 다른 세션 거부 방식, 메일 외 테스트 상태)을 적는다.

## 재작업 2 (Codex 1단계 리뷰 T-083 반영, 메일과 무관하니 지금 한다)

T-083 `## 리뷰` 1단계를 먼저 읽는다. 시작할 때 `status: doing`, 끝나면 다시 `blocked`(메일 의존성 대기).

1. **P0 발송 제한 트랜잭션:** `sendCode()`가 같은 객체의 `@Transactional` 메서드를 직접 불러 프록시를 안 거친다 → 트랜잭션이 없고 `pg_advisory_xact_lock`이 문장 끝에 풀린다. 잠금·발송 기록 판정·코드 저장이 **한 트랜잭션** 안에 있게 진입점에 트랜잭션을 걸거나 별도 빈으로 나눈다. 메일 발송(외부 I/O)은 그 트랜잭션 안에서 할지 커밋 뒤에 할지 정하고 이유를 결과에 적는다(발송 실패 시 기록·코드가 어떻게 되는지).
   - 검증: 실제 PostgreSQL로 같은 이메일 동시 발송 → 한 건만 `202`인 **새** IT(원본 파일은 건드리지 않는다).
2. **P1 동시 가입 `409`:** UNIQUE 충돌(`DataIntegrityViolationException`)을 `409 ALREADY_SIGNED_UP`으로 바꾼다. 새 IT로 같은 이메일 두 확인 세션 동시 가입 → `201` 하나·`409` 하나.
3. **P1 탈퇴와 프로필 쓰기 경합:** 필터 통과 뒤 탈퇴가 먼저 커밋되면 프로필이 남는다(F7 위반). 계정 행을 지우면 "탈퇴한 ID"와 "슬라이스 1의 계정 없는 테스트 주체"를 구분할 수 없으므로, 방향은 이렇게 한다:
   - 탈퇴는 계정 행을 남기고 `email = NULL`, `withdrawn_at` 기록(같은 이메일 재가입 가능, 복원 없음은 그대로). 다른 세션 거부는 "행이 있고 탈퇴됨"으로 판정.
   - 프로필 쓰기 트랜잭션에서 소유자 계정 행이 있으면 `FOR SHARE`로 잠그고 탈퇴됐으면 거부, 행이 없으면(슬라이스 1 주체) 그대로 허용. 탈퇴의 `UPDATE`는 그 잠금을 기다리고, 그 뒤 프로필을 지운다.
   - 계층: `tutorprofile`이 `account`를 import하면 순환이다. `tutorprofile`에 작은 인터페이스(예: 소유자 쓰기 확인)를 두고 `account`가 구현한다(`account → tutorprofile` 방향 유지). 더 단순한 방법이 있으면 이유와 함께 써도 된다.
   - 재작업 1에서 정한 "세션 저장소를 바꿔도 동작" 조건은 유지.
4. **P2 HMAC 키:** 서로 다른 `CodeHasher` 인스턴스가 같은 코드에 다른 해시를 내는 단위 테스트.

결과 절에 재작업 2 결과를 덧붙인다(바꾼 파일, 1의 메일 발송 위치 판단, 3의 방식, 테스트 상태).

## 메일 연결 (의존성 PR 병합 후 오케스트레이터가 알리면)

- `JavaMailSender` + `SimpleMailMessage`, 발신 주소는 설정값.
- 발송이 트랜잭션·이메일 잠금 안에 있으므로(재작업 2 판단) SMTP 타임아웃을 반드시 둔다: `spring.mail.properties.mail.smtp.connectiontimeout`, `timeout`, `writetimeout`(예: 5000ms). 응답 없는 SMTP가 DB 연결과 잠금을 붙잡지 않게.
- 끝나면 `AuthAcceptanceIT` 20개 green, 전체 `verify.sh full --worktree` 통과 후 `status: done`.

## 재작업 3 (Codex T-083 "1단계 추가 확인" P1, 메일과 무관하니 지금 한다)

재작업 2의 코드 수정 4건은 Codex가 닫힘으로 확인했다. 남은 것은 네 `AuthConcurrencyIT`다. 지금 테스트는 스레드 출발만 맞춰서 **순차로 돌아도 통과**한다. 즉 고치기 전 코드에서도 통과할 수 있다. T-083 해당 절(`AuthConcurrencyIT.java:79-101,136-158,182-216` 지적)을 읽고:

1. 각 경합을 **관찰 가능한 배리어**로 문제 지점에 세운다. 예: 테스트가 별도 JDBC 연결로 해당 행·advisory 키를 먼저 잠가 두 요청이 모두 그 잠금 앞에 도달했음을 확인(`pg_locks`·`pg_stat_activity`의 대기 확인 등)한 뒤 풀기. 방법은 네가 고르되 "두 요청이 동시에 위험 구간에 있었다"를 테스트가 확인해야 한다.
2. 프로필 경합은 **독립 로그인 세션 두 개**로(`copy()` 말고, 60초 넘겨 새 코드로 둘째 로그인).
3. **결함 재현 확인:** 고치기 전 동작(예: `sendCode`의 `@Transactional` 제거, `FOR SHARE` 제거, UNIQUE 변환 제거)을 임시로 되돌려 해당 테스트가 **실패**하는지 확인하고 원복한다. 결과에 어떤 되돌림에서 어떤 테스트가 실패했는지 적는다.
4. 원본 5개 파일은 건드리지 않는다. 끝나면 `status: blocked`(메일 대기).
