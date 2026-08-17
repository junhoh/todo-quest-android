# Step 0: harness-auto-child-execution-policy

## 읽어야 할 파일

- `/AGENTS.md`
- `/.agents/skills/harness/SKILL.md`
- `/docs/DEVELOPMENT.md`
- `/scripts/execute.py`
- `/scripts/run_harness.ps1`
- `/scripts/test_execute.py`

## 작업

Harness phase 파일을 생성한 뒤 사용자가 같은 요청에서 구현까지 지시한 경우, 부모 Codex 세션에서 `/compact` 입력을 요청하지 않고 기존 PowerShell 래퍼를 즉시 호출하여 독립 child Codex 세션에서 구현을 시작하도록 운영 정책을 변경한다.

다음 순서로 작업한다.

1. `/scripts/test_execute.py`
   - 기존 수동 `/compact` 문서 정책 테스트를 먼저 자동 child 실행 정책 테스트로 변경한다.
   - 스킬과 개발 문서가 즉시 구현 요청 시 `.\scripts\run_harness.ps1 -Phase {task-name}` 실행을 지시하는지 검증한다.
   - 사용자에게 `/compact` 입력을 요구하는 기존 문구가 제거되었는지 검증한다.
   - phase 생성만 요청한 경우에는 래퍼를 실행하지 않고, `-Push`는 사용자가 명시한 경우에만 사용한다는 정책을 검증한다.
2. `/.agents/skills/harness/SKILL.md`
   - `Phase 생성 직후 컨텍스트 정리`와 `Step 간 컨텍스트 정리`의 수동 `/compact` 절차를 제거한다.
   - phase 생성 후 즉시 구현 요청이 있으면 사용자에게 추가 입력을 요구하지 말고 `.\scripts\run_harness.ps1 -Phase {task-name}`을 다음 작업으로 호출하도록 명시한다.
   - phase 생성만 요청한 경우에는 래퍼를 실행하지 않으며, `-Push`는 명시적으로 요청된 경우에만 사용한다고 명시한다.
   - `execute.py`가 각 pending step을 별도 `codex exec` child 세션에서 실행하고 완료 step `summary`를 다음 step에 전달하므로 부모 세션 compaction이 필요하지 않다고 설명한다.
   - step이 `blocked` 또는 `error`로 종료되면 기존과 같이 다음 step으로 진행하지 않는다.
3. `/docs/DEVELOPMENT.md`
   - Harness 실행 안내를 같은 자동 child 실행 정책으로 갱신한다.
   - Codex 승인 프롬프트는 실행 권한 확인이며 수동 `/compact` 요청과 별개임을 명확히 한다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
```

## 검증 절차

1. 테스트를 먼저 변경하고 기존 구현에서 새 정책 테스트가 실패하는지 확인한다.
2. 스킬과 개발 문서를 변경한다.
3. Acceptance Criteria 명령을 실행한다.
4. `AGENTS.md`의 CRITICAL 규칙을 확인한다.
5. `/phases/010-019/15-harness-auto-child-execution/index.json`의 step 0 상태와 결과 필드를 업데이트한다.
6. step 완료 시 `/phases/index.json`의 phase 상태를 `completed`로 업데이트한다.

## 금지사항

- `scripts/execute.py`나 `scripts/run_harness.ps1`를 변경하지 마라. 이유: 기존 child Codex 실행 구조가 이미 step별 컨텍스트 격리를 제공한다.
- hook이나 `model_auto_compact_token_limit`로 `/compact`를 흉내 내지 마라. 이유: phase 생성 시점과 결합되지 않으며 부모 세션의 명령 실행을 보장하지 않는다.
- 사용자에게 `/compact` 입력을 요청하지 마라. 이유: 즉시 구현 경로는 harness child 세션으로 자동 전환한다.
- 사용자가 명시하지 않은 `-Push`를 사용하지 마라. 이유: 원격 저장소 변경은 별도 의도가 필요하다.
- 기존 테스트를 깨뜨리지 마라.