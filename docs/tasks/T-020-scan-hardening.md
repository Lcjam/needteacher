---
id: T-020
owner: agy
status: done
scope:
  - scripts/quality_gate.py
  - scripts/verify.sh
  - scripts/test_verify.sh
  - docs/tasks/T-020-scan-hardening.md
---

## 목표

T-018에서 나온 글자 스캔과 0번 검사의 구멍을 막는다. 런타임 검사(T-019/T-021)와 별개인 1차 방어다.

## 완료 조건

- [x] `quality_gate.py` `TEST_JAVA_FORBIDDEN`: 테스트 Java(보호 경로 `architecture/` 제외) 어디든 `hbm2ddl`, `schema-generation`, `allow-bean-definition-overriding`이 나오면 실패. 지금은 `"spring.jpa.hibernate.ddl-auto`만 봐서 `@TestPropertySource(properties = "spring.jpa.properties.hibernate.hbm2ddl.auto=create")`가 통과한다.
- [x] `TEST_CONFIG_WORDS`(테스트 리소스)에 `schema-generation`, `allow-bean-definition-overriding` 추가.
- [x] 운영 설정(`src/main/resources`): 키에 `schema-generation`이 있고 `.action`으로 끝나면 값은 `none`만 허용, `allow-bean-definition-overriding=true` 실패.
- [x] 위 각각을 `self_test()`에 실패 사례로 추가. `python scripts/quality_gate.py --self-test` 통과.
- [x] `verify.sh` 0번 검사: `guard.py` 문자열이 있는지만 보지 말고, `hooks.PreToolUse` 항목 중 `matcher`가 `Bash`·`PowerShell`·`Edit`·`Write` 넷 모두에 정규식 전체 일치하고(`matcher`가 없거나 `""`·`*`면 전부 일치로 본다) 그 항목의 `hooks[].command`에 `scripts/guard.py`가 있는 항목이 있어야 통과.
- [x] `test_verify.sh`에 사례 추가: `matcher`가 `Read`인 항목에만 guard.py가 있으면 실패. 기존 fixture(`matcher` 없음)는 통과. `bash scripts/test_verify.sh` 전부 통과, 결과에 건수.
- [x] `python scripts/test_guard.py`도 통과.

## 참고

- `docs/tasks/T-018-t015-fix-review.md` (P0, P1 두 번째)
- 거짓 양성(주석의 `// @AutoConfigureTestDatabase`)은 엄격한 쪽 오류라 그대로 둔다. 주석 제거는 `"http://..."` 같은 문자열 뒤를 숨기는 새 구멍이 된다.
- Gradle은 돌리지 않는다 (Codex가 T-019에서 사용 중).

## 결과

- 바꾼 파일: `scripts/quality_gate.py`, `scripts/verify.sh`, `scripts/test_verify.sh`, `docs/tasks/T-020-scan-hardening.md`
- quality_gate: TEST_JAVA_FORBIDDEN/TEST_CONFIG_WORDS에 hbm2ddl, schema-generation, allow-bean-definition-overriding 추가, 운영 설정 schema-generation.action(none 외 금지) 및 allow-bean-definition-overriding=true 차단
- verify.sh: 0번 검사에서 PreToolUse 항목의 matcher(Bash·PowerShell·Edit·Write 정규식 전체 일치, 빈값/* 허용) 및 guard.py 명령 등록 확인
- test_verify.sh: matcher=Read 항목만 있는 경우 실패 사례 추가 (총 26건 검사)
- 검증: `python scripts/quality_gate.py --self-test` 통과, `bash scripts/test_verify.sh` 26건 전원 통과, `python scripts/test_guard.py` 통과
- 남은 일: 오케스트레이터의 diff 검토 및 커밋
