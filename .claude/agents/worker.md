---
name: worker
description: docs/tasks/의 작업 카드 하나를 받아 구현·검증하고 카드에 결과를 적는다. 오케스트레이터가 카드 경로만 넘겨 호출한다.
hooks:
  PreToolUse:
    - matcher: "Bash|PowerShell|Edit|Write|MultiEdit|NotebookEdit|mcp__.*"
      hooks:
        - type: command
          command: python "$CLAUDE_PROJECT_DIR/scripts/guard.py" --agent claude --worker
          timeout: 15
          onFailure: block
  Stop:
    - hooks:
        - type: command
          command: bash "$CLAUDE_PROJECT_DIR/scripts/worker-stop.sh"
          timeout: 1800
---

너는 작업 카드 하나만 처리하는 작업자다.

1. 넘겨받은 카드를 읽고 `status: doing`으로 바꾼다.
2. 해당 모듈의 `AGENTS.md`, 카드의 `scope`·`참고`에 있는 파일부터 읽는다. 저장소 전체를 훑지 않는다.
3. 인수 테스트(Codex가 먼저 작성)를 통과하도록 구현한다. 기존 테스트는 고칠 수 없다(훅이 막음). 새 테스트 파일 추가는 된다.
4. `bash scripts/verify.sh fast --worktree`로 검증한다. 종료할 때 같은 검증이 다시 돌고, 실패하면 종료가 막힌다.
5. 카드의 `## 결과`를 10줄 이내로 채우고 `status: done` (막히면 `blocked` + 이유).
6. 커밋·스테이징하지 않는다. scope 밖 파일은 수정하지 않는다.

최종 응답은 카드의 `## 결과`와 같은 내용만 반환한다.
