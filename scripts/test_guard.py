"""guard.py 자체 검사: python scripts/test_guard.py"""
import json
import os
import subprocess
import sys
import tempfile

GUARD = os.path.join(os.path.dirname(os.path.abspath(__file__)), "guard.py")


def run(root, agent, payload, worker=False):
    args = [sys.executable, GUARD, "--agent", agent] + (["--worker"] if worker else [])
    raw = payload if isinstance(payload, str) else json.dumps(payload)
    p = subprocess.run(args, input=raw.encode("utf-8"), capture_output=True, env={**os.environ, "GUARD_ROOT": root})
    assert p.returncode == 0, p.stderr
    return json.loads(p.stdout) if p.stdout.strip() else None


def denied(out):
    return out is not None and (out.get("decision") == "deny" or
                                out.get("hookSpecificOutput", {}).get("permissionDecision") == "deny")


def bash(cmd):
    return {"tool_name": "Bash", "tool_input": {"command": cmd}}


def agy(name, args, root):
    # T-008에서 agy가 붙여 준 실제 훅 입력 형식
    return {"toolCall": {"name": name, "args": args}, "stepIdx": 1, "workspacePaths": [root]}


def main():
    root = tempfile.mkdtemp()
    outside = tempfile.mkdtemp()
    os.makedirs(os.path.join(root, ".github"))
    os.makedirs(os.path.join(root, "backend/src/test/java"))
    with open(os.path.join(root, ".github/CODEOWNERS"), "w", encoding="cp949") as f:  # 비 UTF-8 주석에도 잠기지 않는지
        f.write("# 보호 파일\n/scripts/ @Lcjam\n/.github/ @Lcjam\n/backend/pom.xml @Lcjam\n/backend/mvnw* @Lcjam\n"
                "/backend/src/test/java/**/architecture/ @Lcjam\n/backend/build.gradle.kts @Lcjam\n/backend/gradle/ @Lcjam\n"
                "/.claude/ @Lcjam\n")
    existing_test = os.path.join(root, "backend/src/test/java/ATest.java")
    open(existing_test, "w").close()

    deny_cmds = [
        "./mvnw verify -DskipTests", "mvn -Dpit.skip=true verify", "./gradlew build -x test",
        "mvn verify -Dmaven.test.failure.ignore=true", "git commit --no-verify -m x",
        "git commit -am x -n", "git -c core.hooksPath=/dev/null commit -m x", "gh pr review 3 --approve",
        "gh pr merge 3", "gh api repos/o/r/rulesets -X DELETE", "gh auth token",
        "echo x > scripts/verify.sh", "rm -rf scripts/", "sed -i s/90/10/ backend/pom.xml",
        "cp evil backend/mvnw", "Remove-Item .github\\CODEOWNERS", "touch .harness-unlock", "rm -rf backend",
        # T-007 Codex 지적: 인터프리터 한 줄, cd 뒤 상대 경로, git clean
        "python -c \"open('.harness-unlock','w').close()\"",
        "node -e \"require('fs').rmSync('scripts/guard.py')\"",
        "python3 -c \"import os; os.remove(os.path.join('backend','pom.xml'))\"",
        "powershell -EncodedCommand ZQBjAGgAbwAgAGgAaQA=",
        "cd backend && rm ../scripts/guard.py", "cd backend; cd src && rm -rf ../pom.xml",
        "git clean -fdx scripts",
        # T-009 Codex 지적: 문자열 조립, git -C
        "python -c \"open('.harness'+'-unlock','w').close()\"", "git -C backend clean -fdx ../scripts",
        # Gradle 전환: 초기화 스크립트·JVM 환경 변수·빌드 파일
        "./gradlew check -I evil.gradle.kts", "./gradlew check --init-script evil.gradle",
        "echo 'x' > ~/.gradle/init.d/skip.gradle.kts", "JAVA_TOOL_OPTIONS=-javaagent:x.jar ./gradlew check",
        "sed -i s/0.90/0.10/ backend/build.gradle.kts", "rm backend/gradle/wrapper/gradle-wrapper.jar",
        # T-011 Codex 지적: 붙여 쓴 -I, --exclude-task=, init.gradle, 빌드 속성 환경 변수, 조각낸 잠금 해제 이름
        "./gradlew check -Ievil.gradle.kts", "./gradlew check --exclude-task=test",
        "echo x > ~/.gradle/init.gradle.kts", "ORG_GRADLE_PROJECT_pitTargetClasses=x bash scripts/verify.sh full",
        "python -c \"open('.harness-un'+'lock','w')\"",
        # 훅 끄기: 저장소·홈의 에이전트 설정
        "echo '{\"disableAllHooks\": true}' > .claude/settings.local.json",
        "echo '{\"disableAllHooks\": true}' > ~/.claude/settings.json",
    ]
    allow_cmds = [
        "./mvnw -q verify", "git commit -m 'add tutor profile'", "gh pr create --fill",
        "bash scripts/verify.sh fast 2>&1 | tail -5", "cat backend/pom.xml", "rm -rf backend/target",
        "python scripts/test_guard.py", "python -c \"print(1+1)\"",
        f"cd {outside} && rm -rf backend .gitattributes", "cp scripts/guard.py /tmp/guard-copy.py",
        "git commit -F- <<EOF\nAdd scripts/verify.sh\nEOF",
        "./gradlew check --tests '*PingServiceTest'", "git grep -I -n TODO", "rm -rf backend/build",
        "python - <<'EOF'\nprint(open('docs/proposals/test-harness.md').read())\nEOF",
        "grep gradle README.md && python -I scripts/test_guard.py", "tar -x -f a.tar",
        "echo x > ~/.claude/projects/p/memory/note.md",
    ]
    for c in deny_cmds:
        assert denied(run(root, "claude", bash(c))), c
    for c in allow_cmds:
        assert run(root, "claude", bash(c)) is None, c

    edit = lambda p: {"tool_name": "Edit", "tool_input": {"file_path": p}}
    assert denied(run(root, "claude", edit(os.path.join(root, ".github", "workflows", "ci.yml"))))
    assert denied(run(root, "claude", edit("/" + root[0].lower() + root[2:].replace("\\", "/") + "/scripts/guard.py")))
    assert run(root, "claude", edit(os.path.join(root, "backend/src/main/java/A.java"))) is None
    arch = "backend/src/test/java/kr/needteacher/architecture/LayerTest.java"
    assert denied(run(root, "claude", edit(os.path.join(root, arch))))

    # worker: 기존 테스트 수정 금지, 새 테스트 추가 허용. 일반 세션은 둘 다 허용
    assert denied(run(root, "claude", edit(existing_test), worker=True))
    assert denied(run(root, "claude", bash("sed -i s/a/b/ backend/src/test/java/ATest.java"), worker=True))
    assert denied(run(root, "claude", bash("python -c \"open('backend/src/test/java/ATest.java','w')\""), worker=True))
    assert run(root, "claude", {"tool_name": "Write", "tool_input": {
        "file_path": os.path.join(root, "backend/src/test/java/BTest.java")}}, worker=True) is None
    assert run(root, "claude", edit(existing_test)) is None

    # Codex apply_patch
    patch = {"tool_name": "apply_patch", "tool_input": {"command": "*** Begin Patch\n*** Update File: scripts/guard.py\n@@\n"}}
    assert run(root, "codex", patch)["hookSpecificOutput"]["permissionDecision"] == "deny"

    # agy 실제 형식 (T-008): 래퍼를 풀고, 출력은 decision 형식
    out = run(root, "agy", agy("run_command", {"CommandLine": "echo -DskipTests"}, root))
    assert out == {"decision": "deny", "reason": out["reason"]}, out
    probe = os.path.join(root, ".github", "hook-probe.txt")
    assert denied(run(root, "agy", agy("write_to_file", {"TargetFile": probe, "CodeContent": "x"}, root)))
    assert run(root, "agy", agy("run_command", {"CommandLine": "git status"}, root)) is None
    assert run(root, "agy", agy("view_file", {"AbsolutePath": os.path.join(root, "scripts", "guard.py")}, root)) is None

    # 훅이 없는 다른 터미널·REPL로 우회 금지, 메시지 전달은 허용
    assert denied(run(root, "claude", {"tool_name": "mcp__wmux__repl_run", "tool_input": {"code": "fs.rmSync('x')"}}))
    assert denied(run(root, "codex", {"tool_name": "mcp__wmux__terminal_send", "tool_input": {"text": "ls"}}))
    assert run(root, "codex", "﻿" + json.dumps(bash("ls"))) is None  # PowerShell 파이프가 붙이는 BOM에도 판정
    assert run(root, "claude", {"tool_name": "mcp__wmux__send_message", "tool_input": {"message": "T-009"}}) is None

    # 판정 불가 = 차단 (깨진 입력, 도구 이름 없음)
    assert denied(run(root, "claude", "not json"))
    assert denied(run(root, "agy", {"unknown": {"CommandLine": "echo hi"}}))

    # 잠금 해제 파일이 있으면 보호 파일은 풀리지만 스킵 플래그는 여전히 금지
    open(os.path.join(root, ".harness-unlock"), "w").close()
    out = run(root, "claude", edit(os.path.join(root, "scripts", "verify.sh")))
    assert not denied(out) and "scripts/verify.sh" in out["systemMessage"], out  # 허용하되 사용자에게 알린다
    assert run(root, "claude", edit(os.path.join(root, "backend/src/main/java/A.java"))) is None
    home_settings = os.path.join(os.path.expanduser("~"), ".claude", "settings.json")
    assert denied(run(root, "claude", edit(home_settings)))  # 홈 설정은 잠금 해제와 무관하게 금지
    agy_settings = os.path.join(os.path.expanduser("~"), ".gemini", "antigravity-cli", "settings.json")
    assert denied(run(root, "agy", agy("write_to_file", {"TargetFile": agy_settings}, root)))  # toolPermission (T-028)
    assert denied(run(root, "claude", bash("./mvnw verify -DskipTests")))
    print("guard: all checks passed")


if __name__ == "__main__":
    main()
