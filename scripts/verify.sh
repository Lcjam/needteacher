#!/usr/bin/env bash
# 단일 품질 게이트: scripts/verify.sh fast|full [--worktree]
# 명세: docs/proposals/test-harness.md 1·3·4절. 기본은 커밋될 인덱스(tree)만 검증한다.
# --worktree: 스테이징하지 않는 worker용. 작업 트리 전체(.gitignore 제외)를 임시 인덱스로 묶어 검증한다.
# 기준점은 VERIFY_BASE (기본 HEAD, CI에서는 PR의 base. 전부 0이면 첫 push).
set -euo pipefail

# Git이 비 ASCII 경로(예: 학생Test.java)를 따옴표·8진수로 인용하지 않게 강제 (T-040)
export GIT_CONFIG_COUNT=1 GIT_CONFIG_KEY_0=core.quotePath GIT_CONFIG_VALUE_0=false

mode=${1:-} src=${2:-}
[[ $mode == fast || $mode == full ]] && [[ -z $src || $src == --worktree ]] \
  || { echo "사용법: $0 fast|full [--worktree]" >&2; exit 2; }
repo=$(git rev-parse --show-toplevel)
cd "$repo"

fail() { echo "verify($mode) 실패: $*" >&2; exit 1; }

TESTS=':(glob)**/src/test/**'
MAIN_JAVA=':(glob)backend/src/main/java/**/*.java'
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# 검증 대상 tree. 끝에서 한 번 더 계산해 검증 중 바뀌었으면 실패시킨다.
current_tree() {
  if [[ $src == --worktree ]]; then
    local idx="$work/index.$1" real
    real=$(git rev-parse --git-path index)
    [[ -f $real ]] && cp "$real" "$idx"
    GIT_INDEX_FILE="$idx" git add -A && GIT_INDEX_FILE="$idx" git write-tree
  else
    git write-tree
  fi
}
tree=$(current_tree start)

empty=$(git hash-object -t tree --stdin </dev/null)
base=${VERIFY_BASE:-HEAD}
if [[ $base =~ ^0+$ ]]; then
  base=$empty
elif ! git rev-parse -q --verify "$base^{commit}" >/dev/null; then
  [[ -z ${VERIFY_BASE:-} ]] || fail "VERIFY_BASE가 유효한 커밋이 아님: $base"
  base=$empty  # 첫 커밋 전
fi
echo "verify($mode): tree ${tree:0:12}, 기준 ${base:0:12}"

PY=$(for p in python python3; do "$p" -c 1 >/dev/null 2>&1 && { echo "$p"; break; }; done || true)
[[ -n $PY ]] || fail "python 없음"

# 0. (로컬) Claude 훅이 설정으로 꺼져 있으면 실패. 스크립트를 거쳐 간접으로 쓴 경우까지 커밋 단계에서 드러낸다 (T-012)
# 줄 단위가 아니라 JSON으로 읽는다(줄바꿈 우회, T-015). 저장소 설정이 깨지면 Claude가 파일째 무시해 훅도 꺼지므로 그것도 실패.
if [[ -z ${CI:-} ]]; then
  "$PY" - .claude/settings.json .claude/settings.local.json "$HOME/.claude/settings.json" "$HOME/.claude/settings.local.json" <<'PYEOF' \
    || fail "Claude 훅이 꺼져 있거나 저장소 훅 설정이 깨짐"
import json, os, re, sys
bad = []
tools = ("Bash", "PowerShell", "Edit", "Write", "MultiEdit", "NotebookEdit", "mcp__wmux__repl_run", "mcp__wmux__terminal_send")
for i, path in enumerate(sys.argv[1:]):
    if not os.path.exists(path):
        if i == 0:
            bad.append(f"{path} 없음")
        continue
    try:
        data = json.load(open(path, encoding="utf-8"))
    except ValueError as e:
        bad.append(f"{path} JSON 깨짐: {e}")
        continue
    if data.get("disableAllHooks") is True:
        bad.append(f"{path}: disableAllHooks")
    if i == 0:
        guard_ok = False
        hooks = data.get("hooks", {}) if isinstance(data, dict) else {}
        pre = hooks.get("PreToolUse", []) if isinstance(hooks, dict) else []
        if isinstance(pre, list):
            for entry in pre:
                if not isinstance(entry, dict):
                    continue
                matcher = entry.get("matcher")
                if matcher in (None, "", "*"):
                    matches_all = True
                elif isinstance(matcher, str):
                    try:
                        rx = re.compile(matcher)
                        matches_all = all(rx.fullmatch(t) for t in tools)
                    except re.error:
                        matches_all = False
                else:
                    matches_all = False
                if matches_all:
                    hook_list = entry.get("hooks", [])
                    if isinstance(hook_list, list) and any(
                        isinstance(h, dict) and "scripts/guard.py" in h.get("command", "")
                        for h in hook_list
                    ):
                        guard_ok = True
                        break
        if not guard_ok:
            bad.append(f"{path}: guard.py 훅 없음")
for b in bad:
    print(b, file=sys.stderr)
sys.exit(1 if bad else 0)
PYEOF
fi

# 테스트를 줄이려면 같은 변경에서 보호 파일 quality/test-removals.txt에 경로를 추가해야 한다 (사람 리뷰)
allowed=$(git diff "$base" "$tree" -- quality/test-removals.txt | sed -n 's/^+\([^+#][^[:space:]]*\).*/\1/p')
is_allowed() { grep -qxF -- "$1" <<<"$allowed"; }

# 1. 테스트 무력화 패턴
if hits=$(git grep -nE '@(Disabled|Ignore|EnabledIf|EnabledOn)[A-Za-z]*|Assumptions\.|assume(True|False|That) *\(|assertTrue\( *true *\)' \
    "$tree" -- "$TESTS"); then
  fail "테스트 무력화 패턴"$'\n'"$hits"
fi

# (DB 검증 무력화 설정은 스냅샷을 만든 뒤 4-2에서, *IT의 실제 PostgreSQL 사용은 ArchUnit TestIntegrityTest가 본다)

# 2. 테스트 파일 삭제·이름 변경
while IFS=$'\t' read -r st old new; do
  is_allowed "$old" || fail "테스트 삭제·이름 변경($st): $old ${new:-}"
done < <(git diff --name-status -M --diff-filter=DR "$base" "$tree" -- "$TESTS")

# 2-2. 기존 테스트 파일은 불변 (추가·삭제·변경 모두 금지, 새 테스트는 새 파일로)
while read -r f; do
  is_allowed "$f" || fail "기존 테스트 파일 변경 (새 테스트는 새 파일로, 바꾸려면 등록): $f"
done < <(git diff --name-only --diff-filter=M "$base" "$tree" -- "$TESTS")

# 3. 파일별 테스트 메서드 수 래칫 (여러 줄 주석·한 줄 주석 안의 @Test는 세지 않는다)
count() {
  git show "$1" 2>/dev/null | "$PY" -c '
import re, sys
s = re.sub(r"/\*.*?\*/", "", sys.stdin.read(), flags=re.S)
s = re.sub(r"//[^\n]*", "", s)
print(len(re.findall(r"@(Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)(?![A-Za-z])", s)))'
}
while read -r f; do
  before=$(count "$base:$f") after=$(count "$tree:$f")
  (( after >= before )) || is_allowed "$f" || fail "테스트 메서드 감소 $before→$after: $f"
done < <(git diff --name-only --diff-filter=M "$base" "$tree" -- "$TESTS")

# 3-2. 인수 테스트 원본 검증 (docs/tasks/T-037·T-038)
ACC_ROOT="backend/src/acceptance"
ACC_JAVA="backend/src/acceptance/java"

# 원본 형식 검사 ($tree의 모든 원본은 모드 100644의 .java 파일)
while read -r fmode type obj path; do
  [[ -z ${path:-} ]] && continue
  if [[ $fmode != 100644 ]] || [[ $path != "$ACC_JAVA"/*.java ]]; then
    fail "인수 테스트 원본 형식(모드 100644 .java) 위반: $path"
  fi
done < <(git ls-tree -r "$tree" -- "$ACC_ROOT")

changed_acc=$(git diff --name-only --no-renames "$base" "$tree" -- "$ACC_ROOT")
changed_other=$(git diff --name-only --no-renames "$base" "$tree" | grep -vE '^(backend/src/acceptance/|docs/tasks/|quality/test-removals\.txt$)' || true)

if [[ -n $changed_acc && -z $changed_other ]]; then
  is_acc_only=true
else
  is_acc_only=false
fi

if ! $is_acc_only; then
  if [[ -n $changed_acc ]]; then
    fail "원본 변경은 별도 PR로 먼저 승인·병합"
  fi
  while read -r fmode type orig_blob path; do
    [[ -z ${path:-} ]] && continue
    rel=${path#"$ACC_JAVA"/}
    exec_path="backend/src/test/java/$rel"

    if is_allowed "$exec_path"; then
      continue
    fi

    exec_entry=$(git ls-tree -r "$tree" -- "$exec_path")
    if [[ -z $exec_entry ]]; then
      fail "인수 테스트 누락: $exec_path"
    fi

    read -r exec_mode exec_type exec_blob _ <<<"$exec_entry"
    if [[ $exec_mode != 100644 || $orig_blob != "$exec_blob" ]]; then
      fail "인수 테스트 원본 불일치: $exec_path"
    fi
  done < <(git ls-tree -r "$tree" -- "$ACC_ROOT")
fi

# 4. 스냅샷 + 시크릿
snap="$work/snap"
mkdir "$snap"
git archive "$tree" | tar -x -C "$snap"
command -v gitleaks >/dev/null || fail "gitleaks 미설치 (docs/setup.md)"
(cd "$snap" && gitleaks dir . --no-banner --redact) >&2 || fail "시크릿 탐지"

# 4-2. 설정으로 실제 DB 검증을 끄는 것 (T-013, T-014): 테스트 쪽 DB 설정 덮어쓰기, Flyway 끄기, ddl-auto 변경, 인메모리 DB
if [[ -d $snap/backend ]]; then
  "$PY" "$snap/scripts/quality_gate.py" --scan-config "$snap/backend" || fail "DB 검증 무력화 설정"
fi

# 5. 빌드·테스트 (스냅샷에서)
if [[ ! -f $snap/backend/build.gradle.kts ]]; then
  [[ $mode == fast ]] || fail "backend/build.gradle.kts 없음 (full은 빈 통과 금지)"
  echo "verify(fast): 백엔드 없음, 빌드 생략"
else
  git grep -qE '@AnalyzeClasses' "$tree" -- ':(glob)backend/src/test/java/**/architecture/**' || fail "ArchUnit 테스트 없음"
  # 빌드 산출물이 커밋돼 있으면 Gradle이 테스트를 UP-TO-DATE로 건너뛸 수 있다
  if git ls-tree -r --name-only "$tree" -- backend/build backend/.gradle | grep -q .; then
    fail "backend/build 또는 backend/.gradle이 추적되고 있음"
  fi
  # 변경된 운영 소스: kr/needteacher/ping/PingService 형식
  changed=$(git diff --name-only --diff-filter=AMR "$base" "$tree" -- "$MAIN_JAVA" \
    | sed 's|^backend/src/main/java/||; s|\.java$||')
  cd "$snap/backend"
  # 바깥 환경으로 빌드 속성·JVM 옵션을 주입하지 못하게 비운다 (ORG_GRADLE_PROJECT_pitTargetClasses 등)
  for v in $(compgen -e); do
    [[ $v == ORG_GRADLE_PROJECT_* || $v =~ ^(GRADLE_OPTS|JAVA_OPTS|JAVA_TOOL_OPTIONS|_JAVA_OPTIONS|JDK_JAVA_OPTIONS|GRADLE_USER_HOME)$ ]] \
      && unset "$v"
  done
  # 캐시·이전 결과 재사용 없이 모든 태스크를 실제로 실행한다
  gradle() { bash ./gradlew --no-daemon --no-build-cache --rerun-tasks --console=plain "$@"; }
  if [[ $mode == full ]]; then
    gradle -Pfull check || fail "빌드·형식·테스트·커버리지·뮤테이션"
  elif [[ -n $changed ]]; then
    # 변경 파일이 속한 패키지 전체를 PIT (같은 파일의 다른 최상위·내부 클래스 포함)
    targets=$(dirname $changed | sort -u | tr / . | sed 's/$/.*/' | paste -sd, -)
    gradle check pitest -PpitTargetClasses="$targets" || fail "빌드·형식·테스트·커버리지·뮤테이션(변경 패키지)"
  else
    gradle check || fail "빌드·형식·테스트·커버리지"
    echo "verify(fast): 변경된 운영 클래스 없음, PIT 생략"
  fi

  # 0건 통과 금지 (건너뛴 테스트는 실행으로 치지 않는다)
  executed() {
    local n=0 f t s
    for f in "$1"/TEST-*.xml; do
      [[ -f $f ]] || continue
      t=$(grep -o 'tests="[0-9]*"' "$f" | head -1 | tr -dc 0-9)
      s=$(grep -o 'skipped="[0-9]*"' "$f" | head -1 | tr -dc 0-9)
      n=$((n + ${t:-0} - ${s:-0}))
    done
    echo "$n"
  }
  (( $(executed build/test-results/test) > 0 )) || fail "실행된 단위 테스트 0건"
  (( $(executed build/test-results/integrationTest) > 0 )) || fail "실행된 통합 테스트(*IT) 0건"
  jacoco_xml=build/reports/jacoco/test/jacocoTestReport.xml
  mutations_xml=build/reports/pitest/mutations.xml
  if [[ $mode == full ]]; then
    (( $(grep -o '<class ' "$jacoco_xml" 2>/dev/null | wc -l) > 0 )) || fail "커버리지 측정 대상 0건"
    (( $(grep -o '<mutation ' "$mutations_xml" 2>/dev/null | wc -l) > 0 )) || fail "뮤턴트 0건"
  fi

  # 신규·변경 파일별 기준 + (full) 래칫 바닥값
  baseline=()
  [[ $mode == full ]] && baseline=(--baseline "$snap/quality/baseline.txt")
  # shellcheck disable=SC2086
  "$PY" "$snap/scripts/quality_gate.py" --jacoco "$jacoco_xml" --mutations "$mutations_xml" --src src/main/java \
    "${baseline[@]}" $changed || fail "신규·변경 코드 기준 또는 래칫 바닥값 미달"
  cd "$repo"
fi

[[ $(current_tree end) == "$tree" ]] || fail "검증 중 변경이 생김. 다시 실행"
echo "verify($mode): 통과"
