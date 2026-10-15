#!/usr/bin/env python3
"""Claude·Codex·agy 공통 훅 판정기.

    python scripts/guard.py --agent claude|codex|agy [--worker] < hook_input.json

막을 때만 에이전트 형식의 deny 응답을 출력한다. 판정할 수 없으면 막는다.
보조 방어선이고 최종 판정은 CI다 (docs/proposals/test-harness.md 8절).
ponytail: 정적 문자열 판정이라 파일에 쓴 스크립트를 실행하는 식의 우회는 못 막는다. 그건 CI가 막는다.
"""
import argparse
import fnmatch
import json
import os
import re
import sys

ROOT = os.environ.get("GUARD_ROOT") or os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
# 사용자만 만드는 잠금 해제 파일. 보호 경로라 에이전트는 만들 수 없고, .gitignore에 있어 커밋되지 않는다.
UNLOCK = ".harness-unlock"

BAD_COMMANDS = [
    (r"(?i)harness-unlock", "잠금 해제 파일은 사용자만 다룬다"),
    (r"-D\S*skip", "테스트·검증 스킵 플래그"),
    (r"(?i:failure\.?ignore|ignore\.?failures)|--fail-never|(^|\s)-fn(\s|$)|(^|\s)-fae(\s|$)", "실패 무시 플래그"),
    (r"\bgradlew?(\.bat)?\b[^;&|\n]*\s(-x\s*\w|--exclude-task\b)", "Gradle 태스크 제외"),
    (r"--no-verify", "git 훅 우회"),
    (r"\bgit\b.*\bcommit\b.*\s-[a-zA-Z]*n\b", "git commit -n (훅 우회)"),
    (r"core\.hooksPath", "git 훅 경로 변경"),
    (r"\bgh\s+pr\s+(review|merge)\b", "PR 승인·병합은 사용자만"),
    (r"\bgh\s+api\b.*(/reviews|/merge|rulesets|/protection)", "리뷰·병합·보호 규칙 API는 사용자만"),
    (r"\bgh\s+auth\s+token\b|--show-token|\bgit\s+credential\b|credential-manager|\bcmdkey\b", "자격 증명 조회"),
    (r"(?i)\s-(enc|encodedcommand)\s", "인코딩된 명령"),
    # Gradle 초기화 스크립트는 모든 빌드(로컬 verify 포함)에서 테스트를 끌 수 있다
    (r"\bgradlew?(\.bat)?\b[^;&|\n]*\s(-I|--init-script)|init\.d|init\.gradle", "Gradle 초기화 스크립트"),
    (r"ORG_GRADLE_PROJECT_|JAVA_TOOL_OPTIONS|_JAVA_OPTIONS|JDK_JAVA_OPTIONS|JAVA_OPTS|GRADLE_OPTS|GRADLE_USER_HOME|MAVEN_OPTS",
     "빌드 속성·JVM 환경 변수 조작"),
]
# 저장소 밖에 있는 에이전트·빌드 전역 설정. 훅을 끄거나(disableAllHooks) 모든 빌드에 스크립트를 주입할 수 있다.
HOME_CONFIG = re.compile(
    r"/\.gradle/(init\.d|init\.gradle|gradle\.properties)|/\.claude/settings(\.local)?\.json$"
    r"|/\.codex/(config\.toml|hooks\.json)$|/\.gemini/(settings\.json$|config/|antigravity-cli/)",
    re.I,
)
WRITE_OPS = re.compile(
    r"\btee\b|\bsed\b.*\s-i|\b(rm|mv|cp|chmod|truncate|touch)\b|\bgit\b.*\s(rm|mv|checkout|restore|clean|reset|stash)\b"
    r"|Set-Content|Add-Content|Out-File|Remove-Item|Move-Item|Copy-Item|New-Item",
    re.I,
)
COPY_OPS = re.compile(r"cp|copy-item", re.I)  # 원본은 읽기만 하므로 마지막 인자(대상)만 본다
INLINE_CODE = re.compile(
    r"\b(python[0-9.]*|py|node|deno|perl|ruby|php|pwsh|powershell|bash|sh|zsh|cmd)(\.exe)?\b.*(\s(-c|-e|/c|-command)\b|<<)",
    re.I,
)
CD = re.compile(r"^\s*(cd|pushd|set-location)\s+(.+?)\s*$", re.I)
REDIRECT = re.compile(r"(?<![0-9&])>>?\s*['\"]?([^\s'\";&|]+)")
PATCH_FILE = re.compile(r"^\*\*\* (?:Add|Update|Delete) File: (.+)$|^\*\*\* Move to: (.+)$", re.M)
# 다른 터미널·REPL로 명령을 보내면 그쪽 세션에는 훅이 없다 (wmux repl_run, terminal_send)
BYPASS_TOOL = re.compile(r"repl_run|terminal_send", re.I)
COMMAND_TOOL = re.compile(r"bash|shell|command|exec|terminal|powershell", re.I)
WRITE_TOOL = re.compile(r"edit|write|patch|replace|create|delete|move|rename|notebook", re.I)
CMD_KEY = re.compile(r"command|cmd|script", re.I)
PATH_KEY = re.compile(r"path|file|target|destination", re.I)


def protected_patterns():
    # 패턴은 ASCII라 주석 인코딩이 깨져도 판정에는 영향 없음
    with open(os.path.join(ROOT, ".github", "CODEOWNERS"), encoding="utf-8", errors="replace") as f:
        pats = [line.split()[0] for line in f if line.strip() and not line.lstrip().startswith("#")]
    return pats + [UNLOCK]


def absolute(path, cwd):
    p = os.path.expanduser(path.strip().strip("'\"")).replace("\\", "/")
    m = re.match(r"^/([a-zA-Z])/(.*)", p)  # Git Bash 경로 /c/...
    if m:
        p = f"{m.group(1)}:/{m.group(2)}"
    return os.path.normpath(os.path.join(cwd, p))


def rel(path, cwd):
    try:
        return os.path.relpath(absolute(path, cwd), ROOT).replace("\\", "/")
    except ValueError:  # 다른 드라이브
        return "../"


def is_protected(rp, pats):
    rp = rp.lower()
    if rp.startswith(".."):
        return False
    for pat in pats:
        p = pat.strip("/").lower()
        # 보호 경로 자신·그 아래·그걸 품은 상위 디렉토리(rm -rf backend)
        if (rp in (".", p) or rp.startswith(p + "/") or p.startswith(rp + "/")
                or fnmatch.fnmatch(rp, p) or fnmatch.fnmatch(rp, p + "/*")):
            return True
    return False


def mentioned_protected(text, pats):
    # os.path.join('backend','pom.xml')처럼 조각낸 경로도 잡도록 경로 조각이 모두 등장하면 언급으로 본다
    s = text.lower().replace("\\", "/")
    for pat in pats:
        core = pat.strip("/").lower().split("**")[-1].strip("/").rstrip("*")
        if core and all(re.search(r"(?<![\w.-])" + re.escape(c) + r"(?![\w-])", s) for c in core.split("/")):
            return core
    return None


NOTICES = []  # 잠금 해제 상태에서 허용한 보호 파일 수정. 사용자 화면에 띄워 원치 않은 해제를 알아채게 한다


def check_paths(paths, cwd, ctx):
    for path in paths:
        rp = rel(path, cwd)
        if rp.startswith("..") and HOME_CONFIG.search(absolute(path, cwd).replace("\\", "/")):
            return f"에이전트·빌드 전역 설정 수정 금지: {path}"
        if is_protected(rp, ctx["pats"]):
            if not ctx["unlocked"]:
                return f"보호 파일 수정 금지: {rp} (사용자 승인 필요)"
            NOTICES.append(rp)
        if ctx["worker"] and "src/test/" in rp and os.path.exists(os.path.join(ROOT, rp)):
            return f"worker는 기존 테스트를 고칠 수 없음: {rp}"
    return None


def check_command(cmd, cwd, ctx):
    for pat, why in BAD_COMMANDS:
        if re.search(pat, cmd):
            return f"금지 명령: {why}"
    # 인라인 코드 안의 ; 는 셸 구분자가 아니므로 쪼개기 전에 명령 전체로 본다
    if INLINE_CODE.search(cmd):
        # '.harness-un'+'lock' 같은 조립. 문자 코드로 조립하면 못 막는다 (모듈 주석의 한계, 대신 NOTICES로 드러난다)
        if re.search(r"unlock|harness-un|un['\"]?\s*\+\s*['\"]?lock", cmd, re.I):
            return "인라인 코드로 잠금 해제 파일 접근 금지"
        core = mentioned_protected(cmd, ctx["pats"])
        if core and not ctx["unlocked"]:
            return f"인라인 코드로 보호 경로 접근 금지: {core}"
        if ctx["worker"] and "src/test/" in cmd.replace("\\", "/"):
            return "worker는 인라인 코드로 테스트를 고칠 수 없음"
    for seg in re.split(r"&&|\|\||;|\||\n", cmd):
        m = CD.match(seg)
        if m:  # cd backend && rm ../scripts/x 처럼 같은 명령 안에서 바뀌는 위치를 따라간다
            cwd = absolute(m.group(2), cwd)
            continue
        git_dir = re.search(r"\bgit\s+-C\s+['\"]?([^\s'\"]+)", seg)  # git -C backend clean ../scripts
        seg_cwd = absolute(git_dir.group(1), cwd) if git_dir else cwd
        targets = REDIRECT.findall(seg)
        op = WRITE_OPS.search(seg)
        if op:
            words = seg.split()
            targets += words[-1:] if COPY_OPS.fullmatch(op.group(0)) else words
        reason = check_paths(targets, seg_cwd, ctx)
        if reason:
            return reason
    return None


def walk(x, key=""):
    if isinstance(x, dict):
        for k, v in x.items():
            yield from walk(v, k)
    elif isinstance(x, list):
        for v in x:
            yield from walk(v, key)
    elif isinstance(x, str):
        yield key, x


def first(data, *keys):
    return next((data[k] for k in keys if k in data), None)


def judge(data, worker):
    # Claude·Codex: tool_name/tool_input/cwd. agy: {"toolCall": {"name", "args"}, "workspacePaths": [...]}
    if isinstance(data.get("toolCall"), dict):
        data = {**data, **data["toolCall"]}
    tool = first(data, "tool_name", "toolName", "name") or ""
    if not tool:
        return "도구 이름을 알 수 없어 차단 (훅 입력 형식 확인 필요)"
    if BYPASS_TOOL.search(tool):
        return f"다른 터미널·REPL로 명령 우회 금지: {tool}"
    inp = first(data, "tool_input", "toolInput", "args", "arguments", "input", "parameters")
    inp = data if inp is None else inp
    cwd = first(data, "cwd", "workspaceRoot") or (data.get("workspacePaths") or [ROOT])[0]
    ctx = {"pats": protected_patterns(), "unlocked": os.path.exists(os.path.join(ROOT, UNLOCK)), "worker": worker}
    for key, s in walk(inp):
        if "*** Begin Patch" in s:
            reason = check_paths([a or b for a, b in PATCH_FILE.findall(s)], cwd, ctx)
        elif COMMAND_TOOL.search(tool) and CMD_KEY.search(key):
            reason = check_command(s, cwd, ctx)
        elif WRITE_TOOL.search(tool) and PATH_KEY.search(key):
            reason = check_paths([s], cwd, ctx)
        else:
            reason = None
        if reason:
            return reason
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--agent", required=True, choices=["claude", "codex", "agy"])
    ap.add_argument("--worker", action="store_true")
    args = ap.parse_args()
    try:
        reason = judge(json.loads(sys.stdin.buffer.read().decode("utf-8-sig")), args.worker)
    except Exception as e:  # 판정 불가 = 차단
        reason = f"guard 판정 실패로 차단: {e!r}"
    # ensure_ascii: Windows 콘솔 인코딩(cp949)에서 출력이 깨져 훅이 실패(=허용)하는 걸 막는다
    if not reason:
        if NOTICES:
            msg = "잠금 해제 상태에서 보호 파일 수정: " + ", ".join(sorted(set(NOTICES)))
            if args.agent == "claude":
                print(json.dumps({"systemMessage": msg}))
            else:
                print(msg.encode("ascii", "backslashreplace").decode(), file=sys.stderr)
        return
    if args.agent == "agy":
        out = {"decision": "deny", "reason": reason}
    else:
        out = {"hookSpecificOutput": {"hookEventName": "PreToolUse", "permissionDecision": "deny",
                                      "permissionDecisionReason": reason}}
    print(json.dumps(out))


if __name__ == "__main__":
    main()
