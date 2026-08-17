# Step 1: 기존 phase history를 숫자 bucket으로 이관

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/DEVELOPMENT.md`
- `/.agents/skills/harness/SKILL.md`
- `/scripts/phase_manager.py`
- `/scripts/test_phase_manager.py`
- `/phases/index.json`
- `/phases/taxonomy.json`
- `/phases/README.md`
- `/phases/040-049/44-phase-history-migration/index.json`
- `/phases/040-049/44-phase-history-migration/step0.md`

## 작업

검증된 manager를 사용해 기존 phase 0~43을 10개 단위 bucket으로 실제 이관한다.

- 작업 트리에는 phase 44 생성 및 완료된 step 0 변경만 있어야 하며 예상하지 못한 사용자 변경이 있으면 migration을 실행하지 않고 blocked로 기록한다.
- 실제 적용 직전에 `MigrateLegacy -DryRun`을 다시 실행해 move가 정확히 44개이고 id 44가 제외되며 `strict_ready=true`인지 확인한다.
- `/.\scripts\run_phase_manager.ps1 -Command MigrateLegacy`를 한 번 실행한다. manager가 등록된 legacy phase prefix만 이동·치환하도록 하며 별도 수동 move나 대량 검색/치환을 병행하지 않는다.
- 이동 대상은 `000-009`부터 `040-049` bucket의 0~43 phase다. 이미 nested인 `040-049/44-phase-history-migration`은 현재 runner 경로이므로 그대로 둔다.
- phase-local `index.json`의 project, phase, steps, status, summary, timestamp 값은 변경하지 않는다.
- step Markdown과 두 외부 docs 참조는 old phase prefix만 canonical bucket prefix로 바꾸고 그 밖의 본문은 보존한다.
- migration 뒤 `Sync`, `Validate -Strict`, `Sync -Check`를 실행한다.
- Git이 모든 phase 파일을 rename 또는 동일 내용 이동으로 인식하는지 `git diff --summary`와 `git diff --name-status`로 확인한다.

## Acceptance Criteria

```powershell
.\scripts\run_phase_manager.ps1 -Command MigrateLegacy
.\scripts\run_phase_manager.ps1 -Command Sync
.\scripts\run_phase_manager.ps1 -Command Validate -Strict
.\scripts\run_phase_manager.ps1 -Command Sync -Check
```

```powershell
$flat = Get-ChildItem -LiteralPath phases -Directory | Where-Object { $_.Name -match '^\d+-' }
if ($flat) { throw "legacy flat phase directories remain: $($flat.Name -join ', ')" }
$buckets = @('000-009','010-019','020-029','030-039','040-049')
foreach ($bucket in $buckets) {
  if (-not (Test-Path -LiteralPath (Join-Path phases $bucket) -PathType Container)) { throw "missing bucket: $bucket" }
}
if (-not (Test-Path -LiteralPath 'phases\040-049\44-phase-history-migration\index.json')) { throw 'active phase 44 moved or missing' }
git diff --check
```

## 검증 절차

1. preflight manifest와 작업 트리 범위를 확인한 뒤 manager 한 경로로만 migration을 적용한다.
2. strict validation과 catalog sync가 통과하는지 확인한다.
3. flat phase directory가 0개이고 phase 44가 기존 nested 경로에 남아 있는지 확인한다.
4. `/phases/040-049/44-phase-history-migration/index.json`의 step 1을 completed로 바꾸고 이동 수, reference rewrite 수, strict 결과를 한국어 summary로 기록한다.

## 금지사항

- 수동 `Move-Item`, 별도 rename loop 또는 광범위 정규식 치환을 추가 실행하지 마라. 이유: 검증된 migration manifest와 실제 적용 결과가 달라질 수 있다.
- phase 44 디렉터리를 이동하지 마라. 이유: 실행 중 runner가 현재 index와 step을 잃는다.
- 기존 phase 파일을 삭제·압축·병합하지 마라. 이유: 과거 작업 기록을 파일 단위로 보존해야 한다.
- 파괴적 Git 명령을 실행하지 마라. 이유: AGENTS.md의 금지 규칙과 사용자 기록 보존 요구를 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
