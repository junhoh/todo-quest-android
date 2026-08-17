# Step 5: register-topmost-weapon-runtime-layers

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/README.md`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/art/equipment/README.md`
- `/docs/art/equipment/todo-quest-weapon-layers-spec.json`
- `/docs/art/equipment/layers/weapon_worn_sword.png`
- `/docs/art/equipment/layers/weapon_iron_longsword.png`
- `/docs/art/equipment/layers/weapon_ash_spear.png`
- `/docs/art/equipment/layers/weapon_steel_mace.png`
- `/scripts/build_character_assets.py`
- `/scripts/validate_character_sheet.py`
- `/scripts/validate_character_equipment_layers.py`
- `/app/src/main/java/com/todoquest/domain/model/CharacterLoadout.kt`
- `/app/src/main/java/com/todoquest/ui/character/CharacterLayerCatalog.kt`
- `/app/src/main/java/com/todoquest/ui/character/CharacterBitmapComposer.kt`
- `/app/src/test/java/com/todoquest/ui/character/CharacterLayerCatalogTest.kt`
- `/app/src/test/java/com/todoquest/ui/character/CharacterBitmapComposerTest.kt`
- `/phases/040-049/49-weapon-types-and-item-art/index.json`

## 작업

renderer·builder 테스트를 먼저 확장해 새 runtime path와 최상단 z-order가 아직 지원되지 않는 예상 실패를 확인한 뒤 캐릭터 layer schema와 runtime mapping을 v5로 갱신한다. Room·Shop UI는 수정하지 않는다.

- 네 canonical PNG를 같은 파일명의 `/app/src/main/assets/character/layers/` 경로에 byte-identical하게 복사한다.
- character schema version을 `5`, anchor profile을 `canvas-64-center-x-32-sole-y-58-schema-v5`로 올린다.
- 15개 canonical source 자체는 유지하고 z-order를 다음처럼 바꾼다.

```text
ACCESSORY_BACK → HAIR_BACK → HEADGEAR_BACK → BODY_BASE → SHOES → BOTTOM → TOP
→ HANDS_FRONT → FACE_OVERLAY → HAIR_FRONT → HEADGEAR_FRONT → ACCESSORY_FRONT
→ WEAPON_BACK → WEAPON_HELD → WEAPON_FRONT
```

무기 group의 모든 source가 손·머리·액세서리 뒤에 그려져 무기 픽셀이 캐릭터의 어떤 layer에도 가려지지 않아야 한다. 기존 `WEAPON_DEFAULT_SWORD`는 세 source를 새 최종 group 순서로 유지한다. gameplay weapon 네 ID는 각각 단일 PNG를 정확히 하나의 `WEAPON_FRONT` definition으로 해석한다.

`CharacterLayerDefinition.zIndex == slot.ordinal`과 64×64 full-origin 계약은 유지한다. item별 offset, crop, scale을 추가하지 않는다.

`build_character_assets.py`, `validate_character_sheet.py`와 테스트는 schema v5를 canonical로 인식하도록 갱신한다. 기존 helmet/top-bottom/gloves-shoes equipment spec의 base schema/anchor reference도 v5로 바꾸고, 새 z-order 영향을 받는 generated modular sheet와 모든 착용 preview/hash를 실제 합성 결과로 재생성한다. 비무기 canonical/runtime PNG bytes와 source hash는 바꾸지 않는다.

테스트는 다음을 검증한다.

- runtime asset path set은 기존 27개와 신규 4개를 합친 31개다.
- `4 head × 5 top × 5 bottom × 4 shoes × 3 gloves × 2 accessory × 6 weapon = 7200`개 loadout이 유효하고 합성 가능하다.
- 무기와 손/상의/머리/액세서리가 겹치는 pixel에서는 무기 RGBA가 최종 결과다.
- 네 gameplay weapon의 render state raw RGBA가 step 1의 1× preview와 matrix cell에 일치한다.
- weapon off와 모든 비무기 source는 v4 결과를 보존하고, weapon on derived golden만 승인된 v5 z-order로 변경된다.
- missing/wrong-size weapon asset은 기존처럼 빈 render로 격리되어 일정·구매·장착 상태를 되돌리지 않는다.

weapon spec의 runtime artifact status와 metadata를 실제 PNG 기준으로 `available`로 갱신한다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-weapon-layers-spec.json --check
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-helmet-layers-spec.json --check
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-top-bottom-layers-spec.json --check
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-gloves-shoes-layers-spec.json --check
.\.venv\Scripts\python.exe scripts\build_character_assets.py --check
.\.venv\Scripts\python.exe -m pytest scripts\test_build_character_assets.py scripts\test_validate_character_sheet.py scripts\test_validate_character_equipment_layers.py --basetemp build\pytest-49-weapon-runtime
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.ui.character.CharacterLayerCatalogTest" --tests "com.todoquest.ui.character.CharacterBitmapComposerTest"
.\gradlew.bat :app:assembleDebug
git diff --check
```

## 검증 절차

1. renderer·builder 테스트를 먼저 변경하고 예상 실패를 확인한다.
2. runtime PNG, schema v5, catalog mapping과 generated artifact를 연결한 뒤 AC를 실행한다.
3. canonical/runtime equality, 31개 path, 7200개 조합과 topmost pixel을 확인한다.
4. task index의 step 5를 `completed`로 바꾸고 schema v5·topmost group·runtime 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 손을 무기보다 위에 다시 배치하지 마라. 이유: 사용자가 무기 전체 최상단을 선택했다.
- 새 gameplay 무기를 3분할 source로 강제하지 마라. 이유: 승인 계약은 단일 topmost overlay다.
- 비무기 canonical/runtime PNG를 다시 그리지 마라. 이유: z-order 변경의 범위를 넘어선다.
- 캐릭터 합성에서 장비만 crop·이동·확대하지 마라. 이유: 공통 원점과 anchor를 보존해야 한다.
- 테스트보다 구현을 먼저 작성하지 마라. 이유: AGENTS.md의 테스트 우선 규칙을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
