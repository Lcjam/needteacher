---
id: T-026
owner: codex
status: done    # todo | doing | blocked | done
scope:
  - backend/src/test/java/kr/needteacher/architecture/DatabaseIntegrityGuard.java
  - backend/src/test/java/kr/needteacher/architecture/TestIntegrityTest.java
  - scripts/verify.sh
  - scripts/test_verify.sh
  - docs/proposals/test-harness.md
  - docs/tasks/T-026-guard-fix-2.md
---

## 목표

T-023 지적을 고치고 T-025 인수 테스트를 통과시킨다. agy 훅이 꺼져 있어(T-027) Codex로 재배정. 리뷰는 agy가 재시작 후 따로 한다.

## 완료 조건

1. 스크립트 (바로)
   - [x] `verify.sh` 0번 검사의 `tools`에 `MultiEdit`, `NotebookEdit`, `mcp__wmux__repl_run` 추가 (matcher가 MCP 도구도 덮어야 `repl_run` 우회 차단이 동작).
   - [x] `test_verify.sh`: matcher가 `Bash|PowerShell|Edit|Write`뿐이면 실패하는 사례 추가. 전부 통과, 건수 기록.
2. 가드 (T-025 이후)
   - [x] `DatabaseIntegrityGuard`: `ApplicationContextAware`로 자기 컨텍스트를 기억하고, 다른 컨텍스트(자식)의 이벤트는 무시.
   - [x] DataSource·EntityManagerFactory는 `getBeansOfType`으로 전부 검사 (0개면 위반).
   - [x] Flyway: `pending()` 대신 `info().all()`의 모든 항목이 `getState().isApplied()`인지 (target을 낮춘 `Above Target`, baseline 아래 `Below Baseline`도 위반).
   - [x] `TestIntegrityTest`: 테스트 코드의 `@Bean` 메서드가 `EntityManagerFactory`·`LocalContainerEntityManagerFactoryBean`·`Flyway` 계열을 반환하면 실패 (기존 DataSource 규칙과 같은 방식, `allowEmptyShould`).
   - [x] `cd backend && bash ./gradlew integrationTest check` 통과 (T-025 음성 사례 포함).
   - [x] `test-harness.md` 구현 상태·막은 구멍 목록에 T-023~T-026 반영.

## 참고

- `docs/tasks/T-023-guard-review-codex.md`
- `DatabaseIntegrityIT`(T-019·T-025 인수 테스트)는 고치지 않는다. 안 맞으면 결과에 적고 멈춘다.

## 결과

- `scripts/verify.sh`, `scripts/test_verify.sh`: 0번 matcher에 MultiEdit·NotebookEdit·wmux `repl_run`과 `terminal_send` 포함; 누락 회귀 사례 2건 추가. 자체 검사 28건 통과.
- `DatabaseIntegrityGuard.java`: 자기 컨텍스트의 refresh만 처리하고 DataSource·EMF 전체 빈을 검사; Flyway 전체 이력의 `isApplied()` 판정으로 변경.
- Flyway 12.4.0 jar 확인: `Above Target`·`Below Baseline`의 `isApplied()`는 false.
- `TestIntegrityTest.java`: EMF·`LocalContainerEntityManagerFactoryBean`·Flyway 계열 `@Bean` 반환 금지 규칙 추가.
- `docs/proposals/test-harness.md`: T-023~T-026 방어와 검증 수치 갱신.
- Windows `gradlew.bat integrationTest check` 통과(59초, 단위 12건·통합 11건, T-025 포함); `spotlessJavaCheck` 통과.
- 남은 일: Claude diff·실측 검토, agy 교차 리뷰. Windows 환경이라 카드의 Bash 명령은 동등한 Windows 래퍼로 실행.
