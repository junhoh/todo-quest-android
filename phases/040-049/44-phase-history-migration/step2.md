# Step 2: 이관된 registry, catalog, phase 참조 최종 검증

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/DEVELOPMENT.md`
- `/.agents/skills/harness/SKILL.md`
- `/scripts/phase_manager.py`
- `/scripts/test_phase_manager.py`
- `/scripts/execute.py`
- `/scripts/test_execute.py`
- `/scripts/stop_hook.py`
- `/scripts/test_stop_hook.py`
- `/phases/index.json`
- `/phases/README.md`
- `/phases/taxonomy.json`
- `/phases/040-049/43-harness-phase-catalog/index.json`
- `/phases/040-049/44-phase-history-migration/index.json`
- `/phases/040-049/44-phase-history-migration/step0.md`
- `/phases/040-049/44-phase-history-migration/step1.md`

## 작업

이관 후 기존 harness 동작과 모든 완료 phase/step 참조가 정상인지 최종 검증하고 문서·catalog 상태를 확정한다.

- 현재 phase 44의 step 2와 phase 전체 summary/status를 completed로 먼저 준비한 뒤 `Sync`를 실행해 registry와 README가 최종 상태를 반영하도록 한다.
- `Validate -Strict`와 `Sync -Check`가 통과하고 registry 45개 entry 모두 `id`, `slug`, canonical bucket `dir`, status, 1개 이상 areas, kind, tags를 갖는지 확인한다.
- phase id 0~44가 중복·누락 없이 정확히 한 번 존재하고 bucket 공식과 실제 디렉터리가 일치하는지 확인한다.
- 모든 phase-local index를 읽어 기존 step 상태와 상태별 필수 필드가 유지되는지 확인한다. 특히 phase 11 blocked 상태와 사유가 유지되고 다른 phase 실행을 막지 않아야 한다.
- manager의 reference graph가 모든 step Markdown의 canonical phase path를 해석하고 missing reference 0개인지 확인한다.
- 등록된 old flat prefix를 가리키는 실제 repository 참조가 0개인지 exact registry mapping 기준으로 확인한다. 테스트 fixture의 가상 `0-mvp` 문자열은 실제 phase가 아니므로 legacy 누락으로 오판하지 않는다.
- `Show -Selector 43-harness-phase-catalog`, `Show -Selector 44`, `List -Area harness`가 nested 경로에서 동작하는지 확인한다.
- 기존 Stop hook/execute 테스트와 manager 테스트 전체를 실행한다. Android 소스는 변경하지 않았으므로 Gradle suite는 실행하지 않는다.
- `MigrateLegacy -DryRun`을 다시 실행했을 때 move 0개, reference_count 0이어야 한다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_stop_hook.py scripts/test_execute.py scripts/test_phase_manager.py --basetemp .\.venv\pytest-tmp
```

```powershell
.\scripts\run_phase_manager.ps1 -Command Sync
.\scripts\run_phase_manager.ps1 -Command Validate -Strict
.\scripts\run_phase_manager.ps1 -Command Sync -Check
.\scripts\run_phase_manager.ps1 -Command Show -Selector 43-harness-phase-catalog
.\scripts\run_phase_manager.ps1 -Command Show -Selector 44
.\scripts\run_phase_manager.ps1 -Command List -Area harness
$remaining = .\.venv\Scripts\python.exe scripts\phase_manager.py migrate-legacy --dry-run | ConvertFrom-Json
if ($remaining.moves.Count -ne 0 -or $remaining.reference_count -ne 0) { throw 'migration is not idempotent' }
git diff --check
```

## 검증 절차

1. phase 44 최종 상태를 index에 기록하고 catalog를 동기화한다.
2. 전체 185개 이상 harness suite와 strict manager 검증을 실행한다.
3. id/bucket/reference/blocked-history/idempotence 불변식을 확인한다.
4. 최종 결과가 통과하면 step 2와 phase 전체의 한국어 summary 및 completed_at을 유지하고 종료한다.

## 금지사항

- 완료된 phase index의 summary나 timestamp를 보기 좋게 다시 쓰지 마라. 이유: 역사 기록은 원문 보존 대상이다.
- 자동 reference graph를 child prompt 실행 의존성으로 사용하지 마라. 이유: phase 독립성과 기존 harness 컨텍스트 계약을 유지해야 한다.
- 실패한 검증을 skip하거나 기존 테스트를 삭제하지 마라. 이유: 이관 전 정상 동작의 회귀를 숨길 수 있다.
- Android 소스나 Gradle 구성을 수정하지 마라. 이유: migration 범위를 harness 기록 구조에 한정한다.
