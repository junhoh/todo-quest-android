# Step 4: wire-shop-navigation-insets

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/app/TodoQuestApp.kt`
- `/app/src/main/java/com/todoquest/feature/shop/ShopScreen.kt`
- `/app/src/androidTest/java/com/todoquest/app/AppNavigationTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/shop/ShopScreenTest.kt`
- `/phases/050-059/58-shop-screen-redesign/step3.md`
- `/phases/050-059/58-shop-screen-redesign/index.json`

## 작업

navigation/inset integration test를 먼저 변경하고 Shop이 세 번째 top-level destination이라는 실제 구조를 반영한다.

`ShopScreen` 호출에서 `onBack` wiring을 제거하고 Shop app bar에 `shop-back` node가 존재하지 않게 한다. Calendar/Character/Shop/Compendium bottom tabs의 저장·복원과 Inventory nested navigation은 유지한다. Inventory의 back은 Shop으로 돌아오며 Shop tab을 계속 selected로 표시한다.

root `Scaffold`의 container background와 `NavigationBar`의 불투명 container color를 명시한다. 현재 `WindowInsets.safeDrawing`, outer `innerPadding`을 적용한 NavHost와 NavigationBar system inset 처리를 유지해 navigation bar 아래 시스템 영역까지 앱 배경이 이어지게 한다. 다른 destination의 layout이나 route를 변경하지 않는다.

Shop integration test는 top bar와 bottom navigation이 고정되고 scroll 영역만 움직이는지, `shop-equipment-list` 하단이 bottom navigation 상단을 넘지 않는지, 마지막 판매 card까지 scroll해 전체 bounds가 navigation 위에 표시되는지 검증한다. root capture 또는 pixel assertion으로 navigation/system 하단이 transparent하지 않고 이전 Calendar Battle Map 배경이 노출되지 않는지 확인한다. 작은 화면과 회전 가능한 constraint에서도 절대 좌표를 사용하지 않는다.

기존 실제 Room navigation test helper는 카드 tap으로 상세를 열던 동작을 새 별도 상세 button과 카드 직접 purchase button 계약에 맞춘다. 구매·바로 장착·인벤토리 이동과 Activity recreation test는 그대로 통과해야 한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:assembleDebug --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.app.AppNavigationTest,com.todoquest.feature.shop.ShopScreenTest" --console=plain
git diff --check
```

## 검증 절차

1. top-level Shop back 제거, Inventory back, bottom inset과 마지막 card bounds test를 먼저 작성해 실패를 확인한다.
2. navigation bar의 container가 alpha 1이고 system navigation inset까지 덮는지 확인한다.
3. Calendar·Character·Compendium 탭 이동과 nested Inventory back stack 회귀를 확인한다.
4. Android 도구나 connected device가 없으면 임의 설치하지 말고 step을 `blocked`로 기록한다.
5. 성공 시 task index의 step 4를 `completed`로 바꾸고 navigation/inset 검증을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- Shop에 중복 back action을 남기지 마라. 이유: Shop은 top-level destination이며 bottom navigation으로 전환한다.
- Inventory의 nested back을 제거하지 마라. 이유: Inventory는 Shop으로 돌아가야 하는 별도 화면이다.
- bottom navigation을 Shop의 LazyColumn 안에 넣지 마라. 이유: 앱 전역 고정 navigation 구조가 깨진다.
- 다른 화면의 content padding을 임의 조정하지 마라. 이유: 사용자 요청은 Shop과 공통 하단 배경 경계에 한정된다.
- 기존 테스트를 깨뜨리지 마라.
