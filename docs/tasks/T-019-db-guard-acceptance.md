---
id: T-019
owner: codex
status: done    # todo | doing | blocked | done
scope:
  - backend/src/test/java/kr/needteacher/architecture/DatabaseIntegrityIT.java
  - docs/tasks/T-019-db-guard-acceptance.md
---

## 목표

T-018 P0(다른 IT의 `@TestPropertySource`로 `hbm2ddl.auto=create`) 대응의 인수 테스트를 구현보다 먼저 쓴다. 구현(T-021, agy)은 `TestcontainersConfiguration`에 넣는 런타임 검사로, 이 설정을 `@Import`한 모든 컨텍스트가 시작할 때 DB 무결성을 확인하고 위반이면 시작을 실패시킨다.

## 완료 조건

- [ ] 계약: 위반 시 컨텍스트 시작이 예외로 실패하고, 원인 체인 어딘가의 메시지에 `DB 무결성 위반`이 들어 있다. 이 문자열을 테스트가 확인한다 (다른 이유의 실패와 구분).
- [ ] 음성 사례: `SpringApplicationBuilder(BackendApplication.class, TestcontainersConfiguration.class)`, 웹 없이, 아래 속성 하나씩 넣고 시작 → 위 계약대로 실패하는지 단언
  - `spring.jpa.properties.hibernate.hbm2ddl.auto=create`
  - `spring.jpa.properties.jakarta.persistence.schema-generation.database.action=drop-and-create` (Hibernate가 `hbm2ddl.auto`보다 우선하는지 직접 확인하고 결과에 적기)
  - `spring.flyway.enabled=false`
  - `spring.flyway.locations=classpath:nowhere`
- [ ] 양성 보강: 기존 `flywayMigratesTheSameDatabase`를 같은 DB인지(두 연결의 `getMetaData().getURL()` 일치)와 `flyway.info().pending()`이 비었는지까지 보게 바꾼다 (T-018 P1).
- [ ] 지금(구현 전)은 음성 사례가 실패하는 것을 `bash ./gradlew integrationTest`로 확인하고 결과에 적는다. 늘어난 실행 시간도 적는다.
- [ ] 기존 테스트 메서드는 지우지 않는다.

## 참고

- `docs/tasks/T-018-t015-fix-review.md`
- 보호 파일이지만 지금은 하네스 구축 중이라 잠금 해제 상태. 범위 밖 보호 파일은 건드리지 않는다.
- agy가 동시에 T-020(`scripts/`)을 작업한다. Gradle은 이 카드만 돌린다.

## 결과

- 변경: `DatabaseIntegrityIT.java`에 위반 속성 4종의 컨텍스트 시작 실패·원인 체인 `DB 무결성 위반` 인수 테스트를 추가하고 기존 Flyway 검사에 DB URL·pending 확인을 보강했다(재시작 전 작업).
- 구현 전 기준 `integrationTest`는 재시작 전 2분 14초에 통과했다. 첫 red 실행은 테스트 코드의 미사용 try 자원 경고로 컴파일 실패했고, 해당 경고는 수정했다.
- blocked: 재시작 후 내 셸에서 요청한 `bash ./gradlew spotlessApply integrationTest`를 실행하려 했으나 exec_command가 `helper_unknown_error: setup refresh had errors`로 프로세스를 만들지 못했다. red 4건과 기존 3건의 최종 결과·증가 시간은 미확인이다.
- 재시작 후 출력에 문자 그대로의 `Hook failed`는 없었다. 잠금 해제 경로를 언급한 조회 명령은 PreToolUse가 차단했으며, 이후 셸 실행은 위 helper 오류로 실패했다.
- 남은 일: 셸 실행 장애를 해결한 뒤 요청 명령으로 red 4건·기존 3건 및 JPA schema-generation 우선순위를 확인한다. 다른 파일은 수정하지 않았다.
- Claude 실측(대신 실행): `spotlessApply integrationTest` → 8건 중 음성 4건만 "nothing was thrown"으로 실패(의도한 red), 기존 3건·BackendApplicationIT 통과. 음성 4건 합 약 13초, 전체 53초.
