---
id: T-082
owner: agy
status: done    # todo | doing | blocked | done
scope:
  - docs/tasks/T-082-auth-impl-unknowns-agy.md
---

## 목표

구현(T-077) 전에 확인이 필요한 Spring 동작 두 가지를 바이트코드·공식 문서로 확인한다. 코드는 쓰지 않는다.

**피드백 먼저:** 네 T-080은 "지적 없음"이었는데, 독립 리뷰(T-079 P0-1)가 구현 쪽 P0을 찾았다. CSRF가 켜져 있으면 `CsrfConfigurer`가 로그아웃에 `CsrfLogoutHandler`를 붙여 `XSRF-TOKEN`을 지우므로, 로그아웃 직후 토큰 없이 보낸 POST는 `403`이다(테스트는 `401`을 기대했다). T-075에서 직접 `.logout()`을 권했으니 짚었어야 할 부분이다. 아래 확인에서는 "권장한 설정이 만드는 부수 효과"까지 본다.

## 확인할 것

1. **`CsrfLogoutHandler`:** 위 동작이 Spring Security 7.1.1에서 맞는지(`CsrfConfigurer`가 언제 붙이나, `CookieCsrfTokenRepository#saveToken(null, ...)`이 `Max-Age=0` 쿠키를 내는지). 계약(T-073)은 이제 "로그아웃은 XSRF도 지운다"로 적혀 있다.
2. **메일 자동 설정과 `DynamicPropertyRegistrar`:** 테스트 헬퍼(`backend/src/test/java/kr/needteacher/account/AuthTestConfiguration.java`)는 `spring.mail.host`·`port`를 `DynamicPropertyRegistrar`로 넣는다. Boot 4.1.1의 `MailSenderAutoConfiguration`(또는 같은 역할) 조건이 이 값을 보는지. 안 본다면 운영 `application.properties`에 `spring.mail.host` 기본값이 있어야 `JavaMailSender` 빈이 생기고, 그 뒤 실제 접속 주소는 레지스트라 값이 이기는지(`MailProperties` 바인딩 시점).
3. **세션 유휴 설정값:** 테스트는 `Environment`에서 `server.servlet.session.timeout`을 `Duration`으로 읽어 14일을 기대한다. `14d`로 쓰면 맞고, 단위 없는 숫자(`1209600`)는 밀리초로 읽혀 틀리는지.

## 완료 조건

- [x] `## 리뷰`에 1~3 각각 결론 + 근거(클래스·메서드·바이트코드 오프셋 또는 문서 링크) + 구현 카드(T-077)에 넣을 한 줄.
- [x] 결과 10줄 이내.

## 참고

T-073, T-077, T-079 `## 리뷰`, `docs/research/auth.md`.

## 리뷰

### 1. `CsrfLogoutHandler`와 로그아웃 시 `XSRF-TOKEN` 쿠키 삭제

- **결론:** **맞다.** `csrf.spa()`와 `.logout(...)`이 함께 켜져 있으면 `CsrfConfigurer#configure`가 `LogoutConfigurer`에 `CsrfLogoutHandler`를 자동 등록하고, 로그아웃 요청 처리 시 `CookieCsrfTokenRepository#saveToken(null, request, response)`가 호출되어 빈 값(`""`)과 `Max-Age=0`을 가진 `Set-Cookie: XSRF-TOKEN=; Max-Age=0; ...` 응답 헤더가 내려간다. 따라서 로그아웃 직후 `AuthHttpClient`는 `XSRF-TOKEN` 쿠키를 제거하며(`AuthHttpClient.java:87-88`), 새 GET 요청 없이 보낸 다음 POST는 `CsrfFilter`에서 `403 FORBIDDEN`이 된다.
- **근거 (Boot 4.1.1 BOM 기준 `spring-security-config:7.0.6` · `spring-security-web:7.0.6` 바이트코드):**
  1. `org.springframework.security.config.annotation.web.configurers.CsrfConfigurer#spa()` (`offset 0–19`):
     - `offset 1–4`: `CookieCsrfTokenRepository.withHttpOnlyFalse()`를 호출해 `this.csrfTokenRepository` 필드에 저장(`putfield #10`).
  2. `CsrfConfigurer#configure(H)` (`offset 94–128`):
     - `offset 94–105`: `http.getConfigurer(LogoutConfigurer.class)` 조회.
     - `offset 107–128`: `LogoutConfigurer`가 존재하면(`ifnull 129`) `new CsrfLogoutHandler(this.csrfTokenRepository)`를 생성(`offset 114–122`)하여 `LogoutConfigurer#addLogoutHandler(LogoutHandler)`(`offset 125`)로 등록.
  3. `org.springframework.security.web.csrf.CsrfLogoutHandler#logout(HttpServletRequest, HttpServletResponse, Authentication)` (`offset 0–12`):
     - `offset 0–7`: `this.csrfTokenRepository.saveToken(null, request, response)` 호출(`offset 4: aconst_null`, `offset 7: invokeinterface #21`).
  4. `org.springframework.security.web.csrf.CookieCsrfTokenRepository#saveToken(CsrfToken, HttpServletRequest, HttpServletResponse)` (`offset 0–248`):
     - `token == null` 분기에서 쿠키 값을 `""`로 두고(`offset 0–13: aload_1; ifnull 13; ... 13: ldc #63 // ""`), `ResponseCookieBuilder#maxAge(long)`에 `0L`을 전달(`offset 81–94: aload_1; ifnull 93; ... 93: lconst_0; 94: invokeinterface #108`).
     - 이후 `mapToCookie(ResponseCookie)`(`offset 33–41: Duration#getSeconds → l2i → Cookie#setMaxAge(0)`)를 거쳐 `response.addCookie(cookie)`(`offset 159`)를 호출하고, `request.setAttribute(CSRF_TOKEN_REMOVED_ATTRIBUTE_NAME, Boolean.TRUE)`(`offset 224–231`)를 남긴다.
- **구현 카드(`T-077`)에 넣을 한 줄:**
  > `- 로그아웃은 SecurityConfig의 .logout(...)으로 두며, CsrfConfigurer가 자동 등록하는 CsrfLogoutHandler가 XSRF-TOKEN 쿠키(Max-Age=0)를 함께 지우므로 이를 막지 않고 기본 동작을 그대로 유지한다(T-073:95).`

---

### 2. 메일 자동 설정(`MailSenderAutoConfiguration`)과 `DynamicPropertyRegistrar` 실행 순서

- **결론:** **자동 설정 조건(`@ConditionalOnProperty`)은 `DynamicPropertyRegistrar`가 넣은 값을 보지 못한다.** 따라서 **운영 `application.properties`에 `spring.mail.host=localhost`(등 비어 있지 않은 기본값)가 반드시 있어야** `MailSenderPropertiesConfiguration` 조건이 통과되어 `JavaMailSenderImpl` 빈 정의가 등록된다. 그 뒤 빈 초기화 단계에서 `DynamicPropertyRegistrar`가 `Environment` 최우선순위(`addFirst`)로 등록한 Mailpit의 `spring.mail.host`·`spring.mail.port` 값이 `MailProperties` 바인딩 시점에 이겨 실제 메일은 Mailpit으로 발송된다.
- **근거 (`spring-context:7.0.8` · `spring-test:7.0.8` 바이트코드 및 Spring Boot `MailSenderPropertiesConfiguration`):**
  1. **컨텍스트 리프레시 순서 (`org.springframework.context.support.AbstractApplicationContext#invokeBeanFactoryPostProcessors`, `offset 0–183`):**
     - `offset 5`: `PostProcessorRegistrationDelegate.invokeBeanFactoryPostProcessors(beanFactory, getBeanFactoryPostProcessors())`가 **먼저** 실행된다. 여기서 `ConfigurationClassPostProcessor`가 자동 설정 클래스를 파싱하며 `MailSenderPropertiesConfiguration`의 `@ConditionalOnProperty(prefix = "spring.mail", name = "host")`를 평가한다.
     - `offset 124–172`: 모든 `BeanFactoryPostProcessor`가 끝난 **뒤에야** `beanFactory.getBeanNamesForType(BeanFactoryInitializer.class, false, false)`를 찾아(`offset 129`) `BeanFactoryInitializer#initialize(beanFactory)`를 호출(`offset 172`)한다.
  2. **`DynamicPropertyRegistrar` 실행 시점 (`org.springframework.test.context.support.DynamicPropertyRegistrarBeanInitializer#initialize`, `offset 17–131`):**
     - `DynamicPropertyRegistrarBeanInitializer`는 `BeanFactoryPostProcessor`가 아닌 `BeanFactoryInitializer` 구현체다.
     - `initialize()` 안에서 `DynamicValuesPropertySource.getOrCreate(this.environment)`(`offset 33`)를 부르고, `DynamicValuesPropertySource#getOrCreate`(`offset 41–44`)가 `MutablePropertySources#addFirst`로 `"Dynamic Test Properties"`를 최우선 PropertySource로 넣은 뒤 `DynamicPropertyRegistrar#accept(DynamicPropertyRegistry)`(`offset 120`)를 실행한다.
     - 따라서 `application.properties`에 `spring.mail.host`가 없으면 `offset 5`에서 `@ConditionalOnProperty`가 `false`가 되어 `JavaMailSender` 빈 정의 자체가 등록되지 않는다(`NoSuchBeanDefinitionException`). 반대로 `application.properties`에 `spring.mail.host=localhost`가 있으면 `offset 5`에서 빈 정의가 등록되고, `offset 172`에서 `DynamicValuesPropertySource`가 `addFirst`로 들어간 뒤 싱글턴 생성 단계(`finishBeanFactoryInitialization`)에 `MailProperties`가 바인딩될 때는 최우선 PropertySource인 Mailpit 호스트·포트 값이 적용된다.
- **구현 카드(`T-077`)에 넣을 한 줄:**
  > `- application.properties에 spring.mail.host=localhost(및 spring.mail.port=1025)를 반드시 둔다 — DynamicPropertyRegistrar(BeanFactoryInitializer)는 자동 설정 조건(@ConditionalOnProperty) 평가 뒤에 실행되므로 기본값이 없으면 JavaMailSender 빈이 생성되지 않고, 실제 접속 시에는 레지스트라의 최우선(addFirst) 값이 이긴다.`

---

### 3. 세션 유휴 설정값 (`server.servlet.session.timeout`과 `Duration` 변환)

- **결론:** **맞다.** `AuthAcceptanceIT`가 호출하는 `environment.getProperty("server.servlet.session.timeout", Duration.class)`는 대상 필드의 `@DurationUnit(ChronoUnit.SECONDS)` 메타데이터 없이 문자열을 `Duration`으로 변환하므로, 단위 없는 숫자(`1209600`)를 쓰면 기본 단위인 **밀리초(`1209600ms` = 20분 9.6초)**로 읽혀 단언이 실패한다. 반드시 **`14d`**(또는 `1209600s`)처럼 단위를 명시해야 `Duration.ofDays(14)`가 된다.
- **근거 (`spring-boot-web-server:4.1.1` · `spring-boot:4.1.1` 바이트코드):**
  1. **바인딩 시 기본 단위 (`org.springframework.boot.web.server.servlet.Session`, `spring-boot-web-server-4.1.1.jar`):**
     - `private java.time.Duration timeout` 필드에 `@org.springframework.boot.convert.DurationUnit(value = ChronoUnit.SECONDS)` 애노테이션이 선언되어 있어, `ServerProperties` 바인딩 시에는 단위 없는 숫자를 초(SECONDS)로 해석한다.
  2. **`Environment#getProperty` 직접 변환 (`org.springframework.boot.convert.StringToDurationConverter` · `DurationStyle$1` · `DurationStyle$Unit`, `spring-boot-4.1.1.jar`):**
     - `environment.getProperty("...", Duration.class)`는 필드 애노테이션이 없는 `TypeDescriptor.valueOf(Duration.class)`를 넘기므로, `StringToDurationConverter#getDurationUnit(TypeDescriptor)`(`offset 0–24`)가 `null`(`offset 23: aconst_null`)을 반환한다.
     - `DurationStyle$1`(`SIMPLE`, 패턴 `^([+-]?\d+)([a-zA-Z]{0,2})$`)의 `parse(String, ChronoUnit)`(`offset 0–50`)은 단위 접미사(`group(2)`)가 있으면(`"14d"`) `DurationStyle$Unit.fromSuffix("d")`(`offset 32`)로 `DAYS`를 선택해 `Duration.of(14L, ChronoUnit.DAYS)`를 반환하지만, 접미사가 없으면(`"1209600"`) `DurationStyle$Unit.fromChronoUnit(null)`(`offset 38–39`)을 호출한다.
     - `DurationStyle$Unit#fromChronoUnit(ChronoUnit)`(`offset 0–7`)은 인자가 `null`일 때 **`MILLIS`(`offset 4: getstatic #10 // Field MILLIS`)**를 반환하므로 `Duration.ofMillis(1209600)`이 된다.
- **구현 카드(`T-077`)에 넣을 한 줄:**
  > `- application.properties의 server.servlet.session.timeout=14d(및 server.servlet.session.cookie.max-age=14d)는 반드시 단위 접미사(14d)를 붙여 적는다 — 단위 없는 숫자는 Environment#getProperty(..., Duration.class)에서 밀리초(MILLIS)로 변환되어 테스트가 실패한다.`

## 결과

- 3가지 항목 모두 로컬 Gradle 캐시의 Spring Boot `4.1.1` / Spring Framework `7.0.8` / Spring Security `7.0.6` 실제 바이트코드(`javap -c -p -v`)로 검증 완료.
- 1(`CsrfLogoutHandler`): `CsrfConfigurer#configure`(`offset 94-128`)가 `LogoutConfigurer`에 `CsrfLogoutHandler`를 자동 추가하고 `CookieCsrfTokenRepository#saveToken(null, ...)`(`offset 81-94`)이 `Max-Age=0` 쿠키를 내려 `XSRF-TOKEN`을 삭제함.
- 2(`DynamicPropertyRegistrar`): `AbstractApplicationContext#invokeBeanFactoryPostProcessors`에서 `@ConditionalOnProperty` 평가(`offset 5`)가 `BeanFactoryInitializer`(`DynamicPropertyRegistrarBeanInitializer`, `offset 172`)보다 먼저 실행되므로, `application.properties`에 `spring.mail.host=localhost`가 있어야 빈 정의가 생기고 바인딩 시에는 `addFirst`된 레지스트라 값이 이김.
- 3(`session.timeout`): `Environment#getProperty(..., Duration.class)`는 `@DurationUnit` 없이 `DurationStyle$Unit#fromChronoUnit(null)`(`offset 0-7`)을 타서 단위 없는 숫자를 `MILLIS`로 읽으므로 반드시 `14d`로 설정해야 함.
