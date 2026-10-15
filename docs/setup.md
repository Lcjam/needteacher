# needteacher 환경 설정 가이드

테스트 하네스 v4(`docs/proposals/test-harness.md`)를 운영하기 위해 사용자가 직접 수행해야 하는 사전 설정 안내서입니다.

---

## 1. Windows gitleaks v8.30.1 설치

로컬 커밋 전 시크릿 유출을 방지하기 위해 `gitleaks` CLI를 설치합니다.

### 설치 방법 (택 1)

1. **winget (권장)**:
   ```powershell
   winget install --id Gitleaks.Gitleaks --version 8.30.1
   ```
2. **scoop**:
   ```powershell
   scoop install gitleaks
   ```
3. **수동 다운로드**:
   - [Gitleaks v8.30.1 Releases](https://github.com/gitleaks/gitleaks/releases/tag/v8.30.1)에서 `gitleaks_8.30.1_windows_x64.zip` 다운로드.
   - 압축 해제 후 `gitleaks.exe`가 있는 폴더를 시스템 `PATH` 환경변수에 추가.

### 설치 확인 명령
새 터미널을 열고 버전을 확인합니다:
```powershell
gitleaks version
# 출력: v8.30.1
```

---

## 2. 에이전트용 GitHub App 생성 및 설치

에이전트가 사용자 권한을 흉내 내지 못하도록 격리된 저권한 봇 계정(GitHub App)을 생성합니다.

### 2.1 GitHub App 생성 절차
1. GitHub 웹사이트 우측 상단 프로필 클릭 → **Settings** 이동.
2. 좌측 사이드바 맨 하단 **Developer settings** → **GitHub Apps** 클릭.
3. **New GitHub App** 버튼 클릭.
4. 기본 정보 입력:
   - **GitHub App name**: `needteacher-agent` (중복 시 고유한 이름 사용)
   - **Homepage URL**: `https://github.com/Lcjam/needteacher`
   - **Webhook**: **Active 체크 해제** (웹훅 비활성화)
5. **권한 (Permissions > Repository permissions)** 설정:
   - `Contents`: **Read and write** (브랜치 푸시용)
   - `Pull requests`: **Read and write** (PR 생성 및 관리용)
   - `Metadata`: **Read-only** (기본 필수)
   - **주의**: `Administration`, `Workflows`, `Secrets` 등 기타 모든 권한은 **No access** 유지.
6. **Where can this GitHub App be installed?**:
   - **Only on this account** 선택.
7. **Create GitHub App** 버튼 클릭.

### 2.2 개인키(Private Key) 발급 및 보관
1. 생성된 App 상세 페이지에서 상단의 **App ID**를 기록해 둡니다.
2. 하단 **Private keys** 섹션으로 스크롤하여 **Generate a private key** 클릭.
3. `.pem` 개인키 파일이 자동 다운로드됩니다.
4. **보관 위치 주의**:
   - **반드시 저장소(`needteacher/`) 밖**에 보관합니다 (예: `C:\Users\lcj\.github-apps\needteacher-agent.pem`).
   - 저장소 내부에 두면 git commit/gitleaks에 의해 유출 위험 및 차단이 발생합니다.

### 2.3 저장소에 설치
1. App 상세 페이지 좌측 메뉴에서 **Install App** 클릭.
2. 본인 계정(`Lcjam`) 우측의 **Install** 버튼 클릭.
3. **Repository access** 설정:
   - **Only select repositories** 선택 → 드롭다운에서 `needteacher` 선택.
4. **Install** 버튼 클릭하여 설치 완료.

---

## 3. main 브랜치 Ruleset 설정

서버(GitHub) 레벨에서 관리자 우회 없이 테스트 및 보호 파일 리뷰를 강제합니다.

### 설정 단계별 절차
1. `needteacher` 저장소 페이지 → 상단 **Settings** 탭 클릭.
2. 좌측 사이드바 **Rules** → **Rulesets** 클릭.
3. **New ruleset** 드롭다운 클릭 → **New branch ruleset** 선택.
4. **기본 설정**:
   - **Ruleset Name**: `main-protection`
   - **Enforcement status**: **Active** (활성화)
   - **Bypass list**: **비워둠 (None)**
     > 관리자(Admin)를 포함한 어떤 사용자도 규칙을 우회할 수 없습니다.
5. **Target branches**:
   - **Add target** 클릭 → **Include default branch** 선택 (`main` 대상).
6. **Branch rules (세부 규칙 체크)**:
   - [x] **Restrict deletions**: 체크 (main 브랜치 삭제 방지)
   - [x] **Block force pushes**: 체크 (`--force` 푸시 방지)
   - [x] **Require a pull request before merging**: 체크
     - Required approving reviews: `1`
     - [x] **Dismiss stale pull request approvals when new commits are pushed** (새 커밋 푸시 시 기존 승인 취소)
     - [x] **Require review from Code Owners** (보호 파일 변경 시 `.github/CODEOWNERS` 필수 승인)
     - [x] **Require approval of the most recent reviewable push** (마지막 푸시에 대한 승인 필수)
   - [x] **Require status checks to pass**: 체크
     - [x] **Require branches to be up to date before merging** (최신 main 반영 필수)
     - **Status checks that are required** 목록에 추가:
       - `verify-full` 추가 (GitHub Actions 출처)
       - `gitleaks` 추가 (GitHub Actions 출처)
7. 하단 **Create** (또는 Save changes) 클릭하여 저장.

---

## 4. 자격 증명 격리 및 복구 방법

개발 PC에 사용자(`Lcjam`)의 개인 토큰이 남아 있으면 로컬 에이전트가 이를 이용해 자가 승인(`gh pr review --approve`)할 수 있으므로 자격 증명을 격리합니다.

### 4.1 격리 절차 (에이전트 작업 모드)
1. **GitHub CLI 로그아웃**:
   ```powershell
   gh auth logout -h github.com
   ```
2. **Git Credential Manager (GCM) 자격 증명 제거**:
   - 방법 A (PowerShell 명령):
     ```powershell
     cmdkey /delete:git:https://github.com
     ```
   - 방법 B (GUI):
     1. Windows 시작 검색에서 **자격 증명 관리자** 실행.
     2. **Windows 자격 증명** 선택.
     3. 일반 자격 증명 목록에서 `git:https://github.com` 클릭 → **제거** 클릭.
3. **승인 및 병합 원칙**:
   - 모든 PR 검토, CODEOWNERS 승인, 병합은 **웹 브라우저(GitHub 웹)**에서 사람이 직접 수행합니다.

### 4.2 복구 절차 (사용자가 로컬 CLI를 직접 쓸 때)
1. GitHub CLI 재로그인:
   ```powershell
   gh auth login -h github.com
   ```
2. Git 푸시 시 GCM 로그인 창이 뜨면 브라우저를 통해 재인증하면 정상 복구됩니다.

---

## 5. 에이전트가 App 신원으로 push·PR 하도록 연결

2절에서 기록한 App ID와 개인키 경로를 사용자 환경 변수로 등록합니다. 새 터미널부터 적용됩니다.

```powershell
setx NT_APP_ID "<App ID>"
setx NT_APP_KEY "C:\Users\lcj\.github-apps\needteacher-agent.pem"
```

확인 (토큰이 `ghs_`로 시작하면 정상):

```bash
bash scripts/gh-app-token.sh | cut -c1-4
```

에이전트는 이렇게 씁니다. 승인·병합은 판정기가 막으므로 사용자가 브라우저에서 합니다.

```bash
git push "https://x-access-token:$(bash scripts/gh-app-token.sh)@github.com/Lcjam/needteacher.git" HEAD
GH_TOKEN=$(bash scripts/gh-app-token.sh) gh pr create --fill
```

---

## 6. 마무리 (하네스 구축이 끝난 뒤 한 번)

프롬프트에 `!`를 붙여 실행하면 판정기를 거치지 않습니다. 아래 둘은 에이전트가 할 수 없게 막혀 있어서 사용자가 직접 실행해야 합니다.

1. 커밋 훅 연결 (커밋할 때마다 `scripts/verify.sh fast` 실행):
   ```
   ! git config core.hooksPath .githooks
   ```
2. 잠금 해제 파일 삭제 (이걸 지워야 보호 파일 잠금이 다시 걸립니다):
   ```
   ! rm .harness-unlock
   ```
3. 세 세션 재시작: Claude Code(`worker` 에이전트 로드), Codex(`.codex/hooks.json`), agy(`.agents/hooks.json`). 재시작 후 각 세션에서 `echo -DskipTests`가 막히는지 확인합니다.
