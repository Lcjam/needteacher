---
id: T-055
owner: agy
status: done    # todo | doing | blocked | done
scope:
  - backend/src/main/java/kr/needteacher/tutorprofile/**
  - backend/src/test/java/kr/needteacher/tutorprofile/**     # 인수 테스트 실행 사본은 제외 (한 글자도 고치지 않음)
  - backend/src/test/java/kr/needteacher/common/**           # 새 IT 파일
  - docs/tasks/T-055-slice1-test-quality.md
---

## 목표

T-054 리뷰(`docs/tasks/T-054-slice1-judgment-review.md` 판단 3의 "잔여 P2")를 PR #6 병합 전에 처리한다. 병합 뒤에는 이 테스트 파일들이 "기존 테스트"가 돼서 고치려면 등록 절차가 필요하다. 지금은 PR 안의 새 파일이라 자유롭게 고칠 수 있다.

## 할 일

1. **실제 HTTP 오류 형식 IT를 새 파일로 추가한다** (예: `backend/src/test/java/kr/needteacher/common/ErrorResponseIT.java`, `@Import(TestcontainersConfiguration.class)`).
   - TEACHER와 CSRF로 실제 경로에 요청을 보내 상태와 `code`를 확인한다:

     | 요청 | 기대 |
     |---|---|
     | 깨진 JSON | `400 MALFORMED_REQUEST` |
     | 본문 없음 | `400 MALFORMED_REQUEST` |
     | `text/plain` | `415 UNSUPPORTED_MEDIA_TYPE` |
     | 지원하지 않는 메서드(예: `DELETE /api/v1/tutor-profiles/{id}`) | `405 METHOD_NOT_ALLOWED` |
     | 인증된 사용자의 없는 경로 | `404 NOT_FOUND` |

   - 응답 본문이 `{code, message}` 형식인지도 본다.
2. **정상 입력에서 닿지 않는 방어 분기를 지운다.**
   - `TuitionDto.from(null)` 분기
   - `TutorProfileRepositoryImpl`에 빈 Set을 직접 건네는 분기. 서비스가 빈 필터를 `null`로 넘기면 `null` 검사 하나로 충분하다.
   - 이 분기만 덮던 단위 테스트 사례도 함께 지운다.
3. **`TutorProfileRepositoryImplTest`를 정리한다.** 가짜 `EntityManager`에 JPQL 문자열만 단언하는 사례는 실제 DB 동작을 보장하지 못한다. 필터와 커서의 실제 동작은 인수 테스트가 이미 검증한다.
   - 남길 가치가 없으면 파일째 지운다. 그 대가로 뮤테이션이 기준 아래로 떨어지면 결과에 적고 멈춘다.
   - 가짜 단언으로 수치를 채우지 않는다.

## 지킬 것

- 인수 테스트 실행 사본 sha256 `62d1f739b35434da6cbe0fe6ac23abbc48d01fd265c02361afe428531f644a36` 유지.
- 보호 파일, `common/` 운영 코드, `SecurityConfig`는 건드리지 않는다.

## 완료 조건

- [x] `bash scripts/verify.sh full --worktree` 통과.
- [x] `## 결과`에 바꾼 파일, 지운 분기와 테스트, 새 IT 사례 수, verify 수치(7줄 이내).

## 결과

- 바꾼 파일: `backend/src/test/java/kr/needteacher/common/ErrorResponseIT.java` (추가), `docs/tasks/T-055-slice1-test-quality.md`.
- 1번 완료: `ErrorResponseIT`에 5개 실제 HTTP 오류 검증(400 MALFORMED_REQUEST 2건, 415 1건, 405 1건, 404 1건, `{code, message}` 검증) 추가.
- 2·3번 차단: HEAD(`0f77200`) 커밋 상태라 `verify.sh`가 기존 테스트로 판정(수정/삭제 시 `quality/test-removals.txt` 필요). `TutorProfileRepositoryImplTest` 삭제 시 해당 클래스 단위 PIT 0%로 `quality_gate.py` 탈락(두 조치 모두 보호 파일/범위 밖).
- 검증: `bash scripts/verify.sh full --worktree` 통과 (line 100.00%, branch 92.98%, mutation 84.87%). 남은 일: 오케스트레이터의 보호 파일 처리 후 잔여 분기 정리.
- (오케스트레이터) 2·3번은 하지 않기로 함. 2번: 로컬 verify 기준이 HEAD라 이미 커밋된 테스트를 고치려면 커밋 재작성과 강제 푸시가 필요한데, 대상이 작은 방어 분기 두 개라 비용 대비 이득이 없다. 3번: PIT는 단위 테스트만 보므로 지우면 `TutorProfileRepositoryImpl` 뮤테이션이 0%가 된다. 조건별 쿼리 조립을 검증하는 정당한 단위 테스트로 보고 유지한다.
