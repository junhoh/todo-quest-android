# Step 0: define-gloves-shoes-asset-contract

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/README.md`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/art/character/layers/hands_front.png`
- `/docs/art/character/layers/shoes_adventure.png`
- `/docs/art/equipment/README.md`
- `/docs/art/equipment/todo-quest-helmet-layers-spec.json`
- `/docs/art/equipment/todo-quest-top-bottom-layers-spec.json`
- `/scripts/validate_character_equipment_layers.py`
- `/scripts/test_validate_character_equipment_layers.py`
- `/phases/040-049/48-shop-gloves-shoes-item-art/index.json`

## 작업

아트 검증 테스트를 먼저 작성해 아직 없는 장갑·신발 계약에서 실패하는지 확인한 뒤, 상점의 기존 `가죽 장갑(1011)`·`여행자의 장화(1012)`와 신규 `강철 건틀릿(1015)`·`바람걸음 장화(1016)`에 사용할 네 개의 독립 layer 계약을 추가한다. 이 step에서는 PNG와 Android Kotlin 코드를 수정하지 않는다.

새 계약 파일은 `/docs/art/equipment/todo-quest-gloves-shoes-layers-spec.json`이다. 기존 `schemaVersion: 1`, `contractKind: character-equipment-layer-variants`를 유지하고 `scripts/validate_character_equipment_layers.py`의 공개 CLI가 투구, 상·하의, 장갑·신발 세 spec을 모두 처리하게 한다.

```text
python scripts/validate_character_equipment_layers.py --spec <path> --check-contract
python scripts/validate_character_equipment_layers.py --spec <path> --check-sources
python scripts/validate_character_equipment_layers.py --spec <path> --check
```

공통 계약은 캐릭터 schema v4의 `64×64 RGBA`, `[0,0,63,63]`, 중심축 `x=32`, 발 기준 `y=58`, 정확한 16색 production palette, 이진 alpha `[0,255]`, transparent RGBA `[0,0,0,0]`, source-over와 nearest-neighbor를 사용한다. 캐릭터 합성에서는 원점 이동·crop·scale을 금지하고 상점 thumbnail에서만 불투명 bounds를 읽기 전용으로 확대한다.

장비 정의는 다음 값으로 고정한다.

| 장비 | equipment ID/key | imageKey/layerKey | canonical/runtime |
|---|---|---|---|
| 가죽 장갑 | `1011`, `leather_gloves` | `gloves_leather` | `layers/gloves_leather.png`, `character/layers/gloves_leather.png` |
| 강철 건틀릿 | `1015`, `steel_gauntlets` | `gloves_steel_gauntlets` | `layers/gloves_steel_gauntlets.png`, `character/layers/gloves_steel_gauntlets.png` |
| 여행자의 장화 | `1012`, `travelers_boots` | `shoes_travelers_boots` | `layers/shoes_travelers_boots.png`, `character/layers/shoes_travelers_boots.png` |
| 바람걸음 장화 | `1016`, `windwalker_boots` | `shoes_windwalker_boots` | `layers/shoes_windwalker_boots.png`, `character/layers/shoes_windwalker_boots.png` |

두 장갑은 `renderSlot=hands_front`, `equipmentSlot=GLOVES`이며 `/docs/art/character/layers/hands_front.png`의 alpha mask와 정확히 같아야 한다. 전체 inclusive opaque bounds는 `[21,39,43,45]`, opaque pixel은 38개, 8-connected component는 왼손 `[21,39,24,45]` 19개와 오른손 `[40,39,43,45]` 19개다. 장갑은 피부 silhouette를 넓히거나 줄이지 않고 `weapon_held → hands_front variant → weapon_front` grip 순서를 보존한다.

두 신발은 `renderSlot=shoes`, `equipmentSlot=SHOES`이며 `/docs/art/character/layers/shoes_adventure.png`의 alpha mask와 정확히 같아야 한다. 전체 bounds는 `[23,53,41,58]`, opaque pixel은 104개, 두 component는 왼발 `[23,53,31,58]` 52개와 오른발 `[33,53,41,58]` 52개다. 왼쪽 `[24,53,31,54]`, 오른쪽 `[33,53,40,54]` 발목 overlap을 모두 채우고 sole row `y=58`과 center를 유지한다.

각 artifact에는 초기 `pendingGeneration` status와 nullable count, byte count, file/raw-RGBA/alpha-mask SHA-256를 둔다. preview 계약은 장비별 `*-equipped.png`, `*-equipped@8x.png` 8개와 `gloves-shoes-combination-matrix.png` `128×128`, `gloves-shoes-combination-matrix@4x.png` `512×512`를 정의한다. 초기 hash를 임의로 넣지 않는다.

validator 테스트는 세 spec 분기, 안전 상대 경로, 크기·mode·alpha·palette·bounds·mask 불일치, 장갑 component와 grip mask, 신발 component·발목·sole, metadata hash, preview 크기·최근접 확대, canonical/runtime 불일치를 포함한다. fixture는 `tmp_path`만 사용한다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_character_equipment_layers.py --basetemp build\pytest-48-gloves-shoes-contract
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-gloves-shoes-layers-spec.json --check-contract
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-helmet-layers-spec.json --check
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-top-bottom-layers-spec.json --check
git diff --check
```

## 검증 절차

1. 테스트를 먼저 추가하고 새 spec·validator 분기 전 예상 실패를 확인한다.
2. 선언형 계약과 validator를 구현한 뒤 AC를 실행한다.
3. 기존 투구·상하의 spec과 character schema v4의 canonical source가 변경되지 않았는지 확인한다.
4. task index의 step 0을 `completed`로 바꾸고 네 key와 두 alpha mask 계약을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- PNG나 Android Kotlin 코드를 수정하지 마라. 이유: 이 step은 아트 계약과 검증 도구만 다룬다.
- 장갑을 새 z-order slot으로 추가하지 마라. 이유: 기존 `hands_front` 대체 계약으로 검 grip을 보존한다.
- 신발 높이나 발 위치를 바꾸지 마라. 이유: 모든 하의와의 발목 seam과 `soleY=58`을 보존해야 한다.
- 테스트보다 validator 구현을 먼저 작성하지 마라. 이유: AGENTS.md의 테스트 우선 규칙을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
