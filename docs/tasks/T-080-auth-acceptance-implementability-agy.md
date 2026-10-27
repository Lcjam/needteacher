---
id: T-080
owner: agy
status: done    # todo | doing | blocked | done
scope:
  - docs/tasks/T-080-auth-acceptance-implementability-agy.md
---

## 목표

Codex가 쓴 인수 테스트(`backend/src/test/java/kr/needteacher/account/`, T-073)를 네가 구현하게 된다. 승인되면 한 글자도 못 고치므로, 지금 **구현 쪽에서 막힐 곳**만 찾는다. 테스트를 약하게 하자는 제안은 하지 않는다(커버리지·단언 강도는 다른 리뷰어 T-079가 본다). 코드는 고치지 않는다.

## 볼 것

1. **계약 밖 강제:** 테스트가 T-073 `## 계약`에 없는 구현 세부를 요구하는 곳(예: 계약에 없는 응답 필드·헤더·메일 제목·테이블 컬럼, 특정 클래스·빈 이름, 특정 SQL). 계약에 근거가 있으면 지적하지 않는다.
2. **구현 불가·비결정:** Spring Boot 4.1 / Security 7.1 / 톰캣 / PostgreSQL에서 계약대로 구현해도 통과할 수 없는 단언, 실행마다 결과가 달라질 수 있는 단언(타이밍, 메일 도착, 동시성, 쿠키 처리).
3. **헬퍼와 운영 코드의 충돌:** 테스트 헬퍼(`AuthTestConfiguration`, `AdjustableClock` 등)가 만드는 빈·속성이 운영 코드의 자연스러운 구현과 부딪히는 곳(빈 이름·타입 중복, `@Primary` 범위, 속성 이름).

## 완료 조건

- [x] `## 리뷰`에 지적마다 P0(계약대로 구현해도 통과 불가)·P1·P2 + `파일:줄` + 근거(공식 문서·바이트코드·계약 줄) + 최소 수정안.
- [x] 지적이 없으면 "없음"과 확인한 범위.
- [x] 결과 10줄 이내.

## 참고

T-073, T-077(구현 카드 초안), `docs/research/auth.md`.

## 리뷰

**지적 사항: 없음 (P0 0건, P1 0건, P2 0건). 이대로 원본 확정·잠금 가능.**

### 확인한 범위

1. **계약 밖 강제 여부 (`AuthAcceptanceIT.java:1-672`, `AuthMailInbox.java:1-65`):**
   - **엔드포인트·응답·오류 형식:** 6개 경로, 상태 코드, JSON 필드(`result`, `account.{id,email,role,createdAt}`, `code`, `message`, `fieldErrors[].field`), `Location: /api/v1/me`, `Retry-After` 정수 초 모두 `T-073:26-37,72` 계약 범위 안이다. (`AuthAcceptanceIT.java:94-96`의 기존/신규 이메일 `202` 응답 `Content-Type` 헤더 비교도 양쪽 모두 빈 본문 `ResponseEntity.accepted().build()`로 응답하면 둘 다 `Optional.empty()`로 일치한다.)
   - **메일 형식 (`AuthMailInbox.java:43-52`):** 수신 주소(`To[0].Address`)와 본문(`Text` 또는 `HTML`)에 6자리 숫자(`(?<!\d)\d{6}(?!\d)`)가 정확히 1개 있는지만 검사하며, 계약에 없는 메일 제목이나 문구를 강제하지 않는다(`T-073:62`).
   - **DB 관찰 (`AuthAcceptanceIT.java:265-280,567-569`):** `email_codes` 테이블의 `email` 컬럼만 조건으로 써서 행 개수(`COUNT(*)`)와 `SELECT row_to_json(c)::text FROM email_codes c WHERE email = ?`에 평문 6자리 코드가 포함되지 않는지만 읽기 전용으로 확인한다(`T-073:77-83`). 해시를 Base64/Hex로 저장하고 기준 시각(`2030-01-...T00:00:00Z`)을 쓰므로 다른 컬럼 값과 우연히 겹칠 위험도 없다.

2. **구현 가능성·결정성 (`AuthAcceptanceIT.java`, `AuthHttpClient.java`, `AdjustableClock.java`):**
   - **시간·격리 (`AuthAcceptanceIT.java:68-72,240-258`):** 테스트마다 고유 이메일(`UUID`)과 3일 간격의 초기 시각을 부여해 테스트 간 발송 이력·시계 간섭이 없다. 24시간 경계(`AuthAcceptanceIT.java:256`, `firstSend.plus(Duration.ofDays(1))`)도 `sent_at > now - 24h` 조건과 정확히 일치한다(`T-073:70`).
   - **동시성 (`AuthAcceptanceIT.java:195-224`):** `CountDownLatch(1)`로 2개 스레드를 동시에 출발시키고 두 응답 상태 코드 정렬이 `[200, 400]`인지만 단언해 순서 비결정성이 없다. PostgreSQL `READ COMMITTED` + `SELECT ... FOR UPDATE`(또는 조건부 `DELETE`)로 결정적으로 통과 가능하다.
   - **쿠키·세션·CSRF (`AuthHttpClient.java:84-93`, `AuthAcceptanceIT.java:284-314,401-413,616-622`):** `HttpCookie.parse`로 `Max-Age=0` 삭제 쿠키와 신규 쿠키 덮어쓰기를 모두 처리하며, `setCookie()` 헬퍼도 빈 값(`name=;`)을 걸러내고 마지막 유효 쿠키를 읽는다. 톰캣 `request.changeSessionId()` / `request.getSession(true)`와 `CookieCsrfTokenRepository.withHttpOnlyFalse()` 조합으로 모든 단언을 통과할 수 있다.

3. **헬퍼와 운영 코드의 충돌 여부 (`AuthTestConfiguration.java:11-31`):**
   - **시계 빈 이름·타입 (`AuthTestConfiguration.java:14-18`):** 테스트 시계 빈 메서드가 `@Bean @Primary Clock authTestClock()`으로 선언되어 있어, `T-077:36`의 운영 빈(`@Bean Clock systemClock()`, 또는 `clock()`)과 빈 이름이 겹치지 않으며 `@Primary`로 `AuthAcceptanceIT.java:53` 및 운영 서비스에 단일 주입된다.
   - **메일 컨테이너·속성 (`AuthTestConfiguration.java:20-31`):** `authMailpitContainer`와 `authMailProperties`(`spring.mail.host`, `spring.mail.port`)만 등록하므로 운영 `application.properties` 기본값 및 `TestIntegrityTest`·`DatabaseIntegrityGuard`·`quality_gate.py`와 충돌하지 않는다.

## 결과

- 확인 대상: `backend/src/test/java/kr/needteacher/account/` 5개 파일(`AuthAcceptanceIT.java` 20개 테스트, `AdjustableClock.java`, `AuthTestConfiguration.java`, `AuthHttpClient.java`, `AuthMailInbox.java`) 및 `T-073`·`T-077`.
- 1(계약 밖 강제), 2(구현 불가·비결정), 3(헬퍼·운영 코드 충돌) 전 항목 점검 결과 막히는 지점 **없음**(P0/P1/P2 0건).
- `AuthTestConfiguration`의 시계 빈(`authTestClock`, `@Primary`)과 Mailpit 동적 속성 등록이 `T-077` 구현 조건 및 하네스 가드와 충돌 없이 맞물림을 확인.
- 판정: **수정 없이 원본 확정 가능**.
