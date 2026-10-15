"""품질 게이트 (리포트 분석). 빌드가 하는 고정 기준(90/85/80) 위에 두 가지를 더 막는다.

1. 신규·변경 소스 파일마다 따로 line 90 / branch 85 / mutation 80 (사용자 결정).
   기존 코드의 높은 점수가 새 코드의 낮은 점수를 가리지 못하게 한다. 같은 파일의 모든 최상위·내부 클래스를 포함한다.
2. 래칫: 프로젝트 전체 수치가 quality/baseline.txt 바닥값 아래로 내려가면 실패 (full에서만).

    python scripts/quality_gate.py --jacoco X --mutations Y --src DIR [--baseline FILE] [변경 파일 ...]
    python scripts/quality_gate.py --self-test

변경 파일은 소스 루트 기준 확장자 없는 경로 (kr/needteacher/ping/PingService).
"""
import argparse
import os
import re
import sys
import tempfile
import xml.etree.ElementTree as ET

LINE, BRANCH, MUTATION = 0.90, 0.85, 0.80
# build.gradle.kts의 커버리지·뮤테이션 제외 목록과 같아야 한다
EXCLUDED = {"kr/needteacher/BackendApplication"}
# 실행 코드: 클래스류 선언 또는 메서드·생성자 본문 (인터페이스의 default/static 메서드 포함)
HAS_CODE = re.compile(r"\b(class|enum|record)\s+\w+|\)\s*(throws\s[^{;]*)?\{|\bstatic\s*\{")


# 설정으로 실제 DB 검증을 끄는 것 (T-013, T-014, T-020). 테스트 쪽은 TestcontainersConfiguration이 DB를 대신하므로 덮어쓸 이유가 없다.
TEST_CONFIG_WORDS = re.compile(
    r"\b(flyway|datasource|ddl-auto|ddl_auto|hbm2ddl|schema-generation|allow-bean-definition-overriding|sql\.init|database-platform)\b",
    re.I,
)
TEST_JAVA_FORBIDDEN = re.compile(
    r'"spring\.(flyway|datasource|sql\.init|test\.database)|"spring\.jpa\.hibernate\.ddl-auto'
    r"|AutoConfigureTestDatabase|jdbc:(h2|hsqldb|derby)"
    r"|hbm2ddl|schema-generation|allow-bean-definition-overriding",
    re.I,
)


def flatten_config(text, yaml):
    """properties 또는 YAML(들여쓰기 계층)을 (점으로 이은 키, 값) 목록으로"""
    entries, stack = [], []
    for raw in text.splitlines():
        line = raw.split(" #")[0].rstrip()
        if not line.strip() or line.lstrip().startswith(("#", "!", "---", "- ")):
            continue
        if not yaml:
            m = re.match(r"\s*([^=:\s]+)\s*[=:\s]\s*(.*)", line)
            if m:
                entries.append((m.group(1), m.group(2).strip()))
            continue
        indent = len(line) - len(line.lstrip())
        key, sep, val = line.strip().partition(":")
        if not sep:
            continue
        while stack and stack[-1][0] >= indent:
            stack.pop()
        key = key.strip().strip("'\"")
        val = val.strip().strip("'\"")
        if val:
            entries.append((".".join([k for _, k in stack] + [key]), val))
        else:
            stack.append((indent, key))
    return entries


def config_problems(backend):
    found = []
    for base, kind in (("src/test", "test"), ("src/main/resources", "main")):
        for dirpath, _, names in os.walk(os.path.join(backend, base)):
            for name in names:
                path = os.path.join(dirpath, name)
                rel = os.path.relpath(path, backend).replace("\\", "/")
                with open(path, encoding="utf-8", errors="replace") as fh:
                    text = fh.read()
                if kind == "test" and name.endswith(".java"):
                    if "/architecture/" in rel:  # 금지 규칙을 정의하는 보호 파일(ArchUnit)이라 이름이 들어 있다
                        continue
                    m = TEST_JAVA_FORBIDDEN.search(text)
                    if m:
                        found.append(f"{rel}: 테스트 코드에서 DB 설정 덮어쓰기 금지 ({m.group(0)})")
                elif not name.endswith((".properties", ".yml", ".yaml")):
                    continue
                elif kind == "test":
                    m = TEST_CONFIG_WORDS.search(text)
                    if m:
                        found.append(f"{rel}: 테스트 리소스에서 DB 설정 덮어쓰기 금지 ({m.group(0)})")
                else:
                    # 1차 방어. 최종 판정은 Spring이 실제 적용한 값을 보는 architecture/DatabaseIntegrityIT (T-015)
                    for key, val in flatten_config(text, name.endswith((".yml", ".yaml"))):
                        k, v = key.lower(), val.lower()
                        if v.startswith(("{", "[")):
                            found.append(f"{rel}: {key}: {val} (설정 YAML은 블록 형식만, 인라인 맵 금지)")
                        elif k.endswith("flyway.enabled") and v == "false":
                            found.append(f"{rel}: {key}={val} (Flyway 끄기 금지)")
                        elif (k.endswith("ddl-auto") or "hbm2ddl" in k) and v != "validate":
                            found.append(f"{rel}: {key}={val} (ddl-auto는 validate만)")
                        elif "schema-generation" in k and k.endswith(".action") and v != "none":
                            found.append(f"{rel}: {key}={val} (schema-generation action은 none만)")
                        elif "allow-bean-definition-overriding" in k and v == "true":
                            found.append(f"{rel}: {key}={val} (빈 정의 덮어쓰기 금지)")
                        elif re.search(r"jdbc:(h2|hsqldb|derby)", v):
                            found.append(f"{rel}: {key}={val} (인메모리 DB 금지)")
    return found


def ratio(covered, total):
    return covered / total if total else None


def jacoco_files(jacoco):
    """{패키지/파일명(확장자 없음): {LINE: (covered, total), BRANCH: ...}}, 프로젝트 전체 카운터"""
    root = ET.parse(jacoco).getroot()
    files = {}
    for pkg in root.iter("package"):
        for sf in pkg.findall("sourcefile"):
            key = pkg.get("name") + "/" + sf.get("name").rsplit(".", 1)[0]
            files[key] = {c.get("type"): (int(c.get("covered")), int(c.get("covered")) + int(c.get("missed")))
                          for c in sf.findall("counter")}
    total = {c.get("type"): (int(c.get("covered")), int(c.get("covered")) + int(c.get("missed")))
             for c in root.findall("counter")}
    return files, total


def mutation_files(mutations):
    """{패키지/파일명: (detected, total)}, 프로젝트 전체 (detected, total). NON_VIABLE 제외"""
    files, det_all, tot_all = {}, 0, 0
    if not os.path.exists(mutations):
        return None, None
    for m in ET.parse(mutations).getroot().iter("mutation"):
        if m.get("status") == "NON_VIABLE":
            continue
        pkg = (m.findtext("mutatedClass") or "").rsplit(".", 1)[0].replace(".", "/")
        key = pkg + "/" + (m.findtext("sourceFile") or "").rsplit(".", 1)[0]
        det, tot = files.get(key, (0, 0))
        hit = m.get("detected") == "true"
        files[key] = (det + hit, tot + 1)
        det_all, tot_all = det_all + hit, tot_all + 1
    return files, (det_all, tot_all)


def problems(jacoco, mutations, src, changed, baseline=None):
    found = []
    cov_files, cov_total = jacoco_files(jacoco)
    mut_files, mut_total = mutation_files(mutations)
    for f in changed:
        if f in EXCLUDED:
            continue
        cov = cov_files.get(f)
        if cov is None:
            with open(os.path.join(src, f + ".java"), encoding="utf-8", errors="replace") as fh:
                if HAS_CODE.search(fh.read()):
                    found.append(f"{f}: 실행 코드가 있는데 커버리지 리포트에 없음")
            continue
        for kind, need in (("LINE", LINE), ("BRANCH", BRANCH)):
            r = ratio(*cov.get(kind, (0, 0)))
            if r is not None and r < need:
                found.append(f"{f}: {kind} {r:.0%} < {need:.0%}")
        if mut_files is None:
            found.append(f"{f}: 뮤테이션 리포트 없음")
            continue
        detected, total = mut_files.get(f, (0, 0))
        line_cov = ratio(*cov.get("LINE", (0, 0)))
        if total == 0 and cov.get("LINE", (0, 0))[1] > 0:
            # PIT 대상 제외는 보호 파일 build.gradle.kts로만 가능하므로, 순수 데이터 클래스(변이 0개)는 라인 100%일 때만 허용
            if line_cov is None or line_cov < 1.0:
                found.append(f"{f}: 실행 코드가 있는데 변이가 0개 (PIT 대상에서 빠졌거나 검증할 동작이 없음)")
        elif total and detected / total < MUTATION:
            found.append(f"{f}: 뮤테이션 {detected / total:.0%} < {MUTATION:.0%}")

    if baseline:
        floors = dict(line.split("=", 1) for line in open(baseline, encoding="utf-8")
                      if "=" in line and not line.lstrip().startswith("#"))
        current = {"line": ratio(*cov_total.get("LINE", (0, 0))), "branch": ratio(*cov_total.get("BRANCH", (0, 0))),
                   "mutation": ratio(*mut_total) if mut_total else None}
        for k, floor in floors.items():
            v = current.get(k.strip())
            if v is not None and v + 1e-9 < float(floor):
                found.append(f"래칫: 프로젝트 {k.strip()} {v:.2%} < 바닥값 {float(floor):.2%}")
        print("현재 프로젝트 수치: " + ", ".join(f"{k} {v:.2%}" for k, v in current.items() if v is not None))
    return found


def self_test():
    d = tempfile.mkdtemp()
    src = os.path.join(d, "src")
    os.makedirs(os.path.join(src, "a"))
    jacoco, muts, base = (os.path.join(d, n) for n in ("jacoco.xml", "mutations.xml", "baseline.txt"))

    def write(new_line, killed, survived, helper_survived=0):
        with open(jacoco, "w", encoding="utf-8") as f:
            f.write(f"""<report><package name="a">
<sourcefile name="Old.java"><counter type="LINE" missed="0" covered="1000"/></sourcefile>
<sourcefile name="Quiet.java"><counter type="LINE" missed="1" covered="3"/></sourcefile>
<sourcefile name="Data.java"><counter type="LINE" missed="0" covered="3"/></sourcefile>
<sourcefile name="New.java"><counter type="LINE" missed="{10 - new_line}" covered="{new_line}"/>
<counter type="BRANCH" missed="1" covered="9"/></sourcefile></package>
<counter type="LINE" missed="{10 - new_line}" covered="{1000 + new_line}"/></report>""")
        ms = ([("a.Old", "Old.java", "true")] * 50 + [("a.New", "New.java", "true")] * killed
              + [("a.New$Inner", "New.java", "false")] * survived + [("a.Helper", "New.java", "false")] * helper_survived)
        with open(muts, "w", encoding="utf-8") as f:
            f.write("<mutations>" + "".join(
                f'<mutation detected="{det}" status="X"><sourceFile>{sf}</sourceFile><mutatedClass>{c}</mutatedClass></mutation>'
                for c, sf, det in ms) + "</mutations>")

    write(9, 8, 2)
    assert problems(jacoco, muts, src, ["a/New"]) == []
    write(5, 8, 2)  # 전체는 99%여도 새 파일 라인 50%면 실패
    assert any("LINE" in p for p in problems(jacoco, muts, src, ["a/New"]))
    write(9, 7, 3)  # 내부 클래스 생존 뮤턴트 포함 70%
    assert any("뮤테이션" in p for p in problems(jacoco, muts, src, ["a/New"]))
    write(9, 8, 0, helper_survived=3)  # 같은 파일의 별도 최상위 클래스(Helper)도 그 파일 점수에 들어간다 (8/11)
    assert any("뮤테이션" in p for p in problems(jacoco, muts, src, ["a/New"]))

    # 리포트에 없는 변경 파일: 실행 코드가 있으면 실패, 순수 인터페이스면 통과
    write(9, 8, 2)
    with open(os.path.join(src, "a", "Ghost.java"), "w") as f:
        f.write("package a;\nclass Ghost { int x() { return 1; } }\n")
    with open(os.path.join(src, "a", "Port.java"), "w") as f:
        f.write("package a;\npublic interface Port { String name(); }\n")
    assert any("Ghost" in p for p in problems(jacoco, muts, src, ["a/Ghost"]))
    assert problems(jacoco, muts, src, ["a/Port"]) == []
    with open(os.path.join(src, "a", "Shout.java"), "w") as f:  # default 메서드 = 실행 코드 (T-012)
        f.write("package a;\npublic interface Shout { default String up(String s) { return s.toUpperCase(); } }\n")
    assert any("Shout" in p for p in problems(jacoco, muts, src, ["a/Shout"]))
    assert any("변이가 0개" in p for p in problems(jacoco, muts, src, ["a/Quiet"]))  # 커버리지는 있는데 변이 0 (T-012, 라인 < 100%)
    assert problems(jacoco, muts, src, ["a/Data"]) == []  # 변이 0개여도 라인 100%면 통과 (T-035)
    assert any("뮤테이션 리포트 없음" in p for p in problems(jacoco, os.path.join(d, "none.xml"), src, ["a/New"]))

    # 래칫: 바닥값 아래면 실패
    with open(base, "w", encoding="utf-8") as f:
        f.write("# 바닥값\nline=0.99\nmutation=0.97\n")  # 현재 58/60 = 96.7%
    assert any("래칫: 프로젝트 mutation" in p for p in problems(jacoco, muts, src, [], base))
    with open(base, "w", encoding="utf-8") as f:
        f.write("line=0.99\nmutation=0.90\n")
    assert problems(jacoco, muts, src, [], base) == []

    # 설정 스캔 (T-014: YAML 계층으로 Flyway 끄기)
    b = os.path.join(d, "backend")

    def put(rel, text):
        p = os.path.join(b, rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, "w", encoding="utf-8") as fh:
            fh.write(text)

    put("src/main/resources/application.properties", "spring.jpa.hibernate.ddl-auto=validate\n")
    put("src/test/java/a/OkIT.java", "@Import(TestcontainersConfiguration.class) class OkIT {}\n")
    put("src/test/java/a/architecture/RuleTest.java", "// 규칙 정의: AutoConfigureTestDatabase 금지\n")  # 하네스 자신
    assert config_problems(b) == []
    put("src/test/resources/application.yml", "spring:\n  flyway:\n    enabled: false\n")
    assert any("flyway" in p for p in config_problems(b))
    os.remove(os.path.join(b, "src/test/resources/application.yml"))
    put("src/test/resources/application.yml", "spring: {datasource: {url: x}}\n")  # 인라인 맵
    assert any("datasource" in p for p in config_problems(b))
    os.remove(os.path.join(b, "src/test/resources/application.yml"))
    put("src/main/resources/application.yml", "spring:\n  jpa:\n    hibernate:\n      ddl-auto: update\n")
    assert any("ddl-auto" in p for p in config_problems(b))
    put("src/main/resources/application.yml", "spring: {flyway: {enabled: false}}\n")  # 운영 인라인 맵 (T-015)
    assert any("인라인 맵" in p for p in config_problems(b))
    put("src/main/resources/application.yml", "spring:\n  jpa:\n    properties:\n      hibernate.hbm2ddl.auto: create\n")
    assert any("hbm2ddl" in p for p in config_problems(b))
    os.remove(os.path.join(b, "src/main/resources/application.yml"))
    put("src/test/java/a/SneakyIT.java", "@AutoConfigureTestDatabase class SneakyIT {}\n")
    assert any("AutoConfigureTestDatabase" in p for p in config_problems(b))
    put("src/test/java/a/SneakyIT.java", "@org.x.AutoConfigureTestDatabase class SneakyIT {}\n")  # 패키지 경로 (T-016)
    assert any("AutoConfigureTestDatabase" in p for p in config_problems(b))

    # T-020: 테스트 Java 금지어 (hbm2ddl, schema-generation, allow-bean-definition-overriding)
    put("src/test/java/a/SneakyIT.java", '@TestPropertySource(properties = "spring.jpa.properties.hibernate.hbm2ddl.auto=create") class SneakyIT {}\n')
    assert any("hbm2ddl" in p for p in config_problems(b))
    put("src/test/java/a/SneakyIT.java", '@TestPropertySource(properties = "jakarta.persistence.schema-generation.database.action=create") class SneakyIT {}\n')
    assert any("schema-generation" in p for p in config_problems(b))
    put("src/test/java/a/SneakyIT.java", '@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true") class SneakyIT {}\n')
    assert any("allow-bean-definition-overriding" in p for p in config_problems(b))
    os.remove(os.path.join(b, "src/test/java/a/SneakyIT.java"))

    # T-020: 테스트 리소스 TEST_CONFIG_WORDS
    put("src/test/resources/application.properties", "jakarta.persistence.schema-generation.database.action=create\n")
    assert any("schema-generation" in p for p in config_problems(b))
    os.remove(os.path.join(b, "src/test/resources/application.properties"))
    put("src/test/resources/application.properties", "spring.main.allow-bean-definition-overriding=true\n")
    assert any("allow-bean-definition-overriding" in p for p in config_problems(b))
    os.remove(os.path.join(b, "src/test/resources/application.properties"))

    # T-020: 운영 설정 (schema-generation .action은 none만 허용, allow-bean-definition-overriding=true 실패)
    put("src/main/resources/application.yml", "spring:\n  jpa:\n    properties:\n      jakarta.persistence.schema-generation.database.action: create\n")
    assert any("schema-generation" in p for p in config_problems(b))
    put("src/main/resources/application.yml", "spring:\n  jpa:\n    properties:\n      jakarta.persistence.schema-generation.database.action: none\n")
    assert config_problems(b) == []
    put("src/main/resources/application.yml", "spring:\n  main:\n    allow-bean-definition-overriding: true\n")
    assert any("allow-bean-definition-overriding" in p for p in config_problems(b))
    os.remove(os.path.join(b, "src/main/resources/application.yml"))
    assert config_problems(b) == []
    print("quality_gate: all checks passed")


def main():
    sys.stdout.reconfigure(encoding="utf-8")  # Windows 콘솔 기본(cp949)에서 한글이 깨지지 않게
    sys.stderr.reconfigure(encoding="utf-8")
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        return 0
    if sys.argv[1:2] == ["--scan-config"]:
        errors = config_problems(sys.argv[2])
        for e in errors:
            print(e, file=sys.stderr)
        return 1 if errors else 0
    ap = argparse.ArgumentParser()
    ap.add_argument("--jacoco", required=True)
    ap.add_argument("--mutations", required=True)
    ap.add_argument("--src", required=True)
    ap.add_argument("--baseline")
    ap.add_argument("changed", nargs="*")
    a = ap.parse_args()
    errors = problems(a.jacoco, a.mutations, a.src, a.changed, a.baseline)
    for e in errors:
        print(e, file=sys.stderr)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
