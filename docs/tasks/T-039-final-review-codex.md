---
id: T-039
owner: codex
status: done    # todo | doing | blocked | done
scope:
  - docs/tasks/T-039-final-review-codex.md
---

## 목표

첫 커밋 전 최종 판정. agy의 T-036(기존 테스트 파일 불변)·T-038(인수 테스트 원본 바이트 비교, 사용자 결정 "원본 비교만" — 원본 없는 새 테스트 거부 규칙은 일부러 뺌)을 리뷰한다. 파일은 고치지 않는다.

## 완료 조건

- [x] `verify.sh` 2-2·3-2와 `test_verify.sh` 새 사례에서 우회 경로나 거짓 양성. 재현했으면 방법.
- [x] 흐름상 막히는 곳: 원본이 Spotless 형식에 안 맞으면 복사본이 `check`에서 실패하고, 고치면 원본 불일치가 된다. 원본을 옮기기 전에 형식을 맞추는 단계가 필요한지(예: ①에서 Codex가 `spotlessApply` 후 red 확인). 그 밖에 첫 기능 개발에서 걸릴 곳.
- [x] T-031·T-033 P0(인수 테스트 변조)이 이 범위(원본 비교만 + 기존 파일 불변)로 닫혔는지. 남는 것은 명세 '남은 한계'에 적을 문구로.
- [x] 결론 한 줄: "첫 커밋 승인" 또는 "P0: …".

## 참고

- `docs/tasks/T-033-signoff-debate-codex.md`, `docs/tasks/T-036-test-files-immutable.md`, `docs/tasks/T-037-acceptance-original-design.md`, `docs/tasks/T-038-acceptance-originals.md`
- Claude 실측: 자체 검사 5종 통과(`test_verify.sh` 45건), `verify full --worktree` 63초 통과.

## 결과

- 2-2는 기준 테스트 파일의 모든 수정(M)을 등록 없이는 막고, 삭제·이름 변경은 2번이 막는다. 3-2는 승인된 원본의 모드·실행 파일 존재·블롭 일치를 일반 변경에서 확인한다. 의도된 `test-removals.txt` 등록 예외 외 P0 우회는 찾지 못했다.
- P2 오탐 재현: Git 기본 `ls-tree`가 한글 Java 경로를 따옴표·8진수로 인용해 3-2가 유효한 `.java` 원본을 거부한다. 임시 저장소에서 `학생Test.java` 출력이 인용됨을 확인; 구현 시 `-z` 경로 파싱 권장.
- 흐름 보완: Codex가 red 테스트를 옮기기 **전에** `spotlessJavaCheck`로 형식을 확인하고, 필요하면 새 파일만 형식 수정 후 red를 다시 확인해야 한다. `spotlessJavaCheck --dry-run`은 컴파일 태스크 없이 실행됨을 확인. 복사 후 원본과 다른 자동 형식 수정을 하지 않는다.
- 첫 기능은 새 테스트 파일명을 기존 파일과 구분하고, red 테스트가 새 헬퍼 Java 파일을 요구하면 함께 원본으로 옮겨야 원본 전용 PR이 green이다.
- 명세 '남은 한계' 권고: 승인된 원본만 바이트 비교한다. 원본 없는 새 테스트·기능의 요구 누락은 자동 판정하지 않으며 카드/사람 리뷰로 확인한다. `quality/test-removals.txt`의 이번 변경 등록은 코드오너 승인 예외다.
- 검증: `test_verify.sh`, `quality_gate.py --self-test`, `test_guard.py`, `test_hooks.py` 모두 통과. 이 셸에는 gitleaks가 없어 full은 재실행하지 않았고, Claude의 63초 통과 기록을 참고했다. 바꾼 파일은 이 카드만.
- 결론: 첫 커밋 승인(하네스 코드 기준). GitHub CI·ruleset의 실제 보호는 저장소 설정 후 확인해야 한다.
