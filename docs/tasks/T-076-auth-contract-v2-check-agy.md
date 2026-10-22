---
id: T-076
owner: agy
status: done    # todo | doing | blocked | done
scope:
  - docs/tasks/T-076-auth-contract-v2-check-agy.md
---

## 목표

`docs/tasks/T-073-auth-acceptance.md` 계약 v2를 확인한다. v2는 네 리뷰(T-075)와 Codex 리뷰(T-074)를 합친 것이다. 이번에는 T-074도 읽는다. 코드는 쓰지 않는다. 짧게 끝낸다. 이 확인이 끝나면 Codex가 바로 인수 테스트를 쓴다.

## 볼 것

1. T-075 지적 5건이 v2에 반영됐나(반영 / 다르게 반영·괜찮음 / 누락).
2. **T-074(Codex)의 지적 중 구현이 어려운 것:** 특히 실패 횟수 세션 간 원자적 누적, 같은 코드 동시 확인 시 한 요청만 성공, 확인된 이메일 10분·1회·다른 이메일 확인 시 폐기, 24시간 10회 슬라이딩 구간과 `Retry-After` 산식. Spring/JPA/PostgreSQL로 만들 방법이 있는지 한 줄씩(예: 조건부 `UPDATE ... RETURNING`, `SELECT ... FOR UPDATE`).
3. **오케스트레이터가 두 리뷰를 합친 곳:** 만료 삭제를 "요청 시점 정리(IT 관찰) + 1분 주기 정리(60초 상한, 단위 테스트)"로 했다. 네 P1-3의 플래키 우려(주기 정리가 조작 시계와 섞임)가 이 방식에서도 남는지 판단한다.
4. v2에서 새로 생긴 모순·모호함.

## 완료 조건

- [x] `## 리뷰`에 1~4. 새 지적은 P0/P1/P2 + 교체 문장.
- [x] 판정: **확정 가능 / 고쳐서 확정**.
- [x] 결과 10줄 이내.

## 참고

T-073(v2), T-074, T-075, `docs/research/auth.md`.

## 리뷰

### 1. T-075 지적 5건 반영 확인

| 항목 | 주제 | 상태 | 확인 위치 |
|---|---|---|---|
| **P0-1** | `csrf.spa()` `XorCsrfTokenRequestAttributeHandler` 동작에 맞춘 토큰 교체 검증 | **반영** | `T-073:48` — `SIGNUP_REQUIRED` 포함 코드 확인·가입 성공 시 새 `XSRF-TOKEN` 쿠키가 내려오고, "새 `XSRF-TOKEN` 쿠키 + 이전 토큰 값의 `X-XSRF-TOKEN` 헤더" 조합 POST가 `403`임을 명시 |
| **P1-1** | `SIGNUP_REQUIRED` 단계 세션 고정 방지(세션 ID 교체) 검증 | **반영** | `T-073:44-47` — 사전 세션을 만든 뒤 코드 확인 시 새 `SESSION` 쿠키 발급 및 구 `SESSION` 쿠키로 `/signup` 시 `401`, 가입·`LOGGED_IN` 후 구 쿠키로 `/me` 시 `401` 명시 |
| **P1-2** | `POST /api/v1/auth/signup` 다중 실패 조건 우선순위 | **반영** | `T-073:26,30,36` — CSRF(`403`) → 이미 로그인한 세션(`409 ALREADY_SIGNED_UP`) → 확인된 이메일 없음·만료·이미 사용됨(`401`) → 요청 본문 형식 오류(`400 VALIDATION_FAILED`) → 해당 이메일 활성 계정 이미 존재(`409 ALREADY_SIGNED_UP`) 순서 확정 |
| **P1-3** | 만료 코드 물리 삭제 트리거(요청 시점 정리 + 주기 정리) | **다르게 반영·괜찮음** | `T-073:76-82`, `mvp.md:131` — 발송·확인 요청 시점 정리와 1분 주기 정리를 병행하되, IT는 이메일 C 발송 요청을 트리거로 A·B 행 삭제를 관찰하고 주기 정리는 단위 테스트로 검증하도록 절충 |
| **P2-1** | `fieldErrors[].field` 이름(`email`, `code`, `role`, `ageOver14`) 및 `Retry-After` 올림 산식 | **반영** | `T-073:37,72` — 4개 필드명 고정 및 `max(60초 경과 시각, 24시간 내 가장 오래된 발송 + 24시간) - 현재 시각` 초 단위 올림(최소 `1`) 산식 명시 |

---

### 2. T-074(Codex) 주요 항목의 Spring / JPA / PostgreSQL 구현 가능성 (한 줄씩)

- **실패 횟수 세션 간 원자적 누적 & 5회 실패 즉시 삭제:** `email_codes` 테이블(이메일당 활성 코드 1행)에서 확인 요청 시 `SELECT ... FROM email_codes WHERE email = :email FOR UPDATE`로 행 잠금을 잡거나 조건부 `UPDATE email_codes SET fail_count = fail_count + 1 WHERE email = :email AND expires_at > :now RETURNING fail_count`를 실행하고, 반환된 `fail_count >= 5`이면 즉시 `DELETE FROM email_codes WHERE email = :email`을 커밋(`REQUIRES_NEW` 또는 예외 미롤백 처리)한 뒤 `400 INVALID_CODE`를 응답한다.
- **같은 코드 동시 확인 시 최대 한 요청만 성공:** 확인 트랜잭션에서 `SELECT ... FROM email_codes WHERE email = :email FOR UPDATE`로 잠근 뒤 해시가 일치하면 `DELETE FROM email_codes WHERE id = :id`를 수행(또는 `DELETE FROM email_codes WHERE email = :email AND code_hash = :hash AND expires_at > :now AND fail_count < 5`의 영향받은 행 수 `== 1`로 판정)하므로, 동시 요청 중 정확히 한 건만 행을 소비해 `200`을 받고 후행 요청은 행이 없어 `400 INVALID_CODE`를 받는다.
- **확인된 이메일 10분 유효 · 1회 사용 · 다른 이메일 확인 시 폐기:** `HttpSession`의 단일 속성 키(`"VERIFIED_EMAIL"`)에 불변 레코드 `VerifiedEmail(String email, Instant verifiedAt)` 하나만 저장하면 같은 세션에서 다른 이메일 확인 시 이전 값이 즉시 덮어써지고, `/signup`에서 `WebUtils.getSessionMutex(session)` 동기화 아래 `clock.instant().isBefore(verifiedAt.plusSeconds(600))`을 검사한 뒤 가입 성공 시 `session.removeAttribute("VERIFIED_EMAIL")`로 즉시 제거하여 10분 만료·1회 사용·동시 가입 방어를 모두 충족한다.
- **24시간 10회 슬라이딩 구간과 `Retry-After` 산식:** 코드 저장 테이블(`email_codes`)과 별도로 발송 이력 테이블(`email_send_logs(id, email, sent_at)`)을 두고 `pg_advisory_xact_lock(hashtext(:email))`으로 이메일별 동시 발송을 직렬화한 뒤 `sent_at > :now - INTERVAL '24 hours'` 이력을 조회해, 최신 이력이 `now - 60s` 이후이거나 개수가 10개 이상이면 이력을 추가하지 않고 `blockedUntil = max(latest + 60s, oldestOf10 + 24h)`로부터 `Math.max(1L, Duration.between(now, blockedUntil).plusNanos(999_999_999).getSeconds())`를 계산해 `Retry-After` 헤더로 반환한다.

---

### 3. 만료 삭제 절충안("요청 시점 정리 + 1분 주기 정리")의 플래키 우려 판단

- **판단: 플래키 우려 해소됨 (문제없음).**
- **이유:**
  1. 주기 정리 작업과 요청 시점 정리 모두 주입된 `Clock` 빈을 기준으로 `DELETE FROM email_codes WHERE expires_at <= :now`만 수행하므로, 시계를 앞으로 감기 전(유효 시간 10분 이내)에는 백그라운드 스케줄러가 돌아도 활성 코드를 절대 지우지 않는다.
  2. IT에서 시계를 10분 넘긴 뒤 이메일 C 발송 요청을 보내 이메일 A·B 행이 0개임을 확인할 때, 설령 그 직전에 1분 주기 정리가 먼저 실행되었더라도 결과 상태(`expires_at <= :now`인 A·B 행 0개)가 100% 동일(멱등)하므로 테스트 단언이 흔들리지 않는다.

---

### 4. v2에서 새로 생긴 모순·모호함 및 최종 판정

- **새 모순·모호함:** **없음** (P0/P1/P2 추가 지적 없음).
- **판정:** **확정 가능** — Codex가 이대로 `T-073` 인수 테스트 작성에 착수해도 좋다.

## 결과

- `docs/tasks/T-073-auth-acceptance.md`(v2) 및 `docs/proposals/mvp.md` 대조 완료: T-075 지적 5건(P0 1건, P1 3건, P2 1건) 모두 반영 확인.
- T-074(Codex)가 추가한 원자적 실패 횟수 누적·동시 코드 확인 단일 성공·세션 내 확인 이메일 수명주기·24시간 슬라이딩 발송 제한 및 `Retry-After` 산식 모두 Spring/JPA/PostgreSQL로 간결하게 구현 가능함을 확인.
- 만료 코드 삭제의 "요청 시점 정리(IT) + 1분 주기 정리(단위 테스트)" 절충안은 `Clock` 기반 멱등 삭제(`expires_at <= :now`)이므로 테스트 플래키 우려가 없음을 확인.
- 신규 모순·모호함 없음, 최종 판정 **확정 가능**.
