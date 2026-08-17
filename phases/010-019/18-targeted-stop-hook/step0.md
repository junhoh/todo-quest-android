# Step 0: stop-hook-runtime

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/DEVELOPMENT.md`
- `/.agents/skills/harness/SKILL.md`
- `/.codex/hooks.json`
- `/.codex/rules/harness.rules`
- `/scripts/execute.py`
- `/scripts/run_harness.ps1`
- `/scripts/test_execute.py`
- `/requirements-dev.txt`

## 작업

현재 모든 일반 Codex turn 종료 때 harness pytest와 Android Gradle 전체 검증을 실행하는 Stop hook을 변경 감지형 검증기로 교체한다. Stop hook은 turn 단위로 실행되고 Stop event의 matcher가 무시되므로, hook 내부에서 실행 맥락과 관련 변경을 판별해야 한다.

테스트를 먼저 작성한다.

1. `/scripts/test_stop_hook.py`를 새로 만들고, `/scripts/test_execute.py`의 기존 inline hook 문자열 검증을 새 runner 위임 계약에 맞게 수정한다.
2. 다음 동작을 순수 함수와 subprocess mock 중심으로 검증한다.
   - `permission_mode=plan`이면 검증 명령을 실행하지 않는다.
   - `TODO_QUEST_HARNESS_CHILD=1`이면 stdin이 비어 있어도 즉시 성공한다.
   - clean repository와 관련 없는 변경은 검증하지 않는다.
   - harness 관련 변경은 Python 검증군만 선택한다.
   - Android 관련 변경은 Gradle 검증군만 선택한다.
   - 두 분류가 함께 변경되면 두 검증군을 선택한다.
   - 같은 검증군의 성공 fingerprint는 재사용하고, 같은 경로의 파일 내용이 바뀌면 다시 검증한다.
   - 실패한 검증은 성공 cache로 기록하지 않는다.
   - 실패나 필수 도구 누락은 stdout에 유효한 `continue: false` JSON만 출력한다.
   - Windows와 non-Windows hook 명령은 저장소 루트의 runner를 호출하며 timeout은 900초다.

`/scripts/stop_hook.py`를 Python 표준 라이브러리만 사용해 구현한다. 진입 계약은 Codex Stop event JSON을 stdin으로 받고 프로세스 종료 코드를 반환하는 `main()`이다.

- `TODO_QUEST_HARNESS_CHILD=1` 확인은 JSON parsing보다 먼저 수행한다.
- Stop event의 `permission_mode`가 `plan`이면 성공 종료한다.
- 저장소 루트는 `git rev-parse --show-toplevel`로 계산하고, changed path는 staged, unstaged, untracked 파일을 모두 포함한다. rename과 delete도 안정적으로 fingerprint에 포함한다.
- 성공과 skip은 stdout 없이 exit 0으로 끝낸다.
- event JSON 손상, 검증 실패, timeout, 필수 interpreter/wrapper 누락은 traceback 대신 구조화된 continuation JSON을 stdout에 출력하고 exit 0으로 끝낸다.

관련 경로를 다음 두 검증군으로 분류한다.

1. `harness`
   - `.agents/skills/harness/`, `.codex/hooks.json`, `.codex/rules/harness.rules`
   - `scripts/execute.py`, `scripts/run_harness.ps1`, `scripts/stop_hook.py`
   - `scripts/test_execute.py`, `scripts/test_stop_hook.py`, `docs/DEVELOPMENT.md`, `requirements-dev.txt`
2. `android`
   - `app/`, 루트 Gradle 설정, `gradle/`, `gradlew`, `gradlew.bat`
   - Kotlin/Kotlin Script, `AndroidManifest.xml`, Android resource 경로

각 검증군은 독립 fingerprint와 성공 cache를 가진다.

- fingerprint에는 분류 버전, 실행 명령, path 상태, 현재 파일 bytes 또는 삭제 marker를 포함한다.
- cache는 `/build/codex-stop-hook/cache.json`에 schema version과 검증군별 성공 fingerprint로 기록하고 임시 파일 후 replace 방식으로 원자적으로 갱신한다.
- subprocess 전체 출력은 `/build/codex-stop-hook/latest-{suite}.log`에 UTF-8로 기록하고, `stopReason`에는 끝부분만 크기 제한해서 포함한다.
- 각 검증군은 자신의 fingerprint가 바뀐 경우에만 다시 실행한다.

검증 명령은 다음 의미를 유지한다.

- `harness`: 프로젝트 `.venv` Python으로 `scripts/test_stop_hook.py`와 `scripts/test_execute.py`를 실행한다. `basetemp`는 `/build/codex-stop-hook/` 아래 turn별 임시 디렉터리를 사용하며 제한 시간은 120초다.
- `android`: 플랫폼별 Gradle wrapper로 `test lint assembleDebug --offline --console=plain`을 한 번의 프로세스로 실행하며 제한 시간은 720초다.
- 실행 순서는 `harness`, `android`이며 전체 hook timeout 900초 안에 끝나야 한다.
- Stop hook에서 개발 도구를 설치하거나 네트워크 다운로드하지 않는다.

`/.codex/hooks.json`의 긴 inline 검증 명령은 runner 호출만 담당하도록 단순화한다.

- non-Windows는 `python3`, Windows는 `py -3`로 저장소 루트의 `/scripts/stop_hook.py`를 실행한다.
- 하위 디렉터리에서도 `git rev-parse --show-toplevel` 기준으로 runner를 찾는다.
- `timeout`은 900, `statusMessage`는 `Checking relevant project changes`로 설정한다.
- `/scripts/execute.py`가 child에 전달하는 env와 bypass 옵션은 변경하지 않는다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_stop_hook.py scripts/test_execute.py --basetemp .\build\pytest-phase18-step0
Get-Content -Raw -Encoding UTF8 .codex\hooks.json | ConvertFrom-Json | Out-Null
git diff --check
```

## 검증 절차

1. Acceptance Criteria 명령을 모두 실행한다.
2. 테스트가 실제 Gradle 작업을 실행하지 않고 mock으로 단일 invocation을 검증하는지 확인한다.
3. `hooks.json`의 command가 더 이상 pytest나 Gradle 명령을 inline으로 포함하지 않는지 확인한다.
4. Windows child skip integration test가 Gradle 다운로드를 시도하지 않는지 확인한다.
5. ARCHITECTURE, ADR, AGENTS.md의 CRITICAL 규칙을 확인한다.
6. `/phases/010-019/18-targeted-stop-hook/index.json`의 step 0 상태와 결과 필드를 업데이트한다.

## 금지사항

- Android 앱 소스나 Gradle 의존성 버전을 수정하지 마라. 이유: 이 step은 Codex Stop hook runtime만 다룬다.
- `/scripts/execute.py`의 child bypass, retry, commit 정책을 변경하지 마라. 이유: 기존 harness 실행 계약을 유지해야 한다.
- Stop hook에서 dependency나 Android 도구를 설치하지 마라. 이유: turn 종료를 외부 환경 변경과 네트워크 대기에 결합하면 안 된다.
- transcript 파일 형식에 의존하지 마라. 이유: Codex transcript는 안정적인 hook API가 아니다.
- 테스트가 실제 사용자 Gradle cache나 전역 Codex 설정을 수정하게 하지 마라. 이유: unit test는 저장소 내부에서 결정적으로 실행되어야 한다.
- 기존 테스트를 깨뜨리지 마라.
