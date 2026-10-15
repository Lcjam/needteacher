# 테스트 하네스 v4 (합의본)

v1(Claude) → 1차(Codex T-001, agy T-002) → 사용자 결정 → v2 → 2차 토론(T-003, T-004) → v3 → 3차 토론(T-005, T-006) → v4.
이 문서가 구현 명세다. 바꾸려면 다시 토론 카드를 돌린다.

목표: 테스트를 통과하지 못한 코드는 `main`에 들어갈 수 없다. 규칙 문서가 아니라 기계적으로 막는다.

## 결정 사항

| 항목 | 결정 | 근거 |
|---|---|---|
| 최종 게이트 | GitHub public 저장소 + Actions + ruleset. 로컬은 빠른 피드백용 | 사용자 결정, 로컬 훅은 우회 가능 (T-001) |
| 기준치 | 래칫. 첫 기능에서 기준선 고정 후 하향 불가. 신규·변경 코드는 line 90 / branch 85 / mutation 80 | 사용자 결정 |
| 빌드 | Gradle 9.7 Kotlin DSL(래퍼 커밋), Spring Boot 4.1.1, Java 17 | 3자 합의는 Maven(T-005, T-006)이었으나 사용자 결정으로 Gradle (2026-10-13). 빌드 캐시는 끔(통합 테스트 결과 재생 방지) |
| 에이전트 신원 | GitHub App 설치 토큰으로 push·PR. 승인은 사람 코드오너(Lcjam)만 | 3자 합의 (T-003~T-006) |

## 1. `scripts/verify.sh fast|full`

- 검증 대상 고정: `tree=$(git write-tree)` → `git archive $tree`를 임시 디렉토리에 풀어 빌드. **모든 검사**(금지 패턴, 삭제·변경 diff, 시크릿)는 `$tree` 또는 그 스냅샷 기준 (`git diff HEAD $tree`, `gitleaks dir <스냅샷>`). 끝날 때 `git write-tree`가 `$tree`와 다르면 실패.
- `fast` (로컬 커밋, worker 종료): 검사 + 단위·통합 + 커버리지 + 변경 운영 클래스만 PIT(`withHistory` 허용). 변경 운영 클래스 0개면 PIT 생략을 출력.
- `full` (CI 필수 검사): 위 전부 + 이력 없는 전체 PIT. 병합 필수 검사는 반드시 full.
- 0건 통과 금지 (full): 단위 테스트 수, `*IT` 수, 측정 대상 클래스 수, 뮤턴트 수 중 하나라도 0이면 실패. ArchUnit 테스트가 없으면 실패.
- 스냅샷에는 `.git`이 없으므로 빌드·OpenAPI 생성은 git 메타데이터에 의존하지 않는다.

## 2. 빌드 기준 (`backend/build.gradle.kts`)

- `test`(단위) + `integrationTest`(`*IT`, ArchUnit 엔진 제외), JaCoCo 검증, Spotless(palantir), PIT는 `-Pfull`일 때만 `check`에 포함, ArchUnit, 경고=에러, 찾은 테스트 0개면 실패. PIT의 `targetTests`는 항상 전체로 고정.
- DB 테스트는 Testcontainers로 운영과 같은 엔진(H2 금지, 재사용 기능 미사용).
- Flyway: 빈 DB에 전체 마이그레이션 + 엔티티 스키마 검증.
- 기준치·제외 목록은 `quality/`.

## 3. 테스트 약화 방어

| 약화 방법 | 막는 수단 |
|---|---|
| `@Disabled`, `@Ignore`, `Assumptions.assume*`, `assertTrue(true)` | verify 금지 패턴 |
| 테스트 파일 삭제·이름 변경 | `git diff HEAD $tree --diff-filter=DR` |
| 기존 테스트 파일 변경·조기 return 삽입 (T-036) | `verify.sh`가 `--diff-filter=M` 차단 (기존 테스트 파일은 불변, 새 테스트는 새 파일로, `quality/test-removals.txt` 등록 시만 허용) |
| 인수 테스트 원본 변조·누락 (T-037, T-038) | 승인된 원본 `backend/src/acceptance/java/`(보호 파일, 소스셋 제외)과 `backend/src/test/java/`의 실행 파일을 `verify.sh`·CI가 바이트 단위 비교. 원본 전용 PR로 먼저 승인·병합, 원본 대기 중 무관한 일반 PR 차단 |
| assertion 약화 | 생존 뮤턴트 증가 → 뮤테이션 래칫 실패 |
| ArchUnit 규칙 완화 | ArchUnit 테스트를 보호 경로 `backend/src/test/java/**/architecture/`에 둠 |
| 커버리지·뮤테이션 제외 확대 | 제외 목록이 `quality/`(보호 파일) |
| 같은 호출 비교, `assertNotNull`·`any()`만 검증 (T-013) | PIT `STRONGER` 변이 + 생존 뮤턴트로 점수 하락 |
| 테스트 설정으로 Flyway 끄기·`ddl-auto` 변경·인메모리 DB (T-013, T-016) | `quality_gate.py`가 `backend/src/**` 스캔해 실패. 글자 스캔 보강: 설정 YAML 인라인 맵(`{`, `[`) 금지, `hbm2ddl`·`schema-generation`·`allow-bean-definition-overriding` 키 스캔 (T-020) |
| 인라인 맵·이스케이프·프로필·`hbm2ddl.auto` 등 설정 우회 (T-015, T-016) | 런타임 `architecture/DatabaseIntegrityIT`가 Spring이 실제 적용한 값으로 판정 (실제 PostgreSQL 연결, Flyway 빈 존재·동일 DB 마이그레이션 확인, `hibernate.hbm2ddl.auto=validate`) |
| 다른 IT의 `@TestPropertySource`·`@SpringBootTest(properties)`로 설정 우회 (T-018) | `TestcontainersConfiguration`이 `@Import(DatabaseIntegrityGuard.class)`로 각 IT 컨텍스트 시작 시 실제 PostgreSQL, Flyway(URL·locations·전체 이력 적용), ddl-auto(validate), schema-generation(none) 무결성 강제 (T-021, T-026) |
| 정상 `@Primary` 뒤의 다른 DataSource·EntityManagerFactory, 낮춘 Flyway `target`·baseline (T-023) | `DatabaseIntegrityGuard`가 DataSource·EMF 전체 빈과 Flyway `info().all()`의 적용 상태를 검증 (T-026) |
| 테스트의 `@Bean`으로 EMF·Flyway 교체 (T-023) | ArchUnit `TestIntegrityTest`가 EMF·`LocalContainerEntityManagerFactoryBean`·Flyway 반환 타입을 차단 (T-026) |
| `*IT`에서 `@MockitoBean`·`@TestBean`으로 바꿔치기 (T-013) | ArchUnit `architecture/TestIntegrityTest`(보호 파일). 외부 연동은 WireMock·Testcontainers로 |
| `HikariDataSource` 등 하위 타입 `@Bean`으로 DataSource 교체 (T-016) | ArchUnit `TestIntegrityTest`가 `assignableTo(DataSource.class)` 반환 타입 검사로 차단 |
| 패키지 경로를 붙인 `@AutoConfigureTestDatabase`로 DB 대체 (T-016) | ArchUnit 바이트코드 규칙 + `quality_gate.py` 글자 스캔에서 `@` 접두어 제거로 차단 |
| 다른 패키지의 같은 이름 가짜 설정으로 우회 (T-016) | ArchUnit `TestIntegrityTest`가 전체 이름(`kr.needteacher.TestcontainersConfiguration`)으로 비교 |
| 실제 DB를 안 쓰는 `*IT` (주석·미사용 import 포함, T-014, T-016) | ArchUnit `TestIntegrityTest`가 바이트코드의 실제 `@Import(TestcontainersConfiguration.class)` 애노테이션 값 검사로 실패시킴 |

## 4. 시크릿·계약

- gitleaks v8.30.1: 로컬 `gitleaks dir <스냅샷>`, CI `gitleaks/gitleaks-action@v3`. `.gitleaks.toml`·`.gitleaksignore`는 보호 파일.
- OpenAPI: CI에서 `openapi.json` 재생성 후 커밋본과 diff 있으면 실패. 프론트는 openapi-typescript로 타입 생성 + Vitest + `tsc` (프론트 스택 결정 후).

## 5. 보호 파일 (`.github/CODEOWNERS`, 소유자 Lcjam)

`scripts/`, `.githooks/`, `.github/`, `quality/`, `.claude/`, `.codex/`, `.agents/`, `.gitattributes`, `.gitleaks.toml`, `.gitleaksignore`, `backend/build.gradle.kts`, `backend/settings.gradle.kts`, `backend/gradle.properties`, `backend/gradle/`, `backend/gradlew*`, `backend/src/test/java/**/architecture/`, `backend/src/test/java/kr/needteacher/TestcontainersConfiguration.java`, `backend/src/acceptance/`.
- 로컬: `scripts/guard.py`가 이 파일을 읽어 에이전트의 수정을 막는다. 사용자만 만드는 `.harness-unlock`이 있으면 해제.
- 서버: 보호 파일이 바뀐 PR은 코드오너 리뷰 필수.
- 의존성 추가도 `build.gradle.kts` 변경이라 사람 리뷰를 거친다 (공급망 검토 겸).

## 6. main ruleset

- PR 필수, force push·삭제 금지, 우회 목록 비움(관리자 포함).
- 필수 상태 검사 `verify-full`, `gitleaks`: job에 `if`·`continue-on-error` 금지, 출처를 GitHub Actions로 고정.
- 코드오너 리뷰 필수, 새 push 시 기존 승인 취소, 마지막 push에 대한 승인 필수.

## 7. 에이전트 신원·자격 증명

- GitHub App 권한: Contents·Pull requests 읽기/쓰기, Metadata 읽기만. Administration·Workflows 권한 없음 → 키가 새도 App은 ruleset을 못 바꾸고 워크플로도 못 고친다.
- 사용자 자격 증명 격리 (사용자 결정 대기):
  - 기본: 이 PC의 `gh`·Git Credential Manager에서 Lcjam 토큰 제거, 승인·병합은 브라우저에서만. guard가 자격 증명 조회 명령을 막는다.
  - 강화(선택): 에이전트를 별도 비관리자 Windows 계정에서 실행 (같은 계정이면 저장된 자격 증명을 읽을 수 있음, T-005).

## 8. 에이전트 훅 (보조 방어선)

- 공통 판정 `scripts/guard.py --agent claude|codex|agy [--worker]` + 에이전트별 설정 (`.claude/settings.json`, `.codex/hooks.json`, `.agents/hooks.json`). 자체 검사 `scripts/test_guard.py`.
- 차단: 스킵·실패 무시 플래그, `--no-verify`, `core.hooksPath`, 보호 파일 수정, PR 승인·병합·ruleset API, 자격 증명 조회. worker는 기존 테스트 수정도 차단.
- worker 종료 시 `verify fast` 실패면 종료 차단.
- Codex·agy 훅은 오류 시 허용으로 흐른다. 최종 판정은 CI.

## 9. 프로세스: 테스트 작성자 ≠ 구현자

1. 카드의 `완료 조건` → Codex가 `backend/src/test/java/`에 실패하는 인수 테스트를 먼저 작성(red 확인).
2. 사용자 `.harness-unlock` 중 오케스트레이터가 `backend/src/acceptance/java/`로 이동, 잠금 복원 후 원본과 카드만 커밋.
3. 원본 전용 PR: 코드오너(Lcjam)가 내용 검토·승인 후 `main`에 병합 (보호 경로, Gradle 소스셋 제외). 원본 대기 중에는 다른 일반 PR 병합 불가.
4. 구현 담당이 병합된 원본을 `backend/src/test/java/` 같은 경로로 복사하고 기능 구현(green). 기존 테스트 불변.
5. Codex 리뷰(`verify full` + 생존 뮤턴트·경계값) → 구현 PR → CI가 원본 ↔ 실행 테스트 바이트 일치 검증 후 병합. 요구 변경·폐기는 원본 전용 PR로 먼저 확정 (T-038).

## 10. Windows·CI

- `.gitattributes`: 셸 스크립트·`gradlew` → `eol=lf`, `*.jar` binary. CI에서 gradle-wrapper.jar 변조 검사.
- CI: ubuntu `verify-full`(필수), windows `self-check-windows`(하네스 스크립트 호환성만. GitHub Windows 러너는 Linux 컨테이너를 못 띄움). 로컬 실측: full 58초, fast 42초.

## 구현 상태 (2026-10-15)

| 항목 | 상태 | 검증 |
|---|---|---|
| `scripts/verify.sh` | 완료 | `scripts/test_verify.sh` 50건. 로컬 훅 0번 검사, 기존 테스트 파일 불변성 강제(T-036), 인수 테스트 원본 바이트 단위 비교 및 원본 전용 PR 분류(T-038), Git `core.quotePath=false` 강제 및 비 ASCII·공백 경로 지원(T-040). 저장소 `full --worktree` 58초, `fast --worktree` 42초 통과 |
| 인수 테스트 원본 검증 (`backend/src/acceptance/`) | 완료 | `scripts/test_verify.sh`(원본 추가·복사·누락·한글자변경·조기return·등록·운영코드동시·원본대기중일반변경·심볼릭링크 등). CODEOWNERS 등록(T-038) |
| `scripts/quality_gate.py` (신규·변경 파일 기준 + 래칫) | 완료 | `--self-test`. 바닥값 `quality/baseline.txt`. 변이 0개 데이터 클래스는 라인 100% 시 통과(T-035) |
| `scripts/guard.py` | 완료 | `scripts/test_guard.py`. Claude 훅은 이 세션에서 실제 차단 확인 |
| 훅 설정 3종 실행 검사 | 완료 | `scripts/test_hooks.py`: Windows는 Claude Git Bash·Codex PowerShell/cmd·agy cmd, 그 밖은 sh로 실제 설정 명령의 차단·허용 확인. CI 두 job (T-022, T-025) |
| `scripts/gh-app-token.sh` | 완료 (App 생성 전) | `--self-test`로 JWT 서명 검증. 실제 발급은 App 생성 후 |
| Codex 훅 `.codex/hooks.json` | 작성(T-007) | 훅 명령 직접 실행으로 차단 확인. Codex 재시작 후 실측 필요 |
| agy 훅 `.agents/hooks.json` | 작성(T-008), 경로 수정 | `.agents/`에서 실행해 차단 확인. agy 재시작 후 실측 필요 |
| worker 훅·종료 게이트 | 작성 | 세션 재시작 후 worker가 로드되면 실측 필요 |
| 백엔드 골격 + Gradle 게이트 | 완료 | 단위 12(ArchUnit 10 포함), IT 11(PostgreSQL, 2개 클래스·음성 인수 6건·가드 없는 대조 1건 포함), PIT 2/2. Windows `integrationTest check` 59초 통과(T-026). assertion 약화 시 PIT 0%, 형식 위반·IT 0개 시 실패 확인 |
| CI·ruleset·GitHub App | 작성(ci.yml) / 미설정 | 저장소 생성 후 `docs/setup.md` |

리뷰(T-007~T-036)와 구현 중 막은 구멍:
- 명령 우회: wmux `repl_run`·`terminal_send`, 인터프리터 한 줄 코드(조각낸 경로 포함), 명령 안의 `cd`·`git -C`, `git clean`, Gradle 초기화 스크립트(`-I`, `~/.gradle/init.*`), `--exclude-task`, Gradle 프로젝트 속성·JVM 옵션 환경 변수(`JDK_JAVA_OPTIONS`), 훅 끄기(`.claude/settings.local.json`, 홈 에이전트 설정).
- 로컬 훅 우회: `disableAllHooks` 줄바꿈 우회 방지(JSON 로드), 저장소 `.claude/settings.json` 유효성(깨진 JSON 차단) 및 guard.py 훅 matcher(Bash·PowerShell·Edit·Write·MultiEdit·NotebookEdit·wmux `repl_run`·`terminal_send`)/등록 검증 (0번 검사, T-020·T-026).
- 입력 형식: agy `toolCall` 래퍼, 이름 없는 입력은 차단, PowerShell 파이프가 붙이는 BOM.
- agy 훅 호출: `--dangerously-skip-permissions`가 PreToolUse 훅을 건너뛰어(T-027) `toolPermission: always-proceed`로 전환했고(T-028), Windows 명령의 따옴표 이스케이프로 훅이 조용히 허용하던 문제는 인수 전달 방식까지 검사한다(T-030).
- 훅 실패 = 허용: Codex는 실패한 훅을 통과시킨다(공식 문서, 차단으로 바꾸는 설정 없음). `.codex/hooks.json` `commandWindows`가 PowerShell에서 파싱되지 않아 Codex 훅이 꺼져 있던 것을 찾아 고쳤고(T-022), `test_hooks.py`가 재발을 막는다.
- 검증 범위: worker용 `--worktree`와 종료 시 재확인, 주석 처리한 `@Test`(여러 줄 포함), 건너뛴 테스트, 잘못된 `VERIFY_BASE`, 추적된 빌드 산출물(+`--rerun-tasks`), ArchUnit 엔진이 IT로 집계되던 문제, PIT `targetTests` 기본값.
- 테스트 무결성·설정 우회 (T-013~T-026):
  - 테스트 설정 DB 조작 1차 차단: YAML 계층 스캔, 인라인 맵(`{`, `[`) 금지, `hbm2ddl`·`schema-generation`·`allow-bean-definition-overriding` 스캔 및 운영 설정 차단 (T-020).
  - Spring 런타임 무결성 검증: `architecture/DatabaseIntegrityIT`가 Spring이 실제 적용한 값 검증(실제 PostgreSQL 연결, Flyway 빈 존재 및 마이그레이션 확인, `hibernate.hbm2ddl.auto=validate`).
  - 모든 IT 컨텍스트 런타임 검사: `TestcontainersConfiguration`이 `architecture/DatabaseIntegrityGuard`를 `@Import`해 자기 컨텍스트만 검증한다. 모든 DataSource·EMF와 Flyway(URL·locations·전체 이력 적용), ddl-auto(validate), schema-generation(none) 무결성 강제 (T-018, T-021, T-026). T-025는 두 JPA 자동 설정을 제외하면 가드 없이 앱이 뜨지만 가드가 EMF 누락을 차단함을 확인했다.
  - ArchUnit `TestIntegrityTest`: `@MockitoBean`·`@MockitoSpyBean`·`@TestBean` 금지, DataSource·EMF·`LocalContainerEntityManagerFactoryBean`·Flyway 계열 `@Bean` 금지, FQN 패키지 경로 `@AutoConfigureTestDatabase` 차단, `TestcontainersConfiguration` 전체 FQN 일치 검사 및 바이트코드 애노테이션 검증(주석·미사용 import 차단).
- 테스트 불변성(T-035, T-036): 기존 테스트 파일은 추가를 포함해 불변(diff-filter=M 차단, 새 테스트는 새 파일로 작성), 조기 return 삽입이나 본문 변조 차단. quality/test-removals.txt로만 예외 허용. verify.sh의 판정 스크립트 호출을 작업 트리가 아닌 스냅샷(`$snap`) 기준으로 격리.
- 기준: 신규·변경 파일별 90/85/80(같은 파일의 모든 클래스), 리포트에 없는 실행 코드(인터페이스 default/static 메서드 포함) 실패, 실행 코드가 있는데 변이 0개인 파일 실패(순수 데이터 클래스·DTO·enum은 라인 100% 시 통과, T-035), 래칫 바닥값, full은 PIT 대상 지정 무시.
- 남은 한계: 자동 판정은 승인된 원본만 바이트 비교한다. 원본 없는 새 테스트·기능의 요구 누락은 자동 판정하지 않으며 카드·사람 리뷰로 확인한다. `quality/test-removals.txt` 등록은 코드오너 승인 예외다. 문자 코드로 조립하는 등 의도적 난독화는 정적 판정으로 못 막는다. 테스트 Java 주석에 금지 단어가 있어도 실패(거짓 양성, 주석 제거 우회 방지를 위해 일부러 둠). 잠금 해제 상태의 보호 파일 수정은 사용자 화면에 알림으로 드러나고, 최종 방어는 CI와 코드오너 리뷰다.

## 사용자 사전 작업

- Docker Desktop 실행. (완료)
- gitleaks 설치, GitHub App 생성·설치, ruleset: `docs/setup.md` (T-010).
- 자격 증명 격리 수준 결정 (7절).
