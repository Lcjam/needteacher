---
id: T-005
owner: codex
status: done    # todo | doing | blocked | done
scope:
  - docs/tasks/T-005-test-harness-v3-debate.md
---

## 목표

`docs/proposals/test-harness.md` v3 최종 토론. 이번 라운드에서 합의를 끝내는 게 목적.

## 완료 조건

- [ ] T-003 지적이 v3에서 막혔는지 항목별 판정
- [ ] Maven vs Gradle: agy의 Gradle 논거(T-004 5순위)를 읽고 최종 입장. 입장을 바꿔도 된다
- [ ] 자격 증명 격리(v3 6절)의 구멍과 Windows에서의 현실적 방법
- [ ] v3에 남은 우회 경로. 없으면 없다고 쓴다

## 참고

- `docs/proposals/test-harness.md` (v3)
- `docs/tasks/T-003-test-harness-v2-review.md`
- `docs/tasks/T-004-test-harness-v2-research.md` (agy 2차 의견)

## 결과

- P0/T-003① **설계상 반영**: ruleset·필수 job의 무조건 실행·검사 출처 고정이 들어왔다. 실제 GitHub 설정과 CI 파일은 아직 없으므로 우회 불가 판정은 스캐폴드 후 실패/skip PR로 확인해야 한다.
- P0/T-003② **부분 반영**: `write-tree`/`archive`와 끝의 tree ID 비교로 빌드 입력은 고정된다. 그러나 `git diff --cached`와 `gitleaks protect --staged`는 여전히 실행 시점의 공유 인덱스를 읽는다. 삭제·변경 검사도 `git diff HEAD "$tree"`처럼 고정 tree를 참조해야 한다. [Git](https://git-scm.com/docs/git-diff)
- P0/T-003③ **부분 반영**: POM·래퍼·측정 대상 0건 보호는 해결됐다. 기존 테스트 메서드/ArchUnit 규칙을 파일 안에서 지우거나 약화해도 테스트 수와 클래스 존재 조건은 통과할 수 있다. 인수 테스트·ArchUnit 테스트 변경은 별도 사람 리뷰 대상에 넣거나 기준 커밋과의 차이를 CI에서 심사해야 한다.
- P0/남은 우회: `.gitleaks.toml`·`.gitleaksignore`가 보호 목록 밖이면 탐지 규칙/허용 목록을 바꿀 수 있다. 둘 다 CODEOWNERS에 포함하고, `protect`는 공식 문서상 숨겨진 구형 명령이므로 v8.30.1에서 staged 입력과 양성 샘플 검출을 확인한다. [Gitleaks](https://github.com/gitleaks/gitleaks#usage)
- P1/T-003 질문1·2: GitHub App 설치 토큰+사람 CODEOWNER, 에이전트별 훅 어댑터는 설계상 반영됐다. App **설치** 토큰으로 PR을 열어야 봇 신원이 유지되며, 사용자 위임 토큰이나 현재 Lcjam 자격 증명을 에이전트가 읽으면 승인 분리가 무너진다. [GitHub](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/differences-between-github-apps-and-oauth-apps)
- P1/Windows 자격 증명: 세 에이전트가 Lcjam Windows 계정에서 실행되면 `gh` 로그아웃/환경 변수 변경만으로 GCM·브라우저 자격 증명을 격리할 수 없다. 별도 비관리자 Windows 계정(또는 별도 VM)에서 에이전트를 실행하고 공유 작업 폴더에만 권한을 주며, 사람 브라우저는 Lcjam 계정에 둔다. App 개인키는 별도 브로커가 보관하고 에이전트에는 단기·최소권한 설치 토큰만 준다. [Microsoft](https://learn.microsoft.com/en-us/windows/win32/api/dpapi/nf-dpapi-cryptprotectdata)
- P1/T-003 질문3·추가 지적: `archive`로 skip-worktree/충돌 인덱스 문제는 줄었지만 스냅샷에는 `.git`가 없다. 빌드·OpenAPI 생성에서 Git 메타데이터 사용 여부, 래퍼 실행 비트·심볼릭 링크·LFS/서브모듈을 실제 Windows/Ubuntu CI에서 확인해야 한다.
- P1/T-003 나머지: fast/full PIT 분리, 전체 PIT 이력 배제, OpenAPI 재생성 diff, Testcontainers 재사용 배제가 v3에 반영됐다. fast는 운영 클래스 변경이 없을 때 PIT 생략을 허용하므로 병합의 필수 검사는 반드시 full이어야 한다.
- **최종 입장: Maven 유지.** agy의 Gradle 캐시는 임시 스냅샷 사이에도 재사용 가능하다는 점을 인정한다. 다만 외부 DB 통합 테스트 캐시는 입력 관리가 어렵고 PIT 비용은 별개이며, 현재는 단일 Spring 백엔드다. 프론트/OpenAPI는 CI 단계로 연결 가능하다. 스캐폴드 후 fast/full 실측에서 병목이 확인되면 Gradle 전환을 재검토한다. [Gradle](https://docs.gradle.org/current/userguide/caching_java_projects.html)
- 검증: v3·T-003·T-004와 Git/Gradle/GitHub/Microsoft/Gitleaks 공식 문서를 대조했다. 수정 파일은 이 카드뿐; 남은 일은 CI·ruleset·별도 Windows 신원 구성 후 우회 시나리오와 실행 시간 실측이다.
