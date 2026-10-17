---
id: T-054
owner: codex
status: done    # todo | doing | blocked | done
scope:
  - docs/tasks/T-054-slice1-judgment-review.md
---

## 목표

PR #6(MVP 1번 조각)에서 오케스트레이터가 팀 리뷰 없이 내린 판단 세 가지를 검토한다. 네가 사용량 소진으로 빠진 동안 결정한 것들이다. 파일은 고치지 말고 `## 리뷰`에만 쓴다. 실험은 임시 복사본(`git worktree add ../nt-review origin/feat/tutor-profile` 등)에서만 하고, 공유 작업 트리에서는 gradle을 돌리지 않는다.

## 판단 1: 보호 하네스 변경 (사용자 승인함)

- **배경:** 엔티티와 저장소가 생기자 Flyway나 JPA를 뺀 설정이 가드보다 먼저 Hibernate 검증이나 빈 생성에서 실패했다. `DatabaseIntegrityIT` 음성 사례가 "다른 이유"로 실패한 것이다.
- **구현 담당의 첫 대응:** 운영 코드에 `DatabaseIntegrityEnforcer`를 넣었다. 오케스트레이터는 받지 않았다.
- **오케스트레이터가 한 것:**
  - `DatabaseIntegrityGuard`
    - `BeanFactoryPostProcessor`: 빈 정의 단계에서 `getBeanNamesForType(EntityManagerFactory|Flyway, true, false)`를 확인한다.
    - `BeanPostProcessor`: `LocalContainerEntityManagerFactoryBean` 초기화 전에 Flyway의 위치와 미적용 마이그레이션을 확인한다.
  - `DatabaseIntegrityIT.combinedJpaExclusionStartsWithoutGuard` 삭제. 이 통제 테스트 때문에 서비스가 저장소를 `ObjectProvider`로 받아야 했다.
  - 결과: `DatabaseIntegrityIT` 9/9 통과.
- **볼 것:**
  - 이 설계에 새 우회 경로가 있는가?
  - FactoryBean 타입 예측(`allowEagerInit=false`)이 깨질 수 있는 설정이 있는가?
  - 통제 테스트를 지워서 잃은 보장이 있는가? 있다면 대체 사례가 필요한가?

## 판단 2: 로컬 gitleaks 래퍼 (저장소 밖)

- **현상:** `~/.local/bin/gitleaks` 래퍼가 `wslpath`로 경로를 바꾸는데, Git Bash에는 `wslpath`가 없다. 빈 경로가 넘어가서 `verify.sh`의 `gitleaks dir "$snap"`이 스냅샷 대신 현재 폴더를 검사했다. 그러다 `backend/build/` 테스트 리포트의 Spring 임시 비밀번호 로그를 잡아 실패했다.
- **조치:** `wslpath`가 없으면 `cygpath -w`를 쓰도록 래퍼를 고쳤다.
- **볼 것:**
  - 이 진단이 맞는가?
  - 래퍼가 잘못돼도 스냅샷을 검사하도록 `verify.sh`(보호 파일)를 단단하게 할 필요가 있는가? 예: `(cd "$snap" && gitleaks dir . …)`.
  - 커밋 전 훅도 같은 영향을 받는가?
  - 고칠 거면 최소 변경안을 제시한다.

## 판단 3: T-051 리뷰와 재작업이 닫혔는지

- **경위:** T-051 리뷰는 네 대신 Claude 리뷰어가 했다. agy가 T-052·T-053에서 고쳤다.
- **볼 것:**
  - P1 4개가 실제로 닫혔는가? PATCH 검증 단일화, 변경 없으면 `updatedAt` 유지, Jackson 강제 변환으로 나던 500, 오류 형식.
  - 리뷰가 놓친 결함이 있는가?
  - 새로 생긴 게이트 우회가 있는가? 운영에서 닿지 않는 코드를 단위 테스트로만 덮는 패턴 등.

## 완료 조건

- [ ] 판단마다 "동의 / 수정 필요"와 근거(파일:줄). 수정이 필요하면 P0/P1/P2와 최소 변경안.
- [ ] PR #6 병합 전에 고칠 것과 후속 PR로 미룰 것을 구분한다.

## 참고

- `docs/tasks/T-050`~`T-053`, PR #6 본문, `backend/AGENTS.md`

## 리뷰

**판정:** PR #6의 현재 코드에서 P0/P1 재발은 찾지 못했다. 아래 P2는 후속 PR로 분리 가능하다. 판단은 로컬 PR 헤드 `0f77200`과 카드·코드의 정적 대조에 근거한다. 이 환경에서 GitHub PR 본문은 직접 읽지 못했고, 공유 작업 트리에서 Gradle은 실행하지 않았다.

### 판단 1 — 동의 (후속 P2: 가드의 대조 사례 보강)

- `DatabaseIntegrityGuard.java:41-47`은 빈 생성 전에 EMF·Flyway 정의가 없으면 가드 메시지로 실패시킨다. `:53-56`은 표준 `LocalContainerEntityManagerFactoryBean` 초기화 전에 Flyway 위치·적용 상태를 검사하고, `:61-135`는 컨텍스트가 뜬 뒤 실제 PostgreSQL/DataSource/Flyway/EMF 속성을 확인한다. `DatabaseIntegrityIT.java:71-90`의 6개 음성 설정은 단순 기동 실패가 아니라 가드 메시지를 요구한다. 서비스는 `TutorProfileService.java:27-36`에서 저장소를 필수 생성자 인자로 받는다. 현재 설정에서 새로 **통과하는** 우회는 찾지 못했다.
- `getBeanNamesForType(..., true, false)`는 타입을 미리 알 수 없는 사용자 정의 `FactoryBean`이나 factory method 설정에서 EMF가 실제로 만들어질 수 있어도 누락으로 판정할 수 있다. 이는 조용한 우회가 아니라 **거짓 실패**다. 현재 표준 JPA/Flyway 설정은 위 음성 테스트와 정상 IT가 기동함으로써 동작이 확인된다. [Spring `ListableBeanFactory` 문서](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/beans/factory/ListableBeanFactory.html)는 `allowEagerInit=false`에서 FactoryBean 제품 타입 검색이 제한됨을 명시한다. JPA 빈 생성 방식이 바뀔 때 이 전제와 BPP의 `LocalContainerEntityManagerFactoryBean` 타입 한정을 재검토해야 한다.
- 삭제된 `combinedJpaExclusionStartsWithoutGuard`는 “가드만 빼면 같은 설정이 정상 기동한다”는 대조 보장을 잃었다. 반면 현재 음성 테스트는 `causeChainContains("DB 무결성 위반")`을 요구하므로 다른 예외로 실패한 사례를 성공으로 세지는 않는다(`DatabaseIntegrityIT.java:81-99`). **P2 후속:** 운영 전체 앱과 무관한 경량 설정에서 가드의 유무만 바꾸는 대조 사례를 새 테스트 파일로 추가한다. 이를 위해 운영 의존성을 선택 주입으로 되돌리거나 PR #6을 막을 필요는 없다.

### 판단 2 — 수정 필요 (P2, 후속 보호 파일 PR)

- 진단은 맞다. 로컬 `~/.local/bin/gitleaks:3-11`은 존재하는 경로 인자를 `wslpath` 또는 `cygpath`로 변환한다. 이전 `wslpath` 실패로 빈 인자가 전달되면 `verify.sh:187-191`의 검사 대상이 스냅샷에서 현재 디렉터리로 바뀔 수 있다. 현재 래퍼의 `cygpath -w` 분기는 Git Bash 경로를 처리한다. `.githooks/pre-commit:3`도 같은 `verify.sh fast`를 호출하므로 같은 영향을 받는다. 별도 CI `gitleaks` 작업은 `.github/workflows/ci.yml:64-70`에 있다.
- 최소 변경안: 보호 파일 `scripts/verify.sh:191`을 `(cd "$snap" && gitleaks dir . --no-banner --redact) >&2 || fail "시크릿 탐지"`로 바꾼다. 래퍼가 경로를 빈 문자열로 만들어도 프로세스의 현재 디렉터리가 스냅샷이다. `scripts/test_verify.sh:8,63`의 가짜 gitleaks는 종료 코드만 확인하므로, 후속 PR에서 실행 위치가 스냅샷인지 검사하는 사례를 함께 추가한다. 임의의 *다른* 경로로 잘못 변환하는 래퍼까지 이 변경 하나로 막는다고 주장하지는 않는다.
- 로컬 래퍼 수정으로 현재 장애는 해소됐고, PR #6 CI에는 별도 gitleaks 검사가 있으므로 이 보호 파일 변경은 **후속 PR**로 둔다. 보호 목록은 `.github/CODEOWNERS:3`이며 사용자 코드오너 리뷰가 필요하다.

### 판단 3 — 동의 (P1 네 건 닫힘, 후속 P2 테스트 품질)

- **PATCH 단일 검증:** 현재 상태를 JSON으로 만들고 보낸 필드를 병합한 후 `validateCreate`를 호출한다(`TutorProfileService.java:122-238`). 검증기는 중복 코드와 타입 오류를 거부한다(`TutorProfileValidator.java:43-113,177-229`). 비객체 본문·숫자 이름·객체 원소·소수 금액의 단위 테스트도 있다(`TutorProfileServiceTest.java:188-197,271-291`).
- **무변경 시각:** 새 값들을 값으로 비교하고 변경이 있을 때만 `updatedAt`을 설정한다(`TutorProfileService.java:265-290`, `TuitionVo.java:49-57`). 빈 PATCH·동일 값 단위 테스트(`TutorProfileServiceTest.java:200-225`)와 실제 HTTP 인수 테스트(`TutorProfileAcceptanceIT.java:354-380`)가 이를 확인한다.
- **Jackson 500:** 문자열·정수에 `isString`·`isInt`를 먼저 적용한다(`TutorProfileValidator.java:43-47,90-103,183-187,205-224`; visibility는 `TutorProfileService.java:300-307`). 객체·배열 원소·`1.5`·`1e12` 거부 단위 사례가 있다(`TutorProfileValidatorTest.java:331-457`). 검증 후 값 변환이 이뤄진다(`TutorProfileService.java:43-71,238-263`).
- **오류 형식:** MVC 기본 예외는 `ResponseEntityExceptionHandler`의 `handleExceptionInternal`에서 `{code,message}`로 바꾸고, 미처리 예외도 일반 메시지로 감싼다(`GlobalExceptionHandler.java:14-15,53-92`). `GlobalExceptionHandlerTest.java:148-242`는 예외 객체를 직접 주입해 400/404/405/415/기타 4xx/500을 확인한다. 다만 실제 HTTP에서 깨진 JSON·415·405를 거쳐 이 advice에 닿는지는 이 테스트가 검증하지 않는다.
- **잔여 P2 (게이트 품질):** `TutorProfileRepositoryImplTest.java:25-28,35-67`은 가짜 `EntityManager`에 JPQL 문자열과 파라미터를 단언한다. 이 메서드는 실제 검색 경로에서 호출되지만 이 단위 테스트만으로 JPQL 실행 의미를 보장할 수 없다. 인수 테스트의 필터·커서 확인(`TutorProfileAcceptanceIT.java:574-819`)이 실제 DB 동작을 별도로 검증한다. `TuitionDto.from(null)`(`TuitionDto.java:8-11`)과 빈 Set을 직접 건네는 저장소 분기(`TutorProfileRepositoryImpl.java:30-38,52-60`; `TutorProfileRepositoryImplTest.java:91-102`)는 정상 서비스 입력에서 닿지 않아 커버리지 수치에 기여하는 방어 분기다. **후속:** 실제 HTTP 오류 형식 IT를 새 파일로 추가하고, 불필요한 방어 분기·직접 호출 단위 테스트를 정리한다. 기존 테스트 수정은 `quality/test-removals.txt` 등록 절차를 따른다.

**병합 전:** 이 리뷰에서 요구하는 추가 수정 없음. **후속:** 가드 대조 사례, 스냅샷에서 gitleaks 실행하도록 하네스 보강과 경로 테스트, HTTP 오류 형식 IT 및 불필요한 방어 분기 정리. 후속 보호 파일·기존 테스트 변경은 카드와 코드오너 절차가 필요하다.

## 결과

- 변경 파일: 이 카드의 상태와 `## 리뷰`만.
- 검증: 로컬 PR 헤드·T-045/T-050~053·운영/테스트 코드·로컬 gitleaks 래퍼 정적 대조. 공유 트리 Gradle 미실행.
- 남은 일: 후속 P2 항목은 별도 카드/PR에서 처리.
