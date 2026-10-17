---
id: T-056
owner: codex
status: done    # todo | doing | blocked | done
scope:
  - docs/tasks/T-056-harness-followups-draft.md
  - C:\Users\lcj\nt-drafts\T-056\**     # 저장소 밖 초안 폴더 (보호 경로는 에이전트가 쓸 수 없음)
---

## 목표

네가 T-054에서 제안한 보호 파일 후속 두 건의 **초안**을 만든다. 보호 경로는 훅이 막으므로 저장소 밖 `C:\Users\lcj\nt-drafts\T-056\`에 쓴다. 오케스트레이터가 검토한 뒤 잠금 해제 중에 옮긴다. 저장소 안 파일은 이 카드 말고는 고치지 않는다.

## 할 일

1. **gitleaks 실행 위치 (판단 2):**
   - `scripts/verify.sh`가 스냅샷 안에서 gitleaks를 돌리게 하는 최소 변경을 `verify.sh.patch`(unified diff, 저장소 루트 기준 경로)로 쓴다.
   - `scripts/test_verify.sh`에 가짜 gitleaks의 실행 위치가 스냅샷인지 검사하는 사례를 넣는 변경도 같은 형식의 `test_verify.sh.patch`로 쓴다.
   - 두 패치를 임시 복사본에 적용해 `bash scripts/test_verify.sh`가 통과하는지, 일부러 되돌리면 새 사례가 실패하는지 확인한다. 복사본은 `git worktree add ../nt-harness origin/main` 등으로 만든다.
2. **가드 대조 사례 (판단 1):**
   - "가드만 빼면 같은 설정이 정상 기동한다"는 보장을 되살리는 새 테스트 파일을 쓴다. 기존 테스트 파일은 수정하지 않는다.
   - 위치는 `backend/src/test/java/kr/needteacher/architecture/`에 둘 예정이다. 초안은 `nt-drafts/T-056/` 아래 같은 상대 경로로 둔다.
   - 운영 앱 전체(`BackendApplication`)가 아니라 경량 설정을 쓴다. 기능 서비스가 JPA 저장소를 필수로 받아 JPA 없이는 앱이 뜨지 않기 때문이다.
   - 임시 복사본에서 `integrationTest --tests` 해당 파일이 통과하는지, 가드를 끼웠을 때 같은 설정이 거부되는지 확인한다. Docker가 필요하다.
3. 결과에 각 초안의 경로, 확인한 명령과 결과, 남은 위험을 적는다.

## 참고

- `docs/tasks/T-054-slice1-judgment-review.md` (네 리뷰), `backend/AGENTS.md` 테스트 절, `.github/CODEOWNERS`
- PR #6 브랜치 `feat/tutor-profile`에 가드 변경이 들어 있다. 1번은 main 기준, 2번은 PR #6 브랜치 기준으로 만든다. 2번 복사본은 `git worktree add ../nt-guard origin/feat/tutor-profile`로 만든다.

## 결과

- 초안: `C:\Users\lcj\nt-drafts\T-056\verify.sh.patch`, `test_verify.sh.patch`, `backend/src/test/java/kr/needteacher/architecture/DatabaseIntegrityControlIT.java`.
- `main`의 `git archive` 임시 복사본에서 두 패치 `git apply --check`·적용 성공, Git Bash `scripts/test_verify.sh` 통과. `verify.sh` 한 줄을 원복하면 새 스냅샷 위치 사례가 실패하고 전체 검사가 exit 1이었다.
- PR #6 헤드의 `git archive` 임시 복사본에서 `spotlessJavaCheck`, `test --tests '*TestIntegrityTest'`, `integrationTest --tests '*DatabaseIntegrityControlIT'` 통과(IT 1/1, Docker PostgreSQL). 운영 앱 대신 경량 Boot 설정에서 같은 DB 설정에 가드 빈 정의만 제거한 통제 컨텍스트와 가드 포함 컨텍스트를 비교했다.
- 저장소 안의 보호 파일·기존 테스트는 수정하지 않았다. 남은 위험: 전체 `verify.sh full`은 초안 단계에서 미실행이며, 보호 파일로 옮길 때 코드오너 검토가 필요하다.

## 재작업 1 (오케스트레이터 검토)

초안 세 개는 방향이 맞다. 아래만 보탠다.

1. **우회 경로 판단 (네 의견 필요):** `DatabaseIntegrityControlIT`는 IT에서 가드 정의를 지우는 방법(`BeanDefinitionRegistryPostProcessor`)을 보여 준다. 이 패턴을 보호되지 않은 일반 IT(`kr.needteacher.tutorprofile` 등)에 복사하면 가드를 빼고 DB 설정 덮어쓰기를 숨길 수 있다. 이 테스트가 없어도 원래 가능했던 구멍이다.
   - 막아야 한다고 보면: `architecture` 패키지 밖의 테스트 클래스가 `BeanFactoryPostProcessor`·`BeanDefinitionRegistryPostProcessor`를 구현하거나 `DatabaseIntegrityGuard`를 참조하지 못하게 하는 ArchUnit 규칙을 **새 파일**로 초안한다(기존 `TestIntegrityTest` 수정 없이). 위치는 `nt-drafts/T-056/backend/src/test/java/kr/needteacher/architecture/`.
   - 일부러 위반하는 클래스를 임시 복사본에 넣어 규칙이 실패하는지 확인한다.
   - 막을 필요가 없다고 보면 이유를 적는다.
2. `DatabaseIntegrityControlIT`의 영어 주석을 한국어로 바꾼다(저장소 관례).
3. `test_verify.sh.patch`의 `export CHECK_SNAPSHOT=1 && check … && unset CHECK_SNAPSHOT`은 `check`가 실패하면 변수가 뒤 사례로 새어 나간다. `CHECK_SNAPSHOT=1 check 0 "…"`처럼 그 호출에만 걸리게 바꾸고, 다시 통과·실패를 확인한다.

결과는 아래에 이어 쓴다.

## 재작업 1 결과

- **막아야 함:** `TestIntegrityTest`는 `@Import` 선언만 확인하므로 일반 IT의 빈 정의 제거를 막지 못한다. `C:\Users\lcj\nt-drafts\T-056\backend\src\test\java\kr\needteacher\architecture\GuardBypassIntegrityTest.java`를 새 ArchUnit 초안으로 추가했다. `architecture` 밖 테스트의 `BeanFactoryPostProcessor` 계열 의존성(하위 `BeanDefinitionRegistryPostProcessor` 포함)과 `DatabaseIntegrityGuard` 참조를 금지하며, 정상적으로 가드를 가져오는 `TestcontainersConfiguration`만 정확한 클래스명으로 제외한다.
- PR #6 임시 복사본에서 `test --tests '*GuardBypassIntegrityTest'`·`spotlessJavaCheck` 통과. 일반 `tutorprofile` 패키지에 후처리기 구현과 가드 참조 임시 클래스를 각각 넣자 두 규칙 모두 예상대로 실패했다. 임시 위반 클래스를 제거한 뒤 재검사 통과.
- `DatabaseIntegrityControlIT.java`의 영어 주석을 한국어로 바꾸고 외부 초안에 반영했다. 기존 Docker IT 1/1 통과 결과는 유지하며 이 재작업에서는 IT를 다시 실행하지 않았다.
- `test_verify.sh.patch`는 `CHECK_SNAPSHOT=1 check 0 "…"`로 바꿨다. main 임시 복사본에서 두 초안 패치 `git apply --check` 통과, 수정된 `test_verify.sh` 전체 통과. `verify.sh` 수정 한 줄을 원복하면 새 사례가 실패하고 전체 exit 1이었다.
- 남은 위험: 이 ArchUnit 규칙은 문자열 이름·리플렉션 등으로 가드를 지우는 모든 방법을 증명상 차단하지 않는다. 보호 파일 반영과 전체 `verify.sh full`은 오케스트레이터/코드오너 단계에 남는다.
