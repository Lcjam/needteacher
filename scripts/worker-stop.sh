#!/usr/bin/env bash
# worker 종료 게이트 (Claude Stop 훅). 작업 트리 기준 verify fast가 실패하면 종료를 막고 고치게 한다.
# 한 번 막힌 뒤에도 실패하면 무한 루프를 피하려고 종료는 허용하되, worker가 바꾼 카드를 강제로 blocked로 돌린다.
# 커밋은 pre-commit·CI가 다시 막고, 오케스트레이터는 done을 믿지 않고 직접 재검증한다 (CLAUDE.md).
input=$(cat)
root=$(git rev-parse --show-toplevel)
out=$(bash "$root/scripts/verify.sh" fast --worktree 2>&1) && exit 0
if grep -qE '"stop_hook_active" *: *true' <<<"$input"; then
  # --untracked-files=all: 카드 디렉토리가 통째로 미추적이어도 파일 단위로 본다 (T-012)
  git -C "$root" status --porcelain --untracked-files=all -- docs/tasks | sed 's/^...//' | while read -r card; do
    grep -q '^owner: *claude' "$root/$card" 2>/dev/null \
      && sed -i 's/^status:.*/status: blocked   # worker-stop: verify fast 실패/' "$root/$card"
  done
  echo "verify 여전히 실패. 카드를 blocked로 돌렸다" >&2
  exit 0
fi
printf '%s\n종료 전에 verify fast를 통과시켜라 (기존 테스트 수정 금지).\n' "$(tail -40 <<<"$out")" >&2
exit 2
