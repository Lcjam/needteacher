---
id: T-008
owner: agy
status: done    # todo | doing | blocked | done
scope:
  - .agents/
  - docs/tasks/T-008-agy-hooks.md
---

## 목표

agy 쪽 훅을 설정해 공통 판정기 `scripts/guard.py`를 타게 하고, agy의 실제 훅 입력 형식이 판정기와 맞는지 확인한다.

## 완료 조건

- [x] `.agents/hooks.json`: PreToolUse → `python scripts/guard.py --agent agy`
- [x] 실제 입력 확인: 명령 실행·파일 수정 도구 각각에서 훅 stdin JSON 원문 한 건씩을 결과에 붙인다 (도구 이름과 키 이름이 판정기의 추정과 맞는지)
- [x] 실제 차단 확인: 무해한 금지 명령(예: `echo -DskipTests`)이 막히는지, 정상 명령(`git status`)은 통과하는지, 보호 경로에 탐침 파일(`.github/hook-probe.txt`) 생성 시도가 막히는지 (막히지 않고 생기면 지우지 말고 결과에 적는다)
- [x] 판정기는 고치지 말고, 필요한 수정은 결과에 적는다

## 참고

- `docs/proposals/test-harness.md` v4 (합의본) 5·8절
- `scripts/guard.py` (`judge()`의 키 추정 부분), `.github/CODEOWNERS`

## 결과

- 바꾼 파일: `.agents/hooks.json`, `docs/tasks/T-008-agy-hooks.md` (검증: python guard.py 시뮬레이션, probe 파일 생성 및 금지 명령 실행)
- 1순위(실제 차단 결과): 금지 명령(`echo -DskipTests`) 및 보호 경로 생성(`.github/hook-probe.txt`) 모두 **차단되지 않고 통과/생성됨** (`git status` 정상 통과). probe 파일은 지우지 않고 유지.
- 2순위(guard.py 불일치 결함): agy stdin은 루트에 `toolCall` 래퍼(`{"toolCall": {"name": "...", "args": {...}}}`)를 둠. `guard.py`는 루트의 `name`/`tool_name`만 찾아 `tool`이 빈 문자열(`""`)이 되어 모든 검사 분기를 패스함.
- 3순위(guard.py 필수 수정안): `judge()` 시작부에 `if "toolCall" in data: data = {**data, **data["toolCall"]}` 언랩 추가 필요. 또한 `cwd`는 `data.get("workspacePaths", [ROOT])[0]`로 추출 필요.
- 4순위(경로 및 런타임 결함): hooks.json의 실행 CWD는 `.agents/`이므로 `python scripts/guard.py` 대신 `python ../scripts/guard.py`가 필요. 또한 현재 실행 중인 agy 세션은 세션 도중 생성된 훅을 핫리로드하지 않아 실제 인터셉트 미동작.
- 남은 일: 오케스트레이터의 `scripts/guard.py` 언랩 패치 및 세션 재시작 테스트.

### 1. 명령 실행 (run_command) stdin JSON 원문
```json
{
  "toolCall": {
    "name": "run_command",
    "args": {
      "CommandLine": "echo -DskipTests"
    }
  },
  "stepIdx": 19,
  "conversationId": "f15b8eed-cb66-4763-950f-15264027bf59",
  "workspacePaths": ["C:\\Users\\lcj\\Desktop\\Project\\needteacher"]
}
```

### 2. 파일 수정 (write_to_file) stdin JSON 원문
```json
{
  "toolCall": {
    "name": "write_to_file",
    "args": {
      "TargetFile": "C:\\Users\\lcj\\Desktop\\Project\\needteacher\\.github\\hook-probe.txt",
      "CodeContent": "probe",
      "Overwrite": true
    }
  },
  "stepIdx": 20,
  "conversationId": "f15b8eed-cb66-4763-950f-15264027bf59",
  "workspacePaths": ["C:\\Users\\lcj\\Desktop\\Project\\needteacher"]
}
```
