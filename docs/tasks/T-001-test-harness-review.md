---
id: T-001
owner: codex
status: done    # todo | doing | blocked | done
scope:
  - docs/tasks/T-001-test-harness-review.md
---

## 목표

`docs/proposals/test-harness.md` 초안을 비판적으로 리뷰한다. 승인이 아니라 구멍 찾기가 목적.

## 완료 조건

- [ ] 에이전트가 게이트를 우회할 수 있는 경로 (초안이 놓친 것)
- [ ] Gradle vs Maven 추천과 이유 (이 게이트들 기준)
- [ ] 수치(90/85/80)와 PIT 실행 시간의 현실성
- [ ] Windows + Git Bash 환경에서 깨질 부분
- [ ] Codex CLI 쪽에서 같은 게이트를 강제할 수단 (훅, 설정 등)이 있는지

## 참고

- `AGENTS.md` (공통 규칙. Codex가 만든 원본은 Claude가 덮어썼고, 내용은 루트와 `backend/AGENTS.md`로 나눠 옮김)
- `docs/proposals/test-harness.md`

## 결과

- P0: 로컬 pre-commit은 `--no-verify`·`core.hooksPath` 변경·훅 파일 수정으로 우회된다. 목표가 “커밋 불가”라면 불가능하며, 병합 게이트는 서버 CI의 필수 상태 검사/브랜치 보호로 둬야 한다. [GitHub 문서](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches)
- P0: `git diff --diff-filter=D`는 기본적으로 미스테이징 변경만 본다. 스테이징된 삭제·이름 변경·테스트 수정·빌드 설정/verify 스크립트 변조도 검사해야 하며, 커밋할 인덱스와 검증한 파일 집합을 일치시켜야 한다.
- P0: `backend/src`가 없으면 통과하는 조건은 빈 스캐폴드/소스 이동에 취약하다. `*IT` 미발견, PIT 대상 0개, ArchUnit 테스트 제거, JaCoCo/PIT 제외 범위 확대로도 수치만 통과할 수 있으므로 대상·실행 건수를 검증해야 한다.
- P1: **Maven 추천**: 단일 Spring 백엔드에 Surefire·Failsafe(`*IT`)·JaCoCo `verify`·PIT 공식 플러그인의 실행 경로가 명확하다. 단, PIT는 `mvn verify`에 자동 포함되지 않으므로 별도 goal을 명시/바인딩해야 한다. [Failsafe](https://maven.apache.org/surefire/maven-failsafe-plugin/) · [PIT](https://pitest.org/quickstart/maven/)
- P1: 90% line/85% branch/80% mutation은 코드·측정 범위가 없는 지금 근거가 없다. 첫 수직 기능의 기준선을 재고 DTO/설정 등 제외 목록을 고정한 뒤 상향하되, 신규 비즈니스 코드와 변경 코드의 회귀 기준을 먼저 적용한다.
- P1: 전체 PIT를 매 커밋 실행하면 테스트·Testcontainers 수와 함께 비용이 커진다. 빠른 로컬 게이트와 전체 PIT CI를 분리하고 실제 소요 시간을 측정해야 한다. PIT 증분 이력은 의존 클래스 변경을 충분히 추적하지 않아 최종 필수 게이트로 쓰기 어렵다. [PIT 문서](https://pitest.org/quickstart/incremental_analysis/)
- P1: Windows+Git Bash에서는 `.sh`의 CRLF/실행권한, PowerShell의 래퍼(`mvnw.cmd`), 경로 인용·MSYS 경로 변환, Docker/Testcontainers 연결을 실제 Windows와 Linux CI 양쪽에서 검증해야 한다.
- P1: Codex CLI도 프로젝트 `.codex/hooks.json`/`config.toml`의 `PreToolUse`·`Stop` 훅을 지원한다. 다만 신뢰 승인 전 훅은 건너뛰며 오류/타임아웃은 허용으로 흐를 수 있어 강제 경계가 아니다; `PreToolUse`는 `Bash`·`apply_patch`·MCP를 함께 다뤄야 한다. [OpenAI Docs](https://learn.chatgpt.com/docs/hooks)
- 검증: 제안서·`backend/AGENTS.md`와 공식 OpenAI/Gradle/Maven/PIT 문서를 대조했다. 실행 검증은 스캐폴드가 없어 불가; 남은 일은 빌드 구성과 Windows/CI 실측.
