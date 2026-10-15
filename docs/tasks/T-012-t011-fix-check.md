---
id: T-012
owner: codex
status: done    # todo | doing | blocked | done
scope:
  - docs/tasks/T-012-t011-fix-check.md
---

## 목표

T-011 지적이 실제로 막혔는지 재현으로 확인한다. 새 우회 경로가 보이면 함께 적는다.

## 완료 조건

- [ ] T-011 항목별 판정 (해결/부분/미해결 + 재현 근거)
  - `scripts/quality_gate.py`(구 changed_quality.py): 소스 파일 단위 매칭, 리포트에 없는 실행 코드 파일 실패, 같은 파일의 별도 최상위 클래스 포함
  - 래칫: `quality/baseline.txt` + full에서 프로젝트 수치 비교
  - 잠금 해제 조립: 판정 강화 + 잠금 해제 상태의 보호 파일 수정을 사용자에게 알림(`systemMessage`)
  - Gradle: 붙여 쓴 `-I`, `--exclude-task=`, `~/.gradle/init.gradle(.kts)`, `ORG_GRADLE_PROJECT_*` (가드 차단 + verify.sh 환경 정리 + full은 대상 지정 무시)
  - 여러 줄 주석 `@Test`, 추적된 `build/`·`.gradle/` 거부 + `--rerun-tasks`, worker 재실패 시 카드 blocked
- [ ] 추가로 찾은 것: `.claude/settings.local.json`의 `disableAllHooks`로 Claude 훅 전체를 끄는 경로 → `.claude/` 전체 보호 + 홈 설정 파일 쓰기 차단. 이 대응이 충분한지
- [ ] 자체 검사 4종 실행 결과 (`test_guard.py`, `test_verify.sh`, `quality_gate.py --self-test`, `gh-app-token.sh --self-test`)

## 참고

- `docs/tasks/T-011-gradle-review.md`
- `scripts/`, `backend/build.gradle.kts`, `quality/baseline.txt`, `.github/CODEOWNERS`

## 결과

- P1 미해결: JaCoCo 항목은 있으나 해당 파일 PIT 변이가 0개면 변이율 검사를 통과한다(다른 파일 변이만 있는 리포트에서 확인).
- P1 미해결: 실행 코드가 있는 Java interface가 JaCoCo에서 빠져도 HAS_CODE 정규식이 class/enum/record만 찾아 통과한다.
- P1 미해결: worker-stop.sh는 미추적 카드가 ?? docs/tasks/로 묶이면 재실패 후 exit 0을 내면서 카드 status를 blocked로 바꾸지 못한다. 현재 작업 트리도 이 상태다.
- P1 부분: .claude/ CODEOWNERS와 프로젝트·홈 settings.local.json 직접 쓰기 차단은 확인했다. 간접 스크립트 실행은 가드가 허용하므로 disableAllHooks의 로컬 우회는 남는다.
- P2 미해결: verify.sh가 JDK_JAVA_OPTIONS를 지우지 않아 기존 환경값이 Gradle 실행에 전달될 수 있다.
- 해결: quality_gate.py의 소스 파일 기준 집계·같은 파일의 별도 최상위 클래스 포함·리포트에서 빠진 class 실패를 확인했다.
- 해결: full 래칫은 baseline.txt의 프로젝트 수치와 비교하며 하락 시 실패한다.
- 해결: 잠금 해제 문자열 조립, Gradle -I/--exclude-task=/홈 init/ORG_GRADLE_PROJECT 직접 입력 차단과 systemMessage를 확인했다. verify.sh는 해당 Gradle 환경값을 지우고 full은 대상 지정을 무시한다.
- 해결: 여러 줄 주석의 @Test 제외, 추적된 build/.gradle 거부, --rerun-tasks, 추적 카드의 worker blocked 전환을 확인했다.
- 자체 검사 4종(test_guard.py, test_verify.sh, quality_gate.py --self-test, gh-app-token.sh --self-test)은 앞서 모두 통과했다.
