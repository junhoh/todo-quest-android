# Step 1: harness-stop-policy-docs

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/DEVELOPMENT.md`
- `/.agents/skills/harness/SKILL.md`
- `/.codex/hooks.json`
- `/scripts/stop_hook.py`
- `/scripts/test_stop_hook.py`
- `/scripts/test_execute.py`
- `/phases/010-019/18-targeted-stop-hook/index.json`

## 작업

Step 0의 변경 감지형 Stop hook을 harness workflow의 공식 정책으로 문서화한다. 문서 assertion 테스트를 먼저 수정한 뒤 문서를 변경한다.

`/scripts/test_execute.py`의 harness 문서 테스트에 다음 계약을 추가하고, 더 이상 유효하지 않은 “일반 Codex 세션에서는 항상 기존 전체 hook 검증 유지” assertion은 제거한다.

- harness child는 `TODO_QUEST_HARNESS_CHILD=1`로 Stop 검증을 건너뛴다.
- 각 step의 Acceptance Criteria 실행과 step status가 harness 완료 판정의 기준이다.
- phase 완료 후 부모 Codex Stop은 이미 커밋된 phase 전체를 다시 검증하지 않는다.
- 일반 Codex turn도 Plan, 관련 변경 없음, 동일 fingerprint 성공 상태에서는 즉시 종료한다.
- 관련 미커밋 변경이 있을 때만 harness 또는 Android 검증군을 선택한다.
- hook 실패는 `continue: false`로 수정 또는 blocked 보고를 요구한다.
- hook 정의 변경 후 Codex 재시작과 `/hooks` 검토·신뢰가 필요하다.

`/.agents/skills/harness/SKILL.md`와 `/docs/DEVELOPMENT.md`의 Stop hook 설명을 같은 의미로 갱신한다.

- Stop hook은 turn 단위이며 matcher로 phase 실행만 선별할 수 없어서 runner가 context와 changed path를 판별한다고 설명한다.
- child step이 자신의 AC를 직접 실행한다는 기존 규칙을 유지하고, 부모 Stop을 두 번째 phase acceptance gate로 사용하지 않는다고 명시한다.
- `build/codex-stop-hook/`의 검증군별 fingerprint cache와 로그 위치, Gradle 단일 offline invocation을 설명한다.
- 검증 실패 시 Codex가 수정하거나 도구 부재를 blocked로 보고하도록 continuation JSON을 반환한다고 설명한다.
- hook 변경 후 `/hooks`에서 새 hash를 검토·신뢰하고 Codex를 재시작해야 한다고 안내한다.

앱 공개 API나 domain/data/UI 계층은 변경하지 않는다. `/scripts/run_harness.ps1`, `/scripts/execute.py`, `.codex/rules/harness.rules`의 실행 정책도 변경하지 않는다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_stop_hook.py scripts/test_execute.py --basetemp .\build\pytest-phase18-step1
Get-Content -Raw -Encoding UTF8 .codex\hooks.json | ConvertFrom-Json | Out-Null
git diff --check
```

## 검증 절차

1. Acceptance Criteria 명령을 모두 실행한다.
2. skill과 DEVELOPMENT 문서가 동일한 skip, 분류, cache, 실패 정책을 설명하는지 확인한다.
3. `execute.py`와 `run_harness.ps1`에 의도하지 않은 변경이 없는지 확인한다.
4. ARCHITECTURE, ADR, AGENTS.md의 CRITICAL 규칙을 확인한다.
5. `/phases/010-019/18-targeted-stop-hook/index.json`의 step 1 상태와 결과 필드를 업데이트한다.

## 금지사항

- Stop hook을 phase 완료 여부의 유일한 근거로 설명하지 마라. 이유: 각 child step의 Acceptance Criteria와 상태 갱신이 authoritative하다.
- 일반 turn에서 무조건 전체 Gradle 검증을 실행하도록 문서화하지 마라. 이유: 변경 감지형 정책과 모순되어 종료 지연이 재발한다.
- `/scripts/execute.py`, `/scripts/run_harness.ps1`, `.codex/rules/harness.rules`를 변경하지 마라. 이유: 이 step은 문서와 정책 테스트만 다룬다.
- hook 신뢰 검토 절차를 생략하지 마라. 이유: 변경된 project-local hook은 새 hash로 다시 검토되어야 한다.
- 기존 테스트를 깨뜨리지 마라.
