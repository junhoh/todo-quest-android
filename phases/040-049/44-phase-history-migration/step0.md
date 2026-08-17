# Step 0: Migration manifest 및 metadata backfill 검증

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/DEVELOPMENT.md`
- `/.agents/skills/harness/SKILL.md`
- `/scripts/phase_manager.py`
- `/scripts/test_phase_manager.py`
- `/phases/index.json`
- `/phases/taxonomy.json`
- `/phases/README.md`
- `/phases/040-049/43-harness-phase-catalog/index.json`
- `/phases/040-049/43-harness-phase-catalog/step1.md`
- `/phases/040-049/43-harness-phase-catalog/step3.md`
- `/phases/040-049/44-phase-history-migration/index.json`

## 작업

실제 파일을 이동하기 전에 테스트를 먼저 작성하고 legacy migration manifest가 이관 후 strict registry 계약까지 만족하도록 보완한다.

- 현재 manager가 legacy directory 경로만 bucket으로 바꾸고 기존 registry entry에 `id`, `slug`, `areas`, `kind`, `tags`를 채우지 않아 이관 후 `Validate -Strict`가 실패하는 회귀 테스트를 먼저 추가한다.
- `/phases/taxonomy.json`에 phase 0~43의 명시적 legacy classification override를 추가한다. 각 entry는 기존 basename의 실제 작업 범위에 맞는 1개 이상 areas, 정확히 1개 kind, kebab-case tags를 가져야 한다.
- areas는 기존 허용값만 사용하고 교차 작업은 복수 areas로 표현한다. harness 관련 phase는 `harness`, emulator/launch/kapt는 `android-platform`, schedule/calendar/reminder는 `schedule`, character/stats/equipment는 `character`, monster/battle은 `combat`, sprite/sheet는 `art`를 조합한다.
- kind는 docs-only `documentation`, 설계-only `design`, standalone visual asset `asset`, harness/tool bootstrap `infrastructure`, 복구/회귀/마감 polish `fix`, 사용자 기능과 runtime 통합 `feature`로 분류한다.
- migration은 override를 적용해 legacy registry entry에 신규 metadata를 추가하되 기존 `status`, `summary`, `completed_at`, `blocked_at`, `failed_at`과 배열 순서를 보존한다.
- 신규 metadata가 이미 있는 phase 44는 수정하거나 move 대상으로 포함하지 않는다.
- dry-run manifest는 이동 44개(0~43), 신규 nested phase 제외, exact tracked reference rewrite, phase index binary/text hash, metadata backfill 결과와 이관 후 strict validation 가능 여부를 검증할 수 있어야 한다.
- 실제 migration은 이 step에서 실행하지 않는다.

`/scripts/test_phase_manager.py`에 dry-run 무변경, apply 후 strict validation 통과, override 누락/잘못된 taxonomy 거부, 기존 역사 필드 byte-value 보존, nested entry 비변경 테스트를 추가한다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_phase_manager.py --basetemp .\.venv\pytest-tmp
```

```powershell
$before = git status --short
$manifest = .\.venv\Scripts\python.exe scripts\phase_manager.py migrate-legacy --dry-run | ConvertFrom-Json
if (-not $manifest.dry_run) { throw 'migration manifest is not dry-run' }
if ($manifest.moves.Count -ne 44) { throw "expected 44 legacy moves, got $($manifest.moves.Count)" }
if ($manifest.moves.id -contains 44) { throw 'active nested phase 44 must not move' }
if (-not $manifest.strict_ready) { throw 'post-migration registry is not strict-ready' }
$after = git status --short
if (($before -join "`n") -ne ($after -join "`n")) { throw 'dry-run changed the worktree' }
git diff --check
```

## 검증 절차

1. strict validation 실패 fixture를 먼저 추가하고 metadata backfill 구현 후 통과시킨다.
2. 실제 저장소 dry-run에서 0~43만 이동 대상으로 잡히고 phase 44는 제외되는지 확인한다.
3. dry-run 전후 작업 트리 변경 목록이 동일한지 확인한다.
4. `/phases/040-049/44-phase-history-migration/index.json`의 step 0을 completed로 바꾸고 한국어 summary와 completed_at을 기록한다.

## 금지사항

- 실제 migration을 실행하지 마라. 이유: metadata backfill과 manifest를 독립적으로 검증한 뒤 다음 step에서 적용해야 한다.
- 기존 status, summary, timestamp를 재생성하거나 정규화하지 마라. 이유: 완료·차단 기록은 보존 대상이다.
- taxonomy 허용값 밖의 임의 분류를 추가하지 마라. 이유: catalog filter의 결정성을 유지해야 한다.
- Android 소스나 Gradle 구성을 수정하지 마라. 이유: 이 step은 harness metadata 계층만 다룬다.
- 기존 테스트를 깨뜨리지 마라.
