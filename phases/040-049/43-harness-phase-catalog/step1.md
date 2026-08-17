# Step 1: Phase manager catalog CLI

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/DEVELOPMENT.md`
- `/scripts/phase_manager.py`
- `/scripts/test_phase_manager.py`
- `/phases/taxonomy.json`
- `/phases/040-049/43-harness-phase-catalog/index.json`
- `/phases/040-049/43-harness-phase-catalog/step0.md`

## 작업

테스트를 먼저 작성하고 registry core 위에 phase 관리 CLI와 PowerShell wrapper를 구현한다.

- `/scripts/run_phase_manager.ps1`는 저장소 루트와 `.venv\Scripts\python.exe`를 기존 harness wrapper와 같은 방식으로 확인하고 `PYTHONUTF8=1`로 `/scripts/phase_manager.py`를 실행한다.
- CLI는 PowerShell wrapper에서 `-Command Suggest|Create|List|Show|Validate|Sync|MigrateLegacy` 형태로 호출할 수 있어야 한다.
- `Suggest`는 slug, step 이름과 의도된 파일 경로를 taxonomy의 결정론적 규칙으로 분석해 areas/kind/tags 후보와 근거를 출력한다. 모호하거나 미분류면 임의 확정하지 않고 구조화된 ambiguity 결과와 non-zero exit를 반환한다.
- `Create`는 다음 id와 bucket을 할당하고 신규 registry entry와 phase `index.json`을 생성한다. 생성 시 timestamp를 넣지 않고 모든 step을 pending으로 둔다. 기존 phase가 있는 경로를 덮어쓰지 않는다.
- `List`는 status/area/kind/tag, `Show`는 id/basename/dir selector를 지원한다.
- `Show`와 catalog는 `step*.md`에 실제로 기록된 다른 phase 경로만 outgoing/incoming reference로 계산한다. 자기 참조는 제외하고 관계 메타데이터를 registry에 저장하지 않는다.
- `Sync`는 phase index에서 status를 읽어 registry와 `/phases/README.md`를 결정론적으로 갱신한다. 최종 summary가 없으면 마지막 completed step summary를 표시용으로만 사용하며 원본 phase index를 수정하지 않는다. `-Check`는 쓰지 않고 drift 여부로 종료 코드를 반환한다.
- `Validate`는 legacy와 신규 entry가 공존하는 bootstrap 상태를 지원하고, strict option에서는 모든 entry의 신규 metadata와 bucket 공식을 요구한다. 실제 phase/step 참조가 존재하는지도 검사한다.
- `MigrateLegacy -DryRun`은 old→bucket 경로, 정확한 tracked reference 치환, 파일 hash와 참조 수 manifest를 출력만 하고 파일을 변경하지 않는다. 실제 적용 기능도 임시 fixture에서 테스트하되 현재 저장소에는 실행하지 않는다.
- migration은 registry에 등록된 정확한 phase prefix만 바꾸고 phase Markdown의 다른 본문과 phase index의 status·summary·timestamp를 변경하지 않는다.

CLI parsing, deterministic README, reference/reverse-reference, summary fallback, dry-run 무변경, exact rewrite, binary 비변경, idempotent sync를 `/scripts/test_phase_manager.py`에 추가한다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_phase_manager.py --basetemp .\.venv\pytest-tmp
```

```powershell
.\scripts\run_phase_manager.ps1 -Command Validate
.\scripts\run_phase_manager.ps1 -Command MigrateLegacy -DryRun
git diff --check
```

## 검증 절차

1. 테스트를 먼저 작성하고 manager CLI와 wrapper를 구현한다.
2. dry-run 전후 `git status --short`를 비교해 phase 이동이나 기존 기록 변경이 없는지 확인한다.
3. `/phases/040-049/43-harness-phase-catalog/index.json`의 step 1을 completed로 바꾸고 생성 파일과 보존 계약을 한국어 summary로 기록한다.

## 금지사항

- 현재 저장소에 실제 migration을 실행하지 마라. 이유: bootstrap runner 통합과 hook 검증이 아직 완료되지 않았다.
- reference 분석 결과를 실행 의존성이나 child prompt에 자동 주입하지 마라. 이유: phase는 독립 작업이며 기존 harness도 다른 phase를 자동 주입하지 않는다.
- 모호한 분류를 임의로 확정하지 마라. 이유: 잘못된 taxonomy를 자동 누적하면 catalog 신뢰성이 떨어진다.
- 기존 테스트를 깨뜨리지 마라.
