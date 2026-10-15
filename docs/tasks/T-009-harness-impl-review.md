---
id: T-009
owner: codex
status: done    # todo | doing | blocked | done
scope:
  - docs/tasks/T-009-harness-impl-review.md
---

## 목표

하네스 구현물의 우회 경로를 찾는다. 판정기(T-007)에 이어 나머지 전부.

## 완료 조건

- [ ] `scripts/verify.sh`: tree 고정, `--worktree`, 테스트 래칫, 0건 검사, fast PIT 대상 계산에서 우회·오판 경로
- [ ] `.github/workflows/ci.yml`: 필수 검사가 skip/neutral로 통과할 수 있는 경로, `VERIFY_BASE` 계산 오류 가능성
- [ ] `scripts/worker-stop.sh` + `.claude/agents/worker.md`: worker가 종료 게이트를 피할 수 있는지
- [ ] 백엔드 `pom.xml` 초안: 기준치를 낮추거나 PIT·JaCoCo·Failsafe를 무력화할 수 있는 경로 (아래 경로, 저장소 밖 임시 폴더)
- [ ] 판정기 수정본: T-007 지적 3개가 막혔는지
- [ ] `bash scripts/test_verify.sh`, 수정본 `test_guard.py` 실행 결과

## 참고

- `docs/proposals/test-harness.md` v4
- 백엔드 초안: `C:\Users\lcj\AppData\Local\Temp\claude\C--Users-lcj-Desktop-Project-needteacher\fad357aa-fa33-4a79-82c2-fb63284ecd33\scratchpad\initializr\scaffold\` (`pom.xml`, `src/`)
- 판정기 수정본: 같은 임시 폴더의 `scratchpad\guard-next\guard.py`, `test_guard.py`
- 판정기 수정본에는 agy가 T-008에서 실측한 입력 형식(`toolCall` 래퍼)과 T-007 지적이 반영됨

## 결과

- P0 `pom.xml` 초안: JaCoCo는 BUNDLE 90/85, PIT는 전체 80만 검사한다. v4의 첫 기능 기준선·하향 금지·신규/변경 코드별 기준이 없어 기존 고득점으로 새 코드의 낮은 점수를 가릴 수 있다.
- P0 수정본 `guard.py`: `python -c "open('.harness'+'-unlock','w').close()"` 허용(잠금 없는 임시 루트에서 재현). 잠금 해제 파일 생성 우회가 여전히 가능하다.
- P1 수정본 `guard.py`: `git -C backend clean -fdx ../scripts` 허용; Git dry-run에서 `../scripts/` 삭제 대상 확인. 기존 `cd backend && rm ...`와 직접 `git clean`은 차단됨.
- P1 `verify.sh:46`: `// @Test`도 테스트 수로 센다. 실제 `@Test` 삭제 후 주석을 남기면 메서드 감소 래칫을 통과한다.
- P1 `verify.sh:81-83`: XML의 `tests`만 합산하고 `skipped`를 빼지 않아 실제 실행된 단위/통합 테스트가 0건이어도 0건 검사를 통과할 수 있다.
- P1 `verify.sh:71-74`: fast PIT의 정확한 클래스명 `targetClasses`는 내부 클래스를 포함하지 않는다(PIT 문서). 변경 Java 파일의 내부 클래스 뮤턴트가 빠진다.
- P1 `worker-stop.sh:5-8`: 첫 실패 후 `stop_hook_active=true`면 재실패에도 exit 0. 카드 `blocked` 전환도 강제하지 않아 worker 종료 게이트를 피할 수 있다.
- P1 `verify.sh:91`: `--worktree`는 종료 시 작업 트리 재확인을 건너뛰어 스냅샷 이후 동시 수정이 있어도 성공한다.
- CI: 필수 job에 `if`/`continue-on-error`는 없음. 다만 `VERIFY_BASE`가 유효하지 않으면 `verify.sh:27`이 빈 트리로 대체해 삭제 래칫을 놓친다; 서버 ruleset의 검사 출처 고정은 별도 확인 필요.
- 검증: `bash scripts/test_verify.sh`와 임시 폴더 수정본 `test_guard.py` 모두 통과. 구현 파일은 수정하지 않음.
