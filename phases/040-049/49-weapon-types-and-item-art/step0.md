# Step 0: define-weapon-asset-contract

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/README.md`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/art/character/previews/runtime-equipped-reference@8x.png`
- `/docs/art/character/previews/palette@8x.png`
- `/docs/art/equipment/README.md`
- `/docs/art/equipment/todo-quest-gloves-shoes-layers-spec.json`
- `/scripts/validate_character_equipment_layers.py`
- `/scripts/test_validate_character_equipment_layers.py`
- `/phases/040-049/49-weapon-types-and-item-art/index.json`

## 작업

아트 validator 테스트를 먼저 작성해 아직 없는 무기 계약이 실패하는지 확인한 뒤, 상점 gameplay 무기 네 종의 독립 layer 계약을 추가한다. 이 step에서는 PNG, Android Kotlin, Room schema를 수정하지 않는다.

새 계약 파일은 `/docs/art/equipment/todo-quest-weapon-layers-spec.json`이며 기존 장비 spec과 같은 `contractKind: character-equipment-layer-variants`를 사용한다. 캐릭터 schema v5에서 무기 group 전체가 최상단이라는 예정 계약을 명시하되, validator는 이 step 동안 기존 schema-v4 장비 spec도 계속 검증할 수 있어야 한다.

```text
python scripts/validate_character_equipment_layers.py --spec <path> --check-contract
python scripts/validate_character_equipment_layers.py --spec <path> --check-sources
python scripts/validate_character_equipment_layers.py --spec <path> --check
```

장비 정의는 다음 값으로 고정한다.

| 장비 | ID/key | WeaponType | imageKey/layerKey | canonical/runtime |
|---|---|---|---|---|
| 낡은 검 | `1001`, `worn_sword` | `LONGSWORD` | `weapon_worn_sword` | `layers/weapon_worn_sword.png`, `character/layers/weapon_worn_sword.png` |
| 철 장검 | `1002`, `iron_longsword` | `LONGSWORD` | `weapon_iron_longsword` | `layers/weapon_iron_longsword.png`, `character/layers/weapon_iron_longsword.png` |
| 물푸레나무 창 | `1017`, `ash_spear` | `SPEAR` | `weapon_ash_spear` | `layers/weapon_ash_spear.png`, `character/layers/weapon_ash_spear.png` |
| 강철 철퇴 | `1018`, `steel_mace` | `BLUNT` | `weapon_steel_mace` | `layers/weapon_steel_mace.png`, `character/layers/weapon_steel_mace.png` |

공통 계약은 `64×64 RGBA`, `[0,0,63,63]`, 중심축 `x=32`, 발 기준 `y=58`, 기존 16색 production palette, alpha `[0,255]`, transparent RGBA `[0,0,0,0]`, source-over와 nearest-neighbor다. 네 gameplay 무기는 각각 단일 `weapon_front` source이며 캐릭터 합성에서 full origin을 그대로 사용하고 상점 thumbnail만 불투명 bounds를 읽어 확대한다.

모든 무기는 primary grip anchor `(42,42)`를 불투명하게 포함하고 8-neighbor 기준 하나의 연결된 실루엣이어야 한다. opaque envelope는 `[40,4,58,58]` 안에 두며, 얼굴 보호 영역 `[20,7,44,28]`과 겹치지 않는다. 손 영역 `[40,39,44,45]`에는 손잡이만 겹치고 칼날·창날·철퇴 머리는 겹치지 않는다. 무기 전체가 hands/headgear/accessory 뒤가 아니라 schema-v5 최종 weapon group에서 그려진다는 z-order를 계약으로 고정한다.

artifact는 초기 `pendingGeneration` status와 nullable bounds/count/byte/hash를 사용한다. 장비별 `*-equipped.png`, `*-equipped@8x.png` 8개와 `weapon-combination-matrix.png` `128×128`, `weapon-combination-matrix@4x.png` `512×512` preview 계약을 정의한다. 실제 파일이 생기기 전에 hash나 pixel count를 추정하지 않는다.

validator 테스트는 spec 분기, 안전 상대 경로, 크기·mode·alpha·palette·bounds·연결성·grip·face exclusion·metadata hash·preview 최근접 확대·canonical/runtime byte 불일치를 포함한다. fixture는 `tmp_path`만 사용한다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_character_equipment_layers.py --basetemp build\pytest-49-weapon-contract
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-weapon-layers-spec.json --check-contract
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-helmet-layers-spec.json --check
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-top-bottom-layers-spec.json --check
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-gloves-shoes-layers-spec.json --check
git diff --check
```

## 검증 절차

1. 테스트를 먼저 추가하고 새 spec·validator 분기 전 예상 실패를 확인한다.
2. 선언형 계약과 validator를 구현한 뒤 AC를 실행한다.
3. 기존 세 equipment spec과 15개 character canonical source가 변경되지 않았는지 확인한다.
4. task index의 step 0을 `completed`로 바꾸고 네 key, grip, 최상단 계약을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- PNG나 Android Kotlin 코드를 수정하지 마라. 이유: 이 step은 아트 계약과 검증 도구만 다룬다.
- 최종 pixel count와 hash를 임의로 작성하지 마라. 이유: 생성된 artifact에서 계산해야 한다.
- 무기를 얼굴 영역에 배치하지 마라. 이유: 최상단 합성에서 캐릭터 얼굴을 가리게 된다.
- 테스트보다 validator 구현을 먼저 작성하지 마라. 이유: AGENTS.md의 테스트 우선 규칙을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
