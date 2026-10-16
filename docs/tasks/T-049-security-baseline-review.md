---
id: T-049
owner: codex
status: done    # todo | doing | blocked | done
scope:
  - docs/tasks/T-049-security-baseline-review.md
---

## 목표

agy가 구현한 T-048(보안 기본 설정 + 설정 클래스 뮤테이션 면제)을 리뷰한다. T-047에서 네가 낸 P0/P1이 실제 코드에서 닫혔는지가 핵심이다. 파일은 고치지 말고 `## 리뷰`에만 쓴다. 잠금이 풀려 있어도 보호 파일을 고치지 않는다.

## 대상 (작업 트리, 아직 커밋 전)

- `git diff`: `backend/build.gradle.kts`, `scripts/quality_gate.py`, `backend/AGENTS.md`
- 새 파일:
  - `backend/src/main/java/kr/needteacher/security/`: `SecurityConfig`, `JsonAuthenticationEntryPoint`, `JsonAccessDeniedHandler`
  - `backend/src/test/java/kr/needteacher/security/`: 핸들러 단위 테스트 2개, `SecurityIT`
  - `backend/src/test/java/kr/needteacher/architecture/ConfigurationIntegrityTest.java`
- 오케스트레이터가 이미 고친 것:
  - ArchUnit 메서드 필터를 `SYNTHETIC`만 보게 고침 (`lambda$` 이름 예외 제거)
  - `check_gradle_sync`가 파일이 없으면 실패하게 고침
  - `excludedClasses` 이름이 두 번 이상 나오면 실패하게 고침 (`.add()` 포함)
  - `/api/admin/**` 가상 규칙 제거
- 오케스트레이터 확인 결과: `verify full --worktree` 통과 (line·mutation 100%), 자체 검사 4종 통과.

## 완료 조건

- [ ] T-047 P0 4개(파일 단위 면제 구멍, JaCoCo/목록 일치, 실제 필터 체인 HTTP 테스트, 401/403 별도 핸들러)가 닫혔는지 각각 판정. 근거는 코드 위치.
- [ ] 남은 우회 경로가 있으면 재현 방법과 함께 적는다. 임시 복사본에서만 실험한다.
- [ ] `SecurityIT`의 CSRF 쿠키 왕복이 실제 SPA 흐름을 검증하는지 본다 (`csrf()` 후처리기 흉내가 아닌지, 쿠키가 첫 GET에서 실리는지).
- [ ] 다음 조각(T-045 인수 테스트: `/api/v1/tutor-profiles` 읽기 공개, 쓰기 TEACHER)을 이 구조 위에 얹을 때 막히는 점.
- [ ] P0/P1/P2와 "이대로 PR 올려도 됨 / 고쳐야 함" 판정.

## 참고

- `docs/tasks/T-046-…`, `docs/tasks/T-047-…`, `docs/tasks/T-048-security-baseline.md`

## 리뷰

### 판정: PR #4 (`7b1c47e`)의 보안 기본 설정은 병합 가능

현재 범위에서 수정 필수 P0은 없다. T-047의 네 P0은 **현재 등록된 기본 규칙**에 대해 닫혔다. 이 판정은 새 프로필 경로의 공개·역할 정책까지 검증했다는 뜻은 아니다. 아래 P1을 T-045 구현 조건으로 둔다.

| T-047 P0 | 판정과 코드 근거 |
|---|---|
| 파일 단위 PIT 면제 구멍 | 닫힘. `backend/src/test/java/kr/needteacher/architecture/ConfigurationIntegrityTest.java:24-67`이 면제 클래스 FQCN과 `SecurityConfig.java`에서 컴파일된 운영 클래스 **정확히 하나**, 필드 0개, 무인자 생성자 1개, 비 synthetic `@Bean securityFilterChain` 1개를 강제한다. `scripts/quality_gate.py:203-211`은 면제 파일에 PIT 변이가 나타나면 면제 클래스 자체 또는 같은 파일의 다른 클래스를 모두 오류로 처리한다. `lambda$`라는 이름만으로 일반 메서드를 빼던 조건도 없다(`ConfigurationIntegrityTest.java:56`). |
| JaCoCo 유지·Gradle/Python 목록 일치 | 닫힘. `backend/build.gradle.kts:123`은 PIT에서 정확한 `SecurityConfig`만 제외한다. `scripts/quality_gate.py:21-23,41-56`은 별도 `MUTATION_EXEMPT`와 Gradle 목록의 일치를 검사하고, 설정 파일 부재·`excludedClasses` 중복도 실패시킨다. `quality_gate.py:188-211`은 면제 파일에도 JaCoCo LINE/BRANCH·리포트 존재를 검사한 뒤 mutation 점수만 건너뛴다. |
| 실제 필터 체인 HTTP 테스트 | 현재 기본 규칙에 대해 닫힘. `SecurityIT.java:19-21`은 전체 Spring 컨텍스트와 MockMvc를 사용한다. `:30-84`는 익명 ping 200, 익명 보호 경로 401, 인증된 POST의 CSRF 누락·오류 403, 유효 토큰으로 보안을 통과한 없는 경로 404를 검사한다. `SecurityConfig.java:22-30`의 실제 matcher·예외 처리·`csrf.spa()`가 이 요청들에 적용된다. 새 경로/역할 규칙은 아직 테스트하지 않는다. |
| 401/403 별도 핸들러 | 닫힘. `JsonAuthenticationEntryPoint.java:22-25`와 `JsonAccessDeniedHandler.java:22-25`가 별도 클래스에서 JSON 상태·인코딩·본문을 쓰고, `SecurityConfig.java:27-29`에 연결된다. 각 단위 테스트와 `SecurityIT.java:38-65`가 응답을 확인한다. 403의 현재 통합 사례는 **CSRF 거부**이며 역할 부족 사례는 아니다. |

### CSRF 쿠키 왕복

`SecurityIT.java:70-84`는 첫 익명 `GET /api/ping`의 응답에서 `XSRF-TOKEN` 쿠키를 실제로 꺼내고, 그 값을 쿠키와 `X-XSRF-TOKEN` 헤더로 인증된 POST에 전달해 404를 확인한다. `with(csrf())`는 사용하지 않는다. 따라서 **첫 안전 요청의 쿠키 발급과 필터 체인의 헤더 왕복**은 확인된다. 인증은 `with(user(...))`로 주입하므로 실제 로그인/로그아웃 세션에서 CSRF 토큰이 재발급되는지, 브라우저의 쿠키·자격 증명 설정으로 왕복되는지는 아직 검증하지 않았다(P1, 인증 흐름 도입 시 통합 테스트 필요).

### 잔여 위험과 재현 방법

- **P1, PIT 면제 설정 본문의 의미 변경:** `SecurityConfig`의 허용된 `@Bean` 본문·synthetic 람다 안에 새 URL 예외를 넣어도 구조 검사와 커버리지 수치만으로 정책 오류를 판별하지 못한다. 재현 절차(미실행): 작업 트리가 아닌 임시 복사본에서 `SecurityConfig.java:24-27`의 `anyRequest().authenticated()` 앞에 `/api/private-leak/**`를 `permitAll()`로 추가하고, 해당 경로의 거부 테스트는 추가하지 않은 채 `bash scripts/verify.sh full --worktree`를 실행한다. 현재 `SecurityIT`는 `/api/any-protected`만 거부 경로로 검사하므로 이 예외를 잡을 테스트가 없다. 실제 통과 여부를 확인한 실험 결과로 주장하지 않는다. **새 matcher마다 허용·거부 HTTP 테스트와 설정 변경 리뷰가 필요하다.**
- **P2, 직접 작성한 무인자 생성자:** `SecurityConfig.java:13-14`의 빈 생성자는 구조 검사에 허용된다. 생성자 본문에 부작용을 넣어도 현 구조 검사로는 검출하지 못한다. 현재 본문은 비어 있어 운영 문제는 없으며, 불필요한 생성자는 후속 정리에서 제거할 수 있다.
- T-048 카드의 `verify full --worktree` 통과(line·mutation 100%), 자체 검사 4종 통과 및 임시 복사본 음성 확인을 참조했다. 이번 리뷰에서는 최신 커밋의 코드를 읽기 전용으로 대조했고 실험·빌드를 다시 실행하지 않았다.

### T-045 적용 전 필요한 변경

- **P1, 프로필 정책:** 현재 `SecurityConfig.java:24-27`은 `/api/ping`만 공개하고 나머지는 모두 `authenticated()`다. 따라서 익명 `GET /api/v1/tutor-profiles` 목록·상세는 지금 401이 되며, 인증된 학부모의 쓰기는 보안 필터의 역할 검사 없이 통과할 수 있다. T-045 green 구현 전에 목록·상세 GET의 정확한 공개 matcher, TEACHER 쓰기 권한, `GET /api/v1/me/tutor-profile`의 인증 유지, 작성자 소유권 거부를 명시해야 한다. 공개 GET 200·익명 쓰기 401·학부모/타인 쓰기 403·교사+CSRF 성공을 실제 경로의 HTTP 테스트로 고정한다. `@PreAuthorize`를 선택한다면 현재 설정에 메서드 보안 활성화가 없으므로 함께 검증한다.
- **P1, 403 범위:** 현재 403 JSON 체인은 CSRF 실패로 확인했지만, T-045의 역할 부족 403은 아직 존재하지 않는다. 프로필 권한 규칙을 넣을 때 그 응답 코드·본문까지 검사해야 T-047의 미래 경로 조건이 충족된다.

## 결과

- 변경: 이 카드의 리뷰·결과만 작성. 운영 코드·테스트·보호 파일은 수정하지 않음.
- 판정: PR #4 현재 범위 병합 가능, 수정 필수 P0 없음. T-047 P0 네 항목은 기본 규칙 범위에서 닫힘.
- 검증: `7b1c47e`의 보안 설정·IT·핸들러·ArchUnit·품질 게이트를 읽기 전용으로 대조. 빌드 결과는 T-048 기록 참조.
- 남은 일: T-045에서 공개 GET·TEACHER 쓰기·역할/소유권 403을 검증하고, 인증 흐름 도입 시 CSRF 재발급을 검증.
