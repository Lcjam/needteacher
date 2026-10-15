---
id: T-040
owner: agy
status: done    # todo | doing | blocked | done
scope:
  - scripts/verify.sh
  - scripts/test_verify.sh
  - AGENTS.md
  - docs/proposals/test-harness.md
  - docs/tasks/T-040-quotepath-and-flow.md
---

## 목표

T-039(Codex 최종 판정 "첫 커밋 승인")의 남은 지적을 반영한다.

## 완료 조건

- [x] P2: Git이 비 ASCII 경로(예: `학생Test.java`)를 따옴표·8진수로 인용해 3-2가 정상 원본을 거부하고, 2·2-2·3번의 경로 비교도 어긋난다. 한 곳에서 고친다: `verify.sh` 앞부분에서 이 스크립트의 모든 git 호출에 `core.quotePath=false`가 적용되게 (예: `export GIT_CONFIG_COUNT=1 GIT_CONFIG_KEY_0=core.quotePath GIT_CONFIG_VALUE_0=false`). 공백이 든 경로도 3-2의 `read`가 맞게 읽는지 확인.
- [x] `test_verify.sh` 사례: 한글 이름 원본 추가 → 통과, 다음 변경에서 그대로 복사 → 통과, 한글 이름 기존 테스트 파일 변경(등록 없음) → 실패.
- [x] `AGENTS.md` 순서 ①에 "옮기기 전에 `spotlessJavaCheck`로 형식을 맞추고 red를 다시 확인, red 테스트가 쓰는 새 헬퍼 테스트 파일도 함께 원본으로, 새 테스트 파일 이름은 기존과 겹치지 않게" 반영 (짧게).
- [x] `docs/proposals/test-harness.md` 남은 한계에: 자동 판정은 승인된 원본만 바이트 비교한다. 원본 없는 새 테스트·기능의 요구 누락은 카드·사람 리뷰로 확인한다. `quality/test-removals.txt` 등록은 코드오너 승인 예외다.
- [x] `bash scripts/test_verify.sh`, `python scripts/quality_gate.py --self-test`, `python scripts/test_guard.py`, `python scripts/test_hooks.py` 통과, 건수 기록.

## 참고

- `docs/tasks/T-039-final-review-codex.md`

## 결과

- 바꾼 파일: `scripts/verify.sh`, `scripts/test_verify.sh`, `AGENTS.md`, `docs/proposals/test-harness.md`, `docs/tasks/T-040-quotepath-and-flow.md`
- 구현: `verify.sh` 상단에 `GIT_CONFIG_COUNT=1 GIT_CONFIG_KEY_0=core.quotePath GIT_CONFIG_VALUE_0=false` export 추가 (한글 등 비 ASCII 경로 인용 방지).
- 검증: `test_verify.sh` 50건(한글 원본 추가·복사·기존 변경 실패, 공백 경로 등 5건 추가) 통과. `quality_gate.py --self-test`, `test_guard.py`(41 asserts), `test_hooks.py`(21 asserts) 전체 통과.
- 문서: `AGENTS.md` 순서 ①에 spotlessJavaCheck·새 파일명·헬퍼 파일 반영, `test-harness.md` '남은 한계' 및 구현 표 갱신.
- 남은 일: Claude 검토 및 커밋.
