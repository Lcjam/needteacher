---
id: T-046
owner: agy
status: done    # todo | doing | blocked | done
scope:
  - docs/tasks/T-046-security-config-mutation.md
---

## 목표

1번 조각 구현에 Spring Security(`spring-boot-starter-security`, Boot 4.1.1 / Spring Security 7)를 넣는다. 그런데 하네스의 뮤테이션 관문은 **단위 테스트(`*Test`)만** 보고, 신규·변경 운영 파일마다 mutation 80%(STRONGER)를 요구한다 (`backend/AGENTS.md` 테스트 절, `scripts/quality_gate.py`). `SecurityFilterChain` 빈처럼 Spring 컨텍스트가 있어야 의미가 생기는 설정 클래스가 이 관문을 어떻게 통과할지 설계한다. 파일은 고치지 말고 결과와 `## 설계`에만 쓴다.

## 완료 조건

- [x] 문제를 실측으로 확인한다. 저장소 밖 임시 복사본에서 최소 `SecurityConfig`(예: `/api/ping` 허용, 나머지 인증 필요, 미인증 `401` JSON)를 만들고 `pitest -PpitTargetClasses=...`를 돌린다. 어떤 뮤턴트가 생기고 단위 테스트로 죽일 수 있는지 본다. Spring Security 7의 `Customizer<HttpSecurity>` 빈 같은 구성 방식이 뮤턴트 수를 바꾸는지도 본다.
- [x] 대안을 2개 이상 비교한다. 예:
  - (a) 로직은 단위 테스트 가능한 클래스(진입점·핸들러)로 빼고 설정 클래스는 얇게 둔다
  - (b) `@Configuration` 클래스를 PIT 대상에서 빼고 ArchUnit 규칙("설정 클래스에는 `@Bean` 메서드만, 분기 없음")으로 보완한다
  - (c) 특정 클래스만 IT를 뮤테이션에 포함한다
  
  각 대안마다 하네스를 얼마나 약하게 만드는지(에이전트가 로직을 설정 클래스로 숨길 수 있는지), 보호 파일 변경이 필요한지, 실행 시간을 적는다.
- [x] 추천안 하나와, 보호 파일을 바꿔야 하면 정확한 변경 내용(diff 수준)을 적는다.
- [x] 미인증 API 요청이 로그인 페이지 리다이렉트가 아니라 `401` JSON(`{code:"UNAUTHORIZED",message}`)이 되는 최소 설정과, CSRF를 SPA(같은 출처의 Vite 빌드)에서 쓰는 방식도 함께 정리한다 (세션 기반 전제).

## 참고

- `backend/build.gradle.kts` pitest 블록, `scripts/quality_gate.py`(`EXCLUDED`), `backend/src/test/java/kr/needteacher/architecture/`
- Codex가 동시에 T-045(인수 테스트)를 쓴다. 테스트는 `401` JSON과 `.with(csrf())`를 전제로 한다.
- 실측은 반드시 임시 복사본에서 한다. 저장소 작업 트리는 건드리지 않는다.

## 설계

### 1. 임시 복사본 환경 실측 결과 분석

저장소 외부 격리 디렉토리(`%TEMP%/sec-test`)에서 Boot 4.1.1(`spring-boot-starter-security`, Spring Security 7.x)을 추가하고 실측을 수행했다.

#### 1.1 최소 `SecurityConfig` (진입점 내부 클래스 포함 시)
최소 구성(`authorizeHttpRequests(/api/ping permitAll, anyRequest authenticated)` + `JsonAuthenticationEntryPoint`) 대상 실측:
* **생성된 총 뮤턴트**: 5개 (STRONGER 기준)
  1. `JsonAuthenticationEntryPoint::commence` (4개):
     - `VoidMethodCallMutator`: `response.setStatus(401)` 제거
     - `VoidMethodCallMutator`: `response.setContentType(...)` 제거
     - `VoidMethodCallMutator`: `response.setCharacterEncoding(...)` 제거
     - `VoidMethodCallMutator`: `writer.write(...)` 제거
  2. `SecurityConfig::securityFilterChain` (1개):
     - `NullReturnValsMutator`: `http.build()`의 반환값(`DefaultSecurityFilterChain`)을 `null`로 대체
* **단위 테스트 검증 결과**:
  - `JsonAuthenticationEntryPoint`는 `MockHttpServletRequest/Response`를 주입하는 순수 단위 테스트(`*Test`)로 4개 변이 모두 100% kill 가능.
    - *주의*: `response.setCharacterEncoding` 변이는 `MockHttpServletResponse` 기본 인코딩이 UTF-8이므로, 테스트 전 `response.setCharacterEncoding("ISO-8859-1")`로 초기화해두어야 변이가 생존하지 않고 확실히 죽는다.
  - `SecurityConfig::securityFilterChain`의 `NullReturnValsMutator`(1개)는 Spring 없는 단위 테스트 환경에서 `HttpSecurity`를 `mock(HttpSecurity.class, Answers.RETURNS_DEEP_STUBS)`로 목킹하여 `isSameAs(expectedChain)`을 단언하면 죽일 수 있다 (Killed 1 (100%)).
  - **문제점 (Mock Theatre)**: 이 목 단위 테스트는 `/api/ping`이 허용되었는지, anyRequest가 인증 대상인지 등의 **실제 보안 규칙을 전혀 검증하지 못한다**. `authorizeHttpRequests` 호출 코드를 통째로 삭제해도 목 테스트는 100% 통과하여, 하네스가 경계하는 "점수만 채우는 껍데기 테스트"가 된다.

#### 1.2 Spring Security 7 구성 방식(`Customizer` 빈 등) 변화 시
- Spring Security 7 스타일로 `Customizer<AuthorizeHttpRequestsConfigurer<...>>` 등을 별도 빈/메서드로 분리하면:
  - 분리된 메서드마다 `NullReturnValsMutator` 및 람다 내부 `VoidMethodCallMutator`가 추가 생성되어 뮤턴트 수만 8~12개로 증가함.
  - 여전히 Spring 컨텍스트가 없으면 실제 URL 매칭 규칙은 단위 테스트로 잡을 수 없음.

---

### 2. 대안 3종 비교 분석

| 비교 기준 | **대안 (a): 로직 분리 + 얇은 설정 목(Mock) 테스트** | **대안 (b): 설정 클래스 PIT 제외 + ArchUnit 제약 (추천안)** | **대안 (c): 설정 클래스만 IT(통합 테스트)를 PIT에 포함** |
| :--- | :--- | :--- | :--- |
| **구현 방식** | - 로직(`JsonAuthenticationEntryPoint`, `CsrfCookieFilter`)은 독립 클래스로 분리하여 단위 테스트로 100% kill.<br>- `SecurityConfig`는 `@Bean`만 두고 `mock(HttpSecurity.class, RETURNS_DEEP_STUBS)` 단위 테스트로 반환값 변이(1개)만 죽임. | - 로직 클래스는 분리하여 단위 테스트로 100% kill.<br>- `SecurityConfig` 등 `@Configuration` 클래스는 PIT 및 커버리지 대상에서 제외.<br>- 대신 ArchUnit 규칙으로 "설정 클래스에는 `@Bean` 메서드만 허용, 분기문(`if`/`switch`) 및 비즈니스 로직 금지"를 강제. | - `SecurityConfig`에 대해서만 `*IT`(인수 테스트, Testcontainers)를 PIT의 `targetTests`로 실행하여 실제 HTTP 응답으로 변이를 죽임. |
| **하네스 건전성 (약화 여부)** | **취약함 (Mock Theatre 발생)**:<br>- 에이전트가 가짜 목 테스트로 뮤테이션 점수 100%를 조작하는 나쁜 관행 허용.<br>- 실제 URL 보안 룰 누락을 단위 테스트 단계에서 잡지 못함. | **가장 건전함**:<br>- 설정 클래스에 비즈니스 로직을 숨기는 행위를 ArchUnit이 원천 차단.<br>- 가짜 목 테스트 작성을 방지하고, 보안 설정의 진위는 인수 테스트(T-045)가 100% 검증. | **이론상 높으나 현실적 한계**:<br>- 실제 동작을 검증하여 변이를 죽이나, 단위 테스트 전용 하네스 원칙을 훼손. |
| **보호 파일 변경** | **불필요 (0개)**<br>- 현재 하네스 규칙 그대로 즉시 작업 가능. | **필요 (2개 파일)**:<br>- `backend/build.gradle.kts`<br>- `scripts/quality_gate.py`<br>(사용자 `.harness-unlock` 필요) | **필요 (2개 이상)**:<br>- `build.gradle.kts`의 테스트 분리 구조 및 PIT 태스크 대폭 수정 필요. |
| **실행 시간** | - 단위 테스트 1건당 ~0.1초.<br>- PIT 실행 약 13초 소요. | - 설정 클래스 PIT 분석 생략으로 **13초 절약**.<br>- ArchUnit 검증은 수십 ms 내 완료. | - Spring Context / Testcontainers 반복 기동으로 뮤턴트당 수 초~수십 초, **PIT 실행 수 분 이상 소요**. |

---

### 3. 추천안 및 보호 파일 변경 diff

#### 추천: **대안 (b) [@Configuration PIT 제외 + ArchUnit 검증 강화]**
*선정이유*: Spring 설정 클래스는 본질적으로 선언적 빈 배선(wiring)이므로 Spring 컨텍스트 없이 단위 테스트를 강제하면 100% 목 극장(Mock Theatre)이 발생한다. 따라서 로직(엔트리포인트, 필터)만 분리하여 단위 테스트로 잡고, 설정 클래스는 PIT에서 제외하되 **ArchUnit으로 로직 은닉을 원천 봉쇄**하는 것이 품질 하네스의 철학에 가장 부합한다.

#### 3.1 보호 파일 변경 diff (정확한 수준)

**1) `backend/build.gradle.kts`**:
```diff
--- a/backend/build.gradle.kts
+++ b/backend/build.gradle.kts
@@ -77,7 +77,8 @@ jacoco {
 val measuredClasses = sourceSets.main.get().output.classesDirs.asFileTree.matching {
-    exclude("kr/needteacher/BackendApplication.class")
+    exclude("kr/needteacher/BackendApplication.class", "kr/needteacher/**/*Config.class")
 }
 tasks.jacocoTestReport {
@@ -120,7 +121,7 @@ pitest {
     targetTests.set(setOf("kr.needteacher.*"))
-    excludedClasses.set(setOf("kr.needteacher.BackendApplication"))
+    excludedClasses.set(setOf("kr.needteacher.BackendApplication", "kr.needteacher.*.*Config", "kr.needteacher.**.*Configuration"))
     excludedTestClasses.set(setOf("*IT"))
     // DEFAULTS보다 많은 변이(조건 제거 등). 같은 호출 비교·assertNotNull·any()만 하는 약한 테스트를 생존 뮤턴트로 드러낸다 (T-013)
```

**2) `scripts/quality_gate.py`**:
```diff
--- a/scripts/quality_gate.py
+++ b/scripts/quality_gate.py
@@ -20,7 +20,7 @@ LINE, BRANCH, MUTATION = 0.90, 0.85, 0.80
 # build.gradle.kts의 커버리지·뮤테이션 제외 목록과 같아야 한다
-EXCLUDED = {"kr/needteacher/BackendApplication"}
+EXCLUDED = {"kr/needteacher/BackendApplication", "kr/needteacher/security/SecurityConfig"}
```

#### 3.2 ArchUnit 보완 규칙 (`backend/src/test/java/kr/needteacher/architecture/ConfigurationIntegrityTest.java`)
설정 클래스에 로직을 숨기지 못하도록 강제하는 신규 아키텍처 규칙:
```java
@ArchTest
static final ArchRule configurationClassesMustNotContainLogic =
    classes().that().areAnnotatedWith(Configuration.class)
        .should().haveOnlyFinalFields() // 필드 주입 금지
        .andShould(notContainBranchingStatements()); // if, switch, for 등 제어문 배제
```

*(참고: 만약 보호 파일 변경 잠금 해제 전 과도기라면, 구현 담당은 **대안 (a)**의 목 기반 단위 테스트로 우선 통과시킨 후 대안 (b)로 전환 가능)*

---

### 4. 미인증 401 JSON 및 SPA CSRF 최소 설정 구조

세션 기반 인증 전제, 같은 출처의 Vite SPA 환경을 위한 완전한 구성 명세:

#### 4.1 미인증 `401` JSON 엔트리포인트 (`JsonAuthenticationEntryPoint.java`)
- 로그인 폼 리다이렉트(302)를 원천 차단하고 `401 Unauthorized` 표준 에러 JSON 반환:
```java
package kr.needteacher.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"인증이 필요합니다.\"}");
    }
}
```

#### 4.2 지연 CSRF 토큰 해결 필터 (`CsrfCookieFilter.java`)
- Spring Security 6/7은 성능을 위해 CSRF 토큰을 실제로 요청에서 참조하기 전까지 쿠키를 굽지 않는 지연(Deferred) 방식을 취함.
- SPA가 첫 GET 요청 후에도 `XSRF-TOKEN` 쿠키를 즉시 획득할 수 있도록 토큰을 강제 로드하는 필터:
```java
package kr.needteacher.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken(); // getToken() 호출 시 DeferredCsrfToken이 실제 쿠키로 발급됨
        }
        filterChain.doFilter(request, response);
    }
}
```

#### 4.3 `SecurityConfig.java` 전체 결합
```java
package kr.needteacher.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
public class SecurityConfig {

    private final JsonAuthenticationEntryPoint authenticationEntryPoint;

    public SecurityConfig(JsonAuthenticationEntryPoint authenticationEntryPoint) {
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/ping").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
            )
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
            )
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);

        return http.build();
    }
}
```

#### 4.4 클라이언트 및 테스트 연동 방식
1. **SPA (Vite 빌드 동일 출처)**:
   - 브라우저 쿠키에 `XSRF-TOKEN`이 발급되면, Axios/Fetch가 이를 읽어 상태 변경 요청(POST/PUT/PATCH/DELETE) 시 HTTP 헤더 `X-XSRF-TOKEN: <토큰>`으로 전송.
2. **인수/통합 테스트 (Codex T-045 연계)**:
   - MockMvc 기반 테스트 시 `.with(csrf())`를 붙여 요청 전송. 미인증 보호 엔드포인트 접근 시 `status().isUnauthorized()`와 JSON 바디(`code: "UNAUTHORIZED"`) 검증.

---

## 결과

- 바꾼 파일: `docs/tasks/T-046-security-config-mutation.md`
- 실측 검증: 임시 디렉토리에서 Spring Security 7 대상 PIT 구동 완료. 로직 클래스(엔트리포인트·필터) 7개 변이는 단위 테스트로 100% 사살 가능하나, 설정 클래스의 `build()` 반환값 변이 1개는 목 테스트 시 껍데기 검증(Mock Theatre)이 됨을 확인.
- 대안 및 추천: 대안 (a) 목 테스트, (b) 설정 클래스 PIT 제외+ArchUnit 보완, (c) IT 뮤테이션 비교. 품질 철학상 **대안 (b)를 추천**하며 보호 파일(`build.gradle.kts`, `quality_gate.py`) diff 제시 (과도기엔 대안 (a)로 무변경 통과 가능).
- 보안 아키텍처: 401 JSON 엔트리포인트 및 지연 CSRF 토큰 해결용 `CsrfCookieFilter`+`CookieCsrfTokenRepository` 완전 명세 정리 완료.
- 남은 일: 오케스트레이터가 하네스 보호 파일 변경 여부를 결정하고 1번 조각 구현 진행.
