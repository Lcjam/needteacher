#!/usr/bin/env bash
# verify.sh 자체 검사 (빌드 전 단계): bash scripts/test_verify.sh
set -uo pipefail
here=$(cd "$(dirname "$0")" && pwd)
t=$(mktemp -d)
trap 'rm -rf "$t"' EXIT
mkdir -p "$t/bin" "$t/repo/scripts" "$t/repo/quality" "$t/repo/backend/src/test/java"
cat >"$t/bin/gitleaks" <<'GITLEAKS'
#!/bin/sh
if [ "${CHECK_SNAPSHOT:-0}" = 1 ]; then
  [ "$1" = dir ] && [ "$2" = . ] || exit 1
  case "$PWD" in */snap) ;; *) exit 1 ;; esac
fi
exit "${FAKE_LEAK:-0}"
GITLEAKS
chmod +x "$t/bin/gitleaks"
export PATH="$t/bin:$PATH"
unset CI  # 로컬 실행을 흉내 낸다 (0번 로컬 훅 검사는 CI에서 건너뜀)
cp "$here/verify.sh" "$here/quality_gate.py" "$t/repo/scripts/"
cd "$t/repo"
git init -q && git config user.email t@t && git config user.name t && git config core.autocrlf false
mkdir -p .claude && printf '{"hooks": {"PreToolUse": [{"hooks": [{"command": "python scripts/guard.py"}]}]}}\n' >.claude/settings.json
T=backend/src/test/java/ATest.java
printf '@Test void a(){}\n@TestInstance\n@Test void b(){}\n' >$T
echo "# 테스트 삭제 등록" >quality/test-removals.txt
git add -A && git commit -qm init

fails=0
check() { # check <기대: 0 통과 | 1 실패> <설명>
  local out
  out=$(bash scripts/verify.sh "${MODE:-fast}" ${SRC:-} 2>&1)
  local got=$(($? != 0))
  ((got == $1)) || { echo "FAIL: $2 (기대 $1, 실제 $got): $out"; fails=$((fails + 1)); }
}
clean() { git reset -q --hard && git clean -qfd; }

check 0 "깨끗한 상태"
CHECK_SNAPSHOT=1 check 0 "gitleaks는 스냅샷 안에서 dir . 실행"
printf '@Test void a(){}\n@TestInstance\n' >$T && git add $T && check 1 "@Test 감소 (@TestInstance는 세지 않음)"
printf '@Test void a(){}\n@TestInstance\n// @Test void b(){}\n' >$T && git add $T && check 1 "주석 처리한 @Test는 세지 않음"
printf '@Test void a(){}\n@TestInstance\n/*\n@Test void b(){}\n*/\n' >$T && git add $T && check 1 "여러 줄 주석 안의 @Test도 세지 않음"
echo "$T" >>quality/test-removals.txt && git add quality && check 0 "감소 + 같은 변경에서 등록"
clean
printf '@Test void a_mod(){}\n@TestInstance\n@Test void b(){}\n' >$T && git add $T && check 1 "기존 줄 한 줄 바꾸기"
clean
printf '@Test void c(){}\n' >>$T && git add $T && check 1 "끝에 줄 추가만"
clean
printf 'if (System.getProperty("java.version") != null) return;\n' >>$T && git add $T && check 1 "조기 return 한 줄 삽입"
clean
printf '@Test void c(){}\n' >>$T && echo "$T" >>quality/test-removals.txt && git add -A && check 0 "추가 + 등록"
clean
printf '@Test void a_mod(){}\n@TestInstance\n@Test void b(){}\n' >$T && echo "$T" >>quality/test-removals.txt && git add -A && check 0 "바꾸고 등록"
clean
printf '@Disabled\n' >>$T && git add $T && check 1 "@Disabled 추가"
clean
printf 'assumeTrue (false);\n' >>$T && git add $T && check 1 "assume으로 건너뛰기"
clean
git rm -q $T && check 1 "테스트 파일 삭제"
echo "$T" >>quality/test-removals.txt && git add quality && check 0 "삭제 + 등록"
clean
git mv $T backend/src/test/java/BTest.java && check 1 "테스트 파일 이름 변경"
clean
printf '@Disabled\n' >>$T && check 0 "스테이징 안 된 변경은 검증 대상 아님"
SRC=--worktree && check 1 "--worktree는 스테이징 안 된 변경도 검증" && unset SRC
clean
printf '@Disabled @Test void c(){}\n' >backend/src/test/java/NewTest.java
SRC=--worktree && check 1 "--worktree는 추적 안 된 새 파일도 검증" && unset SRC
clean
echo "$T" >>quality/test-removals.txt && git add -A && git commit -qm reg
printf '@Test void a(){}\n' >$T && git add $T && check 1 "예전 커밋의 등록은 재사용 불가"
clean
export FAKE_LEAK=1 && check 1 "시크릿 탐지" && unset FAKE_LEAK
mkdir -p backend/src/test/resources && printf 'spring.flyway.enabled=false\n' >backend/src/test/resources/application.properties
git add -A && check 1 "테스트 설정으로 Flyway 끄기"
clean
mkdir -p backend/src/test/resources && printf 'spring:\n  jpa:\n    hibernate:\n      ddl-auto: update\n' >backend/src/test/resources/application.yml
git add -A && check 1 "ddl-auto 변경"
clean
mkdir -p backend/src/test/resources && printf 'spring:\n  flyway:\n    enabled: false\n' >backend/src/test/resources/application.yml
git add -A && check 1 "YAML 계층으로 Flyway 끄기 (T-014)"
clean
MODE=full && check 1 "full: 백엔드 없으면 빈 통과 금지" && unset MODE
printf '{"disableAllHooks": true}\n' >.claude/settings.local.json && check 1 "로컬에서 Claude 훅이 꺼져 있으면 실패"
printf '{\n  "disableAllHooks":\n    true\n}\n' >.claude/settings.local.json && check 1 "줄바꿈으로 나눠 써도 실패 (T-015)"
rm .claude/settings.local.json
cp .claude/settings.json "$t/settings.bak" && printf '{"hooks": {' >.claude/settings.json && check 1 "저장소 훅 설정이 깨진 JSON이면 실패"
printf '{"hooks": {}}\n' >.claude/settings.json && check 1 "저장소 훅 설정에서 guard.py가 빠지면 실패"
printf '{"hooks": {"PreToolUse": [{"matcher": "Read", "hooks": [{"command": "python scripts/guard.py"}]}]}}\n' >.claude/settings.json && check 1 "matcher가 Read에만 걸려 있으면 실패 (T-020)"
printf '{"hooks": {"PreToolUse": [{"matcher": "Bash|PowerShell|Edit|Write", "hooks": [{"command": "python scripts/guard.py"}]}]}}\n' >.claude/settings.json && check 1 "matcher가 MCP 도구를 빠뜨리면 실패 (T-026)"
printf '{"hooks": {"PreToolUse": [{"matcher": "Bash|PowerShell|Edit|Write|MultiEdit|NotebookEdit|mcp__wmux__repl_run", "hooks": [{"command": "python scripts/guard.py"}]}]}}\n' >.claude/settings.json && check 1 "matcher가 terminal_send를 빠뜨리면 실패 (T-026)"
cp "$t/settings.bak" .claude/settings.json
export VERIFY_BASE=deadbeef && check 1 "VERIFY_BASE가 잘못되면 실패" && unset VERIFY_BASE
export VERIFY_BASE=0000000000000000000000000000000000000000 && check 0 "첫 push(전부 0)는 빈 트리 기준" && unset VERIFY_BASE

# 인수 테스트 원본 검증 (T-037·T-038)
ACC=backend/src/acceptance/java/kr/needteacher/SampleTest.java
EXEC=backend/src/test/java/kr/needteacher/SampleTest.java
TASK=docs/tasks/T-100-sample.md

# 원본 비 Java·경로 위반·심볼릭 링크
mkdir -p backend/src/acceptance/java/kr/needteacher
printf 'not java\n' >backend/src/acceptance/java/kr/needteacher/readme.txt && git add -A && check 1 "원본 비 Java 파일"
clean
mkdir -p backend/src/acceptance
printf 'class Foo {}\n' >backend/src/acceptance/Foo.java && git add -A && check 1 "원본 java/ 하위 아님"
clean
link_blob=$(printf 'target' | git hash-object -w --stdin)
git update-index --add --cacheinfo 120000,"$link_blob",backend/src/acceptance/java/kr/needteacher/SymlinkTest.java
check 1 "원본 심볼릭 링크"
clean

# 원본만 추가 (원본 전용 변경) 통과
mkdir -p backend/src/acceptance/java/kr/needteacher docs/tasks
printf 'class SampleTest {}\n' >"$ACC"
printf 'task\n' >"$TASK"
git add "$ACC" "$TASK" && check 0 "원본만 추가"

# 원본 추가 + 운영 코드 같이 추가 -> 실패
mkdir -p backend/src/main/java/kr/needteacher
printf 'class Sample {}\n' >backend/src/main/java/kr/needteacher/Sample.java
git add -A && check 1 "원본 추가 + 운영 코드 같이 추가"
clean

# 원본 커밋 (승인·병합 상태 시뮬레이션)
mkdir -p backend/src/acceptance/java/kr/needteacher docs/tasks
printf 'class SampleTest {}\n' >"$ACC"
printf 'task\n' >"$TASK"
git add "$ACC" "$TASK" && git commit -qm "Add acceptance test original"

# 원본 대기 중 무관한 일반 변경 (복사본 누락) -> 실패
mkdir -p backend/src/main/java/kr/needteacher
printf 'class Unrelated {}\n' >backend/src/main/java/kr/needteacher/Sample.java
git add -A && check 1 "원본 대기 중 무관한 일반 변경"
clean

# 다음 변경에서 그대로 복사 -> 통과
mkdir -p backend/src/test/java/kr/needteacher
cp "$ACC" "$EXEC"
git add "$EXEC" && check 0 "다음 변경에서 그대로 복사"

# 복사본 한 글자 변경 -> 실패
printf 'class SampleTest { /* mod */ }\n' >"$EXEC"
git add "$EXEC" && check 1 "복사본 한 글자 변경"

# 복사본 조기 return 추가 -> 실패
printf 'class SampleTest { void t() { if (true) return; } }\n' >"$EXEC"
git add "$EXEC" && check 1 "복사본 조기 return 추가"

# 복사본 변경 + 대응 경로 등록 -> 통과
echo "$EXEC" >>quality/test-removals.txt
git add quality/test-removals.txt && check 0 "복사본 변경 + 대응 경로 등록"
clean

# 원본 수정 + 운영 코드 같이 -> 실패
mkdir -p backend/src/main/java/kr/needteacher
printf 'class SampleTest { /* orig mod */ }\n' >"$ACC"
printf 'class Sample {}\n' >backend/src/main/java/kr/needteacher/Sample.java
git add -A && check 1 "원본 수정 + 운영 코드 같이"
clean

# 원본 전용 수정 -> 통과
printf 'class SampleTest { /* orig mod */ }\n' >"$ACC"
git add "$ACC" && check 0 "원본 전용 수정"
clean

# 한글 이름 원본 및 공백 경로 검증 (T-040)
KOR_ACC=backend/src/acceptance/java/kr/needteacher/학생Test.java
KOR_EXEC=backend/src/test/java/kr/needteacher/학생Test.java

# 1. 한글 이름 원본 추가 -> 통과
printf 'class 학생Test {}\n' >"$KOR_ACC"
git add "$KOR_ACC" && check 0 "한글 이름 원본 추가"

# 2. 한글 원본 커밋 후 다음 변경에서 그대로 복사 -> 통과
git commit -qm "Add Korean acceptance test original"
mkdir -p backend/src/test/java/kr/needteacher
cp "$ACC" "$EXEC"
cp "$KOR_ACC" "$KOR_EXEC"
git add "$EXEC" "$KOR_EXEC" && check 0 "다음 변경에서 한글 이름 원본 그대로 복사"

# 3. 한글 이름 실행 테스트 커밋 후 변경(등록 없음) -> 실패
git commit -qm "Add execution tests including Korean"
mkdir -p backend/src/test/java/kr/needteacher
printf 'class 학생Test { /* mod */ }\n' >"$KOR_EXEC"
git add "$KOR_EXEC" && check 1 "한글 이름 기존 테스트 파일 변경(등록 없음)"
clean
# 경로가 인용되면 등록 목록과 안 맞아 실패한다 → 통과해야 quotePath 수정이 동작하는 것
printf 'class 학생Test { /* mod */ }\n' >"$KOR_EXEC" && echo "$KOR_EXEC" >>quality/test-removals.txt
git add -A && check 0 "한글 이름 기존 테스트 변경 + 등록"
clean

# 4. 공백이 든 원본 경로 검증
SPACE_ACC="backend/src/acceptance/java/kr/needteacher/공백 Test.java"
SPACE_EXEC="backend/src/test/java/kr/needteacher/공백 Test.java"
printf 'class 공백Test {}\n' >"$SPACE_ACC"
git add "$SPACE_ACC" && check 0 "공백이 든 원본 추가"
git commit -qm "Add space original"
mkdir -p backend/src/test/java/kr/needteacher
cp "$SPACE_ACC" "$SPACE_EXEC"
git add "$SPACE_EXEC" && check 0 "공백이 든 원본 그대로 복사"
clean

((fails == 0)) && echo "verify: all checks passed" || exit 1
