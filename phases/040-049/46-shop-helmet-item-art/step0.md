# Step 0: define-helmet-art-contract

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/README.md`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/art/character/layers/headgear_adventure.png`
- `/scripts/build_character_assets.py`
- `/scripts/test_validate_character_sheet.py`
- `/phases/040-049/46-shop-helmet-item-art/index.json`

## 작업

아트 검증 테스트를 먼저 작성해 아직 없는 계약에서 실패하는지 확인한 뒤, 상점의 `가죽 모자(1003)`와 `철 투구(1004)`에 사용할 두 개의 독립 headgear layer 계약과 선언형 validator를 구현한다. Android Kotlin 코드와 PNG는 수정하지 않는다.

생성 파일은 다음과 같다.

- `/docs/art/equipment/README.md`
- `/docs/art/equipment/todo-quest-helmet-layers-spec.json`
- `/scripts/validate_character_equipment_layers.py`
- `/scripts/test_validate_character_equipment_layers.py`

validator 공개 경계는 다음 CLI로 고정한다.

```text
python scripts/validate_character_equipment_layers.py --spec <path> --check-contract
python scripts/validate_character_equipment_layers.py --spec <path> --check-sources
python scripts/validate_character_equipment_layers.py --spec <path> --check
```

- `--check-contract`는 PNG가 아직 없어도 JSON 구조와 값만 검증한다.
- `--check-sources`는 canonical PNG, preview와 저장된 metadata를 검증하되 runtime 사본은 요구하지 않는다.
- `--check`는 canonical/runtime byte equality까지 포함한다.
- 오류는 파일과 첫 실패 좌표를 포함해 stderr에 기록하고 하나라도 있으면 non-zero로 종료한다.

JSON은 UTF-8, `schemaVersion: 1`, `contractKind: character-equipment-layer-variants`로 작성한다. 공통 계약은 다음 값을 정확히 포함한다.

- canvas `64×64`, `RGBA`, 좌표 `[0,0,63,63]`, 중심축 `x=32`, 캐릭터 발 기준 `y=58`.
- layer slot `headgear_front`, anchor profile `canvas-64-center-x-32-sole-y-58-schema-v4`.
- 허용 알파 `[0,255]`, 완전 투명 픽셀 RGBA `[0,0,0,0]`, source-over 합성, nearest-neighbor 표시.
- 캐릭터 schema v4의 정확한 16색 production palette만 허용하고 `#263B5A`를 1 logical pixel 외부 외곽선으로 사용한다.
- 중앙 얼굴 보호 영역 `[23,20,41,28]`은 두 layer에서 완전 투명해야 한다. 눈·코·입과 얼굴 픽셀을 덮지 않는다.
- 모든 불투명 픽셀은 하나의 8-connected component이며 고립 픽셀, 반투명 fringe, chroma key, 2×2 외곽선 덩어리를 허용하지 않는다.
- 캐릭터 합성에서는 layer의 64×64 원점을 유지하고 이동·crop·scale을 금지한다. 상점 thumbnail은 같은 PNG의 불투명 bounds를 읽기 전용으로 확대할 수 있다.

두 layer 정의를 다음과 같이 고정한다.

| item | id/key | source/runtime | inclusive opaque bounds | 디자인 |
|---|---|---|---|---|
| 가죽 모자 | `1003`, `headgear_leather_hat` | `layers/headgear_leather_hat.png`, `character/layers/headgear_leather_hat.png` | `[19,4,45,22]` | 가벼운 갈색 가죽 캡, 낮은 둥근 crown, 짧은 brow band, 열린 얼굴, 뿔·깃털 없음 |
| 철 투구 | `1004`, `headgear_iron_helmet` | `layers/headgear_iron_helmet.png`, `character/layers/headgear_iron_helmet.png` | `[18,4,46,29]` | 좌우 대칭의 개방형 강철 dome, brow guard와 바깥쪽 cheek guard, 열린 눈·표정, visor·뿔·plume 없음 |

각 정의에는 `imageKey`와 `layerKey`를 같은 id/key로 기록하고 canonical/runtime status, bounds, opaque count, file byte count, file/raw-RGBA/alpha-mask SHA-256 필드를 둔다. 최초 계약에서는 아직 없는 산출물 status를 `pendingGeneration`으로 두며 임의 hash를 넣지 않는다. preview 계약은 `previews/leather-hat-equipped.png`, `previews/leather-hat-equipped@8x.png`, `previews/iron-helmet-equipped.png`, `previews/iron-helmet-equipped@8x.png`를 정의한다.

validator 테스트는 contract 값, 안전 경로, 잘못된 size/mode/alpha/palette/bounds, 얼굴 침범, 연결성, 외곽선, metadata hash, preview, canonical/runtime 불일치를 모두 포함한다. fixture는 `tmp_path` 아래에서 만들고 저장소 PNG를 수정하지 않는다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_character_equipment_layers.py --basetemp build\pytest-46-helmet-contract
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-helmet-layers-spec.json --check-contract
git diff --check
```

## 검증 절차

1. validator 테스트를 먼저 작성하고 계약 파일·구현 전 예상된 실패를 확인한다.
2. 선언형 계약과 validator를 구현한 뒤 AC를 실행한다.
3. 기존 schema v4의 15개 canonical source와 generated sheet 계약을 변경하지 않았는지 확인한다.
4. AGENTS.md와 문서의 캐릭터 원점·팔레트·최근접 확대 규칙을 확인한다.
5. task index의 step 0을 `completed`로 바꾸고 생성 파일과 핵심 bounds를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- PNG나 Android Kotlin 코드를 생성·수정하지 마라. 이유: 이 step은 아트 계약과 검증 도구만 다룬다.
- 기존 `character-modular-sheet-spec.json`을 schema v5로 올리지 마라. 이유: gameplay variant는 기존 15-source 외형 기반 계약과 별도다.
- 테스트보다 validator 구현을 먼저 작성하지 마라. 이유: AGENTS.md의 테스트 우선 규칙을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
