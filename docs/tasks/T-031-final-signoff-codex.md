---
id: T-031
owner: codex
status: done    # todo | doing | blocked | done
scope:
  - docs/tasks/T-031-final-signoff-codex.md
---

## 목표

첫 커밋 전 테스트 하네스 최종 승인. agy(T-032)와 따로 판단한다. 파일은 고치지 않는다.

## 완료 조건

- [x] `docs/proposals/test-harness.md` 명세와 실제 구현(`scripts/`, `backend/build.gradle.kts`, `backend/src/test/java/**/architecture/`, `.github/workflows/ci.yml`, 세 훅 설정)이 어긋나는 곳.
- [x] "구현을 하나도 안 하고 테스트만 맞춰서" 또는 "테스트를 약하게 해서" 게이트를 통과하는 방법이 남았는지. 명세의 '남은 한계'에 이미 적힌 것은 제외.
- [x] 첫 기능(예: 과외 선생님 프로필)을 이 하네스로 만들 때 개발자가 막힐 만한 과도한 제약(거짓 양성)이 있는지.
- [x] 결론 한 줄: "승인" 또는 "P0: …".

## 참고

- `docs/tasks/T-001`~`T-030` 결과 절. 저장소 `verify full --worktree` 80초 통과, 테스트 23건, line·mutation 100%.

## 결과

- P0: 기능 인수 테스트의 본문만 기존 `ping` 검증 등으로 바꿔 `@Test` 개수를 유지하면, 기능 구현이 없어도 기존 테스트·커버리지·PIT 점수로 CI가 통과할 수 있다. 일반 테스트는 CODEOWNERS 보호 대상이 아니고 worker 훅은 최종 게이트가 아니다.
- P1 명세 불일치: `verify.sh`의 DB 설정 검사·품질 판정은 스냅샷이 아닌 작업 트리의 `scripts/quality_gate.py`를 실행한다(141·200행). v4의 “모든 검사 tree/스냅샷 기준”과 달라 로컬 인덱스 검증 결과가 달라질 수 있다.
- P1 과도 제약: 변경 파일에 JaCoCo 실행 라인은 있으나 PIT 변이가 0개인 데이터 전용 엔티티·DTO는 테스트로 모든 라인을 실행해도 `quality_gate.py`가 무조건 실패시킨다. 첫 프로필 기능에서 발생 가능하다.
- 검증: `test_guard.py`, `test_hooks.py`, `quality_gate.py --self-test`, `test_verify.sh` 통과. 이 셸의 `gitleaks` 부재로 `verify full --worktree`는 시크릿 검사 전에 중단(기존 T-031 참고의 80초 통과는 별도 실행).
- 바꾼 파일: 이 카드만. 남은 일: P0 방어, P1 범위 판정 및 gitleaks가 있는 환경에서 full 재확인.
