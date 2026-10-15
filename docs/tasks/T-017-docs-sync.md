---
id: T-017
owner: agy
status: done
scope:
  - docs/proposals/test-harness.md
  - backend/AGENTS.md
  - docs/tasks/T-017-docs-sync.md
---

## 목표

T-015(Codex)·T-016(agy) 대응으로 바뀐 하네스를 문서 두 곳에 반영한다. 구현 파일은 건드리지 않는다.

## 완료 조건

- [x] `docs/proposals/test-harness.md` 3절 "테스트 약화 방어" 표에 행 추가
  - 설정을 어떤 방식으로 바꿔도(인라인 맵·이스케이프·프로필·`spring.jpa.properties.hibernate.hbm2ddl.auto`) → `architecture/DatabaseIntegrityIT`가 Spring이 실제 적용한 값으로 판정 (실제 PostgreSQL, Flyway 빈 존재·같은 DB, `hbm2ddl.auto=validate`)
  - 다른 패키지의 같은 이름 가짜 설정 → `TestIntegrityTest`가 전체 이름(`kr.needteacher.TestcontainersConfiguration`)으로 비교
  - `HikariDataSource` 등 하위 타입 `@Bean` → `assignableTo(DataSource)`
  - 패키지 경로를 붙인 `@AutoConfigureTestDatabase` → 바이트코드 규칙 + 글자 스캔에서 `@` 제거
  - 글자 스캔 보강: 설정 YAML 인라인 맵 금지, `hbm2ddl` 키
- [x] 같은 문서 "구현 상태" 표·목록 갱신: `verify.sh` 자체 검사 건수(`bash scripts/test_verify.sh`를 직접 돌려 확인), 로컬 훅 검사가 JSON 파싱 + 저장소 `settings.json` 유효성·guard 훅 존재까지 본다는 점, 마지막 `verify full` 58초 통과
- [x] 같은 문서 5절 보호 파일 목록에 `backend/src/test/java/kr/needteacher/TestcontainersConfiguration.java` 추가 (`.github/CODEOWNERS`와 일치하는지 직접 대조)
- [x] `backend/AGENTS.md` 테스트 절: `*IT`는 `@Import(TestcontainersConfiguration.class)` 필수, IT에서 `@MockitoBean`·`@TestBean`·가짜 DataSource 금지(외부 연동은 WireMock·Testcontainers), 테스트에서 DB 설정 덮어쓰기 금지

## 참고

- `docs/tasks/T-015-final-check-codex.md`, `docs/tasks/T-016-final-check-agy.md`
- `backend/src/test/java/kr/needteacher/architecture/` (`TestIntegrityTest`, `DatabaseIntegrityIT`), `scripts/quality_gate.py`, `scripts/verify.sh` (0번 검사), `.github/CODEOWNERS`

## 결과

- 바꾼 파일: `docs/proposals/test-harness.md`, `backend/AGENTS.md`, `docs/tasks/T-017-docs-sync.md`
- 3절 약화 방어 표: 런타임 `DatabaseIntegrityIT`, `TestIntegrityTest`(FQN, assignableTo), FQN 애노테이션 차단, YAML 인라인 맵/hbm2ddl 스캔 반영
- 5절 보호 파일: `TestcontainersConfiguration.java` 추가 (`.github/CODEOWNERS` 대조 완료)
- 구현 상태 표·목록: `test_verify.sh` 25건, 0번 로컬 훅 검사(JSON 파싱, 유효성/guard 훅 존재), verify full 58초 통과 명시
- backend/AGENTS.md: `*IT` `@Import(TestcontainersConfiguration.class)` 필수, `@MockitoBean`·`@TestBean`·하위타입 DataSource 금지, DB 설정 덮어쓰기 금지 명시
- 검증: `bash scripts/test_verify.sh`(25건 전원 통과), `quality_gate.py --self-test`, `test_guard.py` 통과
- 남은 일: 오케스트레이터의 diff 검토 및 커밋
