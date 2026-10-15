---
id: T-025
owner: codex
status: done    # todo | doing | blocked | done
scope:
  - backend/src/test/java/kr/needteacher/architecture/DatabaseIntegrityIT.java
  - scripts/test_hooks.py
  - docs/tasks/T-025-guard-acceptance-2.md
---

## 목표

T-023·T-024 지적 대응. 구현(T-026, agy) 전에 실패하는 검사를 먼저 쓴다.

## 완료 조건

- [x] `DatabaseIntegrityIT` 음성 사례 추가 (기존 `rejectsDatabaseIntegrityOverrides`에 값 추가 또는 새 메서드, 기존 메서드는 지우지 않음):
  - `spring.autoconfigure.exclude=<Spring Boot 4.1의 Hibernate JPA 자동 설정 클래스 전체 이름>` → EMF 없음으로 `DB 무결성 위반`
  - `spring.autoconfigure.exclude=<Flyway 자동 설정 클래스 전체 이름>` → Flyway 없음으로 `DB 무결성 위반`
  - 클래스 이름은 의존성 jar에서 직접 확인해 결과에 적는다.
- [x] 이 중 지금 이미 통과하는 것과 실패하는 것을 구분해 결과에 적는다 (Flyway 제외는 지금 가드로도 막힐 수 있음).
- [x] `scripts/test_hooks.py` 보강 (T-024):
  - Claude 훅은 Windows에서도 `bash -c`로, `$CLAUDE_PROJECT_DIR`는 문자열 치환 대신 환경 변수로 넘겨 실행
  - agy 훅의 cwd는 실제와 같게 `.agents/`
  - 차단 사례에 각 에이전트 형식의 명령 도구 + 스킵 플래그(`-D` + `skipTests`) 추가. 문자열은 조각내서 쓰면 네 훅에 걸리지 않는다
  - `python scripts/test_hooks.py` 통과
- [x] 셸이 안 되면 코드만 쓰고 "실행 미확인". repl_run·terminal_send 금지.

## 참고

- `docs/tasks/T-023-guard-review-codex.md`, `docs/tasks/T-024-hooks-test-review-agy.md`
- Gradle 실행 확인은 Claude도 한다.

## 결과

- `DatabaseIntegrityIT.java`: Hibernate JPA·Data JPA 리포지토리 동시 제외 음성 사례와 가드 없는 시작 확인 테스트 추가, 기존 메서드는 유지.
- Spring Boot 4.1.1 jar 확인: `spring-boot-hibernate-4.1.1.jar`의 `org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration`, `spring-boot-flyway-4.1.1.jar`의 `org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration`.
- 첫 실행: Hibernate JPA만 제외하면 `jpaSharedEM_entityManagerFactory` 생성 오류가 먼저 발생해 계약 실패(10건 중 1건 red).
- 재작업 jar 확인: `spring-boot-data-jpa-4.1.1.jar`의 `org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration`.
- 동시 제외는 가드 없는 컨텍스트가 실제 시작하고 EMF가 없음(단일 확인 테스트 21초 통과). 가드 포함 시 `DB 무결성 위반: EntityManagerFactory 빈이 없습니다`로 차단되어 11건 전체 통과(41초); Flyway 제외도 기존 가드가 차단.
- `scripts/test_hooks.py`: Claude는 Windows Git Bash와 실제 환경 변수, agy는 `.agents/` cwd·cmd, 세 형식 모두 동적으로 조립한 스킵 플래그 차단 사례 추가.
- `python scripts/test_hooks.py` 통과; Windows `gradlew.bat spotlessJavaCheck` 통과. WSL `bash ./gradlew spotlessJavaCheck`는 경로 해석 오류로 실행 불가.
- 남은 일: 이 EMF 누락 경로는 현재 가드가 이미 막는다. T-026의 다른 지적 사항 구현·검증.

## 재작업 (Claude 검토)

- [x] Hibernate JPA만 빼면 Spring Data JPA(`jpaSharedEM_entityManagerFactory`)가 먼저 실패해 우회가 성립하지 않는다. 진짜 우회는 Spring Data JPA 리포지토리 자동 설정도 같이 빼서 앱이 뜨는 경우다. 그 사례를 `spring.autoconfigure.exclude=<Hibernate JPA>,<Data JPA 리포지토리>`(쉼표로 한 값)로 바꾸고, 클래스 이름은 jar에서 확인. 가드 없이 이 설정으로 컨텍스트가 실제로 뜨는지 먼저 확인해 결과에 적는다 (안 뜨면 이 사례는 가드가 필요 없다는 뜻이니 빼고 이유를 적는다).
- [x] `test_hooks.py`는 그대로 받음.

