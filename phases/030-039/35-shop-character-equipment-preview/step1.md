# Step 1: model-shop-character-preview-state

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/domain/model/Equipment.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomEquipmentRepository.kt`
- `/app/src/main/java/com/todoquest/feature/shop/ShopUiState.kt`
- `/app/src/main/java/com/todoquest/feature/shop/ShopViewModel.kt`
- `/app/src/test/java/com/todoquest/feature/shop/ShopViewModelTest.kt`
- `/phases/030-039/35-shop-character-equipment-preview/step0.md`
- `/phases/030-039/35-shop-character-equipment-preview/index.json`

## 작업

`ShopViewModelTest`를 먼저 확장하고 step 0의 aggregate를 immutable `ShopUiState`로 변환한다. 기존 `characterLevel`, gold, category, 목록, 상세, 구매·장착 command state를 유지하면서 다음 모델을 추가한다.

```kotlin
@Immutable
data class ShopEquipmentSlotUiModel(
    val slot: EquipmentSlot,
    val type: EquipmentType,
    val equipmentId: Long?,
    val nameKey: String?,
    val rarity: EquipmentRarity?,
    val imageKey: String?,
    val isEquipped: Boolean,
    val isSelected: Boolean,
)

@Immutable
data class CharacterStatSummaryUiModel(
    val attack: Int,
    val maxHp: Int,
    val defense: Int,
)
```

`ShopUiState`에는 `characterAppearance`, `characterEquippedItems`, `equipmentSlots`, `statSummary`를 추가한다. loading default는 `CharacterLoadoutCatalog`의 기본 appearance/loadout과 안전한 0값을 사용한다. loaded mapper는 `EquipmentSlot.entries` 일곱 값을 항상 생성하고 `equippedBySlot`에 없는 slot도 빈 UI model로 보존한다. `characterAppearance`와 `characterEquippedItems`는 step 0 snapshot 값을 그대로 전달하고, 요약 스탯은 `derivedStats.attack`, `maxHp`, `defense`를 숫자로 매핑할 뿐 다시 계산하지 않는다.

`ShopEvent.SelectSlot(val slot: EquipmentSlot)`을 추가한다. 처리 시 `slot.toEquipmentType()`으로 기존 `selectedCategory`를 선택하고 상세·구매 확인을 닫는다. category를 직접 선택한 경우에도 대응 slot의 `isSelected`가 갱신되고 `전체` 선택 시 모든 slot이 선택 해제된다. type·slot 문자열 비교는 사용하지 않는다.

구매 성공 뒤 Repository Flow가 새 snapshot을 방출하면 top bar gold, 목록의 `isOwned`와 구매 가능 상태를 갱신한다. 바로 장착 뒤에는 slot UI model, `isEquipped`, 같은 slot 상세 비교, 최종 스탯, 렌더 loadout을 같은 snapshot에서 다시 매핑한다. command 성공 결과만 보고 Composable 또는 ViewModel에서 낙관적 능력치 계산을 만들지 않는다.

테스트는 장착 장비→7개 slot 모델, 빈 slot, slot 선택→category, CHEST/LEGS 독립 선택, 구매 후 gold·보유 상태, 바로 장착 후 slot·스탯·목록·상세 비교, CHEST 교체 시 LEGS 유지와 LEGS 교체 시 CHEST 유지를 검증한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.shop.ShopViewModelTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.EquipmentComparisonCalculatorTest" --tests "com.todoquest.domain.PurchaseEquipmentPolicyTest" --console=plain
git diff --check
```

## 검증 절차

1. UI model, slot event와 commit 후 Flow 갱신 테스트를 먼저 작성하고 실패를 확인한다.
2. AC 명령을 실행하고 ViewModel이 EquipmentRepository 외의 persistence source를 직접 호출하지 않는지 확인한다.
3. task index의 step 1을 `completed`로 바꾸고 UI state/event 계약을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- Composable이나 ViewModel에서 `DerivedStatsCalculator`를 직접 호출하지 마라. 이유: Repository가 제공하는 검증된 최종 계산 결과가 단일 원천이다.
- 선택된 category와 slot을 서로 독립적인 mutable source로 보관하지 마라. 이유: 두 강조 상태가 어긋날 수 있다.
- 구매·장착 성공 state 관찰만으로 command를 다시 실행하지 마라. 이유: 재구성과 재수집에서 중복 실행된다.
- 기존 테스트를 깨뜨리지 마라.
