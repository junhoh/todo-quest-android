# Step 6: present-shop-action-state

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/feature/shop/ShopUiState.kt`
- `/app/src/main/java/com/todoquest/feature/shop/ShopViewModel.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/EquipmentPolicies.kt`
- `/app/src/test/java/com/todoquest/feature/shop/ShopViewModelTest.kt`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/step5.md`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/index.json`

## 작업

ViewModel unit test를 먼저 작성하고 카드와 상세가 공유할 단일 typed item action 상태를 만든다.

각 `ShopEquipmentUiModel`은 다음 우선순위로 하나의 action을 노출한다.

1. 미소유이며 purchase eligible이면 `Purchase(equipmentId)`이고 표시 key는 `구매`다.
2. 미소유이며 구매 불가이면 disabled `PurchaseUnavailable(reason)`이고 표시 key는 `구매 불가`다.
3. 소유했지만 현재 slot에 장착되지 않았으면 `Equip(ownedEquipmentId, slot)`이고 표시 key는 `장착`이다.
4. 해당 owned item이 현재 slot에 장착 중이면 `Unequip(equipmentId, slot)`이고 표시 key는 `해제`다.

긴 레벨/골드/slot 등의 이유는 action label에 넣지 않고 별도 reason model로 유지한다. 카드와 상세 dialog는 동일한 `ShopEquipmentUiModel.action`을 사용해야 하며 별도 분기 규칙을 만들지 않는다. action event는 purchase confirmation, 기존 `EquipOwnedEquipmentUseCase`, `UnequipEquipmentUseCase`로 연결하고 처리 중에는 중복 command를 막는다.

장착 중인 선택 item을 성공적으로 해제하면 `selectedEquipmentId`, `selectedDetail`, 해당 confirmation을 즉시 null로 만들고 preview는 최신 snapshot의 실제 빈 loadout으로 돌아가야 한다. 이를 위해 unequip command/result가 대상 `equipmentId`를 잃지 않게 한다. 다른 slot item을 해제한 경우 현재 선택은 유지한다. 실패 시에는 선택과 preview를 유지하고 retry state를 제공한다.

테스트는 네 action 상태, 카드/상세 동일 action, owned id 전달, 선택 item 해제 성공 시 선택/preview 해제, 다른 item 해제 및 실패 시 선택 유지, 중복 입력 차단을 포함한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.shop.ShopViewModelTest" --console=plain
.\gradlew.bat :app:assembleDebug --console=plain
git diff --check
```

## 검증 절차

1. 네 action 우선순위와 선택 해제 회귀 테스트를 먼저 작성한다.
2. 소유 item의 `장착` event가 equipment id가 아닌 owned equipment id를 전달하는지 확인한다.
3. 선택된 장착 item 해제 후 stat difference가 0이고 캐릭터 preview가 실제 loadout인지 확인한다.
4. 성공 시 task index의 step 6을 `completed`로 바꾸고 action 규칙과 선택 해제 동작을 한국어 `summary` 두 줄로 기록한다.

## 금지사항

- `AlreadyOwned`를 `구매 불가`로 계속 표시하지 마라. 이유: 소유 item에는 `장착` 또는 `해제` action이 필요하다.
- 버튼 label에 레벨/골드 부족 사유를 합치지 마라. 이유: label 길이 때문에 크기와 위치가 변한다.
- 해제 성공 뒤 선택 item의 temporary preview를 유지하지 마라. 이유: 실제 장착 상태와 화면이 불일치한다.
- ViewModel에서 Android 문자열을 직접 조합하지 마라. 이유: 표시 문구는 resource key/typed state로 UI에 전달한다.
- 기존 테스트를 삭제하거나 완화하지 마라.
