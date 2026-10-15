---
id: T-006
owner: agy
status: done    # todo | doing | blocked | done
scope:
  - docs/tasks/T-006-test-harness-v3-debate.md
---

## 목표

`docs/proposals/test-harness.md` v3 최종 토론. 이번 라운드에서 합의를 끝내는 게 목적.

## 완료 조건

- [x] Maven vs Gradle: Codex의 Maven 논거(T-001 P1, T-003)를 읽고 최종 입장. 프론트는 별도 npm 패키지라 빌드 통합이 필요 없다는 반론도 고려. 입장을 바꿔도 된다
- [x] GitHub App 방식 실무 절차: 생성·권한·설치·설치 토큰 발급을 Windows에서 git push/gh에 쓰는 방법
- [x] App이 남긴 승인 리뷰가 ruleset의 필수 승인 수에 포함되는지 (포함되면 구멍)
- [x] Windows에서 gh CLI·Git Credential Manager의 사용자 토큰을 에이전트로부터 격리하는 방법

## 참고

- `docs/proposals/test-harness.md` (v3)
- `docs/tasks/T-003-test-harness-v2-review.md` (Codex 2차 의견)
- `docs/tasks/T-004-test-harness-v2-research.md`

## 결과

- 바꾼 파일: `docs/tasks/T-006-test-harness-v3-debate.md` (검증: GitHub Rulesets/Apps 공식 문서, GCM/gh 인증 메커니즘 확인)
- 1순위(Maven vs Gradle 최종 합의): **Maven 채택 동의**. 프론트는 `frontend/` 독립 npm 구조로 백엔드 빌드 묶임 불필요. 선언적 POM이 에이전트 임의 스크립트 삽입 차단 및 CODEOWNERS 검토에 더 안전.
- 2순위(App 승인 구멍 검증): GitHub 정책상 PR 작성자(App)는 자기 PR 승인 불가(Self-approval 차단). 또한 ruleset에 `Require review from Code Owners`(`* @Lcjam`) 설정 시 App 리뷰는 필수 승인에 미반영되므로 승인 위조 구멍 없음(출처: https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/about-rulesets ).
- 3순위(사용자 토큰 격리 방안): 에이전트 세션의 CLI/GCM에서 사용자 토큰을 완전 로그아웃(`gh auth logout`, 자격증명 삭제)하고 App 임시 토큰만 제공. 사용자는 브라우저에서만 승인·병합하는 것이 가장 완벽하고 현실적인 격리.
- 4순위(GitHub App 실무 절차): App 생성(Contents/PRs Read-Write 권한, Webhook 끔) -> 저장소 설치 -> Private key로 JWT 서명 후 설치 토큰(1시간 유효) 발급 -> `GH_TOKEN` 및 Git HTTP 헤더에 주입하여 push/PR(출처: https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-an-installation-access-token-for-a-github-app ).
- 남은 일: v3 합의 기반으로 사용자 Docker/App 사전 작업 진행 및 프로젝트 스캐폴딩 착수.
