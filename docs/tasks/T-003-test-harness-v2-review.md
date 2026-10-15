---
id: T-003
owner: codex
status: done    # todo | doing | blocked | done
scope:
  - docs/tasks/T-003-test-harness-v2-review.md
---

## 목표

`docs/proposals/test-harness.md` v2를 다시 리뷰한다. T-001 지적이 제대로 반영됐는지, v2에서 새로 생긴 구멍은 없는지.

## 완료 조건

- [ ] T-001 P0 3개가 v2에서 막혔는지 항목별 판정
- [ ] 열린 질문 1 (보호 파일 사용자 승인 강제 방법)에 대한 추천안
- [ ] 열린 질문 3 (`checkout-index` 검증 방식) 함정
- [ ] Codex 훅(`.codex/hooks.json`)의 입력 JSON 형식과, 공통 판정 스크립트에서 쓰려면 무엇이 필요한지 (열린 질문 2)
- [ ] 토론: agy의 T-002 결과 중 동의하지 않는 항목과 근거 (예: PIT 60% 제안, `withHistory` 증분 필수 주장). 전부 동의하면 그렇다고 쓴다.

## 참고

- `docs/proposals/test-harness.md`
- `docs/tasks/T-001-test-harness-review.md`
- `docs/tasks/T-002-test-harness-research.md` (agy 1차 의견)

## 결과

- P0/T-001① **설계상 해소, 설정 검증 전**: 서버 필수 검사가 최종 게이트가 됐다. 다만 필수 job에 `if`/`continue-on-error`를 두면 skipped/neutral도 병합 가능하므로 항상 실행·실패 종료, 고유한 검사명과 GitHub Actions 출처 고정, 관리자 우회 금지를 실제 설정으로 확인해야 한다. [GitHub](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-jobs-with-conditions)
- P0/T-001② **부분 해소**: 스테이징 diff·`checkout-index`가 들어왔으나 공유 인덱스는 추출/테스트 중 변할 수 있다. 인덱스를 별도로 고정해 `git write-tree`의 tree ID로 검증 대상을 기록하고, 커밋 직전 인덱스 tree ID가 같을 때만 로컬 통과로 취급한다.
- P0/T-001③ **부분 해소**: 0건 검사·ArchUnit 존재 검사는 개선이다. `backend/pom.xml`, `.mvn/**`, `mvnw*`가 보호 목록 밖이라 테스트/PIT/JaCoCo 실행·제외 규칙을 무력화할 수 있다. 소스/측정 대상 0건과 신규 코드가 없는 PR의 0 뮤턴트 처리도 명세가 필요하다.
- P0/질문1: 현재처럼 에이전트가 사용자 토큰을 쓰면 서버가 승인 주체를 구별할 수 없다. 별도 저권한 머신 사용자/앱으로 에이전트 PR을 만들고 사람 계정만 CODEOWNER로 지정해 필수 코드오너 리뷰·오래된 승인 취소·관리자 우회 금지를 적용한다. `CODEOWNERS` 자체와 `pom.xml`/래퍼도 소유 경로에 넣는다. [GitHub](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-code-owners)
- P1/질문3: `checkout-index -a`는 skip-worktree 파일을 빼고 충돌 인덱스를 그대로 내보낼 수 없으며, 임시 트리에는 `.git`가 없어 그 안의 `git diff`/버전 플러그인이 실패한다. 스캔은 원본의 고정 인덱스에, 빌드는 스냅샷에 적용하고 symlink·실행 비트·숨은 `.mvn` 파일을 Windows에서 확인한다. [Git](https://git-scm.com/docs/git-checkout-index)
- P1/질문3: 임시 디렉토리마다 `target` 빌드 산출물은 재생성되지만 Maven 로컬 저장소는 기본적으로 사용자 홈에 남는다. Testcontainers 재사용은 실험적이고 CI에 부적합하므로 비용 절감 전제로 삼지 말고 fast/full 실측 시간을 기록한다. [Maven](https://maven.apache.org/guides/mini/guide-configuring-maven.html) · [Testcontainers](https://java.testcontainers.org/features/reuse/)
- P1/질문2: Codex `PreToolUse`는 stdin JSON의 `hook_event_name`, `cwd`, `tool_name`, `tool_input`을 받으며 Bash/`apply_patch`는 `tool_input.command`, MCP는 도구별 인자다. 공통 판정 함수 앞에 에이전트별 입력 정규화·출력 어댑터가 필요하다; Codex 차단 출력은 `hookSpecificOutput:{hookEventName:'PreToolUse',permissionDecision:'deny',permissionDecisionReason:'...'}`이고 Stop은 별도 `decision:'block'`이다. [OpenAI Docs](https://learn.chatgpt.com/docs/hooks)
- P1/T-002 반박: PIT 60%와 80% 모두 실측 전 임의 수치이므로 60% 일괄 하향에 동의하지 않는다. `withHistory` 필수 주장도 반대한다; PIT가 증분을 실험적이며 의존성 변경 판정에 오류 가능성이 있다고 명시한다. CI full은 이력 없이, 로컬 fast에서만 선택적으로 쓰는 편이 안전하다. [PIT](https://pitest.org/quickstart/incremental_analysis/)
- P1/v2 신규 모순: PIT를 Maven `verify`에 무조건 묶으면 fast도 전체 PIT를 실행한다. 모드별 프로필/goal을 분리해야 한다. 커밋된 `openapi.json`은 `tsc`만으로 최신성이 보장되지 않으므로 CI에서 재생성 후 diff도 검사해야 한다.
- 검증: v2·T-001·T-002와 GitHub/Git/OpenAI/Maven/PIT/Testcontainers 공식 문서를 대조했다. 수정 파일은 이 카드뿐이며, 남은 일은 스캐폴드 후 CI 규칙·훅 JSON·Windows 스냅샷/실행 시간 실측이다.
