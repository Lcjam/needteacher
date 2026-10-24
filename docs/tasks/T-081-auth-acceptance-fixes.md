---
id: T-081
owner: codex
status: done    # todo | doing | blocked | done
scope:
  - backend/src/test/java/kr/needteacher/account/**
  - docs/tasks/T-081-auth-acceptance-fixes.md
---

## 목표

네가 쓴 T-073 인수 테스트를 독립 리뷰(T-079)와 agy 구현 가능성 리뷰(T-080: 지적 없음)를 거쳐 고친다. 원본으로 옮기면 못 고치므로 이번이 마지막 수정이다. 지적의 근거·수정안은 `docs/tasks/T-079-auth-acceptance-review.md` `## 리뷰`에 줄 번호와 함께 있다. 계약(T-073)도 이에 맞춰 두 줄을 보탰다(소유자 확인 DB 읽기, 로그아웃이 XSRF 쿠키를 지움).

## 고칠 것

반드시:
- **P0-1** 로그아웃 뒤 가입 `401` 단언 전에 `GET /api/ping`으로 CSRF 토큰을 다시 받는다(표준 `.logout()`은 `CsrfLogoutHandler`가 `XSRF-TOKEN`을 지운다).
- **P1-1** 확인 단계에도 공백·대문자 이메일(같은 계정·정규화된 `email` 단언), 둘째 클라이언트의 실패 입력도 대문자로.
- **P1-2** `Retry-After` 올림·최소 1: 소수 초 경계(예: 59.7초 → `Retry-After 1`).
- **P1-3** 254자 주소 `202`(메일 도착) / 255자 `400 email` 양쪽 경계. 로컬 부분 64자·라벨 63자 이하로 만들어 `@Email`만으로 걸러지지 않게.
- **P1-4** 가입 성공 응답의 CSRF 교체(새 `XSRF-TOKEN`, 이전 헤더 값 POST `403`).
- **P1-5** 익명·유효 CSRF `DELETE /api/v1/me` → `401 UNAUTHORIZED`.
- **P1-6** 프로필 소유자 = `account.id`를 `tutor_profiles.owner_id`로 단언.
- **P1-7** `AdjustableClock.withZone`이 같은 시각 참조를 공유하게.

권장(작으면 같이): P2-1(무효 코드도 같은 메시지 비교), P2-2(탈퇴 전 검색에 있었는지), P2-3(`Path=/` 정확 일치), P2-5(동시 확인을 다른 이메일로 몇 번 반복), P2-7(24시간 1초 전 `429`·`Retry-After 1`), P2-8(확인 없는 세션의 잘못된 본문은 `401`, `role` 누락 `400`), P2-9(`LOGGED_IN`의 `account`도 `assertAccount`), P2-10(역할 `403` 뒤 토큰 유효성 확인), P2-11(가입 전 세션으로 `POST signup` `401`), P2-12(`Location`은 경로만 비교). P2-4·P2-6은 구현 카드 쪽이라 하지 않는다.

## 메일 헬퍼 시험 (1회, 커밋 안 함)

`AuthMailInbox`는 green 경로에서 한 번도 돈 적이 없다(20개 모두 첫 요청에서 `401`). 임시 테스트로 Mailpit 컨테이너에 SMTP로 메일 한 통을 직접 보내(JDK 소켓 등, 새 의존성 없이) 헬퍼가 수신인 검색·최신 메일 선택·6자리 추출을 맞게 하는지 확인하고, 그 임시 파일은 지운다. 결과에 확인했다고 적는다.

## 완료 조건

- [ ] 위 "반드시" 8건 반영. 반영 안 한 권장 항목은 이유 한 줄.
- [ ] `spotlessJavaCheck`·`compileTestJava`·`test --tests 'kr.needteacher.architecture.*'` 통과.
- [ ] `integrationTest --tests '*AuthAcceptanceIT'`가 전부 red이고 실패 이유가 여전히 "경로 없음"(`401`)이다. 테스트 수와 메서드 이름 변화.
- [ ] 메일 헬퍼 시험 결과, 임시 파일이 남지 않았음(`git status --short`).
- [ ] 결과 10줄 이내.

## 참고

T-079 `## 리뷰`, T-073(계약), T-080.

## 결과

- 수정: `AuthAcceptanceIT.java`, `AdjustableClock.java`. 필수 P0-1·P1-1~7 전부 반영.
- 권장 P2-1·2·3·5·7·8·9·10·11·12도 반영. P2-4·6은 카드 지시대로 구현 쪽에 남김.
- `spotlessJavaCheck compileTestJava test --tests 'kr.needteacher.architecture.*'` 통과.
- `integrationTest --tests '*AuthAcceptanceIT'`: 메서드 이름·개수 유지(20개), 20개 모두 red. 없는 인증 경로에서 `401 UNAUTHORIZED`(기대 `202` 18건·`400` 2건).
- Mailpit SMTP 임시 시험: 같은 수신인 2통의 검색·최신 코드·6자리 추출 통과. `MailInboxSmokeIT.java` 삭제 확인.
- 남은 일: 오케스트레이터의 원본 이동·승인·병합 후 구현 green 확인.

