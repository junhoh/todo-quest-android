# Step 4: register-gloves-shoes-runtime-layers

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/art/equipment/todo-quest-gloves-shoes-layers-spec.json`
- `/docs/art/equipment/layers/gloves_leather.png`
- `/docs/art/equipment/layers/gloves_steel_gauntlets.png`
- `/docs/art/equipment/layers/shoes_travelers_boots.png`
- `/docs/art/equipment/layers/shoes_windwalker_boots.png`
- `/app/src/main/java/com/todoquest/domain/model/CharacterLoadout.kt`
- `/app/src/main/java/com/todoquest/ui/character/CharacterLayerCatalog.kt`
- `/app/src/main/java/com/todoquest/ui/character/CharacterBitmapComposer.kt`
- `/app/src/test/java/com/todoquest/ui/character/CharacterLayerCatalogTest.kt`
- `/app/src/test/java/com/todoquest/ui/character/CharacterBitmapComposerTest.kt`
- `/phases/040-049/48-shop-gloves-shoes-item-art/index.json`

## 작업

renderer 테스트를 먼저 확장해 네 새 ID의 runtime path와 장갑 대체 layer가 해석되지 않는 예상 실패를 확인한 뒤 runtime asset과 catalog mapping을 추가한다.

- canonical PNG를 같은 파일명의 `/app/src/main/assets/character/layers/` 경로에 byte-identical하게 복사한다.
- `CharacterLayerCatalog`는 `glovesId == null`이면 기존 `hands_front.png`, 두 유효 장갑 ID이면 대응 장갑 PNG를 정확히 하나의 `CharacterLayerSlot.HANDS_FRONT` definition으로 해석한다.
- 장갑 때문에 enum slot이나 ordinal을 추가하지 않는다. 기존 `WEAPON_HELD → HANDS_FRONT → WEAPON_FRONT` 순서를 유지한다.
- 두 새 신발은 기존 `outfitDefinition` prefix 계약으로 `SHOES` definition이 된다.
- 모든 source는 64×64 full origin, schema-v4 anchor와 `(0,0)` source-over를 사용한다. item별 translation, crop, scale을 추가하지 않는다.

테스트는 다음을 검증한다.

- runtime asset path set은 기존 23개와 신규 4개를 합친 27개다.
- `4 head × 5 top × 5 bottom × 4 shoes × 3 gloves × 2 accessory × 2 weapon = 4800`개 loadout이 catalog에서 유효하고 합성 가능하다.
- 장갑 미착용은 기존 38개 피부 RGBA를 유지하고 장갑 착용은 같은 alpha mask에서 각 장갑 RGBA로 완전히 교체한다.
- 장갑과 검 on/off 조합의 grip pixel과 z-order가 preview와 일치한다.
- 두 신발은 5개 하의 모두에서 발목 overlap, centerX와 soleY를 유지한다.
- 장비별 render state raw RGBA가 step 1의 1× preview와 matrix cell에 일치한다.
- 기존 default render state와 기존 투구·상하의 preview golden은 변경되지 않는다.
- missing/wrong-size asset은 기존처럼 빈 render로 격리된다.

spec의 runtime artifact status와 metadata를 실제 runtime PNG 기준으로 `available`로 갱신한다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-gloves-shoes-layers-spec.json --check
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-helmet-layers-spec.json --check
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-top-bottom-layers-spec.json --check
.\.venv\Scripts\python.exe scripts\build_character_assets.py --check
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.ui.character.CharacterLayerCatalogTest" --tests "com.todoquest.ui.character.CharacterBitmapComposerTest"
.\gradlew.bat :app:assembleDebug
git diff --check
```

## 검증 절차

1. renderer 테스트를 먼저 변경하고 runtime 사본·mapping 전 예상 실패를 확인한다.
2. runtime PNG와 catalog mapping을 연결한 뒤 AC를 실행한다.
3. canonical/runtime SHA-256, preview raw-pixel equality, 4800개 조합과 default golden을 확인한다.
4. task index의 step 4를 `completed`로 바꾸고 27개 path·4800개 조합·grip 보존을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 장갑 전용 z-order enum을 추가하지 마라. 이유: 기존 손 대체로 weapon grip을 보존해야 한다.
- 새 장비를 drawable resource나 generated modular sheet에서 잘라 읽지 마라. 이유: runtime source는 독립 asset layer다.
- 캐릭터 합성에서 장비만 crop·이동·확대하지 마라. 이유: 같은 64×64 원점과 anchor를 보존해야 한다.
- 테스트보다 구현을 먼저 작성하지 마라. 이유: AGENTS.md의 테스트 우선 규칙을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
