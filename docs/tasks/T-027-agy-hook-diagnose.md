---
id: T-027
owner: agy
status: done    # todo | doing | blocked | done
scope:
  - docs/tasks/T-027-agy-hook-diagnose.md
---

## 목표

재시작 후에도 `.agents/hooks.json` 훅이 agy 세션에서 동작하지 않는다(T-026 결과). 원인을 찾는다. 파일은 고치지 않는다.

## 완료 조건

- [x] `agy --version` 결과. 1.1.1 미만이면 "워크스페이스 훅이 폴더 신뢰 후에도 로드되지 않던 버그"(1.1.1에서 수정) 해당 여부.
- [x] 이 폴더(`C:\Users\lcj\Desktop\Project\needteacher`)가 신뢰된 폴더인지: `agy --help`·슬래시 명령(`/help` 등)·공식 문서로 확인 방법을 찾아 결과에 적는다. `--dangerously-skip-permissions`가 신뢰 질문을 건너뛰어 미신뢰로 남기는지도.
- [x] 사용자가 할 일을 한두 줄로 (예: 어떤 명령으로 신뢰 등록, 업데이트 명령).
- [x] 설정 형식 확인: 공식 예시는 최상위 `"hooks": {"PreToolUse": [...]}`, 우리 파일은 이름 붙은 그룹 `"guard": {"PreToolUse": [...]}`. 네 버전이 어느 형식을 읽는지 공식 근거로.
- [x] 홈 설정(`~/.gemini/...`)은 읽기만, 쓰지 않는다.

## 참고

- `docs/tasks/T-008-agy-hooks.md`, `docs/tasks/T-026-guard-fix-2.md`
- 참고 자료: https://agenticcontrolplane.com/blog/antigravity-hooks-reference , https://github.com/yanokamay-org/maiestro/blob/main/docs/session-status.md (Windows는 `cmd /c "<command>"`로 실행, 워크스페이스 훅은 신뢰 폴더에서만)

## 결과

- 바꾼 파일: `docs/tasks/T-027-agy-hook-diagnose.md`
- 버전 확인: `agy --version` 1.3.5로 1.1.1 미만 버그에 해당하지 않음.
- 폴더 신뢰: `agy -p "/config"` 실측 결과 `trustedWorkspaces`에 해당 폴더가 이미 등록되어 신뢰 상태임.
- 훅 형식 검증: 바이너리 타입(`map[string]jsonhook.JSONHookSpec`)과 `agy -p "/hooks"` 출력(`guard enabled PreToolUse...`) 및 공식 문서(`docs/hooks.md`) 모두 이름 붙은 그룹(`"guard": {...}`)이 공식 규격임을 확인.
- 미동작 원인: 현재 agy가 `--dangerously-skip-permissions` 플래그로 구동 중이며, 이 플래그가 모든 도구 권한(PreToolUse) 평가 및 프롬프트를 자동 승인(auto-approve) 처리하여 훅 차단 로직이 우회됨.
- 사용자 조치: wmux에서 agy 실행 시 `--dangerously-skip-permissions` 플래그를 제거하고 재시작.
