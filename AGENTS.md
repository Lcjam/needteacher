# AGENTS.md

Claude Code·Codex CLI·Antigravity CLI 공통 규칙. 항상 컨텍스트에 올라가므로 프로젝트 전체에 해당하는 내용만 둔다.

## 프로젝트

needteacher: 과외 선생님과 학생/학부모를 매칭하는 웹/앱 플랫폼. 전봇대 과외 전단지를 대체하는 게 출발점이고, 첫 타깃은 대구 수성구. 백엔드는 Java/Spring. 아직 코드 없음.

## 컨텍스트 계층

| 계층 | 위치 | 언제 읽나 |
|---|---|---|
| L0 공통 | `AGENTS.md` (이 파일) | 항상 |
| L1 모듈 | `<모듈>/AGENTS.md` | 그 모듈을 작업할 때만 |
| L2 작업 | `docs/tasks/T-NNN-*.md` | 배정받은 카드만 |

- 빌드·테스트 명령, 코딩 컨벤션은 해당 모듈의 L1 파일에 쓴다. 루트에 쓰지 않는다.
- 에이전트끼리는 작업 카드로만 주고받는다. 대화 내용이나 로그를 서로 붙여넣지 않는다.

## 역할

세 세션이 wmux에서 같은 디렉토리·같은 브랜치로 돈다 (`pane_list`로 확인).

| 세션 | 역할 |
|---|---|
| Claude Code | 오케스트레이터: 카드 작성·배정·통합·커밋. 구현은 하위 에이전트에 위임 |
| Codex CLI | 리뷰·테스트 (인수 테스트를 구현보다 먼저 작성) |
| Antigravity CLI | 프론트엔드·자료 조사. `toolPermission: always-proceed`로 승인 없이 실행하되 훅은 켜져 있다 (`--dangerously-skip-permissions`는 훅을 끄므로 쓰지 않는다) |

## 작업 카드 규칙

1. 형식은 `docs/tasks/_template.md`. 리뷰도 별도 카드로 만든다 (`참고`에 원래 카드 번호).
2. 시작하면 `status: doing`, 끝나면 `done`, 막히면 `blocked` + 이유.
3. 카드의 `scope` 안의 파일만 수정한다. 범위 밖 수정이 필요하면 결과에 적고 멈춘다.
4. `## 결과`는 10줄 이내: 바꾼 파일, 검증 방법과 결과, 남은 일. 긴 로그는 붙이지 않는다.
5. 커밋은 오케스트레이터만 한다. 같은 작업 트리를 공유해서 git index가 섞이기 때문. 커밋 제목은 짧은 영어 명령형 (`Add tutor profile model`).
6. 수정 전에 `git status --short`로 다른 세션의 변경을 확인한다. 다른 세션이 만든 파일은 읽고 나서 고치고, 그 변경을 버리지 않는다.

현황: `grep -H '^status:' docs/tasks/T-*.md`

## 테스트 하네스

명세는 `docs/proposals/test-harness.md` (v4 합의본). 최종 판정은 GitHub CI이고 로컬 검사는 빠른 피드백이다.

- 검증: `bash scripts/verify.sh fast` (커밋될 인덱스), `fast --worktree` (스테이징 전 작업 트리), `full` (CI 필수 검사).
- 순서: ① Codex가 `src/test/java`에 인수 테스트 작성(기존과 안 겹치는 새 파일명, 필요 시 새 헬퍼 테스트 파일 포함), 옮기기 전에 `spotlessJavaCheck`로 형식을 맞추고 red 다시 확인 → ② 사용자 잠금 해제 중 오케스트레이터가 `src/acceptance/java`로 이동, 잠금 후 원본만 커밋 → ③ 원본 전용 PR 코드오너(Lcjam) 승인·병합 → ④ 구현 담당이 원본을 `src/test/java` 같은 경로로 복사하고 구현(green) → ⑤ CI가 원본 ↔ 실행 테스트 바이트 일치 비교. 원본 대기 중에는 다른 일반 PR을 병합할 수 없으며, 요구 변경·폐기는 원본 전용 PR로 먼저 확정한다.
- 보호 파일: `.github/CODEOWNERS` 목록. 훅이 수정을 막는다. 바꿔야 하면 카드에 사유를 적고 사용자에게 요청한다. 잠금 해제 파일 `.harness-unlock`은 사용자만 만든다.
- 금지 (훅·verify·CI가 막음): 테스트 스킵·실패 무시 플래그, `--no-verify`, `@Disabled`·`assume*` 류, 등록 없는 기존 테스트 변경·삭제(기존 테스트 파일은 불변, 새 테스트는 새 파일로)·메서드 감소, wmux `repl_run`·`terminal_send`로 다른 터미널에 명령 보내기.
- 기존 테스트 파일을 고치거나 지우거나 줄여야 하면 같은 변경에서 `quality/test-removals.txt`에 경로를 추가한다 (사용자 리뷰).
