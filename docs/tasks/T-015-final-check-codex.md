---
id: T-015
owner: codex
status: done    # todo | doing | blocked | done
scope:
  - docs/tasks/T-015-final-check-codex.md
---

## 목표

하네스 마무리 확인. T-012에서 남긴 지적이 막혔는지 판정하고, 하네스 전체에 대해 아직 막아야 할 P0가 남았는지 최종 의견을 낸다.

## 완료 조건

- [ ] T-012 미해결 5건 판정 (해결/부분/미해결 + 근거)
  - 커버리지는 있는데 변이 0개인 파일 → `quality_gate.py` 실패
  - default/static 메서드가 있는 인터페이스 → "실행 코드" 판정
  - 미추적 카드도 worker 재실패 시 `blocked` 전환 (`--untracked-files=all`)
  - `disableAllHooks` 간접 우회 → 로컬 `verify`가 훅 꺼짐을 발견하면 실패 (0번 검사)
  - `JDK_JAVA_OPTIONS` 환경 정리·판정기 차단
- [ ] T-016에서 옮겨 온 항목 (agy 사용 한도 소진). 코드 검토로 판정한다. 재현은 Claude가 이미 했다:
  - YAML 계층·인라인 맵·`@AutoConfigureTestDatabase` → `quality_gate.py`의 `config_problems` (자체 검사에 사례 있음)
  - 주석·미사용 import로만 `TestcontainersConfiguration`을 적은 `*IT` → `TestIntegrityTest.integrationTestsUseRealPostgres` (임시 복사본에서 실패 확인)
- [ ] 하네스 전체에 남은 P0가 있으면 적고, 없으면 "P0 없음"이라고 명시
- [ ] 자체 검사 4종 결과. Docker가 켜져 있으면 `bash scripts/verify.sh full --worktree` 결과도

## 참고

- `docs/tasks/T-012-t011-fix-check.md`
- `docs/proposals/test-harness.md` (구현 상태 절)

## 결과

- P0 남음(코드 검토): 운영 `application.yml`의 `spring: {flyway: {enabled: false}, jpa: {hibernate: {ddl-auto: create}}}`를 `flatten_config`가 `spring` 값 하나로 처리해 DB 설정 스캔이 놓친다. 수정 필요.
- P1 부분: `disableAllHooks` 직접 설정은 로컬 verify 0번 검사에서 실패하지만 `grep`이 같은 줄의 공백만 허용해 줄바꿈·탭이 있는 유효한 JSON은 놓친다. 간접 우회 방어는 불완전하다.
- 해결: 커버리지 LINE 카운터가 있는 변경 파일에 PIT 변이 0개면 `quality_gate.py`가 실패한다(자체 검사 사례 포함).
- 해결: `HAS_CODE`가 default/static 인터페이스 메서드 본문을 실행 코드로 판정한다(자체 검사 사례 포함).
- 해결: `worker-stop.sh`의 `--untracked-files=all`로 미추적 카드도 파일 단위로 찾아 재실패 시 `blocked`로 바꾼다.
- 해결: `JDK_JAVA_OPTIONS`는 `guard.py`가 명령에서 차단하고 `verify.sh`가 Gradle 실행 전에 환경에서 지운다.
- T-016 부분: 테스트 리소스 YAML 계층·인라인 맵의 DB 키와 Java의 `@AutoConfigureTestDatabase`는 `config_problems`가 잡는다. 운영 인라인 맵은 위 P0로 남는다.
- T-016 해결: `TestIntegrityTest`는 실제 `@Import` 애노테이션 값을 검사하므로 주석·미사용 import만 있는 `*IT`는 통과하지 못한다(코드 검토).
- 자체 검사 4종(`test_guard.py`, `test_verify.sh`, `quality_gate.py --self-test`, `gh-app-token.sh --self-test`) 모두 통과.
- Docker 실행 중이지만 `verify full --worktree`는 gitleaks 미설치로 빌드 전 실패. 별도 Gradle full은 단위·통합·JaCoCo·PIT 2/2 통과 후 작업 트리 CRLF의 Spotless 위반으로 실패. 수정 파일은 이 카드뿐.
