---
id: T-024
owner: agy
status: done
scope:
  - docs/tasks/T-024-hooks-test-review-agy.md
---

## 목표

Codex가 만든 T-022 `scripts/test_hooks.py`와 `.github/workflows/ci.yml` 변경, Claude가 고친 `.codex/hooks.json`·`scripts/guard.py`(BOM)를 리뷰한다. 파일은 고치지 않는다.

## 완료 조건

- [x] `test_hooks.py`가 실제 에이전트 실행 환경과 다른 점: agy 훅이 실제로 어느 셸·어느 cwd에서 도는지(네 런타임 기준으로 확인), Claude Code 훅은 Windows에서 Git Bash로 도는데 검사에 bash가 없는 점, Linux CI에서 `python`·`py` 유무.
- [x] 이 검사가 놓치는 고장 사례가 있는지: 예) 훅이 아무것도 출력하지 않고 0으로 끝나는 고장(전부 허용)을 잡는지.
- [x] `.agents/hooks.json` 명령이 너의 세션에서 지금 실제로 차단하는지 직접 확인 (예: 보호 파일 `scripts/guard.py`에 쓰기 시도 → 잠금 해제 상태라 허용+알림일 수 있으니, 대신 `repl_run` 류 호출이나 `-DskipTests` 명령으로).
- [x] P0이 있으면 적고, 없으면 "P0 없음".

## 참고

- `docs/tasks/T-022-hook-smoke-test.md`, `docs/proposals/test-harness.md` 구현 상태 표

## 결과

- P0 없음.
- agy 실측: 현재 세션에서 `echo -DskipTests`가 차단되지 않고 실행됨 (agy는 훅을 핫리로드하지 않아 세션 재시작 전까지 미적용). 수동 `cmd /c` 파이프 테스트는 deny 정상 반환.
- 셸·CWD 불일치: agy 공식 사양상 Windows에서 `cmd /c`로 실행되고 CWD는 `.agents/`이나, `test_hooks.py`는 `powershell`과 `cmd` 둘 다 돌리고 CWD를 `backend/src`로 테스트함.
- Claude Windows 셸 누락: Claude Code는 Windows에서 Git Bash(`bash -c`)로 훅을 돌리나 `test_hooks.py`에는 `bash`가 없어 실제 환경 미검증 ($CLAUDE_PROJECT_DIR도 파이썬에서 사전 치환).
- Linux CI python/py: Ubuntu 기본 환경에는 `python`·`py`가 없고 `python3`만 존재. 현재는 `ci.yml`의 `setup-python`이 `python`을 주입해 통과하나, 의존성이 숨어 있음.
- 빈 출력(0 종료) 감지: deny 케이스에서 JSON 파싱 및 decision 단언을 하므로 아무것도 출력하지 않는 고장은 잡음.
- 검사 공백: `repl_run` 1건만 차단 검사하므로 `BAD_COMMANDS`(-Dskip 등)나 보호 파일 쓰기(`write_to_file`) 파싱이 깨져도 감지하지 못함.
