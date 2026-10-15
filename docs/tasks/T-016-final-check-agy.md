---
id: T-016
owner: agy
status: done
scope:
  - docs/tasks/T-016-final-check-agy.md
---

## 목표

하네스 마무리 확인. T-014에서 찾은 우회 2건이 막혔는지 재현으로 확인하고, 테스트 의미 쪽에 남은 구멍이 있는지 최종 의견을 낸다. 저장소 파일은 건드리지 않는다.

## 완료 조건

- [x] YAML 계층(`spring:\n  flyway:\n    enabled: false`)·인라인 맵·테스트 Java의 `@AutoConfigureTestDatabase` → `python scripts/quality_gate.py --scan-config <임시 backend 복사본>`이 실패하는지
- [x] 주석·미사용 import로만 `TestcontainersConfiguration`을 적은 `*IT` → 임시 복사본에서 `./gradlew test --tests '*TestIntegrityTest'`의 `integrationTestsUseRealPostgres`가 실패하는지
- [x] 테스트 의미 쪽에 남은 구멍이 있으면 적고, 없으면 "추가 구멍 없음"이라고 명시

## 참고

- `docs/tasks/T-014-t013-fix-check.md`
- `scripts/quality_gate.py` (`config_problems`), `backend/src/test/java/kr/needteacher/architecture/TestIntegrityTest.java`

## 결과

- 바꾼 파일: `docs/tasks/T-016-final-check-agy.md` (검증: $TEMP 임시 복사본에서 quality_gate --scan-config 및 TestIntegrityTest 실측)
- 1순위(우회 1 차단 확인): YAML 계층·인라인 맵(`src/test/resources`의 `flyway`/`datasource`) 및 `@AutoConfigureTestDatabase` 모두 `config_problems`에서 exit=1로 **정상 차단됨**.
- 2순위(우회 2 차단 확인): `*IT`에 주석 또는 미사용 import만 적은 경우, ArchUnit `integrationTestsUseRealPostgres`가 바이트코드 애노테이션 검사로 **정상 차단됨**.
- 3순위(남은 구멍 - FQN 우회): `@AutoConfigureTestDatabase`가 FQN(`@org...AutoConfigureTestDatabase`)으로 쓰이면 `quality_gate.py` 정규식(`@AutoConfigureTestDatabase`)을 우회함 -> `@.*AutoConfigureTestDatabase`로 보완 필요.
- 4순위(남은 구멍 - ArchUnit 단순명/하위타입 우회): `TestIntegrityTest`가 `getSimpleName()`을 보므로 타 패키지 가짜 `TestcontainersConfiguration` 허용, `haveRawReturnType(DataSource.class)`는 `HikariDataSource` 등 하위타입 반환 `@Bean`을 놓침 -> FQN 검사 및 `areAssignableTo`로 보완 필요.
- 남은 일: 오케스트레이터의 미세 우회 2건(FQN/하위타입) 반영 및 하네스 최종 확정.
