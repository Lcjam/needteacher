---
id: T-035
owner: agy
status: done    # todo | doing | blocked | done
scope:
  - scripts/verify.sh
  - scripts/test_verify.sh
  - scripts/quality_gate.py
  - docs/proposals/test-harness.md
  - backend/AGENTS.md
  - AGENTS.md
  - docs/tasks/T-035-test-immutability.md
---

## 목표

T-031·T-034에서 합의한 P0·P1 세 건을 고친다. 같은 변경 안에서 새로 쓴 인수 테스트를 고치는 경우(P0의 나머지 절반)는 Codex 복귀 후 따로 정한다.

## 완료 조건

1. P0 (a) 기존 테스트는 추가만
   - [x] `verify.sh` 2번 근처: 기준(base)에 있던 `$TESTS` 파일이 바뀌었고(`--diff-filter=M`) `git diff --numstat`의 삭제 줄 수가 0보다 크면 실패. 단, 같은 변경에서 `quality/test-removals.txt`에 등록(`is_allowed`)됐으면 통과. 메시지는 "기존 테스트 줄 변경·삭제 (추가만 허용)".
   - [x] `test_verify.sh` 사례: 기존 줄 한 줄 바꾸기 → 실패, 끝에 줄 추가만 → 통과, 바꾸고 등록 → 통과. 기존 사례가 모두 그대로 맞는지 확인.
2. P1 스냅샷 스크립트
   - [x] `verify.sh` 141·200행 근처의 `"$repo/scripts/quality_gate.py"`를 스냅샷의 `"$snap/scripts/quality_gate.py"`로. 검증 대상 트리의 스크립트로 판정해야 명세(모든 검사는 스냅샷 기준)와 맞는다.
3. P1 변이 0개 데이터 클래스
   - [x] `quality_gate.py` `problems`: 변이 0개여도 그 파일의 LINE이 100%면 통과. 100% 미만이면 지금처럼 실패. (PIT 대상 제외는 보호 파일 `build.gradle.kts`로만 가능하므로 이 완화가 새 구멍이 되지 않는다는 근거를 코드 주석 한 줄로)
   - [x] `self_test()`: 변이 0·LINE 100% → 통과 사례 추가, 기존 `a/Quiet` 실패 사례는 그대로 실패하는지.
4. 문서
   - [x] `AGENTS.md` 금지 목록과 `quality/test-removals.txt` 설명, `backend/AGENTS.md` 테스트 절, `docs/proposals/test-harness.md` 3절 표·구현 상태에 반영.
- [x] `python scripts/quality_gate.py --self-test`, `bash scripts/test_verify.sh`, `python scripts/test_guard.py`, `python scripts/test_hooks.py` 모두 통과. 건수 기록.

## 참고

- `docs/tasks/T-031-final-signoff-codex.md`, `docs/tasks/T-034-signoff-debate-agy.md`
- Gradle은 돌리지 않아도 된다 (Claude가 verify full로 확인).

## 결과

- 바꾼 파일: `scripts/verify.sh`, `scripts/test_verify.sh`, `scripts/quality_gate.py`, `docs/proposals/test-harness.md`, `backend/AGENTS.md`, `AGENTS.md`, `docs/tasks/T-035-test-immutability.md`
- P0: `verify.sh` 2-2번에 `git diff --numstat` 삭제 줄 수 검사 추가 (기존 테스트 줄 변경·삭제 차단, `test-removals.txt` 등록 시 허용).
- P1-1: `verify.sh` 141·205행의 `quality_gate.py` 실행 경로를 작업 트리 대신 스냅샷(`$snap`)으로 변경.
- P1-2: `quality_gate.py`에서 변이 0개여도 라인 커버리지 100%면 통과하도록 완화.
- 검증 결과: `test_verify.sh` 31건 통과(줄 변경 실패, 끝 추가 통과, 변경+등록 통과 3사례 추가), `quality_gate.py --self-test` 통과, `test_guard.py` 28건 통과, `test_hooks.py` 10건 통과.
- 남은 일: Claude의 diff 및 실측 검토, Codex 복귀 후 교차 리뷰.
