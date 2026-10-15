#!/usr/bin/env bash
# 에이전트용 GitHub App 설치 토큰(1시간) 발급. 명세: docs/proposals/test-harness.md 7절, 준비: docs/setup.md 2절
#   GH_TOKEN=$(bash scripts/gh-app-token.sh) gh pr create --fill
#   git push "https://x-access-token:$(bash scripts/gh-app-token.sh)@github.com/Lcjam/needteacher.git" HEAD
# 환경 변수: NT_APP_ID(App ID), NT_APP_KEY(개인키 .pem 경로, 저장소 밖), NT_REPO(기본 Lcjam/needteacher)
# 자체 검사: bash scripts/gh-app-token.sh --self-test
set -euo pipefail

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

jwt() { # jwt <app id> <key.pem>
  local now unsigned
  now=$(date +%s)
  unsigned="$(printf '{"alg":"RS256","typ":"JWT"}' | b64url).$(printf '{"iat":%d,"exp":%d,"iss":"%s"}' \
    $((now - 60)) $((now + 540)) "$1" | b64url)"
  printf '%s.%s\n' "$unsigned" "$(printf '%s' "$unsigned" | openssl dgst -sha256 -sign "$2" | b64url)"
}

if [[ ${1:-} == --self-test ]]; then
  t=$(mktemp -d)
  trap 'rm -rf "$t"' EXIT
  openssl genrsa -out "$t/k.pem" 2048 2>/dev/null && openssl rsa -in "$t/k.pem" -pubout -out "$t/pub.pem" 2>/dev/null
  IFS=. read -r h p s <<<"$(jwt 12345 "$t/k.pem")"
  unb64() { local x; x=$(tr -- '-_' '+/' <<<"$1"); while (( ${#x} % 4 )); do x+='='; done; openssl base64 -d -A <<<"$x"; }
  unb64 "$s" >"$t/sig"
  printf '%s.%s' "$h" "$p" | openssl dgst -sha256 -verify "$t/pub.pem" -signature "$t/sig" >/dev/null || { echo "서명 검증 실패"; exit 1; }
  unb64 "$p" | grep -q '"iss":"12345"' || { echo "iss 누락"; exit 1; }
  echo "gh-app-token: all checks passed"
  exit 0
fi

: "${NT_APP_ID:?NT_APP_ID 필요 (docs/setup.md 2절)}" "${NT_APP_KEY:?NT_APP_KEY 필요 (개인키 경로)}"
token=$(jwt "$NT_APP_ID" "$NT_APP_KEY")
api() { curl -sSf -H "Authorization: Bearer $token" -H "Accept: application/vnd.github+json" "$@"; }
PY=$(for p in python python3; do "$p" -c 1 >/dev/null 2>&1 && { echo "$p"; break; }; done || true)
field() { "$PY" -c "import json, sys; print(json.load(sys.stdin)['$1'])"; }  # 최상위 키만
inst=$(api "https://api.github.com/repos/${NT_REPO:-Lcjam/needteacher}/installation" | field id)
api -X POST "https://api.github.com/app/installations/$inst/access_tokens" | field token
