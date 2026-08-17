# Step 10: implement-character-layer-composer

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/app/src/main/assets/character/layers/`
- `/app/src/main/java/com/todoquest/domain/model/CharacterAppearance.kt`
- `/app/src/main/java/com/todoquest/feature/character/CharacterUiState.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMapUiModel.kt`
- `/app/build.gradle.kts`
- `/phases/030-039/32-character-layer-runtime-composition/index.json`

## 작업

테스트를 먼저 작성하고 shared presentation module에 다음 타입을 구현한다.

```kotlin
enum class CharacterLayerSlot
data class CharacterLayerDefinition(
    val assetPath: String,
    val slot: CharacterLayerSlot,
    val zIndex: Int,
    val canvasWidth: Int = 64,
    val canvasHeight: Int = 64,
    val anchorProfileId: String,
)
data class CharacterRenderState(
    val appearance: CharacterAppearance,
    val equippedItems: EquippedItems,
)
```

`CharacterLayerCatalog.resolve(state)`는 spec과 같은 z-order로 definitions를 반환한다. offset, crop, per-item scale을 제공하지 않는다. optional null item은 layer를 생략한다.

`CharacterBitmapComposer`는 AssetManager에서 `inScaled=false`, ARGB_8888로 각 64×64 PNG를 decode하고 path별 layer cache와 render-state별 64×64 composite LRU cache를 사용한다. source-over로 원점에 그린 뒤 불변 bitmap/ImageBitmap을 반환한다. 같은 state는 재decode/recompose하지 않고, state 변경 시 관련 final composite만 새로 만든다. decode 실패나 size 불일치는 일정 기능을 중단시키지 않고 renderer가 빈 결과를 반환하도록 한다.

Robolectric test에서 app assets로 모든 loadout을 합성하고 modular sheet의 `runtime-equipped-reference` raw RGBA와 비교한다. debug sheet는 test golden으로만 읽는다.

## Acceptance Criteria

```powershell
.\gradlew.bat test --tests "com.todoquest.ui.character.*"
.\gradlew.bat lint
git diff --check
```

## 검증 절차

1. catalog order, null optional layers, all paths/canvas/anchor invariants를 검사한다.
2. cache hit가 decode/recompose count를 늘리지 않는지 확인한다.
3. task index의 step 10을 `completed`로 바꾸고 composer/catalog 경로와 golden equality를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- layer별 offset/scale/crop을 추가하지 마라. 이유: 공통 64×64 원점 계약을 무력화한다.
- 각 layer를 서로 다른 destination size로 그리지 마라. 이유: 장비 변경 시 정렬과 pixel sharpness가 깨진다.
- production composer에서 modular sheet를 source로 읽지 마라. 이유: 독립 runtime layer가 source여야 한다.
- 기존 테스트를 깨뜨리지 마라.
