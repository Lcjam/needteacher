---
id: T-011
owner: codex
status: done    # todo | doing | blocked | done
scope:
  - docs/tasks/T-011-gradle-review.md
---

## 목표

사용자 결정으로 Maven → Gradle 9.7(Kotlin DSL)로 바꿨다. Codex가 Maven을 고른 이유(PIT·Failsafe 실행 경로가 명확)가 Gradle에서도 지켜지는지, 전환으로 생긴 우회 경로가 없는지 리뷰한다. T-009 지적 반영도 함께 확인한다.

## 완료 조건

- [ ] `backend/build.gradle.kts`: 기준치를 명령행·프로퍼티·환경으로 낮추거나 `test`/`integrationTest`/JaCoCo/PIT/Spotless를 끌 수 있는 경로
- [ ] 전환 중 찾아 막은 것이 실제로 막혔는지: ArchUnit 엔진이 `integrationTest`에 섞여 IT 0건 검사를 통과하던 문제, PIT `targetTests` 기본값 때문에 fast PIT가 테스트 0건이던 문제
- [ ] `scripts/verify.sh`의 Gradle 경로: 빌드 캐시 끔, 리포트 경로, 0건 검사
- [ ] `scripts/guard.py` Gradle 추가분: 초기화 스크립트(`-I`, `~/.gradle/init.d`), JVM 환경 변수
- [ ] T-009 지적 9개가 막혔는지 항목별 판정
- [ ] `bash scripts/verify.sh fast --worktree` 실행 결과 (gitleaks가 없으면 그 사실만 적는다)

## 참고

- `docs/proposals/test-harness.md` (결정 사항 표의 빌드 행, 2절)
- `docs/tasks/T-009-harness-impl-review.md`
- `scripts/changed_quality.py` (T-009 P0 신규·변경 코드 기준 대응)

## 결과

- P0 `changed_quality.py:24-44`: 변경 클래스가 JaCoCo/PIT XML에 전혀 없어도 성공(빈 XML·변경 경로로 재현). 같은 Java 파일의 별도 최상위 클래스도 경로 매칭에서 빠져 새 코드 기준을 우회할 수 있다.
- P0 기준선 래칫은 여전히 없음. `build.gradle.kts`와 `changed_quality.py`는 고정 90/85/80만 검사해 첫 기능 기준선이 더 높아도 하향을 허용한다.
- P0 `guard.py`: `.harness-un`+`lock` 문자열 조립은 허용(잠금 없는 임시 루트에서 재현). T-009 잠금 해제 우회가 부분적으로 남았다.
- P1 `guard.py`: `~/.gradle/init.gradle.kts` 쓰기, `-Ievil.gradle.kts`, `--exclude-task=test`가 허용된다. 임시 Gradle에서 결합형 `-I`가 실행되고 제외 옵션이 `test`를 빼는 것 확인; `init.d`·공백형 `-I`는 차단된다.
- P1 `ORG_GRADLE_PROJECT_pitTargetClasses=... bash scripts/verify.sh full`은 가드가 허용한다. Gradle 속성으로 전체 PIT 대상을 좁힐 수 있으며, 위 0매칭 허점과 결합된다.
- P1 T-009의 주석 래칫은 여러 줄 `/* ... @Test ... */`를 1건으로 세고, `worker-stop.sh`는 재실패 시 여전히 exit 0이다.
- P1 `--no-build-cache`만으로 재실행은 보장되지 않는다: 임시 복사본의 두 번째 `test`가 `UP-TO-DATE`. 검증 tree에 추적된 빌드 출력이 들어갈 경우를 막으려면 `--rerun-tasks` 등이 필요하다.
- Gradle `check` 그래프에 test·integrationTest·JaCoCo·Spotless, `-Pfull check`에 PIT 포함 확인. `*IT` 삭제 시 실패, 변경 PingService PIT는 1/1 killed로 단위 테스트를 찾았다.
- T-009 9건 판정: 해결=git `-C`·skipped 제외·내부 클래스 PIT·worktree 재확인·잘못된 `VERIFY_BASE`; 부분=신규 코드 기준·unlock·주석; 미해결=worker 종료.
- `fast --worktree`는 gitleaks 미설치로 빌드 전 중단. `test_verify.sh`·`test_guard.py`·`changed_quality.py --self-test`는 통과. 구현 파일 수정 없음.
