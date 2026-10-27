---
id: T-075
owner: agy
status: done    # todo | doing | blocked | done
scope:
  - docs/tasks/T-075-auth-contract-review-agy.md
---

## 목표

`docs/tasks/T-073-auth-acceptance.md`의 계약 v1을 리뷰한다. 네 조사(`docs/research/auth.md`, T-071)와 명세(`docs/proposals/mvp.md` F1·F7)에 비추어 **계약이 맞는가**, 그리고 **구현자가 이 계약을 Spring Boot 4.1 / Security 7.1로 하네스 규칙 안에서 만족시킬 수 있는가**를 본다. 코드는 쓰지 않는다. Codex가 같은 계약을 따로 리뷰한다(T-074). 그 카드는 읽지 않는다.

## 볼 것

1. **조사와의 대조:** 네 권장안 중 계약에 다르게 들어간 것(시간 검증을 IT `@Primary` 시계로 한 것, IP 제한을 뺀 것, `purpose`를 미룬 것 등). 각각 동의 / 반대 + 이유.
2. **명세와의 대조:** `mvp.md` F1·F7과 어긋나거나 명세에 없는 것을 계약이 새로 정한 곳. 새로 정한 것이 명세 취지에 맞는가.
3. **구현 가능성 (근거는 공식 문서·바이트코드):**
   - 쿠키 이름 `SESSION` 고정 + `Max-Age` 14일 + 유휴 14일을 톰캣 메모리 세션에서 설정만으로 할 수 있나.
   - 코드 확인·가입 시 세션 ID 교체 + CSRF 토큰 교체, 이전 토큰 `403`이 네가 조사한 방식으로 그대로 나오나.
   - 익명 로그아웃 `204`가 `logout()` 설정만으로 나오나. CSRF는 그대로 걸리나.
   - **탈퇴한 계정의 다른 세션 거부:** 슬라이스 1 원본(`TutorProfileAcceptanceIT`)은 계정 행 없는 `user("<임의 ID>")`로 쓰기를 한다. 이걸 깨지 않고 "탈퇴한 계정 ID의 세션은 인증 안 됨"을 만드는 방법(예: 묘비 행 확인 필터). `SecurityConfig` 구조 규칙(`ConfigurationIntegrityTest`)과 부딪히지 않는 위치는 어디인가.
   - `429` + `Retry-After` 정수 초.
4. **빠진 것:** 계약에 없지만 구현 전에 정해야 하는 것.

## 완료 조건

- [x] `## 리뷰`에 지적마다 P0(이대로면 구현 불가/명세 위반)·P1(고치는 게 맞음)·P2(선택) + 근거(링크 또는 `파일:줄`) + 교체 문장.
- [x] 판정: **이대로 / 고쳐서 확정 / 다시 설계** 중 하나.
- [x] 결과 10줄 이내.

## 참고

T-073, `docs/research/auth.md`, `docs/proposals/mvp.md`, `backend/src/test/java/kr/needteacher/architecture/ConfigurationIntegrityTest.java`, `backend/src/main/java/kr/needteacher/security/SecurityConfig.java`.

## 리뷰

### 판정: **고쳐서 확정**

전체 구조(2단계 `verify` → `signup`, `SESSION` 쿠키 고정, 조작 가능 `Clock`, Mailpit `GenericContainer`, 슬라이스 1 비침투 경계)는 명세와 하네스 규칙에 잘 맞는다. 다만 `csrf.spa()`(`CookieCsrfTokenRepository`)의 동작 원리상 문구를 그대로 두면 테스트 작성 방식에 따라 구현이 불가능해지는 **P0 1건**과, 경계 동작이 어긋날 수 있는 **P1 3건**, **P2 1건**이 있어 아래 문장을 교정한 뒤 확정하는 것을 권장한다.

---

### 지적 사항 (P0 / P1 / P2)

#### [P0-1] `csrf.spa()` 환경에서 "이전 토큰으로 보낸 POST는 `403`"의 쿠키·헤더 조합 명시 (`docs/tasks/T-073-auth-acceptance.md:44`)

- **근거:** `spring-security-web:7.1.1`의 `CookieCsrfTokenRepository#loadToken`(바이트코드 오프셋 20~65)은 서버 세션에 CSRF 토큰을 저장하지 않고, 매 요청에 실려 온 `Cookie: XSRF-TOKEN` 값을 읽어 `X-XSRF-TOKEN` 헤더 값과 비교하는 무상태 Double-Submit Cookie 방식이다([Spring Security CSRF — CookieCsrfTokenRepository](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html#csrf-components-cookie)).
- **문제:** `RestTestClient`처럼 쿠키를 수동 지정하는 테스트에서 "이전 토큰으로 보낸 POST"를 **이전 `XSRF-TOKEN` 쿠키 + 이전 `X-XSRF-TOKEN` 헤더**로 작성하면 쿠키와 헤더가 둘 다 이전 값으로 일치하므로 `csrf.spa()`에서는 절대 `403`이 나올 수 없다. 브라우저(또는 `CookieManager`)가 로그인/가입 응답의 `Set-Cookie`를 반영해 **새 `XSRF-TOKEN` 쿠키를 가진 상태에서 헤더(`X-XSRF-TOKEN`)에 이전 토큰 값**을 보낼 때 `403`이 반환된다.
- **구현 주의점(바이트코드 확인):** `CsrfAuthenticationStrategy`의 기본 `requestHandler`는 지연(deferred) 핸들러(`XorCsrfTokenRequestAttributeHandler`)라서 그대로 `onAuthentication`을 호출하면 기존 쿠키 삭제(`Max-Age=0`)만 나가고 새 쿠키가 생성되지 않는다. 컨트롤러/헬퍼에서는 `setCsrfRequestAttributeName(null)`을 설정한 핸들러를 넣거나 `CookieCsrfTokenRepository.withHttpOnlyFalse()`로 `generateToken` + `saveToken`을 직접 호출해야 응답에 새 `XSRF-TOKEN` 쿠키가 내려간다.
- **교체 문장 (`docs/tasks/T-073-auth-acceptance.md:44`):**
  > `- **CSRF 토큰 교체:** 로그인(LOGGED_IN)·가입 성공 시 응답 Set-Cookie로 이전과 다른 새 XSRF-TOKEN 쿠키가 내려오고, 갱신된 새 XSRF-TOKEN 쿠키와 함께 이전 토큰 값을 X-XSRF-TOKEN 헤더로 보낸 POST는 403.`

#### [P1-1] `SIGNUP_REQUIRED` 시 세션 고정 방지 검증 방법 구체화 (`docs/tasks/T-073-auth-acceptance.md:43`)

- **근거:** `spring-security-web:7.1.1`의 `AbstractSessionFixationProtectionStrategy#onAuthentication`(바이트코드 오프셋 0~49) 및 서블릿 `HttpServletRequest#changeSessionId()`는 요청에 이미 유효한 세션이 존재할 때만(`request.getSession(false) != null`) ID를 교체한다. `GET /api/ping`과 `POST /api/v1/auth/email-codes`는 세션을 생성하지 않으므로, 첫 `/verify` 호출 시에는 사전 세션이 없을 수 있다. 또한 `SIGNUP_REQUIRED`는 아직 미로그인 상태이므로 **바뀐 새 세션 쿠키로도 `GET /api/v1/me`는 원래 `401`**이다(`SIGNUP_REQUIRED`에서 구 세션 무효화는 구 세션 쿠키로 `POST /api/v1/auth/signup`을 호출했을 때 `401`이 나는지로 검증해야 한다).
- **교체 문장 (`docs/tasks/T-073-auth-acceptance.md:43`):**
  > `- **세션 고정 방지:** 기존 세션이 있는 상태에서 코드 확인 성공(두 결과 모두) 또는 가입 성공 시 Set-Cookie로 새 SESSION ID가 발급된다. 바뀌기 전 SESSION 쿠키로는 SIGNUP_REQUIRED 뒤 POST /api/v1/auth/signup이 401이고, LOGGED_IN 및 가입 성공 뒤 GET /api/v1/me가 401이다.`

#### [P1-2] `POST /api/v1/auth/signup`의 실패 상태 코드 판정 우선순위 명시 (`docs/tasks/T-073-auth-acceptance.md:30`)

- **근거:** `docs/tasks/T-073-auth-acceptance.md:29-30`에서 `LOGGED_IN`으로 바로 로그인한 세션이나 이미 `/signup`을 마친 세션에는 "확인된 이메일" 세션 속성이 없거나 지워진다. 만약 구현자가 "확인된 이메일이 세션에 있는가(`401`)"를 "이미 로그인한 세션인가(`409 ALREADY_SIGNED_UP`)"보다 먼저 검사하면, 로그인된 세션이 `/signup`을 호출할 때 `409`가 아니라 `401`이 나온다.
- **교체 문장 (`docs/tasks/T-073-auth-acceptance.md:30` 실패 칸):**
  > `이미 로그인한 세션 409 ALREADY_SIGNED_UP(최우선 판정), 확인된 이메일 없음 401 UNAUTHORIZED, role이 TEACHER·PARENT 아님·ageOver14 != true 400 VALIDATION_FAILED, 그 이메일의 활성 계정이 이미 있음 409 ALREADY_SIGNED_UP`

#### [P1-3] 만료 코드 삭제에서 "1분 주기" 백그라운드 스케줄러 문구 정리 (`docs/tasks/T-073-auth-acceptance.md:64-65`)

- **근거:** `docs/tasks/T-073-auth-acceptance.md:64-65`는 "다음 코드 요청 처리 때 전부 지우고, 그와 별도로 1분 주기로도 지운다"고 하면서 IT 관찰은 다음 코드 요청 시점만 본다. `@Scheduled` 백그라운드 스레드가 조작 가능 `@Primary Clock`을 공유하면 IT 실행 중 비결정적 시점에 DB에 개입해 플래키 테스트를 유발할 수 있고, IT에서 관찰하지 않는 스케줄러 설정은 `scripts/quality_gate.py:19`(파일별 90/85/80) 달성에 불필요한 부담이 된다. 대신 코드 발송 요청뿐 아니라 코드 확인(`verify`) 요청 시에도 만료 행을 즉시 삭제하도록 명시하면 결정론적이면서 F7("만료 즉시 삭제")을 충족한다.
- **교체 문장 (`docs/tasks/T-073-auth-acceptance.md:64-65`):**
  > `- 만료된 코드는 코드 발송 요청 또는 코드 확인 요청(어느 이메일이든)을 처리할 때 함께 지운다.`
  > `- IT 관찰 방법: 시계를 10분 넘긴 뒤 다른 이메일로 코드를 요청(또는 만료된 코드를 확인 요청)하고, JdbcTemplate로 email_codes에 이전 이메일 행이 0개임을 확인한다. 성공·5회 실패 뒤에도 행 0개를 확인한다.`

#### [P2-1] `400 VALIDATION_FAILED`의 `fieldErrors[].field` 이름 및 `429 Retry-After` 산식 명시 (`docs/tasks/T-073-auth-acceptance.md:28-30, 37, 57-58`)

- **근거:** 슬라이스 1(`backend/src/acceptance/java/kr/needteacher/tutorprofile/TutorProfileAcceptanceIT.java:791-800`)은 `VALIDATION_FAILED` 검증 시 `fieldErrors[].field` 이름을 단언한다. `429`의 `Retry-After`도 남은 초의 올림 정수(최소 `1`)임을 적시하면 59초 경계(`Retry-After: 1`)와 24시간 10회 초과 시 기대값이 명확해진다.
- **교체 문장 (`docs/tasks/T-073-auth-acceptance.md:37` 아래에 추가):**
  > `- VALIDATION_FAILED의 fieldErrors[].field 이름은 요청 JSON 필드명(email, code, role, ageOver14)을 쓴다. 429의 Retry-After 헤더는 다음 발송 가능 시각(60초 쿨다운 시각과 24시간 내 가장 오래된 발송+24시간 중 늦은 시각)까지 남은 초의 올림 정수(최소 1)다.`

---

### 항목별 검토 결과

#### 1. 조사(T-071 `docs/research/auth.md`)와의 대조

- **시간 검증을 IT `@Primary Clock` 빈으로 한 것 → 동의.**
  - `TestIntegrityTest.java:36-56,78-95`는 `@MockitoBean`·`@MockitoSpyBean`·`@TestBean`과 `DataSource`/`EntityManagerFactory`/`Flyway` 반환 `@Bean`만 금지하고, `GuardBypassIntegrityTest.java:15-33` 및 `scripts/quality_gate.py:60-70`도 `Clock` 빈을 막지 않는다. 9분 59초/10분 0초, 59초/60초 경계와 F7 물리 삭제를 인수 테스트(`*IT`)에서 직접 고정할 수 있어 단위 테스트에만 맡기는 것보다 훨씬 강력하다.
- **IP별 요청 제한을 뺀 것 → 동의.**
  - 리버스 프록시(`ForwardedHeaderFilter` / 신뢰 프록시 대역) 구성이 정해지기 전에 `remoteAddr`로 제한하면 모든 요청이 같은 프록시 IP로 묶여 오작동할 위험이 크므로 출시 전 확인 목록으로 미루는 판단이 타당하다.
- **`purpose` 분리를 F3(대학 이메일)로 미룬 것 → 동의.**
  - 이번 조각(슬라이스 2)에는 로그인/가입 코드 한 종류만 존재하므로 YAGNI 원칙에 맞고, F3 계약에서 교차 사용 거부를 인수 테스트로 고정하면 충분하다.
- **코드 확인(`verify`)과 신규 가입(`signup`)을 2단계로 분리한 것 → 동의.**
  - T-071 3절에서 지적한 "이메일 소유권 증명 전 계정 유무/역할 노출 방지"를 가장 깔끔하게 해결한 설계다.

#### 2. 명세(`docs/proposals/mvp.md` F1·F7)와의 대조

- **어긋나는 곳 없음.** 6자리·10분·5회 실패 무효·1분 재전송·만료 즉시 삭제·역할 불변·14세 이상 체크·탈퇴 시 프로필 즉시 삭제 및 복원 불가(`docs/proposals/mvp.md:31-38,123-131`)가 모두 반영되었다.
- **새로 구체화한 것(24시간 10회 상한, `verify` → `signup` 세션 연계, 세션/유휴 14일, 익명 로그아웃 `204`, 탈퇴 시 타 세션 즉시 무효화):** 모두 명세 취지(전단지 대체 지역 서비스의 로그인 유지, 계정 열거·메일 폭탄 방지, 탈퇴 즉시 효력 발생)에 부합한다.

#### 3. 구현 가능성 검증 (공식 문서·바이트코드 근거)

1. **쿠키 이름 `SESSION` + `Max-Age` 14일 + 유휴 14일 (톰캣 메모리 세션 설정만으로 가능한가):**
   - **가능하다.** `spring-boot-web-server:4.1.1`의 `ServletContextInitializers$SessionConfiguringInitializer#configureSessionCookie`(바이트코드 0~221)와 `spring-boot-tomcat:4.1.1`의 `TomcatServletWebServerFactory#configureSession`·`configureCookieProcessor` 확인 결과, `application.properties`에 아래 5줄만 추가하면 코드 없이 적용된다.
     - `server.servlet.session.cookie.name=SESSION`
     - `server.servlet.session.cookie.http-only=true`
     - `server.servlet.session.cookie.same-site=lax`
     - `server.servlet.session.cookie.max-age=14d`
     - `server.servlet.session.timeout=14d`
2. **코드 확인·가입 시 세션 ID 교체 + CSRF 토큰 교체 (`403`):**
   - **가능하다(단, 위 P0-1·P1-1 반영 필요).** 세션 ID 교체는 기존 세션이 있을 때 `request.changeSessionId()`(또는 `ChangeSessionIdAuthenticationStrategy`)로 수행하고, 새 세션 생성·저장 시 `HttpSessionSecurityContextRepository#saveContext`를 호출한다. CSRF 토큰 교체는 위 **P0-1**에서 확인한 대로 `CookieCsrfTokenRepository.withHttpOnlyFalse()`의 `generateToken` + `saveToken`을 명시 호출(또는 즉시 로딩 핸들러 설정)해야 새 `Set-Cookie: XSRF-TOKEN=...`가 내려간다.
3. **익명 로그아웃 `204` + CSRF 적용 (`logout()` 설정만으로 가능한가):**
   - **가능하다.** `spring-security-web:7.1.1`의 `LogoutFilter#doFilter`(바이트코드 0~79)와 `LogoutConfigurer#createLogoutRequestMatcher`(바이트코드 0~19) 확인 결과, 필터 체인에서 `CsrfFilter` → `LogoutFilter` → `AuthorizationFilter` 순서로 동작한다. 따라서 `SecurityConfig`에 `.logout(logout -> logout.logoutUrl("/api/v1/auth/logout").logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))`만 설정하면:
     - CSRF가 활성화되어 있으므로 `POST`만 매칭되고 토큰이 없거나 틀리면 `CsrfFilter`가 `403 FORBIDDEN`(`JsonAccessDeniedHandler`)을 반환한다.
     - 유효한 CSRF 토큰이 오면 `AuthorizationFilter` 전에 `LogoutFilter`가 가로채므로 익명 요청(`Authentication == null`)이어도 `401` 없이 세션을 무효화하고 `204`를 반환한다.
4. **탈퇴한 계정의 다른 세션 거부 + 슬라이스 1(`TutorProfileAcceptanceIT`) 호환 + `ConfigurationIntegrityTest`·`LayerArchitectureTest` 준수 위치:**
   - **가능하다(두 가지 구현 방식 모두 하네스 통과).**
     - **방법 A (세션 레지스트리/맵에서 타 세션 직접 무효화 — 가장 단순):** `kr.needteacher.account` 패키지 안의 세션 관리 컴포넌트가 로그인/가입 시 `accountId -> Set<HttpSession>`을 등록하고, `DELETE /api/v1/me` 처리 시 해당 `accountId`의 모든 `HttpSession`에 `invalidate()`를 호출한다. 이 방식은 `SecurityConfig`를 건드리지 않고 슬라이스 1의 `user("<임의 ID>")`에도 전혀 영향을 주지 않는다.
     - **방법 B (묘비 행 확인 `OncePerRequestFilter`):** "활성 계정이 존재하는가"가 아니라 **"탈퇴(묘비) 처리된 계정 ID인가"**만 확인해, 묘비 ID일 때만 세션을 무효화하고 `SecurityContext`를 비운다. 슬라이스 1의 임의 UUID는 묘비 테이블에 없으므로 그대로 통과한다.
     - **하네스 규칙과 부딪히지 않는 위치:**
       1. `ConfigurationIntegrityTest.java:35-69`는 `SecurityConfig`의 **필드(0개), 생성자(무인자 1개), 선언 메서드(`securityFilterChain` 1개)**만 제한하고 **`securityFilterChain`의 메서드 파라미터는 제한하지 않는다**(`SecurityConfig.java:18-21`에서 이미 핸들러 2개를 파라미터 주입받음).
       2. 단, `LayerArchitectureTest.java:51-52`가 `kr.needteacher.(*)..` 패키지 간 순환 의존을 금지하므로, 만약 `SecurityConfig`(`security`)가 필터를 주입받는다면 필터를 `kr.needteacher.security`에 두고 콜백 인터페이스만 정의하거나, `account` → `security` 역방향 참조가 없도록 단방향(`security` → `account`, `account` → `tutorprofile`)을 유지해야 한다.
5. **`429` + `Retry-After` 정수 초:**
   - **가능하다.** `GlobalExceptionHandler.java`에 `TooManyRequestsException` 핸들러를 추가해 `ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds)).body(new ErrorResponse("TOO_MANY_REQUESTS", ...))`를 반환하면 된다. 단, 코드 행(`email_codes`)은 성공·5회 실패·교체·만료 시 즉시 물리 삭제(`T-073:63-64`)되므로, **발송 제한 이력(60초·24시간 10회)은 `email_codes`와 별도 테이블/저장소에 기록**해야 물리 삭제 후에도 60초·24시간 제한(`T-073:57-58`)이 유지된다.

#### 4. 빠진 것

- 위 **P0-1, P1-1, P1-2, P1-3, P2-1** 외에 추가로 막히는 요구사항은 없다.

## 결과

- 변경 파일: `docs/tasks/T-075-auth-contract-review-agy.md` (상태 `done` 및 리뷰·결과 작성).
- 판정: **고쳐서 확정** (P0 1건, P1 3건, P2 1건 교체 문장 제시).
- 핵심 발견(P0-1): `csrf.spa()`(`CookieCsrfTokenRepository`)는 무상태 쿠키-헤더 대조 방식이라 "새 `XSRF-TOKEN` 쿠키 + 이전 `X-XSRF-TOKEN` 헤더" 조합일 때 `403`이 남을 명시해야 `RestTestClient` 테스트와 구현이 일치함.
- 핵심 발견(P1): ① `SIGNUP_REQUIRED` 시 사전 세션 유무 및 구 세션의 `/signup` `401` 검증 명시, ② `/signup`에서 "이미 로그인한 세션 `409`"를 "확인된 이메일 없음 `401`"보다 먼저 판정, ③ 비결정적 1분 주기 스케줄러 문구 대신 요청(`email-codes`/`verify`) 시점 만료 행 삭제로 통일.
- 구현 검증: 톰캣 `SESSION`·14일 설정, `LogoutFilter` 익명 `204`+CSRF `403`, 슬라이스 1을 깨지 않는 타 세션 탈퇴 무효화 및 `ConfigurationIntegrityTest`·`LayerArchitectureTest` 준수 위치를 바이트코드로 확인 완료.
