---
id: T-051
owner: codex    # Codex 사용량 소진으로 Claude 리뷰어가 대신 수행
status: done    # todo | doing | blocked | done
scope:
  - docs/tasks/T-051-tutor-profile-impl-review.md
---

## 목표

agy가 구현한 T-050(선생님 프로필 + 검색, MVP 1번 조각)과 오케스트레이터의 `DatabaseIntegrityGuard` 수정을 리뷰한다. 파일은 고치지 말고 `## 리뷰`에만 쓴다. 공유 작업 트리에서 gradle을 돌리지 않는다. 실험은 임시 복사본(`git worktree` 등)에서만.

## 대상 (작업 트리, 아직 커밋 전)

- 새 파일: `backend/src/main/java/kr/needteacher/{tutorprofile,common}/`, `backend/src/main/resources/db/migration/V1__create_tutor_profiles.sql`, `backend/src/test/java/kr/needteacher/{tutorprofile,common}/`
- `git diff`: `SecurityConfig.java` (matcher 추가), `backend/src/test/java/kr/needteacher/architecture/DatabaseIntegrityGuard.java` (보호 파일)
- 계약: `docs/tasks/T-045-tutor-profile-acceptance.md`, 인수 테스트 `backend/src/acceptance/java/.../TutorProfileAcceptanceIT.java` (실행 사본과 sha256 동일 확인함)

## 가드 수정 배경

엔티티가 생기자 `DatabaseIntegrityIT`의 음성 사례 3개(Flyway 끔·위치 바꿈 등)가 가드보다 먼저 Hibernate 스키마 검증에서 실패해 "다른 이유"로 통과하지 못했다. agy는 운영 코드에 `DatabaseIntegrityEnforcer`(BPP)를 넣어 맞췄고, 오케스트레이터는 그걸 빼고 테스트 가드에 `postProcessBeforeInitialization`(EMF 빈 초기화 전 Flyway 확인)을 넣었다. `DatabaseIntegrityIT` 10/10 통과.

## 오케스트레이터가 본 의심 지점 (확인·반박해 줘)

1. `TutorProfileService`·`TutorProfileRepositoryImpl`의 생성자 두 개 + `ObjectProvider.getIfAvailable()`: 운영에서 의존성이 null로 조용히 들어갈 수 있다. 커버리지용 구조인지.
2. 생성 경쟁: `existsByOwnerId` 후 `save` → `owner_id UNIQUE` 위반이 500(형식 다른 본문)이 되는지.
3. 오류 형식 누락: 깨진 JSON·본문 없음·Content-Type 오류·예상 못 한 예외가 `{code,message}` 형식이 아닌 Spring 기본 본문으로 나가는지.
4. PATCH 병합 로직이 서비스에 검증을 일부 복제하고, 코드 목록 파싱 오류는 필드 하나만 즉시 던져 생성과 오류 집계 방식이 다른 점.
5. 커서: 필터와 묶이지 않음, `e.getMessage()`를 응답 메시지에 붙임, 시각 정밀도(DB 마이크로초 vs `Instant`)로 경계 행이 빠지거나 중복될 여지.
6. `SecurityConfig`: `GET /api/v1/tutor-profiles/*` 공개가 앞으로 생길 단일 세그먼트 GET까지 공개하는 점, `HttpMethod` FQCN.
7. T-049 P1(공개 GET 200, 익명 쓰기 401, PARENT·타인 403, TEACHER+CSRF 성공)이 실제 경로 HTTP 테스트로 닫혔는지.
8. 가드 BPP: EMF 초기화 중 `getBean(Flyway)`·`flyway.info()` 호출의 부작용, JPA 없는 컨텍스트, 우회 경로.

## 완료 조건

- [ ] 위 8개 각각 판정 (근거는 파일:줄). 그 밖에 찾은 결함도.
- [ ] 품질 게이트 우회(뮤턴트 회피용 구조, 의미 없는 단언 등)가 있으면 지적.
- [ ] P0/P1/P2와 "이대로 PR 올려도 됨 / 고쳐야 함" 판정. 고쳐야 하면 agy에게 줄 재작업 목록을 우선순위대로.

## 참고

- `docs/tasks/T-045-…`, `T-049-…`, `T-050-tutor-profile-impl.md`, `docs/proposals/mvp.md` F2·F4, `backend/AGENTS.md`

## 리뷰

**판정: 고쳐야 함** (P0 없음, P1 4개). 코드만 읽고 판정했고 실행은 하지 않았다. Jackson 동작은 클래스패스의 `jackson-databind-3.1.5` 소스로 확인했다.

| # | 판정 | 근거 | 등급 |
|---|---|---|---|
| 1 | 부분 | 커버리지용은 아니다. 보호 테스트 `DatabaseIntegrityIT.combinedJpaExclusionStartsWithoutGuard`가 JPA 없는 컨텍스트 기동을 요구해서 생긴 `ObjectProvider`다. 두 번째 생성자는 단위 테스트용이다. 운영에서 null이 되면 기동은 되고 첫 요청에서 NPE가 난다 | P2 |
| 2 | 맞음 | `Service:47` 확인 → `:100` 저장. 경쟁 시 UNIQUE 위반이 처리기 없이 500(Boot 기본 본문)으로 나간다 | P2 |
| 3 | 맞음 | `HttpMessageNotReadable`·`HttpMediaTypeNotSupported`·`NoResourceFound`·`Exception` 처리가 없다. Jackson 3에서 객체·배열 노드에 `asString()`, int 범위 밖 수에 `asInt()`를 부르면 예외가 나서 `PATCH {"displayName":{}}`, `{"subjectCodes":[{}]}`, `visibility:{}`, `amountWon:1e12`가 모두 500이다 | P1 |
| 4 | 맞음 | `validateMerged`가 생성 규칙을 다시 쓴 사본이고 개수만 본다. PATCH 중복 코드가 `LinkedHashSet`에서 조용히 합쳐져 `["MATH","MATH"]`가 200(T-045 "중복 불가" 위반). `displayName: 123`도 통과 | P1 |
| 5 | 부분 | 정밀도는 문제없다(세 곳 모두 `truncatedTo(MICROS)`, 비교·정렬은 DB). `CursorEncoder:42`가 `e.getMessage()`를 노출한다. 형식만 맞는 위조 커서는 200 | P2 |
| 6 | 맞음(잠재) | `GET /api/v1/tutor-profiles/*`가 앞으로 생길 단일 세그먼트 GET을 전부 공개한다. FQCN은 스타일 문제 | P2 |
| 7 | 닫힘 | 인수 IT가 실제 필터 체인에서 익명 GET 200, 익명 쓰기 401, PARENT·타인 403, TEACHER+CSRF 성공과 403 본문 code까지 단언한다 | - |
| 8 | 문제없음 | BPP는 EMF `afterPropertiesSet`보다 먼저 돈다. Flyway는 그 전에 끝나 `info()`는 읽기만 한다. JPA 없는 컨텍스트에서는 refresh 시점 검사가 막는다. 새 우회 경로는 없다 | - |

그 밖의 결함:
- P1: 변경 없는 PATCH도 `updatedAt`을 올린다(`Service:253`). T-045 "변경이 없으면 유지" 위반이고, 이걸로 검색 맨 앞으로 끌어올릴 수 있다. 배열 본문 `[1]`도 200.
- P2: visibility를 같은 값으로 보내도 `updatedAt`이 갱신된다. 응답이 계약 `{id, visibility, updatedAt}`이 아니라 소유자 뷰 전체다.
- P2: 검색 N+1. LAZY 컬렉션 3개에 batch 설정이 없어 limit 50이면 쿼리가 약 151개 나간다. EXISTS를 쓰므로 `DISTINCT`도 필요 없다.
- P2: 경로 UUID 형식 오류가 `INVALID_QUERY`로 나간다. 공통 `ConflictException`의 code가 `PROFILE_ALREADY_EXISTS`로 고정돼 있다.
- P2 (게이트 우회): 운영에서 닿지 않는 코드를 단위 테스트로만 덮는다.
  - `GlobalExceptionHandler`의 보안 예외 처리와 `MethodArgumentNotValid` 처리
  - `TuitionDto.toVo()`
  - 반쪽 커서 분기 (`RepositoryImpl:50,72`)
  - 목 EntityManager에 JPQL 부분 문자열만 단언하는 테스트, `isNotNull()`만 보는 컨트롤러 테스트
- P2: 거의 같은 코드 파싱 메서드가 9개다(`Service:318-442`).

오케스트레이터 확인: T-045:26 문구, `validateMerged` 개수 검사, Jackson 3.1.5 `BaseJsonNode.asString()`(컨테이너는 `_asString()==null` → 예외), `DatabaseIntegrityIT` 통제 사례를 직접 대조함. 재작업은 T-052.

## 결과

- Claude 리뷰어가 수행(Codex 사용량 소진). 판정은 "고쳐야 함", P1 4개(PATCH 검증 불일치, 타입 오류 500, 오류 형식 누락, 변경 없는 PATCH의 `updatedAt` 갱신).
- 재작업은 T-052·T-053 (agy). `ObjectProvider`는 사용자 결정으로 보호 테스트를 고쳐 없앴다. 가드에 빈 정의 단계 검사를 넣고 `DatabaseIntegrityIT`의 통제 테스트를 지웠다(오케스트레이터, 잠금 해제 중).
