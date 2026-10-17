---
id: T-053
owner: agy
status: done    # todo | doing | blocked | done
scope:
  - backend/src/main/java/kr/needteacher/common/**
  - backend/src/test/java/kr/needteacher/common/**
  - docs/tasks/T-053-tutor-profile-cleanup.md
---

## 목표

T-052 결과를 오케스트레이터가 확인하다 찾은 작은 정리 두 가지를 한다.

## 할 일

1. **`ConflictException(String message)` 생성자를 지운다.** 운영 코드에서는 아무도 부르지 않고 테스트만 쓴다(`GlobalExceptionHandlerTest`의 `"중복 충돌"` 사례). 그 테스트 사례도 함께 지운다.
2. **`GlobalExceptionHandler`의 매핑 중복을 없앤다.**
   - 지금은 400·404·405·415를 개별 override와 `handleExceptionInternal`의 `switch`가 두 번 매핑한다.
   - 하나만 남긴다. 권장은 `handleExceptionInternal` 하나에서 상태 코드로 code를 정하는 방식이다.
   - 목록에 없는 4xx(406, 413 등)가 `INTERNAL_ERROR`로 나가지 않게 한다. 예를 들어 4xx는 `BAD_REQUEST`, 5xx만 `INTERNAL_ERROR`로 한다.
   - 메시지는 지금처럼 상황별 한국어 문장을 유지한다.

## 지킬 것

- T-052에서 정한 code(`MALFORMED_REQUEST`, `UNSUPPORTED_MEDIA_TYPE`, `METHOD_NOT_ALLOWED`, `NOT_FOUND`, `INTERNAL_ERROR`)와 상태는 그대로 둔다.
- 다른 파일, 특히 `tutorprofile/**`, 보호 파일, 인수 테스트 실행 사본은 건드리지 않는다. 오케스트레이터가 방금 고친 파일들이다.

## 완료 조건

- [x] `bash scripts/verify.sh full --worktree` 통과.
- [x] `## 결과`에 바꾼 파일과 verify 수치(5줄 이내).

## 결과

- 바꾼 파일: `ConflictException.java`, `GlobalExceptionHandler.java`, `GlobalExceptionHandlerTest.java`.
- 1: `ConflictException(String)` 생성자 및 관련 테스트 케이스 제거 완료.
- 2: `GlobalExceptionHandler` 4개 개별 override 제거 및 `handleExceptionInternal` 단일화 (기타 4xx `BAD_REQUEST`, 5xx `INTERNAL_ERROR`, 상황별 한국어 메시지 유지).
- 검증: `verify.sh full --worktree` 통과 (line 100.00%, branch 92.98%, mutation 84.87%).
