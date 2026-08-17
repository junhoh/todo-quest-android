# Step 7: wire-shop-navigation

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/app/src/main/java/com/todoquest/app/TodoQuestApplication.kt`
- `/app/src/main/java/com/todoquest/app/TodoQuestApp.kt`
- `/app/src/main/java/com/todoquest/data/local/TodoQuestDatabase.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomEquipmentRepository.kt`
- `/app/src/main/java/com/todoquest/feature/shop/ShopScreen.kt`
- `/app/src/main/java/com/todoquest/feature/shop/InventoryScreen.kt`
- `/app/src/androidTest/java/com/todoquest/app/AppNavigationTest.kt`
- `/app/src/androidTest/java/com/todoquest/app/TodoQuestDatabaseIsolationTest.kt`
- `/phases/030-039/34-equipment-shop-and-inventory/index.json`

## 작업

application/navigation 연결 test를 먼저 확장한다. `TodoQuestAppContainer`는 기존 단일 database와 clock을 공유하는 `RoomEquipmentRepository`, 구매·장착 UseCase를 application scope로 제공한다. `TodoQuestAppContainer.create()`와 모든 file database test builder에 `MIGRATION_6_7`을 등록한다. test container가 fake repository를 주입할 수 있는 현재 constructor 패턴을 유지한다.

`AppDestination.Shop(route = "shop")`을 Calendar·Character 다음 세 번째 top-level destination으로 추가하고 한국어 label과 구분되는 store icon을 사용한다. Inventory는 하단 NavigationBar item이 아닌 nested `inventory` destination이다. Shop ViewModel/Inventory ViewModel factory는 domain repository/UseCase와 clock만 주입한다. Shop back은 `navigateUp()`하고 back stack이 없으면 Calendar로 복귀한다. Inventory back은 Shop으로, Shop top app bar action과 구매 성공의 `인벤토리로 이동`은 같은 destination으로 연결한다. top-level navigation은 기존 save/restore/singleTop behavior를 유지한다.

구매 성공 뒤 바로 장착은 성공 dialog의 owned id와 typed slot을 ViewModel event로 전달하되 UI label 문자열을 사용하지 않는다. 계속 쇼핑은 결과를 소비하고 현재 category/scroll state를 유지한다. Activity recreation 시 Room의 gold·owned/equipped source는 복원되지만 purchase command와 snackbar/dialog side effect가 다시 실행되지 않아야 한다.

`AppNavigationTest`는 세 탭 선택, Shop→Inventory→back, 구매 성공 이후 destination 선택, CHEST/LEGS 장착 후 Character와 Calendar의 갱신을 검증한다. database isolation test는 v7 migration 등록 상태에서도 각 orchestrator invocation이 분리되는지 확인한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:assembleDebug --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.app.AppNavigationTest,com.todoquest.app.TodoQuestDatabaseIsolationTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.shop.ShopViewModelTest" --tests "com.todoquest.data.repository.RoomEquipmentRepositoryTest" --console=plain
git diff --check
```

## 검증 절차

1. navigation/container/migration registration test를 먼저 추가하고 실패를 확인한다.
2. UI가 DAO나 concrete Room Repository를 직접 참조하지 않고 factory/composition root를 통하는지 확인한다.
3. task index의 step 7을 `completed`로 바꾸고 application wiring과 recreation 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- Inventory를 네 번째 하단 tab으로 추가하지 마라. 이유: 승인된 구조는 Shop에서 여는 nested destination이다.
- UI에서 database나 DAO를 생성하지 마라. 이유: 단일 application-scope source와 레이어 규칙을 깨뜨린다.
- 구매 성공 state를 navigation 진입 때 재실행하지 마라. 이유: 화면 재생성 후 이중 차감 가능성을 만든다.
- 기존 테스트를 깨뜨리지 마라.
