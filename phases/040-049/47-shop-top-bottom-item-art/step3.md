# Step 3: register-outfit-runtime-layers

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/art/equipment/todo-quest-top-bottom-layers-spec.json`
- `/docs/art/equipment/layers/top_cloth.png`
- `/docs/art/equipment/layers/top_leather_armor.png`
- `/docs/art/equipment/layers/top_iron_breastplate.png`
- `/docs/art/equipment/layers/bottom_cloth_pants.png`
- `/docs/art/equipment/layers/bottom_leather_pants.png`
- `/docs/art/equipment/layers/bottom_steel_greaves.png`
- `/app/src/main/java/com/todoquest/domain/model/CharacterLoadout.kt`
- `/app/src/main/java/com/todoquest/ui/character/CharacterLayerCatalog.kt`
- `/app/src/main/java/com/todoquest/ui/character/CharacterBitmapComposer.kt`
- `/app/src/test/java/com/todoquest/ui/character/CharacterLayerCatalogTest.kt`
- `/app/src/test/java/com/todoquest/ui/character/CharacterBitmapComposerTest.kt`
- `/phases/040-049/47-shop-top-bottom-item-art/index.json`

## 작업

renderer 테스트를 먼저 확장해 여섯 새 ID의 runtime path가 아직 해석되지 않는 예상 실패를 확인한 뒤, canonical PNG를 동일 파일명의 `/app/src/main/assets/character/layers/` runtime 경로에 byte-identical하게 복사한다.

`CharacterLayerCatalog`의 기존 `outfitDefinition` prefix 계약을 그대로 사용해 새 top/bottom ID가 각각 `TOP`, `BOTTOM` slot definition으로 해석되게 한다. item별 translation, crop, scale, body/hands/shoes 수정이나 별도 합성 분기를 추가하지 않는다.

테스트는 다음을 검증한다.

- runtime asset path set은 기존 17개와 신규 6개를 합친 23개다.
- head 4 × top 5 × bottom 5 × shoes 2 × accessory 2 × weapon 2의 800개 loadout이 모두 catalog에서 유효하다.
- 새 definition은 64×64, schema-v4 anchor profile과 slot ordinal을 유지한다.
- `CharacterBitmapComposer`는 800개 조합을 non-null immutable ARGB_8888로 합성하고 전체 탐색의 layer decode 수는 23이다.
- 장비별 render state raw RGBA가 step 1의 해당 1× preview와 일치하고 신규 3×3 matrix cell과도 일치한다.
- 기존 default render state와 기존 helmet preview golden은 변경되지 않는다.
- missing/wrong-size runtime asset은 기존처럼 빈 render로 격리된다.

spec의 runtime artifact status와 metadata를 실제 runtime PNG 기준으로 `available`로 갱신한다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-top-bottom-layers-spec.json --check
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-helmet-layers-spec.json --check
.\.venv\Scripts\python.exe scripts\build_character_assets.py --check
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.ui.character.CharacterLayerCatalogTest" --tests "com.todoquest.ui.character.CharacterBitmapComposerTest"
.\gradlew.bat :app:assembleDebug
git diff --check
```

## 검증 절차

1. renderer 테스트를 먼저 변경하고 mapping/runtime 사본 전 예상 실패를 확인한다.
2. runtime PNG와 catalog 허용 ID를 연결한 뒤 AC를 실행한다.
3. canonical/runtime SHA-256, preview raw-pixel equality와 800개 조합을 확인한다.
4. 기존 15-source spec과 generated sheet가 변경되지 않았는지 확인한다.
5. task index의 step 3을 `completed`로 바꾸고 runtime 6개·23개 path·800개 조합을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 새 장비를 drawable resource나 generated modular sheet에서 잘라 읽지 마라. 이유: runtime source는 독립 asset layer다.
- 캐릭터 합성에서 장비만 crop·이동·확대하지 마라. 이유: 같은 64×64 원점과 anchor를 보존해야 한다.
- 기존 default/adventure top·bottom을 덮어쓰지 마라. 이유: appearance fallback은 별도 자산이다.
- 테스트보다 구현을 먼저 작성하지 마라. 이유: AGENTS.md의 테스트 우선 규칙을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
