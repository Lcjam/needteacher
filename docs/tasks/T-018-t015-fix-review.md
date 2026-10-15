---
id: T-018
owner: codex
status: done    # todo | doing | blocked | done
scope:
  - docs/tasks/T-018-t015-fix-review.md
---

## 목표

T-015·T-016 지적에 대한 Claude의 대응 코드를 리뷰한다. 우회 경로나 오탐이 있으면 찾는다.

## 완료 조건

- [ ] `backend/src/test/java/kr/needteacher/architecture/DatabaseIntegrityIT.java`: Spring이 실제 적용한 설정으로 판정한다는 의도가 맞게 구현됐는지, 이 테스트 자체를 피해 갈 방법(예: 다른 IT 클래스만의 `@TestPropertySource` 덮어쓰기)이 남았는지
- [ ] `TestIntegrityTest`: 전체 이름 비교, `assignableTo(DataSource)`, `AutoConfigureTestDatabase` 바이트코드 규칙
- [ ] `scripts/quality_gate.py` `config_problems`: 인라인 맵 금지, `hbm2ddl`, `architecture/` 제외(하네스 자신 오탐 방지)가 새 구멍이 되지 않는지
- [ ] `scripts/verify.sh` 0번 검사: JSON 파싱, 저장소 `settings.json` 유효성·guard 훅 존재
- [ ] `.gitattributes` LF 고정, `TestcontainersConfiguration` public 전환·보호 파일 추가
- [ ] 남은 P0가 있으면 적고, 없으면 "P0 없음"

## 참고

- `docs/tasks/T-015-final-check-codex.md`, `docs/tasks/T-016-final-check-agy.md`
- Claude 실측: 인라인 맵으로 Flyway 끄기 → `DatabaseIntegrityIT` 3건 실패, `hbm2ddl.auto=create` → 1건 실패(글자 스캔은 통과했었음), 가짜 설정·Hikari 빈·패키지 경로 애노테이션 → 해당 ArchUnit 규칙 실패. 저장소 `verify full` 58초 통과

## 결과

- P0 남음: 별도 `OtherIT`에 `@TestPropertySource(properties = "spring.jpa.properties.hibernate.hbm2ddl.auto=create")`를 두면 `config_problems`는 0건, 실제 Hibernate 설정은 `create`. 임시 복사본에서 `OtherIT` 1건과 `DatabaseIntegrityIT` 3건이 같은 integrationTest 실행에서 모두 통과했다. IT별 설정 우회가 남는다.
- P1 부분: `flywayMigratesTheSameDatabase`는 두 연결의 제품명이 PostgreSQL인지 각각 볼 뿐 같은 DB인지, 마이그레이션이 적용됐는지 확인하지 않는다.
- P1 부분: verify 0번 검사는 `hooks` JSON 안의 `guard.py` 문자열 존재만 본다. 실행되지 않는 matcher/명령에 이 문자열을 넣어도 통과하므로 훅 동작은 보증하지 못한다.
- P2 오탐: 일반 테스트 Java의 주석 `// @AutoConfigureTestDatabase`만으로 `config_problems`가 실패한다(임시 파일 검사 확인).
- 해결: 운영 YAML 인라인 맵 금지와 운영 `hbm2ddl` 검사, 기본 컨텍스트의 런타임 `validate` 확인은 코드와 자체 검사에서 확인했다. `architecture/` 제외는 보호 경로에 한정된다.
- 해결: `TestIntegrityTest`는 TestcontainersConfiguration 전체 이름, DataSource 하위 반환 타입, AutoConfigureTestDatabase 애노테이션을 바이트코드 기준으로 검사한다(코드 검토).
- 해결: verify 0번 검사는 JSON 줄바꿈 `disableAllHooks`와 저장소 설정 JSON 오류를 잡는다. 자체 검사 사례도 통과했다.
- 해결: `.gitattributes`는 Java 등에 LF를 지정하고, public `TestcontainersConfiguration`은 CODEOWNERS에 추가됐다. 현재 미추적 작업 트리의 기존 CRLF는 그대로다.
- 자체 검사 4종 모두 통과. 이 환경에 gitleaks가 없어 `verify full`은 재실행하지 못했다. 수정 파일은 이 카드뿐.
