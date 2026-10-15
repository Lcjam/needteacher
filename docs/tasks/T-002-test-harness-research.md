---
id: T-002
owner: agy
status: done    # todo | doing | blocked | done
scope:
  - docs/tasks/T-002-test-harness-research.md
---

## 목표

`docs/proposals/test-harness.md` 초안에 대해 조사 기반 의견을 낸다. 초안이 놓친 걸 찾는 게 목적.

## 완료 조건

- [x] 2026-10 기준 Spring Boot, JaCoCo, PIT, ArchUnit, Testcontainers 최신 안정 버전과 로컬 Java 17 호환 여부
- [x] Antigravity CLI의 워크스페이스 훅(`.agents/hooks.json` 등)으로 agy 쪽에도 같은 게이트를 걸 수 있는지, 형식은 어떤지
- [x] 프론트엔드(웹/앱) 테스트 전략 제안, 백엔드와의 계약 테스트(OpenAPI 등) 포함
- [x] 초안에 빠진 테스트 종류나 게이트

## 참고

- `AGENTS.md` (공통 규칙)
- `docs/proposals/test-harness.md`

## 결과

- 바꾼 파일: `docs/tasks/T-002-test-harness-research.md` (검증: Maven Central 메타데이터 API, Docker/Java CLI, agy hook 스펙)
- 1순위(환경/차단): Docker 29 CLI 설치됐으나 데몬 미기동 확인(Testcontainers 전제 깨짐, 사전 검증 필요). Windows 환경용 verify 실행 수단 필요.
- 2순위(agy 강제 게이트): `.agents/hooks.json` PreToolUse로 금지 플래그/파일수정 차단, Stop 훅으로 verify 실패 시 강제 계속(종료 차단) 구현 가능.
- 3순위(2026-10 버전/Java 17 호환 - 전원 호환, 출처: https://repo1.maven.org/maven2/ ): Boot 3.5.16/4.1.1, JaCoCo 0.8.15, PIT 1.30.0(junit5-plugin 1.2.3), ArchUnit 1.5.1, Testcontainers 2.0.5
- 4순위(계약/프론트): springdoc-openapi -> openapi-typescript 코드생성 기반 컴파일 계약 테스트 도입, 프론트(Vitest/tsc) 게이트 추가 필요.
- 5순위(누락 게이트/현실성): Flyway 마이그레이션 및 시크릿 스캔 누락. PIT 80%는 초기 과도(60% 권장) 및 증분(`withHistory`) 필수.
- 남은 일: 오케스트레이터의 test-harness.md 최종안 반영 및 스캐폴딩 진행.
