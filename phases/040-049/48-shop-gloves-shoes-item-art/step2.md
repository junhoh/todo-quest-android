# Step 2: extend-gloves-shoes-loadout-domain

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/app/src/main/java/com/todoquest/domain/model/CharacterLoadout.kt`
- `/app/src/test/java/com/todoquest/domain/CharacterLoadoutCatalogTest.kt`
- `/docs/art/equipment/todo-quest-gloves-shoes-layers-spec.json`
- `/phases/040-049/48-shop-gloves-shoes-item-art/index.json`

## 작업

순수 Kotlin 테스트를 먼저 추가해 새 장갑 field와 네 visual ID가 아직 거부되는 예상 실패를 확인한 뒤 domain loadout catalog만 확장한다. Android asset, Room entity, UI, DAO와 Repository 구현은 수정하지 않는다.

`EquippedItems`에 기존 호출 호환을 위해 마지막 nullable field를 추가한다.

```kotlin
data class EquippedItems(
    val headId: String?,
    val topId: String,
    val bottomId: String,
    val shoesId: String,
    val accessoryId: String?,
    val weaponId: String?,
    val glovesId: String? = null,
)
```

`CharacterLoadoutCatalog`에 다음 공개 상수를 추가한다.

```kotlin
const val GLOVES_LEATHER = "gloves_leather"
const val GLOVES_STEEL_GAUNTLETS = "gloves_steel_gauntlets"
const val SHOES_TRAVELERS_BOOTS = "shoes_travelers_boots"
const val SHOES_WINDWALKER_BOOTS = "shoes_windwalker_boots"
```

`glovesIds`는 `null`과 두 장갑을 허용하고 `shoesIds`는 기존 default/adventure와 두 새 신발을 허용한다. `defaultEquippedItems.glovesId`는 `null`, `shoesId`는 기존 `SHOES_ADVENTURE`를 유지한다. 다른 slot catalog는 변경하지 않는다.

테스트는 네 ID 각각의 유효성, `gloves_unknown`, `shoes_unknown`, 공백과 반대 slot ID 거부, 기존 상수·기본 loadout 보존, 장갑 또는 신발 교체 시 나머지 field 불변을 검증한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.CharacterLoadoutCatalogTest"
.\gradlew.bat :app:compileDebugKotlin
git diff --check
```

## 검증 절차

1. domain 테스트를 먼저 작성하고 새 field·상수가 없는 예상 실패를 확인한다.
2. 최소 catalog 변경 후 AC를 실행한다.
3. gameplay 소유권, modifier, Room entity와 기본 appearance가 변경되지 않았는지 확인한다.
4. task index의 step 2를 `completed`로 바꾸고 새 field·ID와 기본값 보존을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- imageKey, asset 경로나 Android 타입을 domain model에 넣지 마라. 이유: domain loadout은 UI asset 위치를 알지 않아야 한다.
- 기본 장갑을 자동 착용하거나 기본 신발을 새 상점 장비로 바꾸지 마라. 이유: 기존 사용자 appearance fallback을 보존해야 한다.
- 테스트보다 구현을 먼저 작성하지 마라. 이유: AGENTS.md의 테스트 우선 규칙을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
