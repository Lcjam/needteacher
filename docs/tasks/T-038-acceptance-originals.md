---
id: T-038
owner: agy
status: done    # todo | doing | blocked | done
scope:
  - scripts/verify.sh
  - scripts/test_verify.sh
  - .github/CODEOWNERS
  - AGENTS.md
  - backend/AGENTS.md
  - docs/proposals/test-harness.md
  - docs/tasks/T-038-acceptance-originals.md
---

## 목표

Codex의 T-037 설계를 구현한다. 사용자 결정은 "원본 비교만": 승인된 인수 테스트 원본은 CI가 바이트 단위로 비교해 변조를 막는다. T-037의 "원본 없는 새 테스트 파일 거부" 규칙은 넣지 않는다 (구현 담당이 테스트를 더 추가하는 건 허용).

## 완료 조건

- [x] `.github/CODEOWNERS`에 `/backend/src/acceptance/ @Lcjam` 추가. Gradle 소스셋에는 넣지 않는다 (기본 소스셋이 아니라 빌드에서 안 돈다. `build.gradle.kts`는 건드리지 않음).
- [x] `verify.sh`: Gradle 빌드 전에 아래를 `$base`·`$tree` 기준으로 판정 (원본 `backend/src/acceptance/java/<rel>` ↔ 실행 `backend/src/test/java/<rel>`).
  - 원본 전용 변경: `$base..$tree` 변경 경로가 `backend/src/acceptance/**`, `docs/tasks/**`, `quality/test-removals.txt`뿐이고 원본 변경이 한 건 이상 → 대응 실행 파일의 부재·불일치를 허용.
  - 일반 변경(그 밖의 모든 변경): 원본 폴더가 바뀌었으면 실패 "원본 변경은 별도 PR로 먼저 승인·병합". `$tree`의 모든 원본에 대해 대응 실행 파일이 있어야 하고(없으면 "인수 테스트 누락: <경로>") 블롭이 같아야 한다(다르면 "인수 테스트 원본 불일치: <경로>"). 대응 실행 경로를 같은 변경의 `quality/test-removals.txt`에 등록했으면 예외.
  - 원본은 Git 일반 파일(모드 `100644`)이고 `.java`여야 한다. 심볼릭 링크·비 Java 파일은 실패.
- [x] `test_verify.sh` 사례: 원본만 추가 → 통과 / 다음 변경에서 그대로 복사 → 통과 / 복사본 누락·한 글자 변경·조기 `return` 추가 → 각각 실패 / 대응 경로 등록 → 통과 / 원본 수정 + 운영 코드 같이 → 실패 / 원본 대기 중 무관한 일반 변경 → 실패 / 원본 심볼릭 링크·비 Java → 실패. 기존 사례가 모두 그대로 맞는지.
- [x] 문서: `AGENTS.md` 테스트 하네스 "순서"를 새 흐름으로 (① Codex가 `src/test/java`에 인수 테스트 작성·red 확인 → ② 사용자 잠금 해제 중 오케스트레이터가 `src/acceptance/java`로 이동, 잠금 후 원본만 커밋 → ③ 원본 PR 코드오너 승인·병합 → ④ 구현 담당이 원본을 같은 경로로 복사하고 구현 → ⑤ CI 바이트 비교). 원본 대기 중에는 다른 일반 PR을 병합할 수 없다는 점, 요구 변경은 원본 전용 PR로 먼저. `backend/AGENTS.md` 테스트 절, `docs/proposals/test-harness.md` 3절 표·구현 상태도.
- [x] `bash scripts/test_verify.sh`, `python scripts/quality_gate.py --self-test`, `python scripts/test_guard.py`, `python scripts/test_hooks.py` 통과, 건수 기록.

## 참고

- `docs/tasks/T-037-acceptance-original-design.md` (`## 명세` 절이 근거, 단 "원본 없음" 규칙은 제외)
- 보호 파일은 지금 잠금 해제 상태.

## 결과

- 바꾼 파일: `.github/CODEOWNERS`, `scripts/verify.sh`, `scripts/test_verify.sh`, `AGENTS.md`, `backend/AGENTS.md`, `docs/proposals/test-harness.md`, `docs/tasks/T-038-acceptance-originals.md`
- 구현: `.github/CODEOWNERS`에 `/backend/src/acceptance/ @Lcjam` 등록. `scripts/verify.sh` Step 3-2에 인수 원본 형식 검사(100644, .java) 및 원본 전용/일반 변경 분류, 원본 ↔ 실행 테스트 바이트 비교 검증 추가 (`quality/test-removals.txt` 등록 시 예외).
- 검증: `test_verify.sh` 45건(새 사례 12건 포함: 원본 추가·복사·누락·1자변경·조기return·등록·운영코드동시·원본대기중일반변경·심볼릭링크 등) 전체 통과. `quality_gate.py --self-test`, `test_guard.py`(41 asserts), `test_hooks.py`(21 asserts) 전체 통과.
- 남은 일: Claude 검토 및 커밋, Codex 리뷰.
