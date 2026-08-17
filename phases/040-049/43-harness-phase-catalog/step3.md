# Step 3: Harness validation contract preservation

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/DEVELOPMENT.md`
- `/.agents/skills/harness/SKILL.md`
- `/.codex/hooks.json`
- `/.codex/rules/harness.rules`
- `/scripts/phase_manager.py`
- `/scripts/run_phase_manager.ps1`
- `/scripts/test_phase_manager.py`
- `/scripts/execute.py`
- `/scripts/test_execute.py`
- `/scripts/stop_hook.py`
- `/scripts/test_stop_hook.py`
- `/phases/040-049/43-harness-phase-catalog/index.json`
- `/phases/040-049/43-harness-phase-catalog/step0.md`
- `/phases/040-049/43-harness-phase-catalog/step1.md`
- `/phases/040-049/43-harness-phase-catalog/step2.md`

## 작업

테스트를 먼저 작성하고 manager 도입 후에도 기존 Stop hook과 문서화된 harness 계약이 유지되도록 마무리한다.

- `/scripts/stop_hook.py`의 harness exact paths에 manager Python, PowerShell wrapper, manager 테스트, `/phases/taxonomy.json`을 추가한다.
- harness suite command와 signature에 `/scripts/test_phase_manager.py`를 추가하고 `/scripts/test_stop_hook.py`에서 경로 분류, 명령 순서, cache fingerprint를 검증한다.
- phase 상태마다 바뀌는 `/phases/index.json`과 생성 `/phases/README.md`는 Stop hook exact paths에 넣지 않는다.
- Harness child의 `TODO_QUEST_HARNESS_CHILD=1` 즉시 skip, Plan skip, 관련 변경 없음 skip, 로그/cache/continuation 정책은 유지한다.
- `/.agents/skills/harness/SKILL.md`와 `/docs/DEVELOPMENT.md`에 manager 기반 Suggest/Create/List/Show/Validate/Sync, 10개 bucket, legacy selector 호환, 실제 step 참조의 읽기 전용 catalog 정책을 기록한다.
- 새 phase를 생성할 때 manager를 표준 진입점으로 사용하되 승인 후 step Markdown 작성과 같은 요청의 자동 child 실행, `-Push` 명시 정책은 유지한다.
- manager `Sync`를 실제 저장소에 실행해 `/phases/README.md`를 생성하고 `Sync -Check`, bootstrap-compatible `Validate`, `MigrateLegacy -DryRun`을 통과시킨다. 실제 migration은 실행하지 않는다.
- 현재 102개 기존 harness/Stop hook 테스트를 포함해 전체 suite를 통과시킨다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_stop_hook.py scripts/test_execute.py scripts/test_phase_manager.py --basetemp .\.venv\pytest-tmp
```

```powershell
.\scripts\run_phase_manager.ps1 -Command Sync
.\scripts\run_phase_manager.ps1 -Command Sync -Check
.\scripts\run_phase_manager.ps1 -Command Validate
.\scripts\run_phase_manager.ps1 -Command MigrateLegacy -DryRun
git diff --check
```

## 검증 절차

1. hook 분류와 suite command 테스트를 먼저 추가하고 runner와 문서를 갱신한다.
2. 기존 102개 테스트가 유지되고 manager 테스트가 추가 통과하는지 확인한다.
3. 실제 phase 디렉터리는 여전히 평면 구조이며 dry-run만 수행되었는지 확인한다.
4. `/phases/040-049/43-harness-phase-catalog/index.json`의 step 3과 phase 전체를 completed로 바꾸고 한국어 summary 및 completed_at을 기록한다.

## 금지사항

- `/.codex/hooks.json` 또는 `/.codex/rules/harness.rules`의 승인 범위를 넓히지 마라. 이유: 기존 최소 권한 wrapper 정책을 유지해야 한다.
- phase 0~43을 이 step에서 이동하지 마라. 이유: migration은 신규 nested phase 44가 독립적으로 수행해야 한다.
- Android 소스나 Gradle 구성을 수정하지 마라. 이유: 이 phase는 harness 인프라만 다룬다.
- 기존 테스트를 깨뜨리지 마라.
