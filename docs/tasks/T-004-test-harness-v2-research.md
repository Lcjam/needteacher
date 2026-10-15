---
id: T-004
owner: agy
status: done    # todo | doing | blocked | done
scope:
  - docs/tasks/T-004-test-harness-v2-research.md
---

## 목표

`docs/proposals/test-harness.md` v2의 열린 질문을 조사한다.

## 완료 조건

- [x] agy 훅(`.agents/hooks.json`)의 입력 JSON 형식, 차단 방법(종료 코드/출력), 오류·타임아웃 시 동작 (열린 질문 2)
- [x] GitHub public 저장소에서 브랜치 보호/ruleset으로 "관리자 포함 우회 금지 + 필수 상태 검사 + 특정 경로 변경 시 코드오너 리뷰"가 무료로 되는지 (열린 질문 1)
- [x] 머신 유저(에이전트용 별도 계정) 방식이 GitHub 약관상 허용되는지, 대안(GitHub App 등)
- [x] gitleaks를 pre-commit과 Actions에 붙이는 공식 방법과 현재 버전
- [x] 토론: Codex의 T-001 결과 중 동의하지 않는 항목과 근거 (예: Maven 추천, "PIT 증분은 최종 게이트로 부적합" 주장). 전부 동의하면 그렇다고 쓴다.

## 참고

- `docs/proposals/test-harness.md`
- `docs/tasks/T-001-test-harness-review.md` (Codex 1차 의견)
- `docs/tasks/T-002-test-harness-research.md`

## 결과

- 바꾼 파일: `docs/tasks/T-004-test-harness-v2-research.md` (검증: GitHub Docs/API, agy hooks 스펙, gitleaks 릴리스 확인)
- 1순위(보호파일/1인 개발): GitHub public 저장소는 ruleset(관리자 우회금지, 필수검사, CODEOWNERS 리뷰) 무료 지원. 에이전트용 GitHub App(무료, seat 미소비)으로 PR 생성 시 사용자(Lcjam)가 사람으로서 승인 강제 가능(머신 계정 불필요, https://docs.github.com/en/apps ).
- 2순위(agy 훅 스펙): `.agents/hooks.json`은 stdin(camelCase JSON) 수신, stdout으로 `{"decision":"deny"|"continue","reason":"..."}` 출력 시 차단. 훅 오류/타임아웃은 fail-open(허용 진행)이므로 서버 CI가 최종 방어선 필수.
- 3순위(공통 훅 어댑터): 세 에이전트(Claude/Codex/agy)의 훅 JSON 스키마·도구명이 상이하므로 공통 판정 스크립트 앞단에 훅별 입출력 변환 어댑터 래퍼 필수.
- 4순위(gitleaks): 최신 v8.30.1. pre-commit은 `gitleaks protect --staged`, CI는 `gitleaks/gitleaks-action@v3` + `actions/checkout@v6`(fetch-depth: 0) 공식 지원 (출처: https://github.com/gitleaks/gitleaks/releases ).
- 5순위(Codex 반박 - Maven): 향후 웹/앱(프론트) OpenAPI 타입 생성 및 빌드 태스크 연동 시 Maven xml 설정은 장황·경직됨. 증분 캐시 성능 면에서도 Gradle이 장기적으로 우월.
- 6순위(Codex 반박 - PIT 증분): PIT 증분이 불완전하다며 배제하는 것은 비현실적. 로컬 fast 게이트는 증분(`withHistory`)으로 개발 속도를 지키고, 누락 방지는 CI full(전체 PIT)에 일임하는 2단계 분리가 최선.
- 남은 일: 오케스트레이터의 test-harness.md v3 최종 합의 및 스캐폴딩.
