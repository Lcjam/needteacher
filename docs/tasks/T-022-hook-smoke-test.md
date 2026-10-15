---
id: T-022
owner: codex
status: done    # todo | doing | blocked | done
scope:
  - scripts/test_hooks.py
  - .github/workflows/ci.yml
  - docs/tasks/T-022-hook-smoke-test.md
---

## 목표

`.codex/hooks.json`의 `commandWindows`가 PowerShell에서 파싱되지 않아 Codex 훅이 계속 "exited with code 1"로 실패했고, Codex는 실패한 훅을 통과(허용)로 처리했다. Claude가 따옴표를 고쳤다. 같은 일이 다시 생기지 않게, 세 에이전트의 실제 훅 설정 명령을 실제 셸로 실행해 보는 자체 검사를 만든다.

## 완료 조건

- [x] `scripts/test_hooks.py`: `.claude/settings.json`(`$CLAUDE_PROJECT_DIR` 치환), `.codex/hooks.json`(Windows면 `commandWindows`, 아니면 `command`), `.agents/hooks.json`의 PreToolUse 명령을 설정 파일에서 읽어, 그 플랫폼의 셸(Windows: `powershell -NoProfile -Command`와 `cmd /c` 둘 다, 그 밖: `sh -c`)로 `backend/` 하위 디렉토리에서 실행한다.
- [x] 입력은 각 에이전트 형식의 `repl_run` 호출(차단돼야 함)과 `git status`(허용돼야 함). 종료 코드 0, 차단 사례는 deny JSON, 허용 사례는 출력 없음을 단언한다.
- [x] 지금 저장소에서 `python scripts/test_hooks.py` 통과. 일부러 `commandWindows`의 따옴표를 빼면 실패하는지 임시 복사본에서 확인하고 결과에 적는다.
- [x] `ci.yml`의 자체 검사 단계(ubuntu `verify-full`, `self-check-windows` 둘 다)에 추가.
- [x] Codex 훅이 실패하면 허용되는지(fail-open), 실패를 차단으로 바꾸는 설정이 Codex에 있는지 공식 문서로 확인해 결과에 적는다. 있으면 적용 방법만 적고 `.codex/hooks.json`은 건드리지 않는다.

## 참고

- `scripts/guard.py`(stdin BOM 허용으로 수정됨), `scripts/test_guard.py`
- `wmux repl_run`·`terminal_send` 사용 금지 (AGENTS.md). 셸로 못 돌리는 건 결과에 적고 멈춘다.

## 결과

- `scripts/test_hooks.py` 추가: 세 설정의 실제 PreToolUse 명령을 `backend/src`에서 실행해 차단·허용 응답과 종료 코드를 검사.
- `.github/workflows/ci.yml`의 Ubuntu·Windows 자체 검사에 실행 단계 추가.
- Windows에서 `python scripts/test_hooks.py` 통과(PowerShell·cmd 모두); Linux 셸 검사는 CI에서 확인 필요.
- 임시 JSON 사본의 `commandWindows` 닫는 따옴표를 제거하자 `codex / powershell / deny`에서 파서 오류로 종료 코드 1을 검출.
- [Codex 공식 Hooks 문서](https://learn.chatgpt.com/docs/hooks): 실패한 PreToolUse 훅은 보고 후 도구 호출을 계속할 수 있어 fail-open이며, 명시적 deny JSON 또는 종료 코드 2가 차단 방식.
- [공식 설정 참조](https://learn.chatgpt.com/docs/config-file/config-reference)에 훅 실패를 자동 차단으로 바꾸는 설정은 없음. `.codex/hooks.json`은 수정하지 않음.
