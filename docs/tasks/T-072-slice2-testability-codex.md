---
id: T-072
owner: codex
status: done    # todo | doing | blocked | done
scope:
  - docs/tasks/T-072-slice2-testability-codex.md
---

## 목표

MVP 2번 조각(F1a 이메일 코드 로그인 + 탈퇴, `docs/proposals/mvp.md` F1·F7)의 인수 테스트를 네가 쓰게 된다. 계약을 쓰기 전에, **이 저장소의 하네스 안에서 각 요구를 어떻게 검증할 수 있는지** 쟁점을 정리해 달라. 코드는 쓰지 않는다. 일반 기술 조사(세션·CSRF 정석, OTP 기준, Mailpit 사용법)는 agy가 T-071에서 하므로 여기서는 하지 않는다. T-071은 읽지 않는다(독립 판단).

## 판단할 것

각 항목에 **권장안 하나 + 근거(`파일:줄`) + 대안을 버린 이유 한 줄**.

1. **시간 규칙:** 코드 10분 유효, 재전송 1분 1회, 만료 코드 즉시 삭제. IT에서 `@MockitoBean`·`@TestBean`·빈 덮어쓰기가 금지된 상태로 검증하는 방법.
   - 후보: (a) 운영 `Clock` 빈 + 테스트 `@Configuration`의 `@Primary` 조작 가능 시계, (b) 기간을 설정값으로 빼고 IT에서 줄이기, (c) IT가 DB 시각 컬럼을 직접 당기기, (d) 인수 테스트는 HTTP로 보이는 것만, 시간은 구현자 단위 테스트.
   - `TestIntegrityTest`·`GuardBypassIntegrityTest`·`quality_gate.py`가 각 후보를 막는지 실제 규칙으로 확인한다.
2. **메일 수신:** 테스트가 코드를 알아내는 방법. Testcontainers 메일 서버를 쓰려면 새 의존성이 필요한지(`backend/build.gradle.kts` 현재 목록 기준). 의존성 PR을 원본 PR보다 먼저 내야 하는지.
3. **HTTP 방식:** 슬라이스 1처럼 MockMvc인가, 실제 랜덤 포트 + 쿠키인가. 세션 ID 교체(세션 고정 방지)·쿠키 속성·CSRF 거부를 인수 테스트로 고정하려면 어느 쪽이 필요한가.
4. **슬라이스 1과의 경계:** 1의 원본 기대값은 바꾸지 않는다(`mvp.md` 구현 순서). 1의 테스트는 `user("<계정ID>")`로 계정 없이 프로필을 만든다. 그렇다면 `tutor_profile` 소유자 → 계정 FK를 걸 수 있나. 탈퇴 시 프로필 즉시 삭제를 인수 테스트로 어떻게 확인하나(공개 상세 `404` 등).
5. **기존 보안 테스트:** 새 인증 경로(공개 POST 엔드포인트, 로그아웃, `/me`)를 `SecurityConfig`에 추가할 때 기존 보안 테스트·`ConfigurationIntegrityTest`와 부딪히는 점. "새 경로에는 허용·거부 HTTP 테스트를 함께" 규칙(`backend/AGENTS.md`)을 인수 테스트와 구현자 테스트 중 어디서 채우나.
6. **빠뜨리기 쉬운 요구:** 명세에서 테스트로 고정해야 하는데 놓치기 쉬운 것(예: 같은 이메일 대소문자, 탈퇴 후 재가입 시 새 ID, 계정 존재 여부와 무관한 같은 응답, 5회 실패 뒤 맞는 코드도 거부).

## 완료 조건

- [ ] `## 리뷰`에 1~6 항목별 판단. 근거는 이 저장소의 파일:줄.
- [ ] 결과 10줄 이내.

## 참고

`docs/proposals/mvp.md` F1·F7·구현 순서, `docs/proposals/test-harness.md` 3·9절, `backend/AGENTS.md`, `docs/tasks/T-045-tutor-profile-acceptance.md`(슬라이스 1 계약 형식), `backend/src/test/java/kr/needteacher/architecture/`.

## 리뷰

1. **시간 — (a) 권장.** 운영 코드가 `Clock`을 주입받고, 신규 IT의 `@TestConfiguration`에서 `@Primary` 조작 가능 `Clock`을 제공한다. 테스트마다 시각을 초기화해 10분 직전/정각의 코드 수락·거부와 1분 직전/정각의 재전송 거부·허용을 HTTP로 본다. `TestIntegrityTest`는 IT의 `@MockitoBean`·`@MockitoSpyBean`·`@TestBean`, 테스트 `@Bean`의 DataSource/EMF/Flyway 반환만 금지하므로 `Clock` 빈은 허용한다(`backend/src/test/java/kr/needteacher/architecture/TestIntegrityTest.java:36-56,78-95`). `GuardBypassIntegrityTest`는 BeanFactoryPostProcessor/DB guard 의존만 금지하고(`backend/src/test/java/kr/needteacher/architecture/GuardBypassIntegrityTest.java:15-33`), `quality_gate.py`는 DB 설정 키만 스캔하므로 시간 빈은 막지 않는다(`scripts/quality_gate.py:60-69,100-120`). IT에는 실제 PostgreSQL import가 여전히 필수다(`TestIntegrityTest.java:58-76`). **만료 코드의 물리적 즉시 삭제는 HTTP 거부만으로 증명할 수 없다**(`docs/proposals/mvp.md:131`). 원본 확정 전에 코드 저장 방식과 만료 정리 트리거를 계약으로 정하고, 그 뒤 조작 시계로 만료를 넘긴 후 저장소를 읽는 IT를 추가해야 한다. (b)는 운영 시간을 줄인 다른 계약을 테스트하고, (c)는 DB 컬럼/저장 방식에 결합되며, (d)는 시간 경계와 물리 삭제를 인수 수준에서 놓친다.

2. **메일 수신 — 범용 `GenericContainer`의 SMTP 수신 컨테이너 + 수신함 HTTP 조회 권장.** 신규 IT가 수신함을 비운 뒤 고유 이메일로 요청하고 실제 도착한 메일에서 6자리 코드를 파싱한다. 현재 목록에는 Testcontainers JUnit/PostgreSQL이 있어 core의 범용 컨테이너를 쓸 수 있고, 메일 전용 테스트 의존성은 없다(`backend/build.gradle.kts:32-40`). SMTP 주소·포트만 테스트 속성으로 연결하며 DB 속성은 건드리지 않는다(`scripts/quality_gate.py:60-69,100-120`). 따라서 **수신용 의존성 PR을 원본 PR보다 먼저 낼 필요는 없다**(`docs/proposals/test-harness.md:87-93`); 운영 발송 라이브러리가 필요하면 구현 단계의 보호된 `build.gradle.kts` 변경으로 다룬다. 대안인 메일 서비스 목/코드 DB 직접 읽기는 실제 발송 경로를 검증하지 못하고 IT의 빈 바꿔치기 방침에도 맞지 않는다(`backend/AGENTS.md:32-33`).

3. **HTTP — 슬라이스 2는 랜덤 포트 + 실제 쿠키 저장소 권장.** 슬라이스 1의 MockMvc 계약은 그대로 두고(`docs/tasks/T-045-tutor-profile-acceptance.md:20`), 새 IT에서 로그인 전후 `JSESSIONID` 변경, `Set-Cookie` 속성, 로그아웃 후 쿠키의 무효화, CSRF 쿠키/헤더 왕복과 누락·오류 토큰의 `403`을 실제 HTTP로 검증한다. 현재 보안은 `csrf.spa()`이고 기존 MockMvc IT도 CSRF 왕복을 검사하지만(`backend/src/main/java/kr/needteacher/security/SecurityConfig.java:38-40`, `backend/src/test/java/kr/needteacher/security/SecurityIT.java:68-84`), MockMvc는 서블릿 컨테이너의 실제 `Set-Cookie` 동작을 보증하지 않는다. MockMvc만 쓰는 대안은 필터 체인 검증에는 충분해도 세션 쿠키 속성 계약에는 부족하다.

4. **슬라이스 1 경계 — `owner_id` 문자열 유지, 탈퇴 때 소유자 ID로 프로필 삭제 권장.** 1의 `user("<계정ID>")`는 계정 행 없이 프로필을 만들고 기존 스키마도 `owner_id VARCHAR(100)`에 계정 FK가 없다(`docs/tasks/T-045-tutor-profile-acceptance.md:20`, `backend/src/main/resources/db/migration/V1__create_tutor_profiles.sql:1-4`). 비어 있지 않은 계정 FK를 소급 추가하면 1의 원본이 깨진다(`docs/proposals/mvp.md:167-168`). 새 IT는 **실제 이메일 가입 → 같은 세션으로 프로필 생성·공개 → 탈퇴 → 익명 공개 상세 `404`와 검색 누락**을 확인한다. 다시 가입했을 때 과거 프로필도 복원되지 않아야 한다(`docs/proposals/mvp.md:123-127`, `docs/tasks/T-045-tutor-profile-acceptance.md:29`). FK 강제 대안은 기존 가짜 주체를 받아들이는 계약과 충돌한다.

5. **보안 — 새 인수 IT에 경로별 허용·거부 HTTP 쌍을 넣고, 구현자는 서비스 단위 테스트를 더한다.** 공개 코드 요청/확인 POST의 정상 요청과 CSRF 누락·오류 `403`, 인증된 `/me`와 익명 `401`, 로그아웃 성공과 익명·CSRF 거부를 실제 필터 체인으로 고정한다(`backend/AGENTS.md:31-33`, `docs/proposals/mvp.md:23-25`). 기존 `SecurityIT`는 `/api/ping` CSRF 발급을 기대하고(`backend/src/test/java/kr/needteacher/security/SecurityIT.java:68-84`), `SecurityConfig`는 단일 `securityFilterChain` 메서드 형태를 유지해야 하므로 새 matcher는 그 안에 추가해야 한다(`backend/src/main/java/kr/needteacher/security/SecurityConfig.java:17-42`, `backend/src/test/java/kr/needteacher/architecture/ConfigurationIntegrityTest.java:35-69`). 기존 테스트 수정으로 해결하는 대안은 불변 규칙에 막힌다(`backend/AGENTS.md:35-36`).

6. **누락 방지 — 신규 원본에서 계정 불변식과 남용 경계를 별도 시나리오로 고정 권장.** 6자리 형식·10분/5회 경계(`docs/proposals/mvp.md:31-33`), 이메일 대소문자만 다른 주소의 단일 계정 귀속(`docs/proposals/mvp.md:37`), 첫 가입 역할 선택·이후 역할 불변·14세 이상 자기진술(`docs/proposals/mvp.md:23-25,31-32`), 존재/미존재 이메일 요청의 동일한 외부 응답, 다섯 번째 실패 뒤 정답 거부, 탈퇴 후 기존 세션 사용 불가와 재가입 시 **새 계정 ID·과거 데이터 미복원**(`docs/proposals/mvp.md:123-127`)을 확인한다. 특히 계정 존재 여부를 숨기는 동일 응답은 현 MVP에 명시되지 않았으므로 원본 확정 전 API 계약으로 정해야 한다. 단순 성공 로그인만 확인하는 대안은 계정 열거·역할 변경·코드 재사용·복원 회귀를 놓친다.

## 결과

- 변경: `docs/tasks/T-072-slice2-testability-codex.md`에 6개 쟁점 판단과 저장소 근거 작성. 운영 코드·테스트 변경 없음.
- 확인: 하네스/ArchUnit/품질 스캔, Gradle 의존성, 기존 보안·프로필 원본을 읽고 대조. 문서만 수정해 빌드는 실행하지 않음.
- 원본 작성 전 확정 필요: 만료 코드의 물리 삭제 관찰 방식·트리거, 인증 API의 상태/본문, 계정 존재 여부 공통 응답.
