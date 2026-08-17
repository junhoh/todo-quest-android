# Step 2: extend-character-layer-validation

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/art/character/character-base-spec.json`
- `/docs/art/character/character-layer-migration-report.md`
- `/scripts/validate_character_sheet.py`
- `/scripts/test_validate_character_sheet.py`
- `/requirements-dev.txt`
- `/phases/030-039/32-character-layer-runtime-composition/index.json`

## 작업

테스트를 먼저 작성한 뒤 schema v1~v3 회귀를 유지하면서 schema v4 independent layer 검증을 구현한다. 필요하면 `/scripts/build_character_assets.py`와 `/scripts/test_build_character_assets.py`를 추가한다.

builder CLI는 다음 계약을 제공한다.

- `--check-contract`: schema 구조와 canonical reference만 검사한다.
- `--check-layer <layer-id>`: 한 canonical source와 runtime copy가 있으면 64×64 RGBA, alpha 0/255, production palette, bounds/hash, canvas/anchor profile을 검사한다.
- `--write`: 모든 source layer가 존재할 때 runtime byte-identical copies, preview PNG와 final 512×128 sheet를 생성하고 저장된 파일을 다시 읽어 spec의 bounds/file/raw RGBA/alpha/tile hashes를 갱신한다.
- `--check`: 생성 결과를 메모리에서 다시 만들고 저장된 layer/runtime/previews/sheet/spec과 비교하며 아무 파일도 쓰지 않는다.

검증기는 guide를 색상으로 찾지 않고 spec의 generated overlay mask만 사용한다. `#E05252` production pixels와 tealAccent production pixels를 보존하고 debugGuideColor가 runtime layer에 없음을 검사한다. geometry reference가 external base-body인지, legacy base가 body_base가 아닌지, weapon 3개 source가 독립 파일인지, sheet가 독립 layer와 deterministic debug/palette tile에서만 생성되는지, pixel count가 크기와 일치하는지 검사한다.

조합 검증 helper는 top 2 × bottom 2 × shoes 2 × head/accessory/weapon on/off의 64개 상태를 source-over로 만들고 face features, hand protection/weapon grip exception, waist/ankle opaque overlap, neutral underwear exposure, double outline, centerX/soleY와 input layer position 불변을 검사할 수 있어야 한다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_character_sheet.py scripts\test_build_character_assets.py --basetemp .\.venv\pytest-tmp
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-base-sheet.png --spec docs\art\character\character-base-spec.json
.\.venv\Scripts\python.exe scripts\build_character_assets.py --check-contract
git diff --check
```

## 검증 절차

1. 잘못된 크기, mode, partial alpha, 색, stale hash, guide contamination, reference 오류, overlap gap fixture가 각각 실패하는지 확인한다.
2. v1~v3 기존 테스트가 계속 통과하는지 확인한다.
3. task index의 step 2를 `completed`로 바꾸고 builder CLI와 schema v4 테스트 수를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 실제 project layer가 아직 없다는 이유로 검증을 완화하지 마라. 이유: fixture로 계약을 먼저 고정해야 한다.
- debug color를 production palette에 추가하지 마라. 이유: 제작 색과 overlay 역할을 분리해야 한다.
- 검증 명령에서 파일을 수정하지 마라. 이유: `--check`와 `--check-layer`는 read-only여야 한다.
- 기존 테스트를 깨뜨리지 마라.
