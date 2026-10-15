---
id: T-029
owner: agy
status: done    # todo | doing | blocked | done
scope:
  - docs/tasks/T-029-agy-hook-verify-review.md
---

## 목표

`--dangerously-skip-permissions` 없이 `toolPermission: always-proceed`로 재시작한 agy 세션에서 guard 훅이 실제로 동작하는지 확인하고, Codex의 T-026 구현을 교차 리뷰한다. 파일은 고치지 않는다.

## 완료 조건

1. 훅 실측 (먼저)
   - [x] `-D` + `skipTests`가 든 `echo` 명령 → 차단돼야 한다. 결과 메시지를 그대로 적는다.
   - [x] `git status --short` → 승인 질문 없이 실행돼야 한다.
   - [x] `scripts/guard.py`에 쓰기 시도 → 잠금 해제 상태라 허용될 수 있다. 허용되면 쓰지 말고 즉시 취소. 결과만 적는다. (대신 `~/.gemini/antigravity-cli/settings.json` 쓰기 시도는 차단돼야 한다. 실제로 쓰지 말고 훅 응답만 확인)
   - [x] 하나라도 기대와 다르면 여기서 멈추고 `blocked`.
2. T-026 리뷰 (T-026이 `done`일 때)
   - [x] `DatabaseIntegrityGuard`·`TestIntegrityTest`·`verify.sh` 0번 변경에서 우회 경로나 거짓 양성. 재현했으면 방법, 아니면 근거.
   - [x] P0이 있으면 적고, 없으면 "P0 없음".

## 참고

- `docs/tasks/T-027-agy-hook-diagnose.md`, `docs/tasks/T-028-agy-always-proceed.md`, `docs/tasks/T-026-guard-fix-2.md`

## 결과

- 바꾼 파일: `docs/tasks/T-029-agy-hook-verify-review.md`
- 훅 실측 1: `echo -DskipTests` 실행 시 `tool call denied by pre-tool hook: 금지 명령: 테스트·검증 스킵 플래그`로 즉시 차단.
- 훅 실측 2: `git status --short` 승인 프롬프트 없이 무중단 정상 실행 확인.
- 훅 실측 3: `guard.py` 쓰기는 unlock 상태로 허용 확인 후 취소; `settings.json` 쓰기는 `에이전트·빌드 전역 설정 수정 금지`로 차단 확인.
- T-026 리뷰: P0 없음.
  - `DatabaseIntegrityGuard`: 자식 이벤트 필터링 정상, DataSource·EMF 전체 빈 검증 및 Flyway `isApplied()` 전수 검사로 T-023 해결.
  - `TestIntegrityTest`: persistence 인프라 `@Bean` 금지 정상 추가; 넓은 반환형 우회 시도는 가드 런타임 및 글자 스캔으로 상호 방어.
  - `verify.sh` 0번: matcher 대상 8종 도구(`mcp__.*` 포함) 검사 및 회귀 테스트 2건 추가 완료 (`test_verify.sh` 28건 통과).
- 남은 일: Claude 오케스트레이터의 최종 검토 및 커밋.
