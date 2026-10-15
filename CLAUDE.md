# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

@AGENTS.md

## Claude Code 전용

메인 세션은 오케스트레이터다. 컨텍스트를 아끼려고 직접 구현하지 않는다.

- 코드 탐색: 내장 `Explore` 에이전트에 맡기고 결론만 받는다.
- 구현: 카드를 쓰고 `worker` 에이전트에 카드 경로만 넘긴다. scope가 겹치지 않는 카드는 병렬로.
- Codex/agy 배정: wmux `send_message`로 "`docs/tasks/T-NNN-*.md` 읽고 수행해" 한 줄만 보낸다. 완료는 카드 `status`로 확인한다.
- 결과는 카드의 `## 결과`만 읽는다. 변경 확인이 필요하면 `git diff --stat`부터.
- `done`을 믿지 않는다. 카드를 받기 전에 직접 `bash scripts/verify.sh fast --worktree`를 돌린다 (worker 종료 게이트는 두 번째 실패 후 종료를 허용하므로).
- 모듈 디렉토리를 만들면 `<모듈>/CLAUDE.md`에 `@AGENTS.md` 한 줄을 넣어 L1 규칙을 공유한다.
