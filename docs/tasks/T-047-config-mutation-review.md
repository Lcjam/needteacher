---
id: T-047
owner: codex
status: done    # todo | doing | blocked | done
scope:
  - docs/tasks/T-047-config-mutation-review.md
---

## 목표

agy의 T-046 설계(설정 클래스 뮤테이션 처리)와 오케스트레이터 수정안을 리뷰한다. 하네스 보호 파일 변경이 걸려 있으니, 구멍이 생기는지 중심으로 본다. 파일은 고치지 말고 `## 리뷰`에만 쓴다.

## 오케스트레이터 수정안

T-046 실측 결과: `SecurityConfig.securityFilterChain`의 `return http.build()`에서 `NULL_RETURNS` 변이가 하나 생긴다. 단위 테스트로 죽이려면 deep-stub 목밖에 없고, 이는 실제 규칙을 검증하지 않는다. agy 추천안 (b)를 다음처럼 고친다.

1. **제외는 이름 패턴이 아니라 정확한 클래스 목록으로 한다.** agy 안의 `*Config`·`**.*Configuration` 패턴이면 로직 클래스 이름만 바꿔 관문을 피할 수 있다. 목록에 넣으려면 보호 파일을 고쳐야 하므로 매번 사용자 승인이 필요하다.
2. **뮤테이션만 면제하고 JaCoCo line/branch는 유지한다.** IT가 커버리지에 합산되므로, 설정 클래스도 인수 테스트(T-045)가 실제 요청으로 덮어야 한다. agy 안은 JaCoCo에서도 뺐다.
   - `quality_gate.py`에 `EXCLUDED`(전부 제외)와 별도로 `MUTATION_EXEMPT`(뮤테이션만 면제) 집합을 둔다.
   - `build.gradle.kts` pitest의 `excludedClasses`와 일치시킨다.
3. **ArchUnit 규칙은 바이트코드로 확인할 수 있는 것만 둔다.** "분기문 금지"는 ArchUnit으로 구현할 수 없다.
   - 면제 목록의 클래스는 `@Configuration`이 붙어 있어야 한다.
   - 선언 메서드는 생성자, `@Bean` 메서드, 컴파일러가 만든 람다(synthetic)만 허용한다.
   - 필드는 `final`이어야 한다.
   - 람다 안에 숨긴 로직은 2번의 IT 커버리지로 잡는다.
4. **로직은 별도 클래스로 빼고 단위 테스트로 100% 죽인다:** `401` JSON 진입점, 그리고 agy 안에 빠진 `403` JSON `AccessDeniedHandler`.
5. **CSRF는 직접 만든 `CsrfCookieFilter` 대신 Spring Security 7 내장 `csrf.spa()`를 우선 쓴다.** 쿠키가 첫 응답에 실리는지는 구현에서 테스트로 확인하고, 안 되면 그때 필터를 둔다.

## 완료 조건

- [x] 수정안 1~5 각각 동의/반대와 이유. 더 단순하거나 더 안전한 대안이 있으면 제시.
- [x] 남는 우회 경로: 면제 클래스에 로직을 숨기는 방법이 여전히 있는가 (람다, 정적 중첩 클래스, `@Bean` 메서드 안의 분기 등). 각각 무엇이 잡는지, 못 잡으면 P0인지.
- [x] 보호 파일 변경 범위 확정: `backend/build.gradle.kts`, `scripts/quality_gate.py`(+ 자체 검사 사례), `architecture/` 새 ArchUnit 규칙, `backend/AGENTS.md` 테스트 절 문구. 각 파일의 변경을 diff 수준으로 적는다.
- [x] 우선순위 P0/P1/P2.

## 참고

- `docs/tasks/T-046-security-config-mutation.md` (agy 실측·설계)
- `scripts/quality_gate.py`(`EXCLUDED` 사용처), `backend/build.gradle.kts` pitest·jacoco 블록, `backend/src/test/java/kr/needteacher/architecture/`
- T-045 인수 테스트가 `401`/`403` JSON과 `.with(csrf())`를 전제로 한다.

## 리뷰

### 판정

수정안 1·2·4·5에 동의하고, 3은 **더 좁은 구조 규칙으로 보강할 때** 동의한다. T-046의 실측은 `http.build()` 반환값 변이 1개를 목으로 죽여도 URL 권한 규칙을 검증하지 못함을 보여준다. 따라서 정확히 한 설정 클래스의 PIT 면제는 합리적이다. 그러나 현재 `quality_gate.py`는 `sourceFile`로 뮤턴트를 묶는다. `SecurityConfig.java` 파일 전체를 면제하면 동일 파일의 중첩·추가 최상위 클래스까지 검사에서 빠지는 것이 핵심 P0 구멍이다. 또한 JaCoCo 90/85는 보안 정책의 **의미**를 보장하지 않는다.

| 수정안 | 판정·우선순위 | 이유와 보강 |
|---|---|---|
| 1. 정확한 클래스 목록 | 동의, P0 | `*Config` 패턴은 이름 변경만으로 면제 확대가 가능하다. Gradle에는 `kr.needteacher.security.SecurityConfig` 하나만 넣고, Python 목록에도 대응하는 정확한 경로 하나만 넣는다. 목록의 증가는 보호 파일 리뷰를 거친다. 단, 정확한 **클래스** 목록과 파일 단위 집계 사이의 차이를 3번의 동일 소스 파일 검사로 막아야 한다. |
| 2. 뮤테이션만 면제, JaCoCo 유지 | 조건부 동의, P0 | `measuredClasses`에서 설정 클래스를 제외하지 않는다. `quality_gate.py`에서도 line/branch 검사와 JaCoCo 누락 검사를 먼저 수행한 후 **해당 파일에 한해** mutation 판정만 건너뛴다. 단순 커버리지로 `permitAll`/역할/CSRF 규칙의 정확성을 증명할 수 없으므로 실제 HTTP 보안 테스트를 추가해야 한다. |
| 3. 바이트코드로 확인 가능한 ArchUnit | 보강 필요, P0 | `@Configuration`·메서드·필드 검사 방향은 맞지만 `final` 필드에는 가변 객체를 둘 수 있고, 생성자와 `@Bean` 본문에는 로직을 숨길 수 있다. 더 단순하게 **필드 0개, 무인자 생성자 1개, 허용된 `@Bean securityFilterChain` 1개**로 한정한다. 엔트리포인트·거부 핸들러는 `@Bean` 메서드 인자로 받는다. 동일 `.java` 소스에서 컴파일된 다른 최상위/중첩 클래스가 없어야 한다. 람다 synthetic 메서드는 컴파일러 산물이므로 허용하되 그 동작을 HTTP 테스트로 고정한다. `if/switch` 금지를 ArchUnit API만으로 주장하지 않는다. |
| 4. 401·403 핸들러 분리 | 동의, P0 | `AuthenticationEntryPoint`와 `AccessDeniedHandler`를 각각 별도 운영 클래스·별도 단위 테스트로 두어 JSON 상태/헤더/본문의 변이를 죽인다. T-045는 `401`/`403` 본문을 요구하지만 권한별 **모든** 경로와 CSRF 실패를 다루지는 않는다. 핸들러 단위 테스트와 HTTP 통합 테스트를 함께 둔다. |
| 5. `csrf.spa()` 우선 | 동의, P1 | Spring Security 7 공식 문서가 SPA용 `.csrf(csrf -> csrf.spa())`를 제공한다. T-046의 `CsrfTokenRequestAttributeHandler` 단독 설정은 평문 쿠키 토큰과 BREACH 처리의 차이를 직접 다뤄야 한다. 내장 DSL을 우선 적용하고 첫 안전 요청·로그인 성공·로그아웃 뒤 새 쿠키와 `X-XSRF-TOKEN` 왕복을 검증한다. 동작이 부족하다는 실측 뒤에만 필터를 추가한다. [Spring Security 7 CSRF 문서](https://docs.spring.io/spring-security/reference/7.0/servlet/exploits/csrf.html#csrf-integration-javascript-spa). |

### 남는 우회 경로와 판정

| 우회 | 현재 제안이 잡는가 | 필요한 검사 / 잔여 위험 |
|---|---|---|
| `SecurityConfig.java`에 `SecurityConfig$Inner`, 익명 클래스, 다른 최상위 로직 클래스를 둠 | **못 잡음 (P0)**. PIT의 클래스 제외는 정확해도 `quality_gate.py`의 mutation 점수는 `sourceFile` 기준이고 `MUTATION_EXEMPT`가 파일 전체를 건너뛴다. | ArchUnit에서 **면제 소스 파일에 대응하는 운영 클래스가 정확히 `SecurityConfig` 하나**인지 검사한다. `SecurityConfig`의 선언 중첩 클래스도 금지한다. 컴파일러 생성 람다 메서드는 별도 `.class`가 아니므로 대상에서 구분한다. |
| 허용한 synthetic 람다에 권한 분기·우회 로직을 둠 | `@Bean`/필드 구조 검사로는 못 잡음. JaCoCo 90/85는 미실행 분기 일부를 허용한다. **보안 경로 테스트가 없으면 P0**. | 공개 GET·익명 쓰기 401 JSON·학부모/타인 403 JSON·작성자+CSRF 성공·CSRF 누락/오류 403·숨김 상세 404를 실제 필터 체인에서 테스트한다. 새 matcher/역할이 생길 때 그 경로의 성공/거부 테스트를 함께 추가한다. 여전히 테스트되지 않은 의미 변경 가능성은 남으므로 면제 목록·설정 변경을 사람 리뷰 대상으로 둔다. |
| `@Bean` 메서드 본문에서 `if/switch` 또는 부작용 실행 | **못 잡음 (P0)**. ArchUnit은 일반적인 자바 제어 흐름 분석기가 아니다. | 한 `SecurityFilterChain` 빈의 선언적 DSL 배선만 허용한다는 구조/리뷰 규칙과 위 HTTP 테스트가 필요하다. 기계적 무분기 보장이 꼭 필요하면 ASM으로 `IF*`/`TABLESWITCH`/`LOOKUPSWITCH`를 별도 검사하되 컴파일러 생성 분기를 구별하는 비용이 든다. 더 강한 대안은 해당 설정만 PIT에서 IT를 실행하는 것으로, 빌드 시간이 늘어난다. |
| `final` 필드/생성자에 가변 정책·부작용 배치 | **일부만 잡음 (P1)**. `final`은 참조 재할당만 금지한다. 바이트코드만으로 암시적 기본 생성자와 직접 작성한 무인자 생성자를 일반적으로 구별하지 못한다. | 필드와 생성자 인자를 금지하고 프레임워크/핸들러를 빈 메서드 인자로 받는다. 무인자 생성자 본문에 숨긴 부작용은 이 ArchUnit 규칙으로 보장하지 못하므로 변경 리뷰 및 실제 요청 테스트가 필요하다. |
| Gradle 제외와 Python 면제 목록을 서로 다르게 유지 | **못 잡음 (P0)**. 한쪽만 바뀌면 PIT 점수 또는 파일 게이트가 엉뚱하게 통과/실패한다. | 보호 파일 두 목록의 정확한 일치를 CI에서 비교하고 불일치 시 실패한다. PIT XML에 면제 클래스 변이가 나오면 실패하며, 같은 소스의 비면제 클래스가 있으면 실패한다. |
| `fast`에서 변경 클래스가 전부 PIT 제외 대상이라 뮤턴트가 0개 | 현재 `failWhenNoMutations=true` 때문에 **실패한다 (P1)**. 보안 패키지의 핸들러까지 함께 타깃이면 실제 뮤턴트가 생기지만 항상 보장되지는 않는다. | 이 경우를 자체 검사·스냅샷 시험에 넣는다. 일반적인 면제 확장을 허용할 계획이면 `scripts/verify.sh`의 PIT 호출 조건도 변경해야 한다. 지금은 `SecurityConfig`와 비면제 핸들러가 같은 `security` 패키지에 있다는 전제를 명시하고 실제 `fast/full`을 검증한다. |

### 보호 파일 변경 범위: 구현 카드에 넣을 diff 수준

1. **`backend/build.gradle.kts` (보호):** `measuredClasses`의 JaCoCo 제외는 `BackendApplication.class` **그대로** 둔다. `pitest.excludedClasses`만 현재 `BackendApplication`에 **정확히** `kr.needteacher.security.SecurityConfig`를 더한다. `targetClasses`, `targetTests`, 기준치, `excludedTestClasses`는 유지한다. `spring-boot-starter-security` 의존성 추가는 기능 구현 쪽 변경으로 별개다.

   ```diff
   -excludedClasses.set(setOf("kr.needteacher.BackendApplication"))
   +excludedClasses.set(setOf("kr.needteacher.BackendApplication", "kr.needteacher.security.SecurityConfig"))
   ```

2. **`scripts/quality_gate.py` (보호):** `EXCLUDED`는 `BackendApplication`만 유지하고 `MUTATION_EXEMPT = {"kr/needteacher/security/SecurityConfig"}`를 추가한다. `problems()`에서 `cov_files` 조회 및 LINE/BRANCH 임계값 검사를 마친 **뒤**, mutation 리포트 존재를 확인한다. `f in MUTATION_EXEMPT`면 그 파일의 mutation 80%·0개 예외 검사만 생략한다. `mutation_files()`에 `mutatedClass` 목록을 보존하여 면제 대상 변이가 실제 리포트에 나타나거나 같은 소스 파일의 다른 클래스 변이가 면제 뒤 숨으면 실패한다. 빌드 설정의 `excludedClasses`와 두 목록을 대조해 불일치 시 실패시키는 검사도 넣는다. `--self-test`에는 (a) 면제 파일 LINE 89% 실패, BRANCH 84% 실패, 90/85 통과, (b) 일반 파일 mutation 79% 실패, (c) 같은 파일 `SecurityConfig$Inner` 또는 별도 최상위 클래스 변이 발견 시 실패, (d) 목록 불일치·mutation XML 누락 시 실패 사례를 추가한다. `EXCLUDED`를 확장하지 않는다.

3. **`backend/src/test/java/kr/needteacher/architecture/ConfigurationIntegrityTest.java` 신규 (보호):** 운영 클래스 전체를 import한다. 면제 목록의 정확한 FQCN `kr.needteacher.security.SecurityConfig` 존재를 확인하고 그 클래스에 `@Configuration`이 직접 붙었는지 본다. `SecurityConfig.java`에서 나온 모든 운영 클래스의 FQCN 집합이 이 하나뿐인지 검사한다(같은 파일의 다른 최상위·중첩·익명 클래스 금지). 허용 클래스의 선언 필드 0개, **무인자 생성자 1개만**, 비 synthetic 선언 메서드는 정확히 `@Bean SecurityFilterChain securityFilterChain(...)` 하나인지 검사한다. 무인자 생성자의 본문이 비어 있다는 뜻은 아니므로 변경 리뷰가 필요하다. 람다 synthetic 메서드는 허용하되 보안 HTTP 테스트로 검증한다. 이 규칙이 `test` 태스크의 ArchUnit 엔진에서 실제로 실행되는지 확인한다. `@Configuration` 전체를 무차별 면제하지 않는다.

4. **`backend/AGENTS.md` 테스트 절:** `*Test` 중심 PIT 원칙 뒤에 다음 문구를 추가한다. “`SecurityConfig`만 PIT 면제이며 JaCoCo line/branch와 실제 필터 체인 IT는 유지한다. 같은 소스 파일에 다른 클래스를 두지 않고, 설정 클래스의 필드·직접 작성한 생성자를 두지 않는다. 401/403 핸들러는 별도 클래스·단위 테스트로 검증한다. 새 경로/역할/CSRF 정책에는 허용·거부 HTTP 테스트를 함께 추가한다. 면제 목록 변경은 보호 파일 리뷰가 필요하다.” 기존 점수 기준·DB 하네스 문구는 유지한다.

위 네 파일 외 **조건부**로 `scripts/verify.sh`(보호)가 필요하다. `fast`가 면제 대상만 변경된 경우 PIT 타깃에서 뮤턴트 0개로 실패한다는 실측이 나오면, 면제 대상만인 경우 PIT를 건너뛰는 좁은 분기를 넣고 `full`의 전체 PIT·뮤턴트 0건 실패는 유지해야 한다. 이 변경은 별도 보호 파일 리뷰 범위에 명시해야 한다.

### 우선순위

- **P0:** 파일 단위 면제 구멍 차단, JaCoCo/목록 일치 검사, 빈 내부·람다 보안 정책의 HTTP 테스트, 401/403 별도 핸들러 검증.
- **P1:** `csrf.spa()` 쿠키 발급·재발급/헤더 왕복 테스트, 면제 클래스만 변경된 `fast` 동작 실측, 필드/생성자 금지.
- **P2:** 필요할 때 설정 클래스만 IT 기반 PIT로 돌리는 대안 재검토(실행 시간 증가를 수용할 경우).

## 결과

- 변경: `docs/tasks/T-047-config-mutation-review.md`에 1~5 판정, 남는 우회, 보호 파일별 수정 범위 작성.
- 검증: T-046 실측, 현재 Gradle/`quality_gate.py`/`verify.sh`/ArchUnit 코드를 대조; Spring Security 7 공식 CSRF 문서와 로컬 `CsrfConfigurer.spa()` API 확인.
- 남은 일: 오케스트레이터가 P0 보강을 구현 카드와 보호 파일 리뷰에 반영. 이 카드에서 운영 코드·하네스는 수정하지 않음.
