---
id: T-014
owner: agy
status: done    # todo | doing | blocked | done
scope:
  - docs/tasks/T-014-t013-fix-check.md
---

## 목표

T-013 레드팀 지적에 대한 방어가 실제로 막는지 **재현으로** 확인한다. 저장소 파일은 건드리지 않는다.

## 완료 조건

- [x] 저장소 밖 임시 폴더에 `backend/`를 복사해서(`cp -r backend "$TEMP/t014"` 등) 아래 공격을 하나씩 넣고 막히는지 확인
  - `src/test/resources/application.properties`에 `spring.flyway.enabled=false` → 임시 git 저장소에서 `scripts/verify.sh`의 1-2 검사(또는 같은 정규식)로 걸리는지
  - `*IT`에 `@MockitoBean` 필드, 테스트 코드에 `@Bean DataSource` → `./gradlew test --tests '*TestIntegrityTest'` 실패하는지
  - `TestcontainersConfiguration`을 안 쓰는 `*IT` → verify 1-3 검사로 걸리는지
  - 같은 호출 비교(`assertEquals(s.ping(), s.ping())`)로 바꾼 테스트 → `./gradlew pitest` 점수가 떨어져 실패하는지 (STRONGER)
- [x] T-013 1순위 주장("같은 호출 비교가 PIT를 완벽히 속인다")이 실측으로 맞는지 틀린지
- [x] 새로 보이는 우회가 있으면 함께

## 참고

- `docs/tasks/T-013-test-semantics-redteam.md`
- `backend/src/test/java/kr/needteacher/architecture/TestIntegrityTest.java`, `backend/build.gradle.kts`(`mutators`), `scripts/verify.sh`(1-2, 1-3)

## 결과

- 바꾼 파일: `docs/tasks/T-014-t013-fix-check.md` (검증: $TEMP/t014 임시 복사본에서 Gradle/ArchUnit/PIT 실측 실행)
- 1순위(T-013 1순위 주장 검증): **주장이 틀렸음(Claude 판단 적중)**. 같은 호출 비교 시 변이 코드에서도 테스트가 참이 되어 뮤턴트가 **생존(Survived, 0% kill)**하므로 PIT 임계치(80%) 미달로 **빌드가 즉시 실패**함.
- 2순위(MockitoBean / Bean DataSource 방어): `TestIntegrityTest` 실측 결과 `@MockitoBean` 및 `@Bean DataSource` 삽입 시 `integrationTestFieldsDoNotOverrideBeans` 및 `testCodeDoesNotReplaceDataSource`로 **완벽히 차단됨**.
- 3순위(Flyway 무력화 방어 및 새 우회): `verify.sh` 1-2 정규식으로 properties는 걸리나, YAML 계층 블록(`flyway:\n  enabled: false`) 사용 시 정규식 우회 가능 -> YAML 파싱 또는 키워드 정규식 강화 필요.
- 4순위(Testcontainers 미사용 방어 및 새 우회): 1-3 검사(`git grep -L`)로 미사용 IT가 걸리나, 주석(`// TestcontainersConfiguration`)이나 미사용 import문만 넣어도 문자열 검색이 뚫림 -> `@Import\(.*TestcontainersConfiguration`으로 엄격화 필요.
- 남은 일: 오케스트레이터의 verify.sh 정규식 강화(YAML/Import) 및 스캐폴딩 확정.
