---
id: T-013
owner: agy
status: done    # todo | doing | blocked | done
scope:
  - docs/tasks/T-013-test-semantics-redteam.md
---

## 목표

레드팀. 하네스의 수치 게이트(커버리지 90/85, 뮤테이션 80, 0건 금지, ArchUnit, 금지 패턴)를 **모두 통과하면서** 실제로는 의미 없는 테스트나 검증을 만드는 방법을 찾는다. 지금까지 리뷰는 명령·파일 우회만 봤고, 테스트의 의미 쪽은 아무도 안 봤다.

## 완료 조건

- [x] Spring 테스트 설정으로 검증을 무력화하는 방법 (예: `src/test/resources`의 속성으로 Flyway 끄기·`ddl-auto` 바꾸기, `@MockitoBean`으로 검증 대상 자체를 바꿔치기, `@TestConfiguration`으로 Testcontainers 대신 가짜 DataSource)
- [x] PIT가 못 잡는 약한 테스트 패턴 (예: 예외만 안 나면 통과, 반환값 대신 호출 여부만 검증)
- [x] 각 방법마다 막을 수단 제안 (ArchUnit 규칙, verify.sh 금지 패턴, 테스트 리소스 보호 등)과 오탐 위험
- [x] 실제 파일은 만들지 말고 결과에 코드 조각으로만 적는다

## 참고

- `docs/proposals/test-harness.md`, `backend/AGENTS.md`
- `backend/build.gradle.kts`, `backend/src/test/java/`, `scripts/verify.sh`

## 결과

- 바꾼 파일: `docs/tasks/T-013-test-semantics-redteam.md` (검증: Spring Boot 4/Pitest 메커니즘 분석 및 취약 테스트 패턴 시뮬레이션)
- 1순위(Double Call Self-Comparison 공격): 피검증 메서드를 양변에서 2회 호출(`assertThat(f(x)).isEqualTo(f(x))`). JaCoCo 커버리지 100% 충족 및 뮤턴트 발생 시 양변 동시 변이로 PIT를 완벽히 속임 -> 방어: 정적 분석/정규식으로 양변 동일 호출 차단.
- 2순위(Spring test 설정 우회): `src/test/resources/application.properties`로 `spring.flyway.enabled=false`, `ddl-auto=none` 설정 시 DB/Flyway 검증 무력화 -> 방어: `src/test/resources`를 CODEOWNERS/보호 파일에 등록하고 금지 속성 regex 스캔.
- 3순위(IT 내 MockitoBean/가짜 DataSource 바꿔치기): `*IT`에서 `@MockitoBean Repository` 또는 `@TestConfiguration` H2 Bean으로 실제 Testcontainers PostgreSQL 검증 패스 -> 방어: ArchUnit으로 `*IT` 클래스의 MockitoBean 및 DataSource 빈 오버라이드 금지.
- 4순위(any() 남발 및 assertNotNull 단독 스모크): `verify(repo).save(any())`나 `assertThat(res).isNotNull()`만 작성하여 세부 필드 검증 누락 -> 방어: `save(any())` 단독 금지(ArgumentCaptor 강제), `assertNotNull` 단독 테스트 실패 처리.
- 남은 일: 오케스트레이터의 취약점 검토 및 `quality/` ArchUnit/verify.sh 방어 규칙 반영.

### 레드팀 공격 코드 조각 및 방어책

#### 1. Double Call Self-Comparison (JaCoCo 100% & PIT 통과 무의미 단언)
```java
// 공격: 실제 반환값이 엉터리이거나 계산이 틀려도 양변이 똑같이 변이되어 PIT 뮤턴트가 생존(Survive)함
@Test
void calculateWage_tautological() {
    WageRequest req = new WageRequest("teacher-1", 10, 50000);
    // 동일한 메서드 호출 결과를 양변에 배치
    assertThat(wageService.calculate(req))
        .isEqualTo(wageService.calculate(req));
}
```
- **방어 수단**: `verify.sh` 정규식 또는 Checkstyle/ArchUnit으로 `assertThat\((.+?)\)\.isEqualTo\(\1\)` 및 유사 동일 피연산자 비교 차단.
- **오탐 위험**: 극히 낮음 (정상적인 테스트에서 동일 호출 결과를 비교할 이유가 없음).

#### 2. Spring 테스트 프로퍼티를 통한 Flyway / DDL 검증 무력화
```properties
# src/test/resources/application.properties (비보호 경로를 통한 무력화)
spring.flyway.enabled=false
spring.jpa.hibernate.ddl-auto=none
spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL
```
- **방어 수단**:
  1. `backend/src/test/resources/` 디렉토리 전체를 `.github/CODEOWNERS` 및 `scripts/guard.py` 보호 경로에 추가.
  2. `verify.sh`에서 `spring.flyway.enabled\s*=\s*false` 및 `ddl-auto\s*=\s*(create|update|none)` 설정 스캔 시 실패 처리.
- **오탐 위험**: 낮음 (프로젝트 규칙상 Flyway 및 PostgreSQL Testcontainers 사용이 필수이므로 해당 속성 비활성화는 무조건 거부 대상).

#### 3. `@TestConfiguration` 및 `@MockitoBean`을 통한 Testcontainers 바꿔치기
```java
// *IT 통합 테스트인데 실제 DB를 타지 않고 Mock 또는 In-Memory로 우회
@SpringBootTest
class TeacherIT {
    @MockitoBean
    TeacherRepository teacherRepository; // 실제 DB 조회/저장 로직 무력화

    @TestConfiguration
    static class FakeDbConfig {
        @Bean @Primary
        DataSource dataSource() {
            return new org.h2.jdbcx.JdbcDataSource(); // Testcontainers 대신 H2 빈 주입
        }
    }
}
```
- **방어 수단**: ArchUnit 규칙으로 `*IT` 클래스에서 `@MockitoBean`, `@MockBean`, `@TestConfiguration DataSource` 선언을 명시적으로 금지.
  ```java
  classes().that().haveSimpleNameEndingWith("IT")
      .should().notHaveModifier(Modifier.PRIVATE)
      // 필드에 @MockitoBean/@MockBean 금지
      .andShould(notDeclareAnnotation("org.springframework.test.context.bean.override.mockito.MockitoBean"));
  ```
- **오탐 위험**: 중간 (외부 결제 PG사 등 외부 I/O 모킹이 필요한 정상 IT가 있을 수 있음 -> `Repository`, `DataSource`에 한정하여 차단 권장).

#### 4. 상태 검증 없는 무의미한 호출 검증 및 단순 널 체크
```java
@Test
void registerTeacher_weakVerification() {
    TeacherRequest req = new TeacherRequest("홍길동", -99999); // 비정상 데이터
    TeacherResponse res = teacherService.register(req);

    assertThat(res).isNotNull(); // 내부 데이터 필드(이름, 급여 등)는 검증 안 함
    verify(teacherRepository).save(any()); // any() 매처로 잘못된 저장을 통과시킴
}
```
- **방어 수단**:
  1. PIT 설정을 기본 `DEFAULTS`에서 `mutators.set(setOf("STRONGER"))`로 격상하여 경계값/리턴값 변이율 강화.
  2. ArchUnit 또는 금지 패턴으로 `verify(..).save(any())` 단독 호출 차단, `ArgumentCaptor` 또는 구체 엔티티 매처 사용 강제.
- **오탐 위험**: `createdAt`, `UUID` 등 동적 생성 필드가 포함된 응답에 대해 유연한 검증이 필요할 때 일부 제약 발생 가능.
