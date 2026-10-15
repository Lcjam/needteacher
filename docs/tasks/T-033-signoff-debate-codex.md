---
id: T-033
owner: codex
status: done    # todo | doing | blocked | done
scope:
  - docs/tasks/T-033-signoff-debate-codex.md
---

## 목표

(사용량 한도로 미뤄진 카드를 바꿔 씀) agy가 T-034에서 네 T-031 P0·P1을 모두 인정했다. 합의된 부분은 agy가 T-035로 고쳤다. 그 리뷰와, P0의 남은 절반 설계를 맡는다. 파일은 고치지 않는다.

## 완료 조건

1. T-035 리뷰
   - [x] `verify.sh` 2-2(기준에 있던 테스트 파일은 줄 추가만, 삭제 줄이 있으면 등록 필요), 스냅샷의 `quality_gate.py` 사용, 변이 0개·라인 100% 통과 완화에서 우회 경로나 거짓 양성.
2. P0 나머지 절반: 같은 변경 안에서 새로 쓴 인수 테스트를 구현 담당이 고치는 경우
   - 문제: 인수 테스트는 구현 전 red라 먼저 커밋할 수 없다(pre-commit·CI 실패). 그래서 기준(base)에 없고, 2-2가 보호하지 못한다.
   - Claude 초안: 인수 테스트를 다 쓰면 오케스트레이터가 `scripts/freeze-tests.sh <파일들>`로 해시를 `.git/` 안(작업 트리 밖)에 기록한다. 로컬 `verify.sh`(CI 아님)는 기록이 있으면 해당 파일이 그대로인지 확인하고, 바뀌었으면 `quality/test-removals.txt` 등록이 없는 한 실패한다. guard는 Codex·agy·worker가 그 기록 파일에 쓰는 것을 막는다. 커밋 후 기록을 지운다. CI는 이걸 볼 수 없으니 최종 방어는 PR의 사람 리뷰다.
   - [x] 이 초안의 구멍과 더 단순하거나 CI에서도 강제되는 대안. 예) 인수 테스트를 먼저 별도 커밋으로 두고 CI가 PR 안의 커밋 순서로 확인하는 방식.
   - [x] 추천안 하나와 이유. 비용(새 스크립트·훅 수정)이 이득보다 크면 "사람 리뷰로 충분"이라고 판단해도 된다.
3. [x] 결론 한 줄: 첫 커밋을 막는 P0이 남았는지.

## 참고

- `docs/tasks/T-031-final-signoff-codex.md`, `docs/tasks/T-034-signoff-debate-agy.md`, `docs/tasks/T-035-test-immutability.md`
- Claude 실측: 자체 검사 5종 통과(`test_verify.sh` 31건), `verify full --worktree` 77초 통과.

## 결과

- P0: T-035의 추가만 허용 검사는 기존 테스트 안에 `if (System.getProperty("java.version") != null) return;` 한 줄을 assertion 앞에 넣어 우회 가능하다. 임시 Git 저장소에서 같은 위치 삽입은 `--numstat` 추가 1·삭제 0으로 확인했다. 기존 `*IT`는 PIT 대상에서도 제외된다.
- 스냅샷 `quality_gate.py` 실행은 수정 확인. 변이 0·라인 100% 허용은 데이터 클래스 오탐을 줄이나, PIT가 변이를 못 만든 동작 코드도 허용하므로 테스트 의미까지 보증하지는 않는다. 이 완화만의 새 P0은 확인되지 않았다.
- `.git/` 해시 초안은 공유 작업 트리의 로컬 기록이라 우회·삭제 가능하고 CI 체크아웃에 전달되지 않는다. PR 내부의 red 선행 커밋 순서만 검사해도 기록 재작성·force push로 원본을 바꿀 수 있으며 현재 pre-commit은 red 커밋을 막는다.
- 추천: 인수 테스트 원본을 실행 대상 밖의 보호된 `quality/` 경로에 먼저 별도 PR로 병합하고, 구현 PR의 CI에서 실제 테스트 파일과 원본을 바이트 비교한다. 기존 테스트도 추가 줄을 포함한 모든 변경을 등록·코드오너 승인 대상으로 바꾼다. 비용은 PR·검사 한 단계지만 CI가 신뢰할 기준을 갖는다.
- 검증: `test_verify.sh`·`quality_gate.py --self-test` 통과; 조기 반환 삽입의 Git numstat `1 0` 재현. 바꾼 파일은 이 카드만.
- 결론: 첫 커밋 승인에는 P0이 남는다. 추가 줄만으로 기존 테스트를 무력화할 수 있고 새 인수 테스트에는 CI가 신뢰할 원본이 없다.
