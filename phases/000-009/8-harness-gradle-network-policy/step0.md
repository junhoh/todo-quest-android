# Step 0: harness-gradle-network-policy

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/DEVELOPMENT.md`
- `/.agents/skills/harness/SKILL.md`
- `/.codex/hooks.json`
- `/.codex/rules/harness.rules`
- `/scripts/execute.py`
- `/scripts/test_execute.py`
- `/gradle/wrapper/gradle-wrapper.properties`

## 작업

`harness` 실행 중 Gradle wrapper 배포 zip 다운로드가 sandbox 네트워크 제한에 막혀 같은 승인 재시도 안내가 반복되는 문제를 수정한다.

사용자 결정은 `항상 허용`이다. 일반 harness 실행은 `run_harness.ps1` 승인 경로를 유지하되, `execute.py`가 띄우는 child Codex는 승인/샌드박스 우회 모드로 실행해 Gradle wrapper 다운로드와 의존성 접근이 반복 차단되지 않게 한다.

`scripts/execute.py`를 다음 규칙으로 수정한다.

- child Codex 실행 명령은 `codex exec --dangerously-bypass-approvals-and-sandbox --dangerously-bypass-hook-trust --json` 의미가 되게 한다.
- 기존 `--ask-for-approval never`와 `--sandbox danger-full-access` 조합은 제거한다.
- child process 환경에 `TODO_QUEST_HARNESS_CHILD=1`과 `PYTHONUTF8=1`을 명시적으로 전달한다.
- harness 자체의 step status, retry, output json, commit 흐름은 변경하지 않는다.

`.codex/hooks.json`을 다음 규칙으로 수정한다.

- `TODO_QUEST_HARNESS_CHILD=1`이면 Stop hook 검증을 건너뛰고 skip 메시지만 출력한다.
- 일반 Codex 세션에서는 기존 pytest와 Gradle 검증을 유지한다.
- Windows `commandWindows`와 non-Windows `command` 양쪽 모두 동일한 skip guard를 둔다.

`scripts/test_execute.py`에 테스트를 먼저 추가한다.

- child Codex 명령에 `--dangerously-bypass-approvals-and-sandbox`가 포함된다.
- child Codex 명령에 `--ask-for-approval`, `never`, `--sandbox`, `danger-full-access`가 포함되지 않는다.
- `subprocess.run()` env에 `TODO_QUEST_HARNESS_CHILD=1`, `PYTHONUTF8=1`이 전달된다.
- `.codex/hooks.json`은 유효 JSON이고 `TODO_QUEST_HARNESS_CHILD` skip guard를 포함한다.

문서를 다음 규칙으로 갱신한다.

- `/docs/DEVELOPMENT.md`와 `/.agents/skills/harness/SKILL.md`의 harness 실행 안내에 child Codex 승인/샌드박스 우회 정책과 Stop hook skip 정책을 기록한다.
- 이 정책은 harness 내부 자동화에만 적용되고, 일반 Codex 세션의 hook 검증은 유지된다고 명시한다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
git diff --check
```

## 검증 절차

1. Acceptance Criteria 명령을 실행한다.
2. `scripts/execute.py`의 child Codex 명령이 `--ask-for-approval never`를 사용하지 않는지 확인한다.
3. `.codex/hooks.json`의 일반 세션 검증 경로가 제거되지 않았는지 확인한다.
4. `/phases/000-009/8-harness-gradle-network-policy/index.json`의 step 0 상태와 결과 필드를 업데이트한다.

## 금지사항

- Android 앱 소스나 Gradle 의존성 버전을 수정하지 마라. 이유: 이 step은 harness 실행 정책만 다룬다.
- `run_harness.ps1` 호출 방식을 변경하지 마라. 이유: 사용자가 쓰는 표준 진입점은 유지해야 한다.
- 일반 Codex 세션의 Stop hook 검증을 완전히 삭제하지 마라. 이유: harness 외 작업의 안전망은 유지되어야 한다.
- destructive command 금지 rule을 완화하지 마라. 이유: 프로젝트 CRITICAL 개발 프로세스를 훼손할 수 있다.
- 기존 테스트를 깨뜨리지 마라.
