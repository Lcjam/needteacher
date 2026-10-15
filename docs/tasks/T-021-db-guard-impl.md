---
id: T-021
owner: agy
status: done
scope:
  - backend/src/test/java/kr/needteacher/TestcontainersConfiguration.java
  - backend/src/test/java/kr/needteacher/architecture/DatabaseIntegrityGuard.java
  - docs/proposals/test-harness.md
  - backend/AGENTS.md
  - docs/tasks/T-021-db-guard-impl.md
---

## 목표

Codex가 T-019에서 쓴 `DatabaseIntegrityIT` 인수 테스트를 통과시킨다. `TestcontainersConfiguration`을 `@Import`한 모든 컨텍스트가 시작할 때 DB 무결성을 확인하게 해서, IT 하나만이 아니라 모든 IT의 설정 우회를 막는다 (T-018 P0).

## 완료 조건

- [x] 검사는 보호 경로의 새 클래스 `architecture/DatabaseIntegrityGuard`(예: `ApplicationListener<ContextRefreshedEvent>` 빈)에 두고, `TestcontainersConfiguration`은 `@Import(DatabaseIntegrityGuard.class)`만 추가한다. 검사 코드에 `hbm2ddl` 같은 단어가 들어가므로 `architecture/` 밖에 두면 `quality_gate.py` 스캔(T-020)에 걸린다. 컨테이너 JDBC URL 비교에 필요하면 컨테이너 빈을 주입받는다. 위반이면 메시지에 `DB 무결성 위반`이 들어간 `IllegalStateException`을 던져 컨텍스트 시작을 실패시킨다. 확인할 것:
  - DataSource 연결이 PostgreSQL이고 JDBC URL이 이 설정의 컨테이너 `getJdbcUrl()`과 같다
  - Flyway 빈이 있고(없으면 위반), Flyway의 DataSource URL이 같고, `locations`가 기본값 `classpath:db/migration`이고, `info().pending()`이 비었다
  - `EntityManagerFactory` 속성 `hibernate.hbm2ddl.auto`가 `validate`, 키에 `schema-generation.database.action`이 들어간 속성은 `none`만
  - DataSource·Flyway·EntityManagerFactory가 없는 컨텍스트도 위반 (IT는 실제 DB를 쓰는 전체 컨텍스트만)
- [x] `DatabaseIntegrityIT`(T-019)는 고치지 않는다. 안 맞으면 결과에 적고 멈춘다.
- [x] `cd backend && bash ./gradlew integrationTest` 통과, `bash ./gradlew check` 통과.
- [x] 문서 정정:
  - `backend/AGENTS.md`: DB 설정 덮어쓰기 금지 줄의 근거를 "모든 IT 컨텍스트에서 `TestcontainersConfiguration`의 런타임 검사 + `quality_gate.py` 1차 스캔"으로.
  - `docs/proposals/test-harness.md` 3절 표에 "다른 IT의 `@TestPropertySource`·`@SpringBootTest(properties)`로 설정 우회 (T-018)" 행 추가. 구현 상태 표의 IT 개수·실행 시간을 실제 값으로(`backend/build/test-results/`로 확인), 7절 근처 "로컬 실측: full 50초" 갱신.
  - 남은 한계에 추가: 테스트 Java 주석에 금지 단어가 있어도 실패(거짓 양성, 일부러 둠).

## 참고

- `docs/tasks/T-018-t015-fix-review.md`, `docs/tasks/T-019-db-guard-acceptance.md`, `docs/tasks/T-020-scan-hardening.md`
- 보호 파일이지만 하네스 구축 중이라 잠금 해제 상태.

## 결과

- 바꾼 파일: `backend/.../TestcontainersConfiguration.java`, `backend/.../DatabaseIntegrityGuard.java`, `docs/proposals/test-harness.md`, `backend/AGENTS.md`, `docs/tasks/T-021-db-guard-impl.md`
- DatabaseIntegrityGuard: ApplicationListener로 모든 IT 컨텍스트 refresh 시 PostgreSQL URL, Flyway(빈/URL/locations/pending), ddl-auto(validate), schema-generation(none) 검증 및 'DB 무결성 위반' 예외 강제
- TestcontainersConfiguration: `@Import(DatabaseIntegrityGuard.class)` 추가. DatabaseIntegrityIT는 일절 미수정
- 문서 갱신: AGENTS.md 런타임 검사 근거 반영, test-harness.md 3절 우회 방어 표/구현 상태(IT 8건, check 57초)/full 58초/주석 오탐 한계 갱신
- 검증: `integrationTest`(8/8 통과, 음성 4건 정상 거부), `check`(단위 11+IT 8+spotless+jacoco 57초 통과), test_verify(26건)/guard/quality_gate 통과
- 남은 일: 오케스트레이터의 diff 검토 및 커밋
