---
id: T-083
owner: codex
status: doing    # todo | doing | blocked | done
scope:
  - docs/tasks/T-083-auth-impl-review-codex.md
---

> **두 단계로 한다.** 지금은 1단계만: agy의 T-077은 메일 발송만 남기고 `blocked`(의존성 대기)다. 메일을 뺀 코드(스키마·계정·코드 저장·세션·보안 필터·탈퇴)를 먼저 리뷰해 구현자가 메일을 붙이는 동안 고칠 수 있게 한다. 2단계(메일이 들어온 뒤 `verify full` 재현·생존 뮤턴트·최종 판정)는 오케스트레이터가 다시 알린다.
>
> **1단계 규칙:** 작업 트리의 커밋 안 된 구현을 읽기만 한다. git 상태를 바꾸지 않는다. Gradle은 단위 테스트·PIT(`-PpitTargetClasses='kr.needteacher.account.*'`)까지만 돌려도 된다(메일 IT는 아직 red가 정상). 결과는 `## 리뷰`에 "1단계"로 적고 `status`는 `doing`으로 둔다.
>
> **오케스트레이터가 먼저 본 것:** T-077 결과에 `accounts(email UNIQUE, is_active)`가 있다. 탈퇴를 `is_active=false`로만 하면 같은 이메일 재가입(T-073 탈퇴 4번째 줄)이 UNIQUE에 막히지 않는지 확인하라.

## 목표

agy가 구현한 MVP 2번 조각(T-077)을 리뷰한다 (하네스 ⑤). 테스트 작성자로서 "인수 테스트가 green인 것"을 넘어, 계약(T-073)을 실제로 지키는지와 테스트가 못 잡는 구멍을 본다. 코드는 고치지 않는다.

## 볼 것

1. **검증 재현:** `bash scripts/verify.sh full --worktree`를 직접 돌린다. 원본 ↔ 실행 사본 바이트 일치, 수치(line/branch/mutation), 새 운영 파일별 기준.
2. **생존 뮤턴트:** PIT 리포트에서 새 파일의 생존 뮤턴트를 보고, 계약상 의미 있는 것(시간 경계, 실패 횟수, `Retry-After` 올림, 정규화, 판정 순서)이 살아 있으면 지적.
3. **계약 대비 코드:** 원자성(동시 확인·실패 누적이 정말 한 문장/잠금인가), 탈퇴한 계정의 다른 세션 거부 방식(슬라이스 1 `user()` 주체는 영향 없는가, 세션 저장소를 바꿔도 동작하나), 평문 비저장·HMAC 키, 만료 삭제의 60초 상한, CSRF·세션 교체 세 경로.
4. **보안:** 계정 열거(응답·시간 차이), 코드 대입, 로그·예외 메시지에 코드·이메일이 새지 않는지, 설정에 비밀값이 없는지.
5. **하네스:** 기존 테스트 파일 불변, 보호 파일 미수정, 금지 패턴, 계층 순환.

## 완료 조건

- [ ] `## 리뷰`에 지적마다 P0/P1/P2 + `파일:줄` + 최소 수정안.
- [ ] 판정: **병합 가능 / 고쳐서 병합 / 다시 구현**.
- [ ] 결과 10줄 이내 (verify 수치 포함).

## 참고

T-073(계약), T-077(구현 카드·결과), T-079·T-080·T-082, `docs/research/auth.md`.

## 리뷰

### 1단계 — 메일 발송 제외, 작업 트리 정적 리뷰

**잠정 판정: 고쳐서 병합.** 메일 의존성과 최종 `verify full --worktree` 판정은 2단계 대기. 현재 V2 스키마는 T-077 결과의 `is_active` 대신 계정 행을 삭제한다(`AccountService.java:47-49`). 따라서 탈퇴 뒤 동일 이메일 재가입은 `accounts.email UNIQUE`에 막히지 않고, 다른 세션은 `ActiveAccountFilter.java:33`의 ID 존재 확인으로 거부된다. 슬라이스 1의 임의 `user()` 주체는 전용 `AccountAuthenticationToken`이 아니므로 필터 대상에서 빠진다.

- **P0 — 발송 제한의 이메일별 잠금이 실제 요청에서 유지되지 않음** (`EmailCodeService.java:39-49`, `EmailSendLogRepository.java:19-20`). 컨트롤러는 `sendCode()`를 호출하고, 그 메서드는 같은 객체의 `@Transactional generateAndSaveCode()`를 직접 호출한다. Spring 프록시를 거치지 않아 트랜잭션이 열리지 않으며 `pg_advisory_xact_lock`은 해당 SQL 문장이 끝나면 풀린다. 동시 요청이 모두 같은 발송 기록을 읽고 60초·24시간 제한을 넘길 수 있다. **최소 수정:** 외부 호출 진입점에 트랜잭션을 걸거나 저장·판정 부분을 별도 트랜잭션 빈으로 분리하고, 실제 PostgreSQL 동시 발송으로 한 건만 성공하는지 검증한다.
- **P1 — 동시 가입의 UNIQUE 충돌이 계약의 `409`가 아닌 `500`으로 감춰질 수 있음** (`AccountController.java:171-175`, `AccountService.java:38-43`, `GlobalExceptionHandler.java:65-68`). 두 세션이 같은 이메일의 `existsByEmail`을 모두 false로 본 뒤 저장하면 한쪽은 DB UNIQUE 위반을 받고 일반 예외 핸들러로 간다. **최소 수정:** UNIQUE 충돌을 트랜잭션 경계에서 `ALREADY_SIGNED_UP`으로 변환하고, 두 확인 세션의 동시 가입에서 `201`·`409`를 검증한다.
- **P1 — 탈퇴와 이미 시작된 프로필 생성이 경합하면 탈퇴 뒤 프로필이 남을 수 있음** (`ActiveAccountFilter.java:30-33`, `AccountService.java:47-49`, `TutorProfileService.java:38,93`). 필터의 계정 존재 확인과 프로필 저장은 별도 트랜잭션이고 `owner_id` FK가 없다. 두 번째 세션의 확인 후 탈퇴가 먼저 삭제·커밋되고 프로필 저장이 나중에 커밋될 수 있다. **최소 수정:** 실제 계정 주체의 프로필 쓰기와 탈퇴를 계정 ID 기준으로 직렬화하고, 임의 `user()` 주체의 슬라이스 1 동작은 유지한다.
- **P2 — HMAC 키 난수성 회귀를 단위 테스트가 못 잡음** (`CodeHasher.java:26`). account 범위 PIT에서 `SecureRandom.nextBytes(secretKey)` 제거 변이가 생존했다. 실제 코드는 난수 키를 만들지만 이 변이가 들어가면 키가 전부 0이 된다. **최소 수정:** 독립 `CodeHasher` 인스턴스가 같은 코드에 서로 다른 해시를 생성하는 단위 테스트를 추가한다.

그 밖에 코드 저장·확인은 `FOR UPDATE`와 `@Transactional verifyCode()`로 소비·5회 실패를 직렬화하며, 평문 대신 HMAC을 저장한다. 요청 시 만료 정리와 1분 예약 정리가 있고, 성공 세 경로의 세션·CSRF 교체는 `SessionHelper`를 통한다. 코드·이메일 로그 출력이나 설정 파일 비밀값은 찾지 못했다. 메일 발송 빈이 없는 현재의 무메일 `202`는 T-077의 의존성 대기 범위로 두고 2단계에서 확인한다.

### 1단계 추가 확인 — 재작업 2

- **기존 P0·P1·P2 코드 지적은 닫힘.** `EmailCodeService.java:39-49`의 외부 진입점 `sendCode()`에 `@Transactional`이 생겨 advisory lock·조회·저장과 메일 호출이 한 트랜잭션에 든다. `AccountService.java:38-47`은 `saveAndFlush`의 UNIQUE 충돌을 `409 ALREADY_SIGNED_UP`으로 바꾼다. `TutorProfileService.java:25,50-52,108`의 쓰기 트랜잭션 안에서 `AccountOwnerAccessValidator.java:32`가 계정 행을 `FOR SHARE`로 잠그며, 탈퇴의 `AccountService.java:51-57` UPDATE가 그 잠금을 기다린다. 계정 행이 없는 슬라이스 1 주체는 통과한다(`AccountOwnerAccessValidator.java:23-38`). `account`가 `tutorprofile.OwnerAccessValidator`를 구현하고 `tutorprofile`은 `account`를 import하지 않아 의존 방향도 유지된다. `CodeHasherTest.java:30-36`의 인스턴스별 키 테스트가 생겼고, PIT의 `SecureRandom.nextBytes` 제거 변이는 `KILLED`다.
- **메일 발송을 잠금·트랜잭션 안에 둔 판단에 동의.** SMTP 실패 시 코드·발송 기록이 함께 롤백되어 사용자에게 쿨다운 없이 재시도를 허용한다. SMTP 성공 뒤 DB 커밋 실패는 배달된 코드가 무효가 될 수 있는 잔여 위험이다. 의존성 연결 때 T-077 지시대로 SMTP 연결·읽기·쓰기 타임아웃을 설정해 잠금 보유 시간을 제한해야 한다.
- **P1 — 새 경합 IT가 핵심 순서를 강제하지 않음** (`AuthConcurrencyIT.java:79-101,136-158,182-216`). `ready`는 HTTP 호출 **전**에 감소하고 `start`는 스레드 출발만 맞춘다. 따라서 발송은 순차 실행으로도 `202/429`, 가입은 순차 실행으로도 `201/409`, 프로필은 생성 후 탈퇴로도 최종 0건이 된다. 옛 결함을 재현하는 프로필 필터 통과 → 탈퇴 커밋 → 프로필 저장 순서를 강제하지 않는다. `c2 = c1.copy()`(`:182`)도 독립 로그인 세션이 아니다. **최소 수정:** DB 잠금/관찰 가능한 배리어로 각 요청을 문제의 SQL 단계에 멈춰 두고 두 요청이 도달했음을 확인한 뒤 해제한다. 프로필 경합에는 별도 로그인 세션을 쓰고 결함 있는 구현에서 테스트가 실패함을 확인한다.
- **새 운영 코드 결함은 추가로 찾지 못함.** 위 경합 검증 공백은 2단계 최종 판정 전 보완이 필요하다. `test` 통과, 새 `AuthConcurrencyIT` 3/3 통과, account PIT의 키 초기화 제거 변이 `KILLED`를 확인했다.

## 결과

- 1단계 잠정 판정: **고쳐서 병합**. P0 1건, P1 2건, P2 1건을 `## 리뷰`에 기록.
- `backend`에서 `.\gradlew.bat test` 통과. account 범위 PIT: 201변이 중 182 제거(91%), line 305/306(99%).
- 실제 V2는 계정 행 삭제 방식이라 같은 이메일 재가입을 허용하고, 다른 세션은 ID 존재 검사로 거부함. 슬라이스 1 임의 주체는 검사 대상 밖.
- 보호 파일·기존 테스트 수정이나 금지 패턴은 발견하지 못함. 구현 파일은 읽기만 했고 git index는 건드리지 않음.
- 남은 일: 메일 의존성 반영 후 2단계 `verify full --worktree`, 전체 PIT 생존 변이·인수 테스트 확인과 최종 판정.
- 재작업 2 추가 확인: 기존 코드 지적 4건 닫힘. 새 경합 IT 3/3 통과했지만 순서를 강제하지 않아 P1 테스트 보강 필요; 메일의 트랜잭션 내 발송은 타임아웃 조건으로 동의.

## 1단계 추가 확인 (재작업 2 이후)

agy가 네 1단계 지적을 T-077 "재작업 2"로 고쳤다(결과는 T-077 `## 결과`). 메일 의존성은 아직 없다. 같은 1단계 규칙(읽기만, git 상태 변경 금지, 단위 테스트·account PIT·새 `AuthConcurrencyIT`까지만 실행)으로:

1. P0(발송 트랜잭션)·P1(동시 가입 `409`)·P1(탈퇴-프로필 경합)·P2(HMAC 키)가 실제로 닫혔는지. 특히 경합 수정: `OwnerAccessValidator`의 `FOR SHARE`와 탈퇴 `UPDATE`가 같은 트랜잭션 경계에서 도는지, 슬라이스 1 주체(계정 행 없음)는 그대로 통과하는지, `account → tutorprofile` 한 방향인지.
2. 메일 발송을 트랜잭션·이메일 잠금 안에서 하기로 한 agy 판단(T-077 결과)에 동의하는지. 반대면 대안.
3. `AuthConcurrencyIT`가 경합을 실제로 재현하는지(우연히 순서대로 돌아 통과하는 테스트가 아닌지).
4. 고치면서 새로 생긴 문제.

`## 리뷰` 끝에 "1단계 추가 확인"으로 적고, `## 결과`에 한 줄 덧붙인다. `status`는 `doing` 유지.
