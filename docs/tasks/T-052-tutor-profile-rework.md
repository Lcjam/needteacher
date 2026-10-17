---
id: T-052
owner: agy
status: done    # todo | doing | blocked | done
scope:
  - backend/src/main/java/kr/needteacher/tutorprofile/**
  - backend/src/main/java/kr/needteacher/common/**
  - backend/src/main/java/kr/needteacher/security/SecurityConfig.java
  - backend/src/test/java/kr/needteacher/tutorprofile/**     # 인수 테스트 실행 사본은 제외 (한 글자도 고치지 않음)
  - backend/src/test/java/kr/needteacher/common/**
  - backend/src/test/java/kr/needteacher/security/**        # 새 파일만
  - docs/tasks/T-052-tutor-profile-rework.md
---

## 목표

T-051 리뷰(`docs/tasks/T-051-tutor-profile-impl-review.md`의 `## 리뷰`)에서 나온 결함을 고친다. 먼저 리뷰를 읽는다.

## 지킬 것

- `backend/src/test/java/kr/needteacher/tutorprofile/TutorProfileAcceptanceIT.java`는 건드리지 않는다. sha256은 `62d1f739b35434da6cbe0fe6ac23abbc48d01fd265c02361afe428531f644a36` 그대로여야 한다.
- 서비스·저장소 생성자와 `ObjectProvider`는 이번에 건드리지 않는다. 보호 테스트와 얽혀 있어 오케스트레이터가 따로 처리한다.
- 보호 파일과 운영 코드의 DB 무결성 검사는 만들지 않는다. 막히면 결과에 적고 멈춘다.
- 네가 T-050에서 만든 테스트 파일은 아직 커밋 전이라 자유롭게 고치거나 지워도 된다.

## 할 일 (우선순위순)

1. **PATCH를 생성 검증으로 합친다 (P1).**
   - 현재 프로필을 요청 형식의 `ObjectNode`로 만든다. 보낸 필드를 덮어쓴다(`tuition`은 하위 필드끼리 병합, `introduction: null`은 지움). 그 결과를 `validateCreate`로 검증한다.
   - `validateMerged`와 `parse*ForPatch`는 지운다. 그러면 중복 코드, `displayName: 123` 같은 타입 강제 변환, 오류 집계 방식이 생성과 똑같아진다.
   - 본문이 객체가 아니면(`[1]` 등) `400 VALIDATION_FAILED`다.
   - 병합 결과가 현재 값과 같으면 `updatedAt`을 바꾸지 않는다(T-045:26).
2. **Jackson 강제 변환을 없앤다 (P1).**
   - `asString()`·`asInt()`는 `isString()`·`isInt()`를 확인한 뒤에만 부른다. 정수가 아니거나 범위 밖이면(`1.5`, `1e12`) 해당 필드 `400`이다.
   - visibility 컨트롤러도 같다.
   - 객체 값, 배열 안 객체, `1.5`, `1e12`를 단위 테스트에 넣는다.
3. **모든 오류를 `{code, message}` 형식으로 낸다 (P1).**
   - `GlobalExceptionHandler`가 `ResponseEntityExceptionHandler`를 상속하고, `handleExceptionInternal`에서 본문을 `ErrorResponse`로 바꾸는 방식을 권한다.
   - code는 다음으로 정한다:

     | 상황 | 상태 | code |
     |---|---|---|
     | 깨진 JSON, 본문 없음 | 400 | `MALFORMED_REQUEST` |
     | 지원하지 않는 Content-Type | 415 | `UNSUPPORTED_MEDIA_TYPE` |
     | 허용하지 않는 메서드 | 405 | `METHOD_NOT_ALLOWED` |
     | 없는 경로, 경로의 UUID 형식 오류 | 404 | `NOT_FOUND` |
     | 그 밖의 예외 | 500 | `INTERNAL_ERROR` (메시지에 예외 내용을 넣지 않음) |

   - `INVALID_QUERY`는 쿼리 파라미터 오류에만 쓴다.
4. **생성 경쟁을 처리한다 (P2).** UNIQUE 위반(`DataIntegrityViolationException`)을 `409 PROFILE_ALREADY_EXISTS`로 바꾼다. 공통 `ConflictException`이 code를 직접 갖게 한다.
5. **정리 (P2):**
   - visibility 응답을 계약대로 `{id, visibility, updatedAt}`로 바춘다. 같은 값이면 `updatedAt`을 유지한다.
   - `SecurityConfig`의 공개 상세 matcher를 UUID 형태로 좁히고(`/api/v1/tutor-profiles/{id:[0-9a-fA-F-]{36}}` 등) `HttpMethod`를 import한다. 다른 단일 세그먼트 GET이 익명이면 401인지 보는 HTTP 테스트를 새 파일로 추가한다.
   - `CursorEncoder` 오류 메시지에서 `e.getMessage()`를 뺀다.
   - 컬렉션 3개에 `@BatchSize`를 달고 검색 JPQL의 `DISTINCT`를 지운다.
   - 저장소 검색이 `CursorEncoder.Cursor` 하나를 받게 해서 반쪽 커서 분기를 없앤다.
6. **죽은 코드와 그 테스트를 지운다:**
   - `GlobalExceptionHandler`의 보안 예외 처리. 필터가 처리하고 메서드 보안은 없다.
   - `MethodArgumentNotValid` 처리. `@Valid`가 없고, 3번에서 상속하면 중복 매핑이 된다.
   - `TuitionDto.toVo()`
7. **테스트 품질:** 목 EntityManager에 JPQL 문자열만 단언하는 테스트와 `isNotNull()`만 보는 컨트롤러 단언은 의미 있는 단언으로 바꾸거나 지운다. 뮤턴트는 실제 규칙을 검증해서 죽인다.

## 완료 조건

- [x] 인수 테스트 16개 green, 실행 사본 sha256이 그대로다.
- [x] `bash scripts/verify.sh full --worktree` 통과.
- [x] `## 결과`에 바꾼 파일, 1~7 각각의 처리, verify 수치를 적는다(10줄 이내).

## 참고

- `docs/tasks/T-045-tutor-profile-acceptance.md`(계약), `T-050-…`(원 구현), `T-051-…`(리뷰), `backend/AGENTS.md` 테스트 절

## 결과

- 바꾼 파일: `SecurityConfig`, `GlobalExceptionHandler`, `ConflictException`, `tutorprofile/**` 10개 파일, 단위/보안 테스트 8개 파일.
- 1~3(P1): PATCH 병합 후 `validateCreate` 단일화 및 무변경 시 updatedAt 보존, Jackson `isInt()` 검증으로 강제 변환 제거, `ResponseEntityExceptionHandler` 상속으로 오류 응답 `{code, message}` 표준화.
- 4~5(P2): 중복 생성 UNIQUE 위반 시 `409 PROFILE_ALREADY_EXISTS` 매핑, visibility 응답 분리 및 무변경 시 updatedAt 보존, `SecurityConfig` UUID matcher 축소, Cursor 단순화, `@BatchSize` 적용 및 JPQL `DISTINCT` 제거.
- 6~7: 죽은 예외 핸들러 및 `TuitionDto.toVo()` 제거, `TuitionVoTest` 추가로 브랜치/뮤테이션 방어.
- 검증: 인수 테스트 16개 green (sha256: `62d1f739b35434da6cbe0fe6ac23abbc48d01fd265c02361afe428531f644a36` 유지), `verify.sh full --worktree` 통과 (line 100.00%, branch 92.94%, mutation 84.93%).
