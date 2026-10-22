---
id: T-079
owner: claude   # 독립 리뷰어 (테스트 작성자 Codex·구현 예정자 agy가 아닌 쪽)
status: done    # todo | doing | blocked | done
scope:
  - docs/tasks/T-079-auth-acceptance-review.md
---

## 목표

`backend/src/test/java/kr/needteacher/account/`의 인수 테스트(T-073, Codex 작성)가 계약 v2(`docs/tasks/T-073-auth-acceptance.md` `## 계약`)를 **빠짐없이, 약하지 않게** 고정하는지 리뷰한다. 승인되면 원본이 되어 고칠 수 없게 되므로 지금 찾는다. 코드는 고치지 않는다.

## 볼 것

1. **조항 ↔ 테스트 대응표:** 계약의 조항(표의 성공·실패 칸 하나하나, 세션·쿠키, 확인된 이메일, 코드 규칙, 저장·삭제, 탈퇴, 슬라이스 1 연결)마다 그것을 단언하는 테스트 메서드·줄. 단언이 없는 조항은 "누락".
2. **약한 단언:** 상태 코드만 보고 본문 `code`를 안 보는 곳, 경계값의 한쪽만 보는 곳(직전만/정각만), "실패하면 통과"처럼 구현이 없어도 통과할 수 있는 단언, 존재 확인만 하고 값을 안 보는 곳.
3. **거짓 red/거짓 green 위험:** 구현이 맞아도 실패할 수 있는 곳(테스트 간 공유 상태, 시계 초기화 순서, 메일 도착 대기, 고유 이메일 충돌, 동시성 테스트의 비결정성), 구현이 틀려도 통과할 수 있는 곳.
4. **하네스 규칙:** `backend/AGENTS.md` 테스트 절, `TestIntegrityTest`, `quality_gate.py` 테스트 Java 금지 문자열, 원본 전용 PR 규칙(헬퍼 포함 전부 원본이 됨)에 걸리는 것.

## 완료 조건

- [ ] `## 리뷰`에 1의 대응표, 2~4의 지적. 지적마다 P0(원본으로 고정되면 틀린 계약/구멍)·P1(고치는 게 맞음)·P2(선택) + `파일:줄` + 최소 수정안.
- [ ] 판정: **원본으로 이동 가능 / 고친 뒤 이동**.
- [ ] 결과 10줄 이내.

## 참고

T-073(계약·결과), T-074·T-075·T-076 `## 리뷰`, `backend/src/test/java/kr/needteacher/architecture/`, `scripts/quality_gate.py`, 슬라이스 1 원본 `backend/src/acceptance/java/kr/needteacher/tutorprofile/TutorProfileAcceptanceIT.java`(형식 참고).

## 리뷰

**판정: 고친 뒤 이동.** P0 1, P1 7, P2 12. 정적 판단이다(Gradle 미실행). 줄 번호는 따로 적지 않으면 `backend/src/test/java/kr/needteacher/account/AuthAcceptanceIT.java`(이하 `IT`) 기준이고, 계약은 `docs/tasks/T-073-auth-acceptance.md`(이하 `T-073`) 기준.

### 1. 조항 ↔ 테스트 대응표

| 계약 조항 (T-073) | 단언 위치 | 판단 |
|---|---|---|
| 코드 요청 `202`·빈 본문·계정 유무 무관 같은 응답·메일 발송 (:28) | `IT:84-97`, `requestCode` `IT:524-529` | 충족 |
| 코드 요청 형식 `400` (:28), 254자 초과 `400` (:61) | `IT:104-105` | **약함** → P1-3 |
| 코드 요청 `429` + `Retry-After` (:28,:72) | `IT:81`, `:168`, `:232`, `:253` | 값은 충족, 올림 규칙 **누락** → P1-2 |
| 확인 `LOGGED_IN` + `account` + 로그인 (:29) | `IT:379-397`, `:508-513` | 충족. `account` 모양은 일부만 확인 → P2-9 |
| 확인 `SIGNUP_REQUIRED` + 세션 보관 (:29) | `IT:113-114`, `:297`, `:335` | 충족 |
| 확인 형식 `400`, 시도 횟수에 안 셈 (:29) | `IT:107-114` (형식 오류 9회 뒤 정답 성공) | 충족 |
| `INVALID_CODE` 같은 메시지: 틀림·만료·사용됨·요청 없음 (:29) | `IT:177-191` | 충족 |
| 같은 메시지: 무효(새 코드로 교체, 5회 실패) (:29) | `IT:138`, `:160-167` (code만 확인) | **약함** → P2-1 |
| 가입 `201`·`Location`·`account`·로그인 (:30) | `signup` `IT:537-542`, `createAccount` `IT:550-553` | 충족 |
| 가입 실패: 로그인 세션 `409`가 맨 먼저 (:30) | `IT:347-348` | 충족 |
| 가입 `401`: 확인 없음 / 만료 / 로그아웃 뒤 (:30,:57) | `IT:356`, `:328`, `:442-443` | 충족(`:442-443`은 P0-1) |
| 가입 `401`: 사용됨 (:30,:57) | `IT:311-313`은 `GET /me`만 봄 | **약함** → P2-11 |
| 가입 `400`: `role`·`ageOver14` 누락·`false`, 계정 안 만듦 (:30) | `IT:343-346` | 충족. `401`이 `400`보다 먼저인 순서와 `role` 누락은 **누락** → P2-8 |
| 가입: 활성 계정 있음 `409` (:30) | `IT:364` | 충족 |
| 로그아웃 `204`·세션 무효화·익명 `204` (:31,:50) | `IT:422-430` | 충족 |
| `GET /me` `200`/`401` (:32) | `createAccount` `IT:552-553`, `IT:426` | 충족 |
| `DELETE /me` `204` (:33) | `IT:478` | 충족 |
| `DELETE /me` 익명 `401` (:33,:103) | 없음 (`IT:456-457`은 CSRF `403`만) | **누락** → P1-5 |
| `account` = `{id, email, role, createdAt}` (:35) | `assertAccount` `IT:571-579` (가입 응답만) | 충족 |
| 모든 POST·DELETE CSRF 누락·오류 `403`, 다른 실패보다 먼저 (:36) | `IT:447-457`, `:420-421`, `:429`, `:477` | 충족 |
| 오류 형식·`fieldErrors[].field` 이름 (:37) | `assertError`/`assertField` `IT:581-594` | 충족 |
| 쿠키 `SESSION`·`HttpOnly`·`SameSite=Lax`·`Path=/`·`Max-Age` (:41-42) | `IT:405-411` | 충족. `Path`는 약함 → P2-3 |
| 유휴 14일 설정값 (:43) | `IT:412` | 충족(P2-4) |
| 세션 고정 방지: `SIGNUP_REQUIRED`·`LOGGED_IN`·가입 (:44-47) | `IT:298`·`:303-305`, `IT:387`·`:391-393`, `IT:307-313` | 충족 |
| CSRF 교체: `SIGNUP_REQUIRED`·`LOGGED_IN` (:48) | `IT:299`·`:301`·`:306`, `IT:388`·`:390`·`:394` | 충족 |
| CSRF 교체: **가입 성공** (:48) | 없음 | **누락** → P1-4 |
| 첫 CSRF는 GET 응답 쿠키로 (:49) | `client()` `IT:517-522` | 충족 |
| 확인된 이메일: 새 세션에만 묶임 / 10분 미만 / 다른 이메일로 대체 (:54-56) | `IT:305`, `:321-328`, `:330-335` | 충족 |
| 정규화(trim+소문자): 같은 계정, 발송 기록·실패 횟수 공유 (:61) | 발송만 확인: `IT:81`, `:85-88`, `:253` | 확인·실패 횟수 쪽 **누락** → P1-1 |
| 메일은 정규화된 주소로, 6자리 숫자는 하나만 (:62) | `AuthMailInbox.java:43`, `:48-51` | 충족 |
| 코드 10분: 9분 59초 수락 / 10분 0초 거부 (:63) | `IT:126-131` | 충족 |
| 새 코드가 이전 코드를 무효화 (:64) | `IT:133-141` | 충족 |
| 4회 틀린 뒤 성공 / 5회 뒤 정답 거부 / 세션 합산 (:65-66) | `IT:150-155`, `:159-167` | 충족 |
| 사용된 코드 재사용 불가 / 동시 요청은 한 건만 성공 (:67) | `IT:188-191`, `IT:194-224` | 충족(P2-5) |
| 60초 제한 59초/60초, 5회 실패·성공 뒤에도 유지 (:69) | `IT:231-236`, `:168-170`, `:78-82` | 충족. 성공 뒤 유지는 `IT:80-81`이 고정함(`IT:226` 메서드 이름과 달리 `:232`는 소비 전) |
| 24시간 10회, 정확히 24시간에 재허용 (:70) | `IT:246-257` | 충족(반대쪽 경계는 P2-7) |
| `429`는 코드·기록을 안 바꿈 (:71) | `IT:232-236`, `:253-257` | 충족 |
| `email_codes.email`, 평문 저장 안 함 (:77,:83) | `IT:265-268` | 충족(P2-6) |
| 성공·5회 실패 시 행 삭제 (:78,:82) | `IT:271`, `:165` | 충족 |
| 교체된 코드 행 삭제 (:78), 확인 요청 때 만료 행 정리 (:79) | 없음 (계약 :80-83 관찰 목록에도 없음) | P2-12 |
| 만료된 A·B 행이 C 요청 뒤 0개 (:81) | `IT:273-280` | 충족 |
| 탈퇴: 재가입하면 새 `id`, `SIGNUP_REQUIRED`, `/me/tutor-profile` `404` (:87,:90) | `IT:487-497` | 충족 |
| 탈퇴: 공개 상세 `404`, 검색에서 빠짐 (:88) | `IT:484`, `:485` | 상세는 충족, 검색은 약함 → P2-2 |
| 탈퇴: 다른 세션의 `GET /me`·`POST 프로필` `401` (:89) | `IT:470-483` | 충족 |
| TEACHER 실제 세션으로 프로필 생성·공개 (:94) | `IT:465-467` | 충족 |
| **소유자 = `account.id`** (:22,:94) | 없음 | **누락** → P1-6 |
| PARENT 프로필 생성 `403`, 역할 불변 (:94-95) | `IT:505`, `:508-514`, `:386` | 충족(P2-10) |

### 2~3. 지적

**P0-1 — 계약을 지킨 표준 로그아웃 구현이 `IT:442-443`에서 거짓 red가 난다.** `logoutDiscardsPendingSignupProof`는 로그아웃(`IT:439`) 직후 GET 없이 POST를 보낸다. 구현 카드는 로그아웃을 `.logout(...)`으로 하라고 정했다(`docs/tasks/T-077-auth-impl.md:50`, T-075 `:110`도 같은 권고). CSRF가 켜진 상태에서는 `CsrfConfigurer`가 `CsrfLogoutHandler`를 로그아웃에 자동으로 붙인다(Spring Security 동작, 정적 판단). 그러면 응답에 `XSRF-TOKEN=; Max-Age=0`이 나가고, 헬퍼가 그 쿠키를 지운다(`AuthHttpClient.java:87-88`). 다음 POST에는 토큰이 없어서 계약대로라면 `403`이다(T-073:36 "CSRF 판정이 먼저"). 테스트는 `401`을 기대하므로, 계약에 없는 "로그아웃 뒤에도 XSRF 쿠키 유지" 요구가 원본으로 굳는다. **수정:** `IT:439` 다음 줄에 `assertStatus(client.get("/api/ping"), 200);`를 넣고(T-073:49 방식으로 토큰을 새로 받음), `stale` 복사(`IT:440`)는 그 뒤에 한다.

**P1-1 — 확인 단계의 이메일 정규화와 실패 횟수 공유가 고정되지 않았다.** 정규화 안 된 입력은 코드 요청에만 쓴다(`IT:81`, `:85`, `:253`). `VERIFY` 호출은 전부 소문자 그대로다. 그래서 발송만 정규화하는 구현도 통과한다. 이 경우 대문자로 입력한 사용자는 로그인하지 못하는데, 계약은 같은 계정이라고 한다(T-073:61). **수정:** `IT:88`의 코드를 변수에 받고, 새 클라이언트로 `verify(…, " " + existing.toUpperCase(Locale.ROOT) + " ", code)`를 보내 `LOGGED_IN`, 같은 `account.id`, 정규화된 `email`을 단언한다. `IT:163`의 둘째 클라이언트는 `fiveEmail.toUpperCase(Locale.ROOT)`로 틀린 코드를 보낸다.

**P1-2 — `Retry-After`의 올림과 최소 `1` 규칙이 비어 있다(T-073:72).** 테스트 시각이 모두 초 단위라서(`IT:80`, `:231`, `AdjustableClock.java:26`) 내림(`Duration#getSeconds`)이나 반올림으로 계산하는 구현도 통과한다. 실제 요청 시각은 항상 소수 초다. **수정:** `IT:229` 앞에 `Instant sent = clock.instant();`를 두고, `IT:231`을 `clock.set(sent.plusMillis(59_700))`로 바꾼다. 기대값은 `Retry-After 1`이다. 내림·반올림은 0을 내므로 걸린다. `IT:235`의 `+1s`는 그대로 둬도 60.7초로 `202`다.

**P1-3 — 254자 경계가 실제로는 단언되지 않는다(T-073:61).** `IT:105`는 로컬 부분이 250자인 256자 주소다. 표준 `@Email` 검증기는 로컬 부분 64자 제한만으로 이 주소를 거절하므로, 전체 길이를 검사하지 않는 구현도 통과한다(추측: Hibernate Validator 동작). 한쪽 경계만 보는 문제도 있다. **수정:** 로컬 부분 64자 이하, 라벨 63자 이하로 만든 254자 주소는 `202`이고 메일이 도착하는지, 255자 주소는 `400`이고 `email` 필드인지 본다.

**P1-4 — 가입 성공 때 CSRF 교체 단언이 없다(T-073:48).** `IT:307-313`은 `SESSION`만 비교하고, `IT:405-411`은 `SESSION`의 `Set-Cookie`만 본다. `changeSessionId`만 하고 CSRF를 교체하지 않는 가입 구현도 통과한다. **수정:** `IT:307`에서 `preSignupCsrf`를 저장하고, 가입은 원시 응답으로 받는다. 그 응답에 `assertSetCookie(resp, "XSRF-TOKEN")`과 `assertNotEquals(preSignupCsrf, client.cookie("XSRF-TOKEN"))`을 걸고, `client.postWithCsrf(LOGOUT, null, preSignupCsrf)`가 `403`인지 본다.

**P1-5 — 익명 `DELETE /api/v1/me` → `401`이 없다.** 표의 실패 칸(T-073:33)이고, 허용·거부 쌍 규칙(T-073:103, `backend/AGENTS.md:31`)에도 해당한다. 지금은 CSRF `403`(`IT:456-457`)과 인증된 `204`(`IT:478`)만 있다. **수정:** `IT:457` 뒤에 `assertError(client.delete(ME), 401, "UNAUTHORIZED");`를 넣는다. 이 클라이언트는 익명이고 CSRF 토큰이 유효하다.

**P1-6 — "소유자 = `account.id`"가 고정되지 않았다(T-073:22,94).** 슬라이스 1 응답에는 `ownerId`가 없다(`TutorProfileAcceptanceIT.java:78`). `owner_id`는 `VARCHAR(100)`이라 이메일도 들어간다(`V1__create_tutor_profiles.sql:3`). 그래서 Spring에서 흔히 하듯 `getName()`을 이메일로 두는 구현도 `IT:460-498`을 모두 통과한다. **수정:** `IT:465` 뒤에서 `jdbc.queryForObject("SELECT owner_id FROM tutor_profiles WHERE id = CAST(? AS uuid)", String.class, profileId)`가 `firstAccount.id`와 같은지 단언한다. T-073:80의 "DB 읽기는 `email_codes.email`만" 문구에 이 읽기를 추가한다.

**P1-7 — `AdjustableClock.withZone`이 시계를 공유하지 않는 사본을 만든다(`AdjustableClock.java:36-40`).** 운영 코드가 `clock.withZone(ZoneId.of("Asia/Seoul"))`를 필드에 저장하면 그 시각은 생성 시점(`EPOCH`)에 멈춘다. 그러면 시계를 쓰는 단언 전부가 거짓 red가 된다. 헬퍼도 원본이 되므로 지금 고친다. **수정:** `private AdjustableClock(AtomicReference<Instant> time, ZoneId zone)` 생성자를 두고 `withZone`이 `new AdjustableClock(time, newZone)`을 돌려주게 해서 같은 참조를 공유한다.

**P2 (선택)**
- P2-1 `IT:138`, `:160-167`: 교체되거나 5회 실패로 무효가 된 코드는 `code`만 본다. `assertSameError`로 메시지도 비교한다(T-073:29).
- P2-2 `IT:485`: 탈퇴 전에 검색 결과에 있었는지를 안 본다. `IT:467` 뒤에 `contains(profileId)`를 단언한다.
- P2-3 `IT:410`: `contains("Path=/")`는 `Path=/api`도 통과시킨다. `COOKIE_AGE`처럼 경계가 있는 정규식으로 바꾼다.
- P2-4 `IT:412`: `Environment` 변환은 단위 없는 숫자를 밀리초로 읽는다(추측). 그래서 `timeout=1209600`처럼 앱에는 14일로 바르게 적용되는 값이 실패한다. 구현 카드에 `14d` 형식을 쓰라고 적는다.
- P2-5 `IT:194-224`: 동시성 경합을 한 번만 시도해서 비원자적 구현이 우연히 통과할 수 있다. 서로 다른 이메일로 몇 번 반복한다.
- P2-6 `IT:137`(코드가 우연히 같을 확률 약 10⁻⁶), `IT:268`(해시나 UUID 문자열에 6자리 숫자가 우연히 포함될 확률): 아주 드문 거짓 red다.
- P2-7 `IT:256`: 24시간 1초 전에 `429`·`Retry-After 1`인지 보는 반대쪽 경계가 없다. 구간 길이는 `IT:253`의 85800이 고정해 준다.
- P2-8 `IT:343-345`: 확인 없는 세션에 잘못된 본문을 보내면 `401`인 순서(`@Valid`가 먼저 돌면 `400`)와 `role` 누락이 빠졌다.
- P2-9 `IT:383-386`: `LOGGED_IN`의 `account`에 `assertAccount`를 쓰지 않아 `email`·`createdAt`을 안 본다.
- P2-10 `IT:505`, `:514`: 역할 `403`과 CSRF `403`의 본문이 같다. `IT:514` 뒤에 `assertStatus(again.post(LOGOUT, null), 204)`로 토큰이 유효했음을 보인다.
- P2-11 `IT:311-313`: 가입 전 세션으로 `POST signup` → `401`("사용됨")도 넣는다.
- P2-12 `IT:562-563`: `Location` 원문을 그대로 비교한다. 슬라이스 1 원본은 경로만 비교한다(`TutorProfileAcceptanceIT.java:65-67`). `URI.create(…).getPath()`로 맞춘다. 교체된 행 삭제와 확인 요청 때 만료 행 정리(T-073:78-79)는 관찰 목록 밖이므로 선택이다.

**거짓 red/green 점검 중 문제없음:** 테스트마다 기준 시각을 3일씩 띄우고 순서대로 늘려서(`IT:70`) 테스트 사이에 시각이 겹치지 않는다. 고유 이메일(`IT:665-667`)과 수신인별 메일 검색(`AuthMailInbox.java:34`)이 있다. 이 컨텍스트에서만 PostgreSQL·Mailpit 빈을 쓴다. 주기 정리는 이미 만료된 행만 지운다(T-073:79). 확인할 것 하나: 메일 헬퍼는 green 경로에서 한 번도 실행되지 않았다(T-073 결과: 20개 모두 첫 요청에서 `401`). Mailpit 검색이 최신순이라는 가정(`AuthMailInbox.java:40`)은 추측이다. 이동 전에 직접 SMTP로 메일 한 통을 보내 헬퍼를 시험해 볼 만하다.

### 4. 하네스 규칙

위반 없음. `@Import`에 `TestcontainersConfiguration`이 있다(`IT:35`). 빈 바꿔치기 애노테이션이 없다(`TestIntegrityTest.java:36-56`). `BeanFactoryPostProcessor` 의존이 없다(`GuardBypassIntegrityTest.java:15-23`, `DynamicPropertyRegistrar`는 해당 안 됨). `"spring.mail.*"`와 `"server.servlet.*"`은 금지 문자열이 아니다(`scripts/quality_gate.py:65-70`). 새 파일만 추가했다. 원본 비교는 `*IT`만이 아니라 원본 트리 전체를 바이트 단위로 본다(`scripts/verify.sh:165-183`). 그래서 5개 파일 모두 `backend/src/acceptance/java/kr/needteacher/account/` 같은 이름으로 옮겨야 한다. 구현 메모(추측): `DynamicPropertyRegistrar`가 등록한 값은 자동 설정 조건(`@ConditionalOnProperty(spring.mail.host)`)을 판정할 때 보이지 않을 수 있다(`AuthTestConfiguration.java:25-31`). 그러면 운영 `application.properties`에 `spring.mail.host` 기본값이 있어야 한다. `spring-boot-starter-mail`은 보호 파일 `backend/build.gradle.kts:24-31`에 아직 없다.

## 결과

- 판정: **고친 뒤 이동.** P0 1 · P1 7 · P2 12. 바꾼 파일은 이 카드뿐이다(코드·테스트 미수정, Gradle·git 상태 변경 없음).
- P0-1: `IT:442-443`. 구현 카드대로 `.logout()`을 쓰면 `CsrfLogoutHandler`가 XSRF 쿠키를 지워 `401` 대신 `403`이 나온다. 로그아웃 뒤 `GET /api/ping`을 넣어서 고친다.
- P1: 확인 단계의 이메일 정규화(1), `Retry-After` 올림(2), 254자 경계(3), 가입 때 CSRF 교체(4), 익명 `DELETE /me` `401`(5), 소유자 = `account.id`(6), `AdjustableClock.withZone`이 시계를 공유하지 않는 사본을 만드는 문제(7).
- 하네스 규칙 위반 없음. 메일 헬퍼는 green 경로에서 아직 검증되지 않았다(이동 전 직접 SMTP로 시험 권장).
- 검증: 계약 T-073 각 조항, 테스트 5개 파일, 하네스 파일(`TestIntegrityTest`·`GuardBypassIntegrityTest`·`quality_gate.py`·`verify.sh`)을 정적으로 대조했다.
- 남은 일: Codex가 위 수정을 `src/test/java`에 반영하고 red를 다시 확인한 뒤, 오케스트레이터가 원본으로 옮긴다.

## 수정 확인 (T-081 반영분, 정적 확인)

**판정: 원본 이동 가능.** P0·P1은 모두 바르게 고쳐졌다. 새 지적은 P2 3건뿐이다. 줄 번호는 수정 후 `AuthAcceptanceIT.java`(이하 `IT`) 기준이다. `AuthHttpClient`·`AuthMailInbox`·`AuthTestConfiguration`은 바뀌지 않았다.

| 항목 | 상태 | 근거 |
|---|---|---|
| P0-1 로그아웃 뒤 CSRF 다시 받기 | 반영 | `IT:486-492`. 로그아웃 → `GET /api/ping` → `stale` 복사 → 가입 `401`. XSRF 쿠키를 지우는 구현과 남기는 구현 모두 통과한다. 계약 문구는 `T-073:95` |
| P1-1 확인 단계 정규화·실패 횟수 공유 | 반영 | `IT:90-96`(공백·대문자로 확인 → `LOGGED_IN`, 같은 `id`, 정규화된 `email`), `IT:177`(둘째 클라이언트가 대문자로 실패 입력) + `IT:181`(행 0개) |
| P1-2 `Retry-After` 올림·최소 1 | 반영 | `IT:262-264`(59.7초 → `1`. 내림·반올림이면 0이 나와 걸린다). 정확히 60초 경계는 `IT:82-88`, `:184-186`에 그대로 남아 있다 |
| P1-3 254/255자 경계 | 반영 | `IT:113-118`. 로컬 부분 64자, 라벨 63자 이하. 길이도 `:115-116`에서 단언한다 |
| P1-4 가입 때 CSRF 교체 | 반영 | `IT:342-354`(새 `XSRF-TOKEN`의 `Set-Cookie`, 값 변경, 이전 헤더로 보낸 POST `403`) |
| P1-5 익명 `DELETE /me` `401` | 반영 | `IT:507`. 클라이언트는 익명이고, 반복문의 `403`이 상태를 바꾸지 않는다 |
| P1-6 소유자 = `account.id` | 반영 | `IT:516-519`(`tutor_profiles.owner_id`). 계약 `T-073:80`, `:94`도 갱신됐다 |
| P1-7 `withZone`이 시각 공유 | 반영 | `AdjustableClock.java:18-21`, `:37-38` |
| P2-1·2·3·5·7·8·9·10·11 | 반영 | `IT:199-210`, `:522`, `:49`·`:457`, `:225-251`, `:288-289`, `:390`·`:403`, `:94`·`:433`, `:570`, `:358` |
| P2-12 `Location`은 경로만 비교 | **미흡**(선택) | 지적한 대상은 슬라이스 1 프로필의 `Location`이었다. 그런데 바뀐 곳은 가입 쪽(`IT:345-348`, `:596-599`)이고, `createProfile`은 여전히 원문 그대로 비교한다(`IT:621-622`). 현재 구현은 상대 경로를 주므로 통과한다 |

**새 지적 (거짓 red/green·계약 밖 강제·하네스 위반 점검)**
- P2-N1 `IT:113`: 254자 주소가 매번 같은 고정 문자열이다. T-073:100은 테스트마다 고유 이메일을 쓰라고 한다. 컨텍스트마다 Mailpit·DB가 새로 뜨므로 지금은 충돌하지 않는다. **수정(선택):** 길이는 그대로 두고 `b` 부분 일부를 UUID 16진수로 바꾼다.
- P2-N2 `IT:117`: 254자 주소면 SMTP 경로가 `<…>`를 포함해 정확히 256옥텟으로, RFC 5321 상한이다. Mailpit SMTP가 이 수신인을 받는지는 실측하지 않았다(추측: 받는다. T-081의 SMTP 시험은 짧은 주소만 썼다). 거부하면 메일이 오지 않아 거짓 red가 난다. **권장:** 구현이 green이 되기 전에 그 주소로 직접 SMTP 1회만 확인한다. 원본은 안 고쳐도 된다.
- P2-N3 `T-073:95`: "로그아웃은 `XSRF-TOKEN`도 지운다"는 계약 문장인데 테스트는 단언하지 않는다(일부러 두 경우 모두 통과하게 했다). 위치도 "슬라이스 1 연결" 절이다. **수정(선택):** "지울 수 있다(…) 테스트는 로그아웃 뒤 GET으로 토큰을 다시 받는다"로 고치고 세션·쿠키 절로 옮긴다.
- 그 밖에 문제 없음: 시계 흐름이 앞으로만 간다(`IT:263`, `:288`, `:290`). 동시성 반복은 매번 새 이메일·클라이언트를 쓴다(`IT:227-231`). 새 DB 읽기는 금지 문자열이 아니다(`scripts/quality_gate.py:65-70`). 메서드 수는 20개로 그대로이고, 임시 `MailInboxSmokeIT`는 남아 있지 않다(`account/`에 5개 파일).
