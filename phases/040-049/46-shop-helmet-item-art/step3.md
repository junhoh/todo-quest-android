# Step 3: register-helmet-runtime-layers

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/art/equipment/todo-quest-helmet-layers-spec.json`
- `/docs/art/equipment/layers/headgear_leather_hat.png`
- `/docs/art/equipment/layers/headgear_iron_helmet.png`
- `/docs/art/equipment/previews/leather-hat-equipped.png`
- `/docs/art/equipment/previews/iron-helmet-equipped.png`
- `/app/src/main/java/com/todoquest/domain/model/CharacterLoadout.kt`
- `/app/src/main/java/com/todoquest/ui/character/CharacterLayerCatalog.kt`
- `/app/src/main/java/com/todoquest/ui/character/CharacterBitmapComposer.kt`
- `/app/src/test/java/com/todoquest/ui/character/CharacterLayerCatalogTest.kt`
- `/app/src/test/java/com/todoquest/ui/character/CharacterBitmapComposerTest.kt`
- `/phases/040-049/46-shop-helmet-item-art/index.json`

## 작업

renderer 테스트를 먼저 확장해 두 새 head ID의 asset path가 아직 해석되지 않는 예상 실패를 확인한 뒤 runtime layer와 catalog mapping을 추가한다.

- canonical PNG를 다음 runtime 경로에 byte-identical하게 복사한다.
  - `/app/src/main/assets/character/layers/headgear_leather_hat.png`
  - `/app/src/main/assets/character/layers/headgear_iron_helmet.png`
- `CharacterLayerCatalog.addHeadgearLayer`가 각 ID를 정확한 파일의 `HEADGEAR_FRONT` definition 하나로 해석하게 한다.
- 기존 z-order, `64×64`, anchor profile과 `(0,0)` source-over 합성을 유지한다. item별 translation, crop, scale 또는 hair/body 수정은 추가하지 않는다.

테스트는 다음을 검증한다.

- canonical asset path set이 기존 15개와 새 두 파일을 합친 17개다.
- head 선택지는 `null`, adventure, leather, iron 네 개이며 모든 top×bottom×shoes×head×accessory×weapon 조합 128개가 유효하게 합성된다.
- 두 새 headgear definition은 slot ordinal 13, 64×64와 schema-v4 anchor profile을 사용한다.
- `CharacterBitmapComposer`가 128개 조합을 모두 non-null immutable ARGB_8888로 만들고 전체 탐색에서 layer decode 수는 17이다.
- leather/iron render state의 raw RGBA가 step 1의 해당 1× preview와 각각 일치한다.
- 기존 default render state는 기존 modular sheet의 `runtime-equipped-reference`와 계속 일치한다.
- missing/wrong-size runtime asset은 기존처럼 빈 render로 격리된다.

spec의 runtime metadata를 실제 runtime PNG 기준으로 `available`과 hash/count 값으로 갱신한다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-helmet-layers-spec.json --check
.\.venv\Scripts\python.exe scripts\build_character_assets.py --check
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.ui.character.CharacterLayerCatalogTest" --tests "com.todoquest.ui.character.CharacterBitmapComposerTest"
.\gradlew.bat :app:assembleDebug
git diff --check
```

## 검증 절차

1. renderer 테스트를 먼저 변경하고 mapping/runtime 사본 전 예상 실패를 확인한다.
2. 두 runtime PNG와 catalog mapping을 추가한 뒤 AC를 실행한다.
3. canonical/runtime SHA-256와 preview raw-pixel equality를 확인한다.
4. 기존 15-source spec과 generated sheet가 변경되지 않았는지 확인한다.
5. task index의 step 3을 `completed`로 바꾸고 runtime 경로, 128조합과 byte equality를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 새 투구를 `drawable` resource나 generated modular sheet에서 잘라 읽지 마라. 이유: 캐릭터 runtime source는 독립 asset layer다.
- 캐릭터 합성 시 투구만 crop·이동·확대하지 마라. 이유: 같은 64×64 원점과 anchor를 보존해야 한다.
- 기존 `headgear_adventure.png`를 덮어쓰지 마라. 이유: 기본 appearance fallback은 별도 자산이다.
- 테스트보다 catalog 구현을 먼저 작성하지 마라. 이유: AGENTS.md의 테스트 우선 규칙을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
