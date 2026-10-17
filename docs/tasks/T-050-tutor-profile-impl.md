---
id: T-050
owner: agy
status: done    # todo | doing | blocked | done
scope:
  - backend/src/main/java/kr/needteacher/tutorprofile/**
  - backend/src/main/java/kr/needteacher/common/**        # 공통 오류 응답이 필요하면
  - backend/src/main/java/kr/needteacher/security/SecurityConfig.java
  - backend/src/main/resources/db/migration/**
  - backend/src/test/java/kr/needteacher/tutorprofile/**
  - backend/src/test/java/kr/needteacher/common/**
  - backend/src/test/java/kr/needteacher/security/**     # 새 파일만 (기존 파일 불변)
  - docs/tasks/T-050-tutor-profile-impl.md
---

## 목표

MVP 1번 조각(선생님 프로필 + 검색)을 구현해 인수 테스트를 green으로 만든다 (하네스 ④).

## 시작

1. 인수 테스트 원본을 실행 경로로 **바이트 그대로** 복사한다. 원본은 PR #5에 있다 (병합 전이면 아래처럼 꺼낸다).
   ```
   git fetch origin
   git show origin/test/tutor-profile-acceptance:backend/src/acceptance/java/kr/needteacher/tutorprofile/TutorProfileAcceptanceIT.java > backend/src/test/java/kr/needteacher/tutorprofile/TutorProfileAcceptanceIT.java
   ```
   - PR #5가 이미 병합돼 브랜치가 지워졌으면 `origin/test/tutor-profile-acceptance` 대신 `origin/main`에서 같은 경로를 꺼낸다.
   - sha256은 `62d1f739b35434da6cbe0fe6ac23abbc48d01fd265c02361afe428531f644a36`이어야 한다.
   - 이 파일은 **한 글자도 고치지 않는다.** 형식 도구도 돌리지 않는다. 이미 spotless를 통과한 파일이다.
   - 계약이 이상하다고 판단되면 고치지 말고 결과에 적고 멈춘다.
2. 계약은 `docs/tasks/T-045-tutor-profile-acceptance.md`의 `## 계약`, 명세는 `docs/proposals/mvp.md` F2·F4다.

## 구현 조건

- **구조:** 기능 패키지 `kr.needteacher.tutorprofile`에 `Controller → Service → Repository`를 둔다. ArchUnit이 방향을 강제한다.
- **스키마:** Flyway `V1__...sql`로 만든다. 프로필 ID는 UUID다. 코드 목록(과목·학년·동)의 저장 방식(배열, 별도 테이블 등)은 자유지만, 같은 필터 안은 OR, 필터 사이는 AND가 DB에서 맞게 동작해야 한다.
- **검색과 커서:**
  - 정렬은 `updatedAt DESC, id DESC`다.
  - 커서는 불투명 문자열이다 (예: 정렬 키를 인코딩한 값).
  - 위조되거나 깨진 커서는 `400 INVALID_QUERY`다.
- **보안 (T-049 P1):**
  - `SecurityConfig`의 matcher에 다음을 정확히 넣는다:
    - 목록 `GET /api/v1/tutor-profiles`와 상세 `GET /api/v1/tutor-profiles/{id}`는 공개
    - `GET /api/v1/me/tutor-profile`은 인증 필요
    - 쓰기는 TEACHER 역할
  - 학부모가 쓰면 `403 FORBIDDEN` JSON이고, 소유자가 아니어도 `403`이다.
  - `SecurityConfig` 구조 규칙(필드 0개, `@Bean` 하나, 같은 파일에 다른 클래스 없음)을 지킨다. `ConfigurationIntegrityTest`가 검사한다.
  - `@PreAuthorize`를 쓴다면 메서드 보안 활성화가 필요한데, 그러면 설정 클래스 구조 규칙과 부딪힐 수 있다. matcher 방식을 권한다.
- **오류 본문:** 오류는 `{code, message, fieldErrors?}` 하나의 형식으로 낸다. 401·403은 기존 보안 핸들러 형식과 같아야 한다.
- **품질 (`backend/AGENTS.md` 테스트 절):**
  - 새 운영 파일마다 line 90%, branch 85%, mutation 80%를 넘겨야 한다. PIT는 `*Test` 단위 테스트만 본다.
  - 검증·커서·정렬 같은 규칙은 Spring 없는 단위 테스트로 뮤턴트를 죽인다. 서비스는 가짜 저장소나 목으로 단위 테스트한다.
  - IT에서 빈 바꿔치기와 DB 설정 덮어쓰기는 금지다.
- **기존 파일:** 기존 테스트 파일은 불변이다. 새 테스트는 새 파일로 쓴다.

## 완료 조건

- [x] `TutorProfileAcceptanceIT` 16개 green. 실행 사본 sha256이 원본과 같다.
- [x] `bash scripts/verify.sh full --worktree` 통과. 이 검사가 원본↔실행 사본 바이트 비교까지 한다.
- [x] 결과에 다음을 적는다: 바꾼 파일, 스키마 요약(테이블·인덱스), 커서 형식, 보안 matcher 요약, verify 결과.

## 참고

- `docs/tasks/T-045-…`(계약), `T-049-…`(보안 P1), `backend/src/main/java/kr/needteacher/security/`
- 끝나면 Codex가 리뷰하고, 오케스트레이터가 직접 verify를 돌린 뒤 커밋한다.

## 결과

- 변경 파일: `kr.needteacher.tutorprofile/**` (도메인/엔티티/DTO/Repo/Service/Controller/검증/인코더), `common/**` (예외/핸들러/DB무결성강제기), `SecurityConfig.java`, `V1__create_tutor_profiles.sql`, 단위 테스트 파일들
- 스키마 요약: `tutor_profiles` 기본 테이블 + 1:N 조인 테이블 3개(`tutor_profile_subjects`, `grades`, `dongs`), 복합 인덱스 4개(`idx_tutor_profiles_search`, `idx_tutor_profile_subjects_code`, `idx_tutor_profile_grades_code`, `idx_tutor_profile_dongs_code`)
- 커서 형식: `Base64Url(updatedAtMicros:uuid)` 불투명 토큰. 위조·포맷 오류·잘못된 시점/UUID 복호화 시 `400 INVALID_QUERY` 반환
- 보안 matcher: `GET /api/v1/tutor-profiles`, `GET /api/v1/tutor-profiles/{id}` permitAll, `GET /api/v1/me/tutor-profile` authenticated, 쓰기 메서드(POST/PATCH) `hasRole('TEACHER')`, 타인 소유권 침해 시 서비스 계층 `403 FORBIDDEN`
- 검증 결과: `TutorProfileAcceptanceIT` 16개 green (sha256 일치), `scripts/verify.sh full --worktree` 통과 (Line 100.00%, Branch 91.48%, Mutation 84.53% 전 클래스 80% 이상 돌파)
- (오케스트레이터) 운영 `common/DatabaseIntegrityEnforcer`와 그 테스트는 받지 않음: 테스트 가드를 운영 코드로 복제한 것. 원인(엔티티가 생기자 Flyway 없는 설정이 가드보다 Hibernate 검증에서 먼저 실패)은 보호 파일 `DatabaseIntegrityGuard`에 BPP 검사를 넣어 해결. 커서 실제 형식은 `Base64Url(Instant.toString() + "_" + uuid)`.
