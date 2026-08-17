# Step 5: wire-and-verify-shared-unequip

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/app/src/main/java/com/todoquest/app/TodoQuestApplication.kt`
- `/app/src/main/java/com/todoquest/app/TodoQuestApp.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomEquipmentRepository.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomCharacterRepository.kt`
- `/app/src/main/java/com/todoquest/feature/shop/ShopViewModel.kt`
- `/app/src/main/java/com/todoquest/feature/shop/InventoryViewModel.kt`
- `/app/src/androidTest/java/com/todoquest/app/AppNavigationTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/shop/ShopScreenTest.kt`
- `/phases/050-059/53-equipment-shop-ux-polish/step4.md`
- `/phases/050-059/53-equipment-shop-ux-polish/index.json`

## 작업

application/container와 navigation integration test를 먼저 확장한다. `TodoQuestAppContainer`는 application-scope `UnequipEquipmentUseCase`를 같은 `EquipmentRepository`로 생성하고 `AppNavigation`, `shopViewModelFactory`, `inventoryViewModelFactory`에 명시적으로 주입한다. UI는 concrete Room Repository나 DAO를 참조하지 않는다.

실제 Room source를 사용하는 연결 테스트에서 장비 구매·장착 후 Shop preview popup 또는 Inventory에서 해제한다. commit 뒤 gameplay slot은 `비어 있음`, owned item은 인벤토리에 남음, modifier와 최종 능력치는 제거됨, 대상 외형 fallback은 승인된 기본 복장으로 변함을 확인한다. 같은 application-scope Flow를 통해 Character screen과 Calendar Battle Map의 shared layered renderer가 별도 refresh command 없이 같은 기본 복장을 표시해야 한다. 투구·무기 등 nullable layer와 상의·하의·신발 default layer를 대표 case로 포함하고 다른 slot 외형은 보존한다.

해제 전후 `MAX_HP`와 current HP 비율, active 중상 modifier, Task reward와 Combat 신규 계산 source를 확인하되 이미 확정된 RewardLedger, completion attack snapshot과 combat event는 바뀌지 않아야 한다. Activity recreation과 tab 재진입은 Room의 해제 상태를 복원하지만 완료 feedback을 다시 표시하거나 command를 재실행하지 않는다.

새 Repository method를 구현하지 않은 test fake를 모두 찾아 의도에 맞게 갱신하고, compile 편의를 위한 production default no-op이나 throw contract가 남아 있지 않게 한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.repository.RoomCharacterRepositoryTest" --tests "com.todoquest.data.repository.RoomCombatRepositoryTest" --tests "com.todoquest.data.repository.RoomTaskRepositoryTest" --console=plain
.\gradlew.bat :app:assembleDebug --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.app.AppNavigationTest,com.todoquest.feature.shop.ShopScreenTest" --console=plain
git diff --check
```

## 검증 절차

1. container/navigation/shared renderer integration test를 먼저 추가해 실패를 확인한 뒤 wiring을 구현한다.
2. Shop·Character·Battle이 같은 Room source와 renderer 결과를 관찰하고 UI가 DAO를 호출하지 않는지 확인한다.
3. Android 도구나 connected device가 없으면 임의 설치하지 말고 step을 `blocked`로 기록한다.
4. task index의 step 5를 `completed`로 바꾸고 application wiring·shared 외형·계산 회귀를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- Shop만 임시 기본 복장으로 렌더링하지 마라. 이유: Character·Battle과 보이는 외형이 달라진다.
- 해제 뒤 소유 item을 인벤토리에서 제거하지 마라. 이유: 사용자는 다시 장착할 수 있어야 한다.
- 과거 RewardLedger나 확정 combat snapshot을 재계산하지 마라. 이유: 보상·전투의 snapshot 멱등성을 깨뜨린다.
- UI에 Room Repository나 DAO를 주입하지 마라. 이유: AGENTS.md 레이어 규칙을 위반한다.
- 기존 테스트를 깨뜨리지 마라.
