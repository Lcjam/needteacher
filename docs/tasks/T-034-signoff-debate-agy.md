---
id: T-034
owner: agy
status: done    # todo | doing | blocked | done
scope:
  - docs/tasks/T-034-signoff-debate-agy.md
---

## 목표

최종 승인 토론 2라운드. 너는 T-032에서 "승인", Codex는 T-031에서 "P0". Codex 결과를 읽고 답한다. 파일은 고치지 않는다.

## 완료 조건

- [x] Codex P0: 구현 담당이 인수 테스트의 본문만 기존 `ping` 검증 등으로 바꾸고 `@Test` 개수를 유지하면, 기능 코드가 없어 신규·변경 파일 기준도 비어 CI가 통과한다. 네가 든 다계층 방어(L1~L4) 중 어느 것이 이걸 막는지, 못 막으면 인정. 가능하면 임시 사본에서 재현.
- [x] Codex P1 두 건(작업 트리의 `quality_gate.py` 실행, 변이 0개 데이터 클래스 무조건 실패)에 동의/반박. 데이터 전용 엔티티·record가 실제로 변이 0개인지 PIT 동작 근거로.
- [x] 결론 한 줄: 네 T-032 결론을 유지하는지 바꾸는지.

## 참고

- `docs/tasks/T-031-final-signoff-codex.md`, `docs/tasks/T-032-final-signoff-agy.md`

## 결과

- 바꾼 파일: `docs/tasks/T-034-signoff-debate-agy.md`
- Codex P0 인정: 본문 변조 후 `@Test` 수만 유지하고 운영 코드 미작성 시, `changed`가 비어 신규 기준 검사가 생략되고 기존 ping 커버리지/래칫만으로 CI를 완전 통과함을 확인. L1~L4 모두 이를 기계적으로 막지 못함.
- Codex P1-1(명세 불일치) 동의: `verify.sh` 141·200행이 `$snap/scripts` 대신 작업 트리 `$repo/scripts/quality_gate.py`를 실행하여 v4 스냅샷 격리 명세와 불일치함.
- Codex P1-2(과도 제약) 동의: PIT은 필드 대입·생성자 초기화·enum 상수에 변이를 생성하지 않으므로, 데이터 전용 엔티티나 DTO/enum은 100% 테스트되어도 `변이 0개`로 `quality_gate.py`가 무조건 실패함.
- 결론: T-032 "승인"을 철회하고 Codex의 "P0"에 동의 (P0 및 P1 2건 조치 필요).
