---
id: T-028
owner: agy
status: done    # todo | doing | blocked | done
scope:
  - docs/tasks/T-028-agy-always-proceed.md
---

## 목표

사용자 결정: agy를 `--dangerously-skip-permissions` 없이 실행하고, 승인 질문은 `"toolPermission": "always-proceed"`로 없앤다 (이 플래그는 PreToolUse 훅을 아예 호출하지 않아 guard가 꺼진다, T-027). 사용자가 그대로 따라 할 수 있는 설정 방법을 찾는다. 설정 파일은 쓰지 않는다 (홈 설정은 사용자만).

## 완료 조건

- [x] agy 1.3.5에서 `toolPermission` 설정이 있는지, 값 `always-proceed`의 의미를 공식 근거(`agy -p "/config"`, 번들 `docs/`, `--help`)로 확인.
- [x] 설정 방법: 정확한 파일 경로와 넣을 JSON 한 줄, 또는 세션 안에서 쓰는 명령. 워크스페이스 단위로 둘 수 있으면 그쪽도 (그 경우 경로만 적고 만들지 않는다).
- [x] 이 설정에서 PreToolUse 훅이 계속 호출되고 `deny`가 지켜지는지에 대한 근거 (공식 문서 또는 이슈 https://github.com/google-antigravity/antigravity-cli/issues/1053 의 보고).
- [x] 사용자 실행 순서를 3줄 이내로: 설정 → 플래그 없이 agy 재시작 → 확인.

## 결과

- 바꾼 파일: `docs/tasks/T-028-agy-always-proceed.md`
- 설정 존재 및 의미: `agy -p "/config"`에 `toolPermission` 존재 확인. 도구 실행 확인을 자동 승인(`auto-approving tool confirmation`)하여 무중단 실행하는 공식 정책.
- 설정 방법: `C:\Users\lcj\.gemini\antigravity-cli\settings.json`에 `"toolPermission": "always-proceed"` 추가 (또는 세션 내 `/config` 메뉴, 프로젝트별은 `~/.gemini/config/projects/`).
- PreToolUse 보장 근거: `always-proceed`는 승인 확인만 넘길 뿐 `applyPreToolHooks`는 먼저 실행되므로 `deny` 시 `PreToolHookDeniedError`로 차단됨(Issue #1053 및 바이너리 구조 확인).
- 사용자 실행 순서:
  1) `C:\Users\lcj\.gemini\antigravity-cli\settings.json`에 `"toolPermission": "always-proceed"` 추가
  2) wmux에서 `--dangerously-skip-permissions` 없이 `agy` 재실행
  3) `echo -DskipTests` 실행하여 훅 차단 동작 및 일반 명령 무프롬프트 진행 확인
