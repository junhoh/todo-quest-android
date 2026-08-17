# Step 2: present-equipment-unequip

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/app/src/main/java/com/todoquest/domain/model/Equipment.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/EquipmentPolicies.kt`
- `/app/src/main/java/com/todoquest/feature/shop/ShopUiState.kt`
- `/app/src/main/java/com/todoquest/feature/shop/ShopViewModel.kt`
- `/app/src/main/java/com/todoquest/feature/shop/InventoryUiState.kt`
- `/app/src/main/java/com/todoquest/feature/shop/InventoryViewModel.kt`
- `/app/src/test/java/com/todoquest/feature/shop/ShopViewModelTest.kt`
- `/app/src/test/java/com/todoquest/feature/shop/InventoryViewModelTest.kt`
- `/phases/050-059/53-equipment-shop-ux-polish/step1.md`
- `/phases/050-059/53-equipment-shop-ux-polish/index.json`

## 작업

ViewModel 테스트를 먼저 작성한다. Shop presentation에는 관리 중인 `EquipmentSlot?`, `ShopUnequipState`의 `Idle`, `Processing(slot)`, `Success(slot, changed)`, `Failed(slot)`, 그리고 `OpenSlotManagement`, `CloseSlotManagement`, `BrowseManagedSlot`, `UnequipManagedSlot`, `ConsumeUnequipResult` event를 추가한다. `BrowseManagedSlot`은 관리 popup을 닫고 기존 slot/category 선택을 적용한다. `UnequipManagedSlot`은 snapshot에서 실제 장착된 slot일 때만 command를 시작하며, `Success`와 `AlreadyEmpty`를 모두 성공 terminal state로 매핑한다. 예외는 같은 slot을 가진 `ShopRetryState.Unequip`으로 보존한다.

Inventory presentation에는 equip과 unequip을 동시에 실행하지 않는 typed processing state, `UnequipSlot(slot)` event, 성공/실패/재시도 결과를 추가한다. 장착 중 item의 해제는 slot을 command key로 사용하며 성공 뒤 owned item은 목록에 남고 `isEquipped`만 Flow source에 따라 바뀌어야 한다. processing 중 중복 equip/unequip event는 무시한다. 화면 재생성이나 Flow 재구독은 완료 dialog/event를 재실행하지 않는다.

두 ViewModel constructor에는 `UnequipEquipmentUseCase`를 주입한다. 기존 production caller가 다음 wiring step 전에도 컴파일 가능하도록 필요하면 repository로부터 만드는 명시적 default parameter를 사용할 수 있지만 UI가 Repository method를 직접 호출하게 만들지 않는다.

`ShopEquipmentUiModel`에는 현재 캐릭터 level과 독립적으로 카드가 제한 상태를 렌더링할 수 있는 `isRequiredLevelMet: Boolean`을 추가하고 `characterLevel >= requiredLevel`로 계산한다. `PurchaseAvailability`의 우선순위가 `AlreadyOwned`나 `InsufficientGold`여도 이 값은 요구 레벨 자체를 정확히 나타내야 한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.shop.ShopViewModelTest" --tests "com.todoquest.feature.shop.InventoryViewModelTest" --console=plain
git diff --check
```

## 검증 절차

1. ViewModel state/event test를 먼저 추가해 실패를 확인한 뒤 구현하고 AC를 실행한다.
2. processing 중복 차단, `AlreadyEmpty` 성공 처리, retry와 결과 소비 비재생을 확인한다.
3. Compose가 소유해야 할 command state가 생기지 않고 모든 상태가 ViewModel `StateFlow`에서 오는지 확인한다.
4. task index의 step 2를 `completed`로 바꾸고 presentation 계약과 테스트를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- Compose local state로 해제 성공·실패를 소유하지 마라. 이유: 회전과 재구독에서 command와 UI가 불일치한다.
- 구매 가능 사유만으로 요구 레벨 충족 여부를 추론하지 마라. 이유: 보유 중 우선순위가 레벨 정보를 가릴 수 있다.
- equip과 unequip을 동시에 실행하지 마라. 이유: 같은 slot의 최종 상태가 command 순서에 의존하게 된다.
- 사용자 표시 문자열을 ViewModel에 하드코딩하지 마라. 이유: 한국어 resource 기본 정책을 위반한다.
- 기존 테스트를 깨뜨리지 마라.
