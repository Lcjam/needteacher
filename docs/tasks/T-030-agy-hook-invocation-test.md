---
id: T-030
owner: codex
status: done    # todo | doing | blocked | done
scope:
  - scripts/test_hooks.py
  - docs/proposals/test-harness.md
  - docs/tasks/T-030-agy-hook-invocation-test.md
---

## 목표

agy 훅이 플래그를 빼도 아무것도 막지 못했다. 원인: agy는 Windows에서 명령 문자열을 인자 하나로 `cmd /c`에 넘긴다(Go식 인자 이스케이프로 안쪽 `"` → `\"`). 그래서 `python -c "..."` 꼴 명령은 Python이 코드를 문자열 리터럴로 받아 아무것도 출력하지 않고 0으로 끝났고, agy는 이를 허용으로 처리했다. Claude가 `.agents/hooks.json`을 `python ../scripts/guard.py --agent agy`로 고쳤다. `test_hooks.py`는 `cmd /c {command}` 문자열 방식만 써서 이 고장을 놓쳤다.

## 완료 조건

- [x] `test_hooks.py`: agy 훅은 Windows에서 `["cmd", "/c", command]` 리스트 방식(agy와 같은 이스케이프)으로도 실행해 검사한다.
- [x] 회귀 확인: 임시 사본 설정으로 예전 명령(`python -c "__import__('runpy')..."`)을 넣으면 실패하는지. 지금은 `--codex-hooks`만 있으니 agy 설정 경로도 바꿀 수 있게 (`--agy-hooks`).
- [x] `python scripts/test_hooks.py` 통과.
- [x] `docs/proposals/test-harness.md` 막은 구멍 목록에 한 줄: agy `--dangerously-skip-permissions`는 PreToolUse 훅을 호출하지 않음(T-027) → `toolPermission: always-proceed`로 전환(T-028), 따옴표 이스케이프로 훅이 조용히 허용하던 문제(T-030).

## 참고

- 재현 스크립트: Claude 임시 폴더의 `agy_escape_test.py` 결과 — 예전 명령은 리스트 방식에서 `exit=0 stdout=b''`, 새 명령은 두 방식 모두 deny.
- `docs/tasks/T-027-agy-hook-diagnose.md`, `docs/tasks/T-028-agy-always-proceed.md`
- https://github.com/yanokamay-org/maiestro/blob/main/docs/session-status.md ("Antigravity runs hook commands with `cmd /c "<command>"`, so the generated commands contain no double quotes")

## 결과

- `scripts/test_hooks.py`: agy Windows 명령을 문자열·리스트 방식 모두 검사하고 `--agy-hooks`를 추가했다.
- `docs/proposals/test-harness.md`: agy 훅 호출 우회와 따옴표 이스케이프 문제를 기록했다.
- `python scripts/test_hooks.py` 통과(`hooks: all checks passed`).
- 임시 agy 설정에 예전 `python -c` 명령 적용 시 `agy / cmd-list / deny: missing deny output`으로 실패(exit 1).
- 남은 일: 없음.
