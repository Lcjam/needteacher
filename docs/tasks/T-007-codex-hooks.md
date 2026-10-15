---
id: T-007
owner: codex
status: done   # todo | doing | blocked | done
scope:
  - .codex/
  - docs/tasks/T-007-codex-hooks.md
---

## 목표

Codex 쪽 훅을 설정해 공통 판정기 `scripts/guard.py`를 타게 하고, 판정기 자체의 우회 경로를 리뷰한다.

## 완료 조건

- [ ] `.codex/hooks.json`(또는 Codex가 읽는 올바른 위치): PreToolUse(Bash·`apply_patch`·MCP) → `python scripts/guard.py --agent codex`
- [ ] 실제 차단 확인: 무해한 금지 명령(예: `echo -DskipTests`)을 실행해 막히는지, 정상 명령(`git status`)은 통과하는지
- [ ] `scripts/guard.py` 리뷰: 우회 가능한 명령·경로 패턴 (P0/P1). 판정기는 고치지 말고 결과에 적는다
- [ ] `python scripts/test_guard.py` 통과 확인

## 참고

- `docs/proposals/test-harness.md` v4 (합의본) 5·8절
- `scripts/guard.py`, `scripts/test_guard.py`, `.github/CODEOWNERS`

## 결과

- `.codex/hooks.json`: PreToolUse 전체 도구 매처에서 저장소 루트의 `scripts/guard.py --agent codex` 호출(Windows/POSIX).
- 훅 명령 직접 호출: `echo -DskipTests` 차단, `git status` 허용, 보호 파일 `apply_patch` 차단; `backend/`에서 호출해도 동작.
- 실제 Codex CLI 검증은 셸 생성 전 `helper_unknown_error: setup refresh had errors`로 불가. 두 명령 모두 실행 실패했으므로 환경 복구 후 재확인 필요.
- `python scripts/test_guard.py`: `guard: all checks passed`.
- P0: `python -c "open('.harness-unlock','w').close()"`가 허용됨. 잠금 해제 파일을 만들면 보호 경로 검사 전체를 우회할 수 있음.
- P1: `cd backend && rm ../scripts/guard.py` 허용. 명령별 작업 디렉터리 변화를 경로 판정에 반영하지 않음.
- P1: `git clean -fdx scripts` 허용. `WRITE_OPS`가 `git clean`을 검사하지 않음.
- `scripts/guard.py`는 수정하지 않음.
