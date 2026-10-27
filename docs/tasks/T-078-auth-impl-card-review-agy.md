---
id: T-078
owner: agy
status: done    # todo | doing | blocked | done
scope:
  - docs/tasks/T-078-auth-impl-card-review-agy.md
---

## 목표

`docs/tasks/T-077-auth-impl.md`(구현 카드 초안)를 리뷰한다. 인수 테스트 원본(T-073)이 병합되면 네가 이 카드로 구현한다. 지금은 Codex가 원본을 쓰는 중이라 구현은 하지 않는다. **구현을 시작하기 전에 카드에서 막힐 곳을 미리 없애는 것**이 목적이다. 코드는 쓰지 않는다.

## 볼 것

1. **scope:** 구현에 필요한데 scope 밖인 파일이 있나(예: `@EnableScheduling`을 둘 위치, `Clock` 빈 설정 클래스, 메일 발신 주소 설정). 슬라이스 1 구현(T-050~T-053)에서 scope 때문에 멈췄던 일이 있으면 그 교훈도.
2. **하네스 충돌:** 카드의 구현 조건이 `ConfigurationIntegrityTest`·`LayerArchitectureTest`·`TestIntegrityTest`·`quality_gate.py`(파일별 90/85/80, 변이 0개 파일 규칙)와 부딪히는 곳. 특히 `Clock` 빈 설정 클래스와 `@Scheduled` 클래스의 PIT·커버리지를 어떻게 채우나, `security → account` 방향 의존이 순환을 만들지 않나(`tutorprofile`이 `security`를 참조하는지 확인).
3. **빠진 조건:** T-073 계약을 만족시키는 데 필요한데 카드에 없는 구현 조건.
4. **과한 조건:** 계약에 필요 없는데 카드가 강제하는 것(빼도 되는 것).

## 완료 조건

- [x] `## 리뷰`에 1~4. 지적마다 P0/P1/P2 + 근거(`파일:줄`) + 카드 교체 문장.
- [x] 결과 10줄 이내.

## 참고

T-077, T-073, T-076, `docs/tasks/T-050-tutor-profile-impl.md`~`T-053`, `backend/AGENTS.md`, `backend/src/test/java/kr/needteacher/architecture/`, `scripts/quality_gate.py`.

## 리뷰

### 1. `scope` 및 빈·설정 위치 (슬라이스 1 교훈 포함)

- **P0 — 운영 `Clock` 빈 이름과 인수 테스트 `@TestConfiguration` 빈 이름 충돌(`BeanDefinitionOverrideException`) 방지 (`T-077:37`, `scripts/quality_gate.py:68,133-134`):**
  - **근거:** Spring Boot 4는 기본적으로 빈 정의 덮어쓰기를 금지하며, `scripts/quality_gate.py:68,133-134`도 `allow-bean-definition-overriding=true`를 막는다. 만약 Codex가 작성하는 불변 인수 테스트(`T-073:100`)의 `@TestConfiguration`에서 `@Bean @Primary Clock clock()`이라는 메서드 이름을 쓰고, 운영 `@Configuration`에서도 똑같이 `@Bean Clock clock()`을 선언하면 `@Primary`가 평가되기 전에 빈 이름(`"clock"`) 중복으로 `BeanDefinitionOverrideException`이 발생해 컨텍스트 기동이 깨진다.
  - **카드 교체 문장 (`T-077:37`):**
    > `- **시간·스케줄링 설정:** BackendApplication.java는 scope 밖이므로 kr.needteacher.account(또는 common) 아래 @Configuration 클래스에 @EnableScheduling과 @Bean public Clock systemClock() { return Clock.systemUTC(); }을 함께 둔다(인수 테스트 @TestConfiguration의 @Primary Clock 빈과 빈 이름 "clock"이 겹쳐 BeanDefinitionOverrideException이 나지 않도록 메서드 이름을 systemClock으로 지정). 모든 시각은 주입한 Clock으로 읽고 Instant.now() 직접 호출은 금지한다.`

- **P2 — 메일 발신 주소(`From`) 기본값 명시 (`T-077:49`, `T-073:62,101`):**
  - **근거:** `JavaMailSender`로 발송할 때 `From` 주소를 지정하지 않으면 OS 사용자명·호스트명에 따라 Jakarta Mail `AddressException`이나 Mailpit SMTP `501` 문법 오류가 날 수 있다. `application.properties`는 이미 `T-077:10` `scope` 안에 있다.
  - **카드 교체 문장 (`T-077:49` 끝에 추가):**
    > `메일 발송 시 From 주소가 빠지지 않도록 기본 발신 주소(예: noreply@needteacher.kr)를 명시해 보낸다.`

- **슬라이스 1(`T-050`~`T-053`) 교훈 점검:**
  - `T-050:8`에서는 `SecurityConfig.java` 단일 파일만 `scope`에 있어 보안 필터·핸들러 확장이 제한됐으나, `T-077:7`은 `security/**` 전체를 열어 두어 해결되었다.
  - 기존 테스트 파일(`GlobalExceptionHandlerTest`, `TutorProfileServiceTest`, `SecurityIT`)은 `scripts/verify.sh:122-125`가 수정을 막으므로, `common`·`tutorprofile`·`security` 수정분에 대한 테스트는 반드시 **새 파일**로 추가해야 한다(`T-077:13-15`에 이미 반영됨).
  - 탈퇴 시 선생님 프로필 삭제는 443줄짜리 `TutorProfileService.java`를 수정하는 대신 순수 인터페이스인 `TutorProfileRepository.java`에 `void deleteByOwnerId(String ownerId);`만 선언해 `AccountService`에서 호출하면(`V1__create_tutor_profiles.sql:21,27,33`에 `ON DELETE CASCADE`도 설정됨), 기존 `TutorProfileService`의 파일별 커버리지·뮤테이션 재검증 부담을 없앨 수 있다.

---

### 2. 하네스 충돌 점검 (`ConfigurationIntegrityTest` · `LayerArchitectureTest` · `DatabaseIntegrityIT` · `quality_gate.py`)

- **P1 — `security` ↔ `account` 패키지 순환 의존 방지 (`T-077:35,47-48`, `LayerArchitectureTest.java:51-52`):**
  - **근거:** `LayerArchitectureTest.java:51-52`의 `slices().matching("kr.needteacher.(*)..").should().beFreeOfCycles()`는 `account`, `security`, `tutorprofile`, `common`, `ping`을 각각 독립 슬라이스로 검사한다. 현재 `tutorprofile`은 `common`만 참조하고 `security`는 참조하지 않는다(`TutorProfileController.java:6`은 Spring의 `Authentication`만 사용). 따라서 `security → account → tutorprofile → common` 단방향은 순환을 만들지 않는다. **단**, `SecurityConfig`(`security`)가 `account`의 필터를 주입받으면서 동시에 `account`가 `security` 패키지의 클래스(예: 전용 `Authentication` 타입을 `security`에 둔 경우)를 import하면 **`security ↔ account` 순환**으로 `LayerArchitectureTest`가 즉시 실패한다.
  - **카드 교체 문장 (`T-077:35`):**
    > `- **구조:** 기능 패키지 kr.needteacher.account. Controller → Service → Repository. 패키지 사이 순환 금지(LayerArchitectureTest: kr.needteacher.(*)..). 탈퇴 시 프로필 삭제는 account → tutorprofile 단방향으로, 보안 연결은 전용 Authentication·세션 검증 필터를 account에 두고 SecurityConfig만 이를 주입받아 security → account 단방향으로 유지한다(account에서 kr.needteacher.security를 import하지 않는다).`

- **P1 — `Clock` 설정 클래스·`@Scheduled` 메서드의 PIT/커버리지 및 `DatabaseIntegrityIT` 충돌 방지 (`T-077:41,51`, `scripts/quality_gate.py:187-219`, `DatabaseIntegrityIT.java:71-91`):**
  - **근거:**
    1. `scripts/quality_gate.py:212-219`는 `BackendApplication`·`SecurityConfig` 외의 모든 새 파일에 대해 `LINE >= 90%`, `BRANCH >= 85%`, `MUTATION >= 80%`(변이 0개 파일은 `LINE == 100%`)를 요구하며, `build.gradle.kts:124`에 의해 PIT는 `*Test` 단위 테스트만 실행한다. 따라서 `@Bean Clock systemClock()`을 둔 `@Configuration` 클래스도 단위 테스트(`new AccountConfig().systemClock()`)로 `NullReturnValsMutator` 변이를 죽여야 하고, `@Scheduled` 정리 메서드도 별도 클래스를 만들지 말고 서비스 안에 두어 단위 테스트로 직접 호출해 `VoidMethodCallMutator` 변이를 죽여야 한다.
    2. `@Scheduled(fixedRate = 60_000)`에 `initialDelay`가 없으면 컨텍스트 기동 즉시 백그라운드 스레드에서 DELETE 쿼리가 실행되어, `DatabaseIntegrityIT.java:81-91`처럼 컨텍스트를 짧게 띄웠다 닫는 테스트에서 종료 중인 DataSource와 경합할 수 있다. `initialDelay = 60_000`을 주면 기동 직후 실행을 막으면서 60초 상한(`T-073:76,79`)을 지킨다.
  - **카드 교체 문장 (`T-077:41`):**
    > `- **만료 삭제:** 코드 발송·확인 요청 처리 때 만료 행 전체 삭제 + 서비스 내 1분 주기 정리 메서드(@Scheduled(fixedDelay = 60_000, initialDelay = 60_000), 같은 Clock). 별도 스케줄러 클래스를 만들지 않고 서비스 메서드를 단위 테스트에서 직접 호출해 PIT 뮤턴트를 죽인다. Clock 설정 클래스도 단위 테스트로 systemClock() 반환값을 단언한다.`

- **P1 — `SecurityConfig` 무결성 및 분기 커버리지 (`T-077:48`, `ConfigurationIntegrityTest.java:35-69`, `scripts/quality_gate.py:196-211`):**
  - **근거:** `SecurityConfig.java`는 PIT 면제이지만 `ConfigurationIntegrityTest.java:36-42`와 `quality_gate.py:206-210`이 같은 소스 파일 내 중첩·익명 클래스(`SecurityConfig$1` 등)를 금지하며, JaCoCo `LINE >= 90%` / `BRANCH >= 85%`는 그대로 검사한다. 따라서 탈퇴 세션 검증 필터 등을 `SecurityConfig.java` 안에 익명 클래스나 분기문으로 쓰면 실패한다. (`T-077:48`에 이미 "필터·핸들러는 `securityFilterChain` 파라미터로 주입"으로 잘 적혀 있으므로 익명 클래스·내부 분기 금지만 덧붙이면 충분하다.)

---

### 3. 빠진 조건 (`T-073` 계약 대비 누락된 구현 조건)

- **P1 — `SIGNUP_REQUIRED`(비로그인 코드 확인 성공)에서도 세션 ID 교체 + 새 `XSRF-TOKEN` 쿠키 발급 필수 (`T-073:44-48` vs `T-077:42-45`):**
  - **근거:** `T-073:44-48`은 "코드 확인 성공(**두 결과 모두**)과 가입 성공 응답은 `Set-Cookie`로 새 `SESSION`을 준다", "코드 확인 성공(**두 결과 모두**)과 가입 성공 응답은 이전과 다른 새 `XSRF-TOKEN` 쿠키를 준다"고 못 박았다. 현재 `T-077:42`는 `**세션 로그인**` 아래에만 세션 ID·CSRF 교체를 적어 두어 `SIGNUP_REQUIRED` 경로에서 빠뜨리기 쉽다.
  - **카드 교체 문장 (`T-077:42-45`):**
    > `- **세션 ID·CSRF 교체 및 로그인 (T-073:44-48, T-075 P0-1):**`
    > `  - 코드 확인 성공(LOGGED_IN과 SIGNUP_REQUIRED 두 결과 모두) 및 가입 성공(201) 세 경로 모두에서: ① 기존 세션이 있으면 request.changeSessionId(), 없으면 request.getSession(true)로 새 SESSION 쿠키가 나가게 하고, ② CookieCsrfTokenRepository.withHttpOnlyFalse()의 generateToken + saveToken을 직접 호출해 새 XSRF-TOKEN 쿠키가 응답에 나가게 한다.`
    > `  - LOGGED_IN과 가입 성공 시에는 전용 Authentication을 담은 SecurityContext를 HttpSessionSecurityContextRepository#saveContext로 명시 저장한다. Authentication#getName()은 account.id(UUID 문자열), 권한은 ROLE_TEACHER 또는 ROLE_PARENT여야 슬라이스 1(hasRole("TEACHER"))과 연결된다(T-073:22,94).`

- **P1 — `POST /api/v1/auth/signup` 실패 판정 우선순위 및 Jackson 타입 엄격 검증 (`T-073:29-30,37`, `T-051:52-53`):**
  - **근거:**
    1. `/api/v1/auth/**`는 `SecurityConfig`에서 `permitAll()`이므로 `/signup`의 `401`은 애플리케이션에서 직접 내려야 하며, `T-073:30`은 실패 우선순위를 **이미 로그인한 세션(`409 ALREADY_SIGNED_UP`) → 확인된 이메일 없음·만료·사용됨(`401 UNAUTHORIZED`) → 본문 형식 오류(`400 VALIDATION_FAILED`, 필드명 `role`·`ageOver14`) → 해당 이메일 활성 계정 이미 존재(`409 ALREADY_SIGNED_UP`)** 순서로 고정했다.
    2. `/verify`에서도 형식 오류(`email` 형식, `code`가 6자리 숫자가 아님)는 `400 VALIDATION_FAILED`(`fieldErrors`: `email`, `code`)이고 **실패 횟수에 세지 않는다**(`T-073:29`).
    3. 슬라이스 1(`T-051:52-53`)에서 겪었듯 Jackson 3(`JsonNode`)에서 `isString()`·`isBoolean()` 확인 없이 `asString()`·`asBoolean()`을 호출하면 타입 불일치 입력에서 500이나 강제 변환이 발생한다.
  - **카드 교체 문장 (`T-077:50`):**
    > `- **오류 및 판정 순서 (T-073:26-37):**`
    > `  - 모든 오류는 기존 {code, message, fieldErrors?} 형식으로 낸다: INVALID_CODE(400), TOO_MANY_REQUESTS(429 + Retry-After 헤더), ALREADY_SIGNED_UP(409), UNAUTHORIZED(401, /signup에서 확인된 이메일 없음·만료·사용됨).`
    > `  - /verify는 email·code(6자리 숫자) 형식 오류 시 실패 횟수를 올리지 않고 400 VALIDATION_FAILED(fieldErrors: email, code)를 반환한다.`
    > `  - /signup은 ① 이미 로그인한 세션(409 ALREADY_SIGNED_UP) → ② 세션의 확인된 이메일 없음·10분 만료·사용됨(401 UNAUTHORIZED) → ③ role(TEACHER|PARENT)·ageOver14(true) 검증 실패(400 VALIDATION_FAILED, fieldErrors: role, ageOver14) → ④ 해당 이메일 활성 계정 이미 존재(409 ALREADY_SIGNED_UP) 순서를 지킨다. JsonNode 파싱 시 isString()·isBoolean()을 먼저 확인한다(T-051 교훈).`

- **P2 — 탈퇴(`DELETE /api/v1/me`) 시 현재 세션 무효화 및 재가입 허용 (`T-073:33,87-90`):**
  - **근거:** `T-077:47`은 탈퇴한 계정의 "다른 세션 거부"만 설명하고 있으나, `T-073:33,87-90`에 따라 탈퇴 요청 자신의 세션도 즉시 무효화(`session.invalidate()` + `SecurityContextHolder.clearContext()`)해야 하며, 같은 이메일로 재가입할 때 `accounts.email` UNIQUE 제약에 막히지 않아야 한다(계정 행을 물리 삭제하면 이메일 재가입·비노출·다른 세션 존재 확인 `existsById == false`가 한 번에 해결된다).
  - **카드 교체 문장 (`T-077:47` 끝에 추가):**
    > `탈퇴 처리 시 선생님 프로필과 계정을 지우고(또는 이메일 연결을 끊어 같은 이메일 재가입 시 새 UUID가 발급되게 하고) 현재 세션도 즉시 무효화한다.`

---

### 4. 과한 조건 (계약에 필요 없는데 카드가 강제하는 것)

- **P2 — `T-077:38`의 기동 시 무작위 HMAC 키 생성 및 `ponytail:` 주석 강제 완화:**
  - **근거:** `T-073:77,83` 계약은 평문 미저장(`email_codes` 행의 모든 문자 값에 평문 6자리 코드 미포함)만 요구한다. `SHA-256`(`MessageDigest`) 해시 + `MessageDigest.isEqual` 비교만으로도 계약을 완전히 충족하며, 기동 시 무작위 키 생성과 `ponytail:` 주석을 강제할 필요가 없다.
  - **카드 교체 문장 (`T-077:38`):**
    > `- **코드:** SecureRandom 6자리. 평문 저장 금지 — SHA-256(또는 HMAC-SHA256) 해시(16진수/Base64 문자열)로 저장하고 비교는 MessageDigest.isEqual로 한다.`

- **P2 — `T-077:39`의 `UPDATE ... RETURNING` / `DELETE ... RETURNING` 문법 한정 완화:**
  - **근거:** PostgreSQL `RETURNING` 절이 아니어도 트랜잭션 내 `SELECT ... FOR UPDATE` 행 잠금이나 조건부 `UPDATE` / `DELETE`의 JDBC 영향 행 수(`int`) 반환만으로 원자적 실패 누적과 동시 확인 단일 성공을 달성할 수 있고, 단위 테스트용 저장소 대역(Fake/Mock) 구현도 훨씬 단순해진다.
  - **카드 교체 문장 (`T-077:39` 첫 문장):**
    > `- **원자성 (T-074 P0-2, 방법은 T-076 2절):** 실패 횟수 누적과 성공 소비는 SELECT ... FOR UPDATE 행 잠금이나 조건부 UPDATE/DELETE 영향 행 수(또는 RETURNING)로 원자적으로 처리한다.`

## 결과

- `docs/tasks/T-077-auth-impl.md` 초안을 `T-073`(v2), 슬라이스 1(`T-050`~`T-053`), 아키텍처 테스트 4종, `quality_gate.py`와 대조해 P0 1건·P1 5건·P2 4건 도출 및 교체 문장 작성 완료.
- **P0 (1. scope/빈):** 운영 `Clock` 빈 이름이 인수 테스트 `@TestConfiguration`의 `@Primary` 빈 이름(`clock`)과 겹쳐 `BeanDefinitionOverrideException`이 나지 않도록 `systemClock()`으로 지정하고 `account`(또는 `common`) 설정 클래스에 `@EnableScheduling`과 함께 배치.
- **P1 (2. 하네스):** `security → account → tutorprofile → common` 단방향 유지(`LayerArchitectureTest` 순환 방지), `Clock`·`@Scheduled` 단위 테스트로 PIT 방어 및 `initialDelay = 60_000`으로 `DatabaseIntegrityIT` 기동 직후 경합 방지.
- **P1 (3. 빠진 조건):** `SIGNUP_REQUIRED`에서도 세션 ID·CSRF 쿠키 교체 필수, `/signup` 4단계 실패 판정 우선순위 및 Jackson `isString()`/`isBoolean()` 검증, `Authentication`의 `ROLE_` 접두사·`getName() = account.id` 명시.
- **P2 (4. 과한 조건):** 무작위 HMAC 키·`ponytail:` 주석 및 `RETURNING` 문법 강제를 완화하고, 탈퇴 시 프로필 삭제는 `TutorProfileRepository.deleteByOwnerId` 추가를 권장.
