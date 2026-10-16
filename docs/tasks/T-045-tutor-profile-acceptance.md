---
id: T-045
owner: codex
status: done    # todo | doing | blocked | done
scope:
  - backend/src/test/java/kr/needteacher/tutorprofile/**
  - docs/tasks/T-045-tutor-profile-acceptance.md
---

## 목표

MVP 구현 순서 1번(선생님 프로필 + 검색, `docs/proposals/mvp.md` F2·F4)의 인수 테스트 원본을 쓴다. 운영 코드는 쓰지 않는다. 구현 전이므로 red여야 한다.

## 계약 (테스트가 고정할 것)

이 카드의 표가 `mvp.md`와 T-043 API 표보다 우선한다. 바꿔야 할 이유가 있으면 테스트를 쓰기 전에 결과에 적고 멈춘다.

**범위:** 출신 고교 입력·필터는 이번 조각에서 뺀다 (고교 코드 목록이 정해진 뒤 1b 조각). 요청에 `highSchool`이 오면 무시하지 말고 `400`.

**인증 주입:** `spring-security-test`의 `SecurityMockMvcRequestPostProcessors`를 쓴다. 쓰기 요청은 `.with(user("<계정ID>").roles("TEACHER"|"PARENT")).with(csrf())`. 계정 ID 문자열이 소유자 식별자다. 실제 로그인(다음 조각)이 붙어도 이 방식은 그대로 둔다.

| 엔드포인트 | 권한 | 성공 | 실패 |
|---|---|---|---|
| `POST /api/v1/tutor-profiles` | TEACHER | `201`, `Location`, 본문=소유자 뷰, `visibility=HIDDEN` | 미인증 `401`, PARENT `403`, 이미 있음 `409`, 검증 `400` |
| `GET /api/v1/me/tutor-profile` | TEACHER | `200`, 소유자 뷰 (숨김 포함) | `401`, PARENT `403`, 없음 `404` |
| `PATCH /api/v1/tutor-profiles/{id}` | 작성자 | `200`, 소유자 뷰. 보낸 필드만 바꾼다. `introduction: null`은 지움. 변경이 없으면 `updatedAt` 유지 | `401`, 타인 `403`, 없음 `404`, 검증 `400` |
| `PATCH /api/v1/tutor-profiles/{id}/visibility` | 작성자 | `200`, `{id, visibility, updatedAt}` | `401`, `403`, `404`, 잘못된 값 `400` |
| `GET /api/v1/tutor-profiles` | 누구나 | `200`, `{items[], nextCursor}` (마지막 페이지면 `nextCursor: null`) | 잘못된 코드·limit·cursor `400` |
| `GET /api/v1/tutor-profiles/{id}` | 누구나 | `200`, 공개 뷰 | 없음·숨김 모두 `404` (작성자가 불러도 공개 경로는 `404`) |

**본문 필드**
- 요청: `displayName`, `subjectCodes[]`, `gradeCodes[]`, `dongCodes[]`, `tuition{amountWon, unit, sessionMinutes, sessionsPerMonth}`, `declarationType`, `registrationNumber`, `university`, `department`, `introduction`
- 공개 뷰: `id` + 요청 필드(정규화) + `universityEmailVerified`(이번 조각에서는 항상 `false`) + `updatedAt`. 계정 ID·이메일은 넣지 않는다.
- 소유자 뷰: 공개 뷰 + `visibility`, `createdAt`
- 검색 목록 항목: 공개 뷰에서 `introduction`을 뺀 것

**검증 (`400`)**

| 필드 | 규칙 |
|---|---|
| `displayName` | 1~20자 |
| `university`·`department` | 1~50자 |
| `introduction` | 최대 1000자, 선택 |
| `subjectCodes` | 1~5개, 중복 불가 |
| `gradeCodes`·`dongCodes` | 1개 이상, 중복 불가 |
| `tuition.amountWon` | 1~10,000,000 |
| `tuition.unit` | `PER_SESSION`이면 `sessionMinutes`(10~600) 필수, `sessionsPerMonth`는 없어야 함. `PER_MONTH`면 둘 다 필수 (`sessionsPerMonth` 1~31) |
| `declarationType` | `REPORTED`면 `registrationNumber` 1~30자 필수. `ENROLLED_EXEMPT`면 `registrationNumber`가 없어야 함 |
| 공통 | 알 수 없는 코드 거부 |

**오류 본문:** `{code, message, fieldErrors?}`. 코드는 다음과 같다.
- `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `PROFILE_ALREADY_EXISTS`
- `VALIDATION_FAILED`: `fieldErrors[{field}]`에 위반 필드가 들어간다.
- `INVALID_QUERY`: 검색 파라미터 오류.
- 미인증은 리다이렉트가 아니라 `401`과 JSON이어야 한다.

**검색**
- 파라미터는 `dongCodes`, `subjectCodes`, `gradeCodes`(쉼표 구분), `limit`(기본 20, 1~50), `cursor`(불투명 문자열)이다.
- 같은 필터 안의 값은 OR, 필터 사이는 AND로 묶는다. 공개 프로필만 나온다.
- 정렬은 `updatedAt DESC, id DESC`. 수정하면 앞으로 온다. 숨겼다 다시 공개해도 앞으로 온다.

**코드 값**

| 목록 | 코드 |
|---|---|
| 과목 | `KOREAN, ENGLISH, MATH, SCIENCE, SOCIAL, KOREAN_HISTORY, ESSAY, CODING, SECOND_LANGUAGE` |
| 학년 | `ELEMENTARY_1_3, ELEMENTARY_4_6, MIDDLE_1, MIDDLE_2, MIDDLE_3, HIGH_1, HIGH_2, HIGH_3, REPEATER` |
| 동 (수성구 23개 행정동) | `BEOMEO_1..4, MANCHON_1..3, HWANGGEUM_1..2, SUSEONG_1GA, SUSEONG_23GA, SUSEONG_4GA, JUNG, SANG, DUSAN, JISAN_1..2, BEOMMUL_1..2, PA, GOSAN_1..3` |

## 완료 조건

- [x] 새 파일만 쓴다 (예: `tutorprofile/TutorProfileAcceptanceIT.java`, 필요하면 같은 폴더에 헬퍼). `*IT`는 `@Import(TestcontainersConfiguration.class)`를 단다. `backend/AGENTS.md` 테스트 규칙을 지킨다 (빈 바꿔치기·DB 설정 덮어쓰기 금지).
- [x] 위 표의 성공·실패를 모두 덮는다. 경계값(20/21자, 0원, 5/6과목, 단위별 필수값)과 권한(미인증·학부모·타인)을 포함한다.
- [x] 테스트끼리 독립적이다. 같은 DB에 다른 테스트 데이터가 있어도 통과해야 한다 (자기가 만든 ID 기준으로 단언하는 등). 스키마·테이블 이름에 기대지 않는다.
- [x] `spotlessJavaCheck` 통과 후 `integrationTest`로 red 확인. 실패 이유가 "구현 없음"(404·누락)이어야 하고, 컴파일 오류나 설정 오류면 안 된다.
- [x] `spring-security-test` 의존성은 오케스트레이터가 사용자 잠금 해제 중에 추가한다. 없어서 컴파일이 안 되면 거기까지 쓰고 결과에 "의존성 대기"라고 적는다. `build.gradle.kts`는 고치지 않는다.
- [x] 결과에 테스트 수, red 확인 명령과 요약, 계약과 다르게 정한 것을 적는다.

## 참고

- `docs/proposals/mvp.md`, `docs/tasks/T-043-mvp-review-codex.md`(API 표), `AGENTS.md` 테스트 하네스 ①~⑤
- 끝나면 오케스트레이터가 원본을 `backend/src/acceptance/java/`로 옮긴다. 그 뒤로는 바이트 단위로 고정된다.

## 결과

- 새 파일: `backend/src/test/java/kr/needteacher/tutorprofile/TutorProfileAcceptanceIT.java` (16개 테스트).
- 검증: `cd backend; .\gradlew.bat spotlessJavaCheck` 통과.
- Red 재확인: `cd backend; .\gradlew.bat integrationTest --tests '*TutorProfileAcceptanceIT'` → 16개 실행·16개 실패. 모두 미구현 API의 404 응답, 컴파일·설정 오류 없음.
- 독립성: UUID 계정과 생성된 프로필 ID로 검증하며 DB 스키마/테이블 이름에 의존하지 않음.
- 추가 계약: 프로필 ID는 UUID 문자열(생성 응답에서 파싱 검증). 그 외 계약과 다르게 정한 것 없음.
- 재작업: 필터별 단독/조합·21개 기본 페이지·PATCH 교차 검증·상세/카드 공개 필드 단언 보강. 운영 코드·`build.gradle.kts`는 수정하지 않음.

## 재작업 (오케스트레이터 검토)

red는 오케스트레이터도 재현했다 (15개 실패, 모두 404). 원본은 옮기면 바이트로 고정되므로 아래를 먼저 고친다. 범위·규칙은 같다.

- [x] **P0 검색 필터가 실제로 검증되지 않는다.** `searchUsesOr...`의 세 번째 프로필은 동과 학년이 함께 달라서, 구현이 `gradeCodes`나 `subjectCodes`를 무시해도 통과한다. 필터마다 그 조건 하나만 다른 프로필을 둬서 제외되는지 확인한다 (동만 다름 / 과목만 다름 / 학년만 다름). 각 필터를 단독으로 줬을 때도 확인한다.
- [x] **P1 기본 페이지 크기 20이 검증되지 않는다** (`size <= 20`은 기본값이 5여도 통과). 공개 프로필을 21개 이상 만든 뒤, `limit` 없이 조회하면 정확히 20개와 `nextCursor`가 오는지 본다.
- [x] **P2 PATCH 병합 후 교차 검증:**
  - `REPORTED` 프로필을 `{"declarationType":"ENROLLED_EXEMPT"}`로만 바꾸면 `400`(`registrationNumber`).
  - `PER_SESSION`을 `PER_MONTH`로 바꾸면서 `sessionsPerMonth`가 없으면 `400`.
  - PATCH에서 알 수 없는 코드는 `400`.
- [x] **P2 공개 표시 단언:**
  - 상세에 `introduction`과 `tuition`(단위·분·횟수)이 있다.
  - 검색 카드에 `tuition`, `university`, `department`, 코드 목록이 있다.
- [x] **ID 형식:** 테스트가 없는 ID로 UUID를 쓰므로 프로필 ID는 UUID 문자열이다. 결과에 계약으로 명시한다.
- 결과 칸은 고쳐 쓴다. red 재확인 포함.
