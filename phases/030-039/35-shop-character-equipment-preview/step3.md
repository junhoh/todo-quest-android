# Step 3: verify-shop-preview-integration

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/app/TodoQuestApplication.kt`
- `/app/src/main/java/com/todoquest/app/TodoQuestApp.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomEquipmentRepository.kt`
- `/app/src/main/java/com/todoquest/feature/shop/ShopUiState.kt`
- `/app/src/main/java/com/todoquest/feature/shop/ShopViewModel.kt`
- `/app/src/main/java/com/todoquest/feature/shop/ShopScreen.kt`
- `/app/src/androidTest/java/com/todoquest/app/AppNavigationTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/shop/ShopScreenTest.kt`
- `/phases/030-039/35-shop-character-equipment-preview/step2.md`
- `/phases/030-039/35-shop-character-equipment-preview/index.json`

## 작업

실제 application composition과 Room Flow를 사용하는 연결 테스트를 먼저 확장한다. production wiring은 기존 single application-scope `EquipmentRepository`, 구매·장착 UseCase와 `ShopViewModel` factory를 유지하고, step 0~2에서 필요해진 최소 연결 변경만 수행한다. UI에서 DAO나 concrete Room Repository를 참조하지 않는다.

`AppNavigationTest` 또는 동일한 실제 Activity 연결 테스트에서 Shop 탭 진입 후 상단 app bar, 캐릭터 프리뷰, 능력치, category와 판매 목록이 순서대로 존재하고 하단 `상점` navigation item이 선택된 상태인지 검증한다. Inventory는 계속 nested destination이며 별도 하단 tab을 만들지 않는다.

실제 Room 상태로 구매를 수행한 뒤 화면 이동이나 수동 refresh 없이 gold, 해당 카드의 `보유 중`과 구매 불가 상태가 갱신되는지 검증한다. 구매 성공의 `바로 장착`을 선택한 뒤 같은 Shop 화면에서 해당 slot, `장착 중`, 공격력·최대 체력·방어력 중 영향받는 값, 같은 slot 상세 비교가 갱신되는지 확인한다. 장착 전후 CHEST와 LEGS를 각각 교체해 반대 slot 표시가 유지되는지도 검증한다. seeded 장비의 layer가 없을 때 캐릭터 fallback이 계속 표시되어야 하고 blank avatar나 navigation 실패로 처리하면 안 된다.

Activity recreation 뒤에는 Room의 gold·소유·slot·프리뷰·최종 능력치가 복원되지만 구매 또는 장착 command/dialog가 재실행되지 않아야 한다. 기존 Shop→Inventory→back, bottom navigation save/restore와 Character·Calendar의 장착 능력치 반영 회귀 테스트도 유지한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:assembleDebug --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.app.AppNavigationTest,com.todoquest.feature.shop.ShopScreenTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.shop.ShopViewModelTest" --tests "com.todoquest.data.repository.RoomEquipmentRepositoryTest" --console=plain
git diff --check
```

## 검증 절차

1. 실제 Activity·Room purchase/equip 갱신 테스트를 먼저 추가하고 실패를 확인한다.
2. AC 명령을 실행하고 bottom navigation, Inventory nested route, recreation 비재생 계약을 확인한다.
3. task index의 step 3을 `completed`로 바꾸고 실제 통합 흐름과 회귀 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- Shop preview 갱신을 위해 화면 재진입이나 수동 refresh를 요구하지 마라. 이유: committed Room Flow가 StateFlow를 즉시 갱신해야 한다.
- Inventory를 하단 navigation item으로 추가하지 마라. 이유: 승인된 구조는 Shop의 nested destination이다.
- 연결 테스트를 fake UI state만으로 대체하지 마라. 이유: 이 step은 실제 Room transaction과 application wiring의 갱신을 검증한다.
- 기존 테스트를 깨뜨리지 마라.
