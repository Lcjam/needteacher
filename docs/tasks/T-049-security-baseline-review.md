---
id: T-049
owner: codex
status: todo    # todo | doing | blocked | done
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

(길이 제한 없음)

## 결과

(작업자가 10줄 이내로 작성)
