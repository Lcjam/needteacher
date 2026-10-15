---
id: T-010
owner: agy
status: done    # todo | doing | blocked | done
scope:
  - docs/setup.md
  - docs/tasks/T-010-setup-guide.md
---

## 목표

사용자가 직접 해야 하는 설정을 따라 하기만 하면 되는 안내서 `docs/setup.md`로 쓴다. 조사 결과는 출처와 함께.

## 완료 조건

- [x] Windows에 gitleaks v8.30.1 설치 (winget/scoop 등 공식 경로) + 확인 명령
- [x] GitHub App 만들기: 권한(Contents·Pull requests 읽기/쓰기, Metadata 읽기만, Administration·Workflows 없음), 웹훅 끔, 이 저장소에만 설치, 개인키 보관 위치(저장소 밖)
- [x] main ruleset 설정 화면 기준 절차 (`docs/proposals/test-harness.md` 6절 항목 하나하나)
- [x] 자격 증명 격리 기본안: `gh auth logout`, Git Credential Manager에서 github.com 자격 증명 삭제, 되돌리는 방법
- [x] 조사: Spotless + palantir-java-format(4칸)을 Maven에 붙이는 현재 버전 (`backend/AGENTS.md`의 "포매터는 첫 스캐폴딩 때" 규칙용). 결과에만 쓴다

## 참고

- `docs/proposals/test-harness.md` v4 5~7절
- `docs/tasks/T-006-test-harness-v3-debate.md` (App 절차 조사)

## 결과

- 바꾼 파일: `docs/setup.md`, `docs/tasks/T-010-setup-guide.md` (검증: winget gitleaks 패키지 확인, Maven Central 메타데이터 조회)
- 1순위(gitleaks v8.30.1): `winget install --id Gitleaks.Gitleaks --version 8.30.1` 공식 지원 확인(`gitleaks version`으로 검증).
- 2순위(GitHub App 생성·설치): Contents/PRs(Read-Write), Metadata(Read) 최소 권한 및 Webhook 비활성화. 개인키는 저장소 외부(`~/.github-apps/`) 보관 안내.
- 3순위(main Ruleset): 관리자 우회 금지(Bypass 없음), force-push/삭제 차단, PR 1인 승인 + CODEOWNERS 필수 + stale 승인 취소 + Actions 필수 검사(`verify-full`, `gitleaks`) 단계별 명시.
- 4순위(자격 증명 격리·복구): `gh auth logout`, `cmdkey /delete:git:https://github.com`으로 로컬 제거 및 브라우저 승인 원칙 수립, `gh auth login` 복구 절차 기술.
- 5순위(조사 - Spotless + palantir-java-format): `spotless-maven-plugin: 3.10.4` + `palantir-java-format: 2.102.0` (출처: https://repo1.maven.org/maven2/com/diffplug/spotless/spotless-maven-plugin/ ). palantirJavaFormat은 4칸 들여쓰기를 자체 강제하므로 `<palantirJavaFormat>` 태그 설정으로 4칸 자동 충족.
- 남은 일: 사용자 안내서 실행(gitleaks/App/Ruleset/자격증명) 후 백엔드 스캐폴딩 착수.
