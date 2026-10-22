---
id: T-071
owner: agy
status: done    # todo | doing | blocked | done
scope:
  - docs/research/auth.md
  - docs/tasks/T-071-auth-research.md
---

## 목표

MVP 2번 조각(F1a 이메일 코드 로그인 + 탈퇴, `docs/proposals/mvp.md` F1·F7) 계약을 쓰기 전에, 기술 선택의 근거를 공식 문서·1차 출처로 모은다. 코드는 쓰지 않는다. 오케스트레이터가 이 조사를 바탕으로 계약 초안(T-072)을 쓴다.

## 조사할 것

스택은 Spring Boot 4.1.1 / Spring Security 7 / Java 17 / PostgreSQL / Flyway(`ddl-auto=validate`)다. 프론트는 아직 없고 나중에 같은 도메인의 SPA(Vite + React)가 붙는다. 항목마다 **권장안 하나 + 근거 링크(직접 연 페이지)**. 버전에 따라 다르면 4.1/7.x 기준으로 적는다.

1. **세션 인증 + CSRF (SPA):** 커스텀 컨트롤러(이메일 코드 확인)에서 로그인시킬 때의 정석.
   - `SecurityContextRepository`에 저장하는 방법과 세션 고정 방지(세션 ID 교체)를 어떻게 하는지.
   - SPA용 CSRF 설정(`csrf.spa()` 같은 것이 7.x에 있는지, `CookieCsrfTokenRepository`). 로그인 전 요청(코드 요청·확인)에도 CSRF를 걸어야 하는지(로그인 CSRF).
   - 쿠키 속성 권장값(HttpOnly, Secure, SameSite). 로그아웃 처리.
2. **세션 저장소:** 톰캣 메모리 세션과 Spring Session JDBC 비교. JDBC로 갈 때 의존성 이름과, 테이블을 Flyway로 만들어야 하는지(우리는 `ddl-auto=validate`, 자동 스키마 생성 금지). 나중에 메모리 → JDBC로 바꿀 때 코드 변경이 있는지.
3. **이메일 코드(OTP) 보안 기준:** OWASP 등 1차 출처로.
   - 코드 길이·유효 시간·시도 횟수.
   - 이메일별·IP별 요청 제한.
   - 계정 존재 여부 노출(열거) 방지: 응답을 어떻게 똑같이 만드나.
   - 저장 시 해시 여부, 비교 방식(상수 시간).
   - 우리 명세(6자리, 10분, 5회 틀리면 무효, 재전송 1분 1회)에서 빠진 것이 있으면 적는다.
4. **메일 테스트 인프라:** 우리 하네스는 IT에서 목·빈 바꿔치기를 금지하고 외부 연동은 Testcontainers·WireMock으로 대체한다(`backend/AGENTS.md` 테스트 절).
   - Mailpit과 GreenMail을 Testcontainers로 띄우는 방법을 비교한다(이미지 이름·고정할 버전 태그, 받은 메일을 HTTP API로 읽는 방법).
   - Spring Boot 4.1에서 `spring.mail.*`를 컨테이너에 연결하는 방법(`@ServiceConnection` 지원 여부, 아니면 `DynamicPropertyRegistrar`).
5. **실제 HTTP 테스트 클라이언트:** Spring Boot 4.1에서 랜덤 포트 서버에 쿠키를 들고 요청하는 테스트 도구. `TestRestTemplate`·`RestTestClient`의 모듈 이름, 필요한 애노테이션, 쿠키 유지 방법. MockMvc로 세션 ID 교체·쿠키 속성을 검증할 수 있는지도 적는다.
6. **운영 메일 발송 (참고만):** 네이버·다음·Gmail 수신을 기준으로 할 때 SPF·DKIM·DMARC 요구사항, 후보 서비스 2~3개(예: AWS SES 서울, 네이버 클라우드 Outbound Mailer)와 현재 가격. 지금 결정하지 않으며 출시 전 확인용이다.

## 완료 조건

- [x] `docs/research/auth.md`에 1~6 항목별 권장안·근거 링크·주의점. 링크는 직접 열어 확인한 것만.
- [x] 각 항목에서 "우리 하네스 규칙과 부딪히는 점"이 있으면 따로 표시한다(예: 테스트용 빈 교체가 필요해지는 방식).
- [x] 결과 10줄 이내.

## 참고

`docs/proposals/mvp.md` F1·F7, `docs/proposals/test-harness.md` 3절, `backend/AGENTS.md`, 기존 조사 형식은 `docs/research/regulation.md`.

## 결과

- 변경 파일: `docs/research/auth.md` 생성, `docs/tasks/T-071-auth-research.md` 상태·결과 갱신.
- 검증: 공식 문서 18개 URL 직접 열람(`200 OK`) 및 로컬 Gradle 캐시의 Spring Boot 4.1.1 / Spring Security 7.1.1 / Spring Framework 7.0.9 바이트코드(`javap`) 대조 확인.
- 1~2(세션·CSRF·저장소): 수동 로그인은 `CompositeSessionAuthenticationStrategy`(`ChangeSessionId`+`Csrf`) + `HttpSessionSecurityContextRepository#saveContext`, CSRF는 `csrf.spa()`(코드 요청·확인·로그아웃 모두 적용), MVP는 톰캣 메모리 세션으로 시작하되 `Serializable` Principal과 쿠키명 고정을 권장. `ConfigurationIntegrityTest` 때문에 `SecurityConfig`에 추가 `@Bean` 선언 불가.
- 3(OTP 보안): `SecureRandom` 6자리·10분·5회 실패 폐기 외에 **재발송 시 구 코드 즉시 폐기, 1회 사용 즉시 폐기, `purpose`(로그인/대학메일) 분리, 코드 확인 전 계정/역할 비노출, SHA-256+`MessageDigest.isEqual` 상수 시간 비교, `Clock` 주입**을 보완 항목으로 도출.
- 4~6(메일 테스트·HTTP 클라이언트·운영 발송): Boot 4.1은 Mail `@ServiceConnection` 미지원이라 `axllent/mailpit:v1.31.6` + `DynamicPropertyRegistrar` 권장(`spring-boot-starter-mail` 추가 시 보호 파일 `build.gradle.kts` 잠금 해제 필요). 실제 세션 쿠키·ID 교체 검증은 `MockMvc`로 불가하므로 이미 전이 포함된 `@AutoConfigureRestTestClient` + `RestTestClient`(`RANDOM_PORT`) 권장.
