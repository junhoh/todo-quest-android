# Step 0: define-outfit-asset-contract

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/README.md`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/art/equipment/README.md`
- `/docs/art/equipment/todo-quest-helmet-layers-spec.json`
- `/scripts/validate_character_equipment_layers.py`
- `/scripts/test_validate_character_equipment_layers.py`
- `/phases/040-049/47-shop-top-bottom-item-art/index.json`

## 작업

아트 검증 테스트를 먼저 작성해 아직 없는 상·하의 계약에서 실패하는지 확인한 뒤, 상점의 상의 3종과 하의 3종에 사용할 독립 layer 계약을 추가한다. 이 step에서는 PNG와 Android Kotlin 코드를 수정하지 않는다.

생성할 계약은 `/docs/art/equipment/todo-quest-top-bottom-layers-spec.json`이며 기존 `schemaVersion: 1`, `contractKind: character-equipment-layer-variants`를 유지한다. `scripts/validate_character_equipment_layers.py`의 공개 CLI도 다음 세 경계를 유지하면서 투구와 새 상·하의 spec을 모두 처리하도록 일반화한다.

```text
python scripts/validate_character_equipment_layers.py --spec <path> --check-contract
python scripts/validate_character_equipment_layers.py --spec <path> --check-sources
python scripts/validate_character_equipment_layers.py --spec <path> --check
```

공통 계약은 캐릭터 schema v4의 `64×64 RGBA`, `[0,0,63,63]`, 중심축 `x=32`, 발 기준 `y=58`, 정확한 16색 production palette, 이진 alpha `[0,255]`, transparent RGBA `[0,0,0,0]`, source-over와 nearest-neighbor를 그대로 사용한다. 모든 외부 경계는 `#263B5A` 1 logical pixel이며 고립 픽셀, 반투명 fringe, chroma key, 의도하지 않은 2×2 외곽선 덩어리를 허용하지 않는다. 캐릭터 합성에서는 원점 이동·crop·scale을 금지하고 thumbnail에서만 불투명 bounds read-only 확대를 허용한다.

장비 정의는 다음 값으로 고정한다.

| 장비 | ID | imageKey/layerKey | canonical/runtime | inclusive bounds |
|---|---:|---|---|---|
| 천 상의 | `1005` | `top_cloth` | `layers/top_cloth.png`, `character/layers/top_cloth.png` | `[20,29,44,45]` |
| 가죽 갑옷 | `1006` | `top_leather_armor` | `layers/top_leather_armor.png`, `character/layers/top_leather_armor.png` | `[20,29,44,45]` |
| 철 흉갑 | `1007` | `top_iron_breastplate` | `layers/top_iron_breastplate.png`, `character/layers/top_iron_breastplate.png` | `[20,29,44,45]` |
| 천 바지 | `1008` | `bottom_cloth_pants` | `layers/bottom_cloth_pants.png`, `character/layers/bottom_cloth_pants.png` | `[24,41,40,54]` |
| 가죽 바지 | `1009` | `bottom_leather_pants` | `layers/bottom_leather_pants.png`, `character/layers/bottom_leather_pants.png` | `[24,41,40,54]` |
| 강철 각반 | `1010` | `bottom_steel_greaves` | `layers/bottom_steel_greaves.png`, `character/layers/bottom_steel_greaves.png` | `[24,41,40,54]` |

상의 contract는 `slot=top`, 하의 contract는 `slot=bottom`으로 구분한다. 모든 상의와 하의는 허리 겹침 `[24,41,40,43]`을 완전히 채운다. 모든 하의는 왼쪽 발목 `[24,53,31,54]`와 오른쪽 발목 `[33,53,40,54]`을 완전히 채운다. 숨겨지는 허리·발목 수평 접합선에는 이중 외곽선을 허용하지 않으며, top/bottom/shoes 합성 결과에 투명 틈이 없어야 한다. 각 장비 layer는 하나의 8-connected component여야 한다.

각 장비의 canonical/runtime artifact에는 `pendingGeneration` status, bounds, nullable count/byte/hash metadata를 둔다. preview contract는 장비별 1×/8× 착용 preview 12개와 신규 3×3 상·하의 조합 matrix의 1× `192×192`, 4× `768×768` 두 파일을 정의한다. 초기 hash와 count를 임의로 채우지 않는다.

validator 테스트는 두 spec의 contract 분기, 안전 상대 경로, 잘못된 size/mode/alpha/palette/bounds/slot, 허리·발목 미충족, 숨은 seam outline, 연결성, metadata hash, preview 크기·pixel 확대, canonical/runtime 불일치를 포함한다. fixture는 `tmp_path`만 사용한다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_character_equipment_layers.py --basetemp build\pytest-47-outfit-contract
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-top-bottom-layers-spec.json --check-contract
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-helmet-layers-spec.json --check
git diff --check
```

## 검증 절차

1. 테스트를 먼저 추가하고 새 spec·validator 분기 전 예상 실패를 확인한다.
2. 선언형 계약과 validator를 구현한 뒤 AC를 실행한다.
3. 기존 투구 spec과 character schema v4의 15개 source 및 generated sheet가 변경되지 않았는지 확인한다.
4. task index의 step 0을 `completed`로 바꾸고 생성 파일과 고정 key/bounds를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- PNG나 Android Kotlin 코드를 수정하지 마라. 이유: 이 step은 아트 계약과 검증 도구만 다룬다.
- 기존 캐릭터 spec을 schema v5로 올리지 마라. 이유: gameplay 장비 variant는 기존 15-source 계약과 분리한다.
- 투구 검증을 새 상·하의 전용 코드로 깨뜨리지 마라. 이유: 하나의 validator가 두 장비 spec을 회귀 검증해야 한다.
- 테스트보다 구현을 먼저 작성하지 마라. 이유: AGENTS.md의 테스트 우선 규칙을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
