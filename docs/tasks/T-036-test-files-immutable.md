---
id: T-036
owner: agy
status: done    # todo | doing | blocked | done
scope:
  - scripts/verify.sh
  - scripts/test_verify.sh
  - docs/proposals/test-harness.md
  - backend/AGENTS.md
  - AGENTS.md
  - docs/tasks/T-036-test-files-immutable.md
---

## 목표

T-033 P0: T-035의 "추가만 허용"은 기존 테스트에 `if (System.getProperty("java.version") != null) return;` 한 줄을 넣어 우회된다(삭제 0줄). 기준(base)에 있던 테스트 파일은 추가를 포함한 모든 변경에 등록이 필요하게 바꾼다. 새 테스트는 새 파일로 쓴다.

## 완료 조건

- [x] `verify.sh` 2-2: `--diff-filter=M`인 `$TESTS` 파일은 추가·삭제 줄 수와 관계없이 `quality/test-removals.txt`에 같은 변경에서 등록돼야 통과. 메시지 "기존 테스트 파일 변경 (새 테스트는 새 파일로, 바꾸려면 등록)".
- [x] `test_verify.sh`: "끝에 줄 추가만" 사례는 이제 실패(1)로, 조기 `return` 한 줄 삽입 사례 → 실패, 추가 + 등록 → 통과. 나머지 사례가 그대로 맞는지.
- [x] 문서: `AGENTS.md` 금지 목록·등록 안내, `backend/AGENTS.md` 테스트 절, `docs/proposals/test-harness.md` 3절 표에 "기존 테스트 파일은 불변, 새 테스트는 새 파일" 반영 (T-035의 "추가만 허용" 문구 교체).
- [x] `bash scripts/test_verify.sh`, `python scripts/quality_gate.py --self-test` 통과, 건수 기록.

## 참고

- `docs/tasks/T-033-signoff-debate-codex.md`, `docs/tasks/T-035-test-immutability.md`

## 결과

- 바꾼 파일: `scripts/verify.sh`, `scripts/test_verify.sh`, `docs/proposals/test-harness.md`, `backend/AGENTS.md`, `AGENTS.md`, `docs/tasks/T-036-test-files-immutable.md`
- `verify.sh`: 2-2단계에서 `--diff-filter=M`인 `$TESTS` 파일의 모든 변경(줄 추가·수정·삭제)을 차단하고 `quality/test-removals.txt` 등록 시만 통과하도록 변경.
- `test_verify.sh`: 끝에 줄 추가만(실패), 조기 return 삽입(실패), 추가+등록(통과) 등 33건 전체 통과.
- 문서: `AGENTS.md`, `backend/AGENTS.md`, `docs/proposals/test-harness.md`의 "추가만 허용" 문구를 "기존 테스트 파일은 불변, 새 테스트는 새 파일로 작성"으로 교체.
- 검증: `test_verify.sh` 33건 통과, `quality_gate.py --self-test` 통과, `test_guard.py` 및 `test_hooks.py` 통과.
- 남은 일: Claude의 diff 및 실측 검토.
