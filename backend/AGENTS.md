# backend/AGENTS.md

Java 17 / Spring Boot 4.1.1 / Gradle 9.7 (Kotlin DSL) / PostgreSQL. 품질 기준은 `docs/proposals/test-harness.md`.

## 명령

전역 Gradle을 가정하지 않는다. 항상 래퍼(`bash ./gradlew`, Windows PowerShell은 `.\gradlew.bat`)를 쓴다.

| 목적 | 명령 (`backend/`에서) |
|---|---|
| 단위 테스트 (`*Test`, Docker 불필요) | `bash ./gradlew test` |
| 단일 테스트 | `bash ./gradlew test --tests '*PingServiceTest'` / 메서드: `--tests '*PingServiceTest.returnsPong'` |
| 통합 테스트 (`*IT`, Docker 필요) | `bash ./gradlew integrationTest` |
| 형식 + 단위 + 통합 + 커버리지 | `bash ./gradlew check` |
| 위 + 전체 뮤테이션 | `bash ./gradlew -Pfull check` |
| 특정 클래스 뮤테이션 | `bash ./gradlew pitest -PpitTargetClasses='kr.needteacher.ping.*'` |
| 형식 고치기 | `bash ./gradlew spotlessApply` |

커밋·완료 판정은 저장소 루트의 `bash scripts/verify.sh fast|full`이 한다 (위 명령은 개발 중 빠른 확인용).

## 구조·스타일

- 기능별 패키지: `kr.needteacher.<기능>` 아래 `XxxController` → `XxxService` → `XxxRepository`. 방향은 ArchUnit(`architecture/`, 보호 파일)이 강제한다. 기능 패키지 사이 순환 금지.
- 형식은 palantir-java-format(4칸)이 `check`에서 강제한다. 손으로 맞추지 말고 `spotlessApply`.
- 컨트롤러는 HTTP 처리만, 비즈니스 규칙은 서비스에.
- 컴파일 경고는 에러다 (`-Xlint:all -Werror`).
- 스키마는 Flyway(`src/main/resources/db/migration/V<n>__*.sql`)로만 바꾼다. JPA는 `ddl-auto=validate`.

## 테스트

- 단위 테스트 `*Test.java`: Spring 없이 생성자로 조립. 뮤테이션은 단위 테스트만 본다. `SecurityConfig`만 PIT 면제이며 JaCoCo line/branch와 실제 필터 체인 IT는 유지한다. 같은 소스 파일에 다른 클래스를 두지 않고, 설정 클래스의 필드·직접 작성한 생성자를 두지 않는다. 401/403 핸들러는 별도 클래스·단위 테스트로 검증한다. 새 경로/역할/CSRF 정책에는 허용·거부 HTTP 테스트를 함께 추가한다. 면제 목록 변경은 보호 파일 리뷰가 필요하다.
- 통합 테스트 `*IT.java`: `@Import(TestcontainersConfiguration.class)` 필수(실제 PostgreSQL 사용). H2 등 인메모리 DB 금지.
- IT에서 `@MockitoBean`·`@MockitoSpyBean`·`@TestBean` 빈 바꿔치기 및 가짜 DataSource(`HikariDataSource` 등 하위 타입 포함) 빈 재정의 금지(ArchUnit `TestIntegrityTest`가 강제). 외부 연동은 목 대신 WireMock·Testcontainers로 대체한다.
- 테스트에서 DB 설정 덮어쓰기 금지: 테스트 리소스(`src/test/resources/`)나 `@TestPropertySource` 등으로 Flyway 끄기, `ddl-auto` 변경, 인메모리 DB 설정 금지(모든 IT 컨텍스트에서 `TestcontainersConfiguration`의 런타임 검사 + `quality_gate.py` 1차 스캔).
- 기존 테스트 불변: 기존 테스트 파일은 불변이며 추가를 포함한 모든 변경이 금지된다. 새 테스트는 새 파일로 작성하고, 기존 테스트 파일을 바꾸거나 지우려면 `quality/test-removals.txt`(보호 파일)에 등록해야 한다 (T-036).
- 인수 테스트 원본 (`backend/src/acceptance/java/`, 보호 파일): Gradle 소스셋에 포함되지 않으며, 일반 변경 CI에서 `backend/src/test/java/`의 실행 테스트와 바이트 단위로 일치해야 한다(누락·불일치 시 실패, `quality/test-removals.txt` 등록 시만 예외). 원본의 추가·수정·폐기는 운영 코드 없이 별도 원본 전용 PR로 코드오너(Lcjam) 승인을 받아 먼저 병합한다. 원본 대기 중에는 다른 일반 PR이 통과할 수 없다 (T-038).
- 기준: 프로젝트 전체 line 90% / branch 85% (JaCoCo, 단위+통합 합산), mutation 80% (PIT STRONGER). 신규·변경 소스 파일마다 따로 같은 기준, 프로젝트 전체는 `quality/baseline.txt` 바닥값 아래로 못 내려간다(래칫). 둘 다 `scripts/quality_gate.py`가 검사하고, 기준 파일은 보호 파일이다. 순수 데이터 클래스(변이 0개)는 라인 100%면 통과한다 (T-035).
