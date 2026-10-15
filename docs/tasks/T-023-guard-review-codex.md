---
id: T-023
owner: codex
status: done    # todo | doing | blocked | done
scope:
  - docs/tasks/T-023-guard-review-codex.md
---

## 목표

agy가 구현한 T-020(글자 스캔·0번 검사 보강)과 T-021(`architecture/DatabaseIntegrityGuard`)을 리뷰한다. 파일은 고치지 않는다.

## 완료 조건

- [x] `DatabaseIntegrityGuard`를 피해 갈 방법이 남았는지: 자식 컨텍스트 이벤트, 빈 덮어쓰기, DataSource 여러 개, EMF가 다른 DataSource를 쓰는 경우, Flyway `target`·`baselineOnMigrate`·`cleanDisabled` 같은 다른 속성, `@DirtiesContext`·컨텍스트 캐시 등. 재현했으면 재현 방법, 아니면 근거.
- [x] 음성 인수 테스트(T-019)가 다루지 않는 검사(URL 불일치, 제품명, pending, EMF 없음)를 지워도 아무 테스트도 실패하지 않는다. 보호 파일이라 사람 리뷰로 충분한지, 음성 사례를 더 둘지 의견.
- [x] T-020 `TEST_JAVA_FORBIDDEN`이 `re.I`가 되면서 생긴 거짓 양성, 0번 검사 matcher 판정의 구멍.
- [x] P0이 있으면 적고, 없으면 "P0 없음".

## 참고

- `docs/tasks/T-018-t015-fix-review.md`, `T-019`~`T-021`
- Claude 실측: 별도 `OtherIT` + `@TestPropertySource(hbm2ddl.auto=create)` → 스캔 실패 + 런타임 "DB 무결성 위반"으로 실패. `verify full --worktree` 71초 통과.
- 셸이 안 되면 코드 읽기만으로 리뷰하고 결과에 적는다. repl_run·terminal_send 금지.

## 결과

- P0: `verify.sh` 0번은 matcher가 Bash·PowerShell·Edit·Write만 덮어도 통과한다. `mcp__.*`를 뺀 `Bash|PowerShell|Edit|Write`는 실제 wmux 호출에 불일치하며, T-022는 matcher를 보지 않고 명령을 직접 실행하므로 CI 자체 검사도 놓친다(정규식 확인: 4개 일치, `mcp__wmux__repl_run` 불일치).
- P1: `DatabaseIntegrityGuard`는 DataSource·EMF를 `getBean(type)` 한 개씩만 검증한다. 정상 `@Primary` 빈과 다른 DB를 쓰는 추가 빈/EMF가 공존하면 실제 사용 경로를 보증하지 못한다. 코드 검토 근거이며 런타임 재현은 안 했다.
- P1: Flyway `target`을 낮추면 미적용 이력은 `Pending`이 아닌 `Above Target`이므로 `pending()`만으로 완전 적용을 보증하지 못한다([Flyway 상태](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/flyway-schema-history-table), [target](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-target-setting)). 현재 마이그레이션 파일은 없어 미래 위험; `baselineOnMigrate`·`baselineVersion`·`cleanDisabled`도 검사하지 않는다.
- P1: T-019의 음성 4건은 URL 불일치·비PostgreSQL·pending·EMF 누락 분기를 보호하지 않는다. 이 분기를 지워도 현재 양성 IT는 정상 기본 설정만 확인하므로 통과한다. 보호 파일의 사람 리뷰에 더해 각 위반의 음성 테스트가 필요하다.
- P2: 부모 가드는 자식의 refresh 이벤트도 받을 수 있는데, 자식 `getBeanNamesForType`은 부모 빈을 열거하지 않아 정상 자식 컨텍스트를 거부할 수 있다([Spring 이벤트 전파](https://docs.spring.io/spring-framework/docs/current-SNAPSHOT/javadoc-api/org/springframework/context/support/AbstractApplicationContext.html), [빈 탐색](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/beans/factory/ListableBeanFactory.html)). `@DirtiesContext`는 재시작 시 재검사하며, 캐시된 컨텍스트의 시작 이후 DB 변경은 감시하지 않는다.
- P2: `TEST_JAVA_FORBIDDEN`의 `re.I`·무조건 글자 스캔은 `// HBM2DDL is forbidden here` 같은 주석도 차단한다(정규식 실행 확인). 0번 검사는 여전히 실행되지 않는 `echo scripts/guard.py` 같은 명령도 문자열로만 인정한다.
- 검증: `python scripts/quality_gate.py --self-test`, `bash scripts/test_verify.sh` 통과. 제품 파일은 수정하지 않았고 런타임 우회 사례는 코드 검토에 한정했다.
