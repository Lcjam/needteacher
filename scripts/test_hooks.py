"""Smoke-test the configured PreToolUse commands through the platform shells."""

import argparse
import json
import os
from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parent.parent
CWD = ROOT / "backend" / "src"
AGY_CWD = ROOT / ".agents"


def commands(path, agent):
    config = json.loads(path.read_text(encoding="utf-8"))
    groups = config["guard"]["PreToolUse"] if agent == "agy" else config["hooks"]["PreToolUse"]
    hooks = [hook for group in groups for hook in group["hooks"]]
    assert hooks, f"{path}: no PreToolUse hooks"
    for hook in hooks:
        assert hook["type"] == "command", f"{path}: expected a command hook"
        key = "commandWindows" if agent == "codex" and os.name == "nt" else "command"
        yield hook[key]


def inputs(agent):
    skip = "-D" + "skipTests"
    if agent == "agy":
        return (
            ({"toolCall": {"name": "mcp__wmux__repl_run", "args": {"code": "1"}},
              "stepIdx": 1, "workspacePaths": [str(ROOT)]}, True),
            ({"toolCall": {"name": "run_command", "args": {"CommandLine": f"./gradlew test {skip}"}},
              "stepIdx": 1, "workspacePaths": [str(ROOT)]}, True),
            ({"toolCall": {"name": "run_command", "args": {"CommandLine": "git status"}},
              "stepIdx": 1, "workspacePaths": [str(ROOT)]}, False),
        )
    return (
        ({"tool_name": "mcp__wmux__repl_run", "tool_input": {"code": "1"}, "cwd": str(CWD)}, True),
        ({"tool_name": "Bash", "tool_input": {"command": f"./gradlew test {skip}"}, "cwd": str(CWD)}, True),
        ({"tool_name": "Bash", "tool_input": {"command": "git status"}, "cwd": str(CWD)}, False),
    )


def shells(agent):
    if os.name == "nt":
        if agent == "claude":
            git_bash = Path(os.environ["ProgramFiles"]) / "Git" / "bin" / "bash.exe"
            assert git_bash.is_file(), f"Git Bash not found: {git_bash}"
            return ((str(git_bash), "-c"),)
        if agent == "agy":
            return (("cmd", "/c"),)
        return (("powershell", "-NoProfile", "-Command"), ("cmd", "/c"))
    return (("sh", "-c"),)


def check(agent, command, shell, payload, deny, cmd_list=False):
    # agy passes the whole command as one /c argument on Windows. Keep the
    # string invocation too, since it exercises a different quoting path.
    invocation = (f"cmd /c {command}" if shell[0] == "cmd" and not cmd_list
                  else [*shell, command])
    process = subprocess.run(
        invocation,
        input=json.dumps(payload).encode("utf-8"),
        cwd=AGY_CWD if agent == "agy" else CWD,
        env={**os.environ, "CLAUDE_PROJECT_DIR": str(ROOT)},
        capture_output=True,
        check=False,
    )
    label = f"{agent} / {shell[0]}{'-list' if cmd_list else ''} / {'deny' if deny else 'allow'}"
    assert process.returncode == 0, f"{label}: exit {process.returncode}; stderr={process.stderr!r}"
    assert not process.stderr.strip(), f"{label}: stderr={process.stderr!r}"
    if deny:
        assert process.stdout.strip(), f"{label}: missing deny output"
        output = json.loads(process.stdout.decode("utf-8-sig"))
        decision = (output.get("decision") if agent == "agy" else
                    output.get("hookSpecificOutput", {}).get("permissionDecision"))
        assert decision == "deny", f"{label}: {output!r}"
    else:
        assert not process.stdout.strip(), f"{label}: stdout={process.stdout!r}"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--codex-hooks", type=Path, default=ROOT / ".codex" / "hooks.json",
                        help="alternate config for mutation checks")
    parser.add_argument("--agy-hooks", type=Path, default=ROOT / ".agents" / "hooks.json",
                        help="alternate agy config for mutation checks")
    args = parser.parse_args()
    configs = {
        "claude": ROOT / ".claude" / "settings.json",
        "codex": args.codex_hooks,
        "agy": args.agy_hooks,
    }
    assert CWD.is_dir(), f"missing test cwd: {CWD}"
    assert AGY_CWD.is_dir(), f"missing test cwd: {AGY_CWD}"
    for agent, path in configs.items():
        for command in commands(path, agent):
            for shell in shells(agent):
                for payload, deny in inputs(agent):
                    check(agent, command, shell, payload, deny)
                    if agent == "agy" and os.name == "nt":
                        check(agent, command, shell, payload, deny, cmd_list=True)
    print("hooks: all checks passed")


if __name__ == "__main__":
    main()
