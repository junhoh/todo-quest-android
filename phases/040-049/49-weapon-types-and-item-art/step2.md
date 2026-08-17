# Step 2: extend-weapon-type-domain

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/character-stats/modifiers-and-equipment.md`
- `/app/src/main/java/com/todoquest/domain/model/Equipment.kt`
- `/app/src/main/java/com/todoquest/domain/model/CharacterLoadout.kt`
- `/app/src/test/java/com/todoquest/domain/CharacterLoadoutCatalogTest.kt`
- `/app/src/test/java/com/todoquest/domain/EquipmentComparisonCalculatorTest.kt`
- `/docs/art/equipment/todo-quest-weapon-layers-spec.json`
- `/phases/040-049/49-weapon-types-and-item-art/index.json`

## 작업

순수 Kotlin 테스트를 먼저 추가해 무기 subtype 불변식과 새 visual ID가 아직 지원되지 않는 예상 실패를 확인한 뒤 domain model만 확장한다. Android asset, Room entity, DAO, Repository와 UI는 수정하지 않는다.

```kotlin
enum class WeaponType {
    LONGSWORD,
    DAGGER,
    SPEAR,
    BLUNT,
}

data class Equipment(
    // 기존 필드 순서와 호출 호환을 보존
    val isForSale: Boolean,
    val weaponType: WeaponType? = null,
)
```

`Equipment`는 `type == EquipmentType.WEAPON`이면 non-null `weaponType`을 요구하고, 다른 type이면 null만 허용한다. `DAGGER`는 확장 가능한 공개 값이지만 이번 catalog에는 대응 장비가 없다. 구매·장착 slot은 계속 단일 `EquipmentSlot.WEAPON`이며 subtype별 slot을 추가하지 않는다.

`CharacterLoadoutCatalog`에 다음 상수를 추가한다.

```kotlin
const val WEAPON_WORN_SWORD = "weapon_worn_sword"
const val WEAPON_IRON_LONGSWORD = "weapon_iron_longsword"
const val WEAPON_ASH_SPEAR = "weapon_ash_spear"
const val WEAPON_STEEL_MACE = "weapon_steel_mace"
```

weapon ID set은 기존 `null`, `WEAPON_DEFAULT_SWORD`와 새 네 ID를 허용한다. 기본 appearance와 `defaultEquippedItems.weaponId = WEAPON_DEFAULT_SWORD`는 유지한다.

테스트는 네 enum 값, weapon/non-weapon 불변식, 네 ID의 유효성, unknown/공백/반대 slot ID 거부, 기존 default loadout 보존, weapon 교체 시 나머지 appearance field 불변을 검증한다. 기존 test fixture의 gameplay weapon에는 정확한 subtype을 제공한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.CharacterLoadoutCatalogTest" --tests "com.todoquest.domain.EquipmentComparisonCalculatorTest" --tests "com.todoquest.domain.PurchaseEquipmentPolicyTest"
.\gradlew.bat :app:compileDebugKotlin
git diff --check
```

## 검증 절차

1. domain 테스트를 먼저 작성하고 예상 실패를 확인한다.
2. enum, 불변식과 loadout ID를 최소 변경으로 구현한 뒤 AC를 실행한다.
3. equipment slot, modifier 계산, 기본 appearance와 Repository API가 변경되지 않았는지 확인한다.
4. task index의 step 2를 `completed`로 바꾸고 subtype 불변식과 네 ID를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- subtype별 EquipmentSlot을 추가하지 마라. 이유: 모든 무기는 기존 WEAPON slot을 공유한다.
- asset 경로나 Android 타입을 domain model에 넣지 마라. 이유: domain은 UI 파일 위치를 알지 않아야 한다.
- 기본 gameplay 무기를 새 상점 장비로 자동 교체하지 마라. 이유: 기존 appearance fallback을 보존해야 한다.
- 테스트보다 구현을 먼저 작성하지 마라. 이유: AGENTS.md의 테스트 우선 규칙을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
