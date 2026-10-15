---
id: T-048
owner: agy
status: done    # todo | doing | blocked | done
scope:
  - backend/build.gradle.kts
  - backend/src/main/java/kr/needteacher/security/**
  - backend/src/test/java/kr/needteacher/security/**
  - backend/src/test/java/kr/needteacher/architecture/ConfigurationIntegrityTest.java
  - scripts/quality_gate.py
  - scripts/verify.sh            # 아래 "조건부"일 때만
  - scripts/test_verify.sh       # 아래 "조건부"일 때만
  - backend/AGENTS.md
  - docs/tasks/T-048-security-baseline.md
---

## 목표

Spring Security 기본 설정을 넣고, 설정 클래스 하나만 뮤테이션을 면제하는 하네스 변경을 함께 한다. 설계는 T-046(agy 실측)과 T-047(Codex 리뷰)에서 확정됐다. **T-047 `## 리뷰`의 "보호 파일 변경 범위" 1~4를 그대로 따른다.** 사용자가 잠금을 풀어 둔 동안 작업한다. scope 밖의 보호 파일은 건드리지 않는다.

## 시작 상태

- 작업 트리는 `main`이다.
- `backend/build.gradle.kts`에는 커밋되지 않은 `testImplementation("org.springframework.security:spring-security-test")` 한 줄이 있다 (PR #3). 지우지 않는다.

## 동작 (테스트로 고정)

| 요청 | 결과 |
|---|---|
| 익명 `GET /api/ping` | `200 pong` (지금과 같음) |
| 익명으로 그 밖의 경로 | `401`, `{"code":"UNAUTHORIZED","message":...}` (리다이렉트·Basic 인증 창 없음) |
| 로그인 사용자의 상태 변경 요청, CSRF 토큰 없음·틀림 | `403`, `{"code":"FORBIDDEN","message":...}` |
| 권한 부족 | `403`, 같은 형식 |
| 로그인 사용자 + 올바른 CSRF로 없는 경로 | `404` (보안 통과 확인용) |

- **CSRF:** `http.csrf(csrf -> csrf.spa())`를 먼저 쓴다. 실제 쿠키 왕복을 테스트한다. 안전한 요청 뒤 받은 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 보내면 상태 변경 요청이 보안을 통과해야 한다. `csrf()` 후처리기로 흉내 내지 않는다. 쿠키가 안 실리면 그때만 필터를 추가하고, 필터에도 단위 테스트를 붙인다.
- 폼 로그인과 HTTP Basic은 끈다. 세션 기반 전제다.

## 구조 (T-047 3·4번)

- **`kr.needteacher.security.SecurityConfig`:**
  - `@Configuration`이 붙는다. 필드 0개, 생성자는 무인자 하나다.
  - 비 synthetic 메서드는 `@Bean SecurityFilterChain securityFilterChain(HttpSecurity, 진입점, 거부 핸들러)` 하나만 둔다.
  - 같은 `.java` 파일에 다른 클래스(중첩·익명·최상위)를 두지 않는다.
- 401 진입점과 403 `AccessDeniedHandler`는 각각 별도 클래스로 둔다. 단위 테스트(`*Test`)로 뮤턴트를 전부 죽인다. `setCharacterEncoding` 변이를 죽이려면 응답 기본 인코딩을 다르게 두고 시작한다 (T-046 실측).
- 실제 필터 체인 동작은 `*IT`로 확인한다. 위 표 전부와 CSRF 쿠키 왕복을 포함한다.

## 하네스 (보호 파일, T-047 diff 그대로)

- **`build.gradle.kts`:**
  - `implementation("org.springframework.boot:spring-boot-starter-security")`를 추가한다.
  - pitest `excludedClasses`에 정확히 `kr.needteacher.security.SecurityConfig`를 더한다.
  - JaCoCo 제외는 바꾸지 않는다.
- **`quality_gate.py`:**
  - `MUTATION_EXEMPT`를 추가한다. 면제 파일은 line/branch 검사를 먼저 하고 mutation 판정만 생략한다.
  - 다음 경우 실패한다:
    - 면제 클래스의 변이가 리포트에 나타남
    - 같은 소스 파일에 다른 클래스의 변이가 있음
    - Gradle `excludedClasses`와 목록이 다름
  - `--self-test` 사례 (a)~(d)를 추가한다.
- **`ConfigurationIntegrityTest`:** 위 구조 규칙을 강제한다. 실제로 `test` 태스크에서 실행되는지 확인한다.
- **`backend/AGENTS.md` 테스트 절:** T-047 4번 문구를 넣는다.
- **조건부 (`verify.sh`·`test_verify.sh`):**
  - `SecurityConfig`만 바뀐 변경에서 `verify fast`가 뮤턴트 0개로 실패하는지 실측한다.
  - 실패하면, 면제 클래스만 바뀐 경우에 PIT를 건너뛰는 좁은 분기와 `test_verify.sh` 사례를 넣는다.
  - `full`의 전체 PIT와 "뮤턴트 0건이면 실패"는 그대로 둔다.
  - 실패하지 않으면 두 파일은 건드리지 않는다.

## 완료 조건

- [x] `bash scripts/verify.sh full --worktree` 통과.
- [x] 자체 검사 전부 통과:
  - `python scripts/quality_gate.py --self-test`
  - `bash scripts/test_verify.sh`
  - `python scripts/test_guard.py`
  - `python scripts/test_hooks.py`
- [x] 음성 확인 (임시 복사본에서):
  - (1) `SecurityConfig.java`에 중첩 클래스를 넣으면 ArchUnit이 실패한다.
  - (2) `SecurityConfig`에 필드를 넣으면 실패한다.
  - (3) Gradle과 Python의 면제 목록을 다르게 하면 `quality_gate`가 실패한다.
  - (4) 401 진입점에서 `setStatus`를 지우면 PIT가 잡는다.
- [x] 결과에 바꾼 파일, 검증 명령과 결과, 조건부 변경 여부를 적는다.

## 참고

- `docs/tasks/T-046-security-config-mutation.md`, `docs/tasks/T-047-config-mutation-review.md`
- 다음 조각(T-045 인수 테스트)은 `401`/`403` JSON과 `.with(csrf())`를 전제로 한다. 그 경로 규칙(`/api/v1/tutor-profiles`)은 이 카드에서 넣지 않는다.

## 결과

- 바꾼 파일: `backend/build.gradle.kts`, `scripts/quality_gate.py`, `backend/AGENTS.md`, `docs/tasks/T-048-security-baseline.md`, `SecurityConfig`, `SecurityIT`, `ConfigurationIntegrityTest`, 진입점/거부핸들러 및 단위 테스트 2종.
- 재작업 반영: ArchUnit 메서드 필터를 SYNTHETIC 단독으로 강화하고 임시 복사본에서 `lambda$x` 일반 메서드 실패 검증 완료. `check_gradle_sync` 파일 부재 및 `excludedClasses` 중복 설정 시 실패하도록 수정하고 `--self-test` 추가. `/api/admin/**` 가상 경로 및 관련 IT 제거.
- 검증: `verify.sh full --worktree` 통과(line 100%, mutation 100%). 자체 검사 4종(`quality_gate --self-test`, `test_verify.sh`, `test_guard.py`, `test_hooks.py`) 전체 통과.
- 남은 일: 오케스트레이터의 변경 승인 및 커밋.

## 재작업 (오케스트레이터 검토)

잠금은 아직 풀려 있다. 범위는 같다.

- [x] **P0 `ConfigurationIntegrityTest`의 메서드 필터 구멍.**
  - 지금은 synthetic이거나 이름이 `lambda$`로 시작하면 제외한다. 자바 식별자에는 `$`를 쓸 수 있어서, `private static boolean lambda$check(...)` 같은 일반 메서드로 로직을 숨길 수 있다.
  - 이름 조건을 지우고 `SYNTHETIC` 수정자만 본다.
  - ArchUnit이 람다 구현 메서드를 synthetic으로 보고하지 않아 테스트가 깨지면, 대신 이름이 `^lambda\$securityFilterChain\$\d+$`이면서 `private static`이고 synthetic인 것만 허용한다.
  - 임시 복사본에서 이름이 `lambda$x`인 일반 메서드를 넣으면 실패하는지 확인한다.
- [x] **P1 `check_gradle_sync`가 실패를 놓친다.**
  - `build.gradle.kts`를 찾지 못하면 `[]`(통과)를 돌려준다. 실패를 돌려주게 바꾼다.
  - 자체 검사는 처음부터 가짜 `build.gradle.kts`를 만들어 두고 쓴다.
- [x] **P1 `SecurityConfig`의 `/api/admin/**` 규칙과 그 IT를 뺀다.**
  - 운영에 없는 가상 경로다. 403 처리기는 CSRF 실패 IT와 단위 테스트로 이미 검증된다.
  - 역할 기반 403은 다음 조각(프로필 쓰기, TEACHER 역할)에서 실제 경로로 테스트한다.
- [x] **P2 같은 설정을 두 번 쓰는 경우.** Gradle은 마지막 `excludedClasses.set(...)`을 쓰지만 검사는 첫 번째만 본다. `excludedClasses`가 파일에 정확히 한 번 나오지 않으면 실패하게 하고, 자체 검사 사례를 넣는다.
- 결과 칸은 고쳐 쓴다. `verify full --worktree`와 자체 검사 4종을 다시 돌린다.
