# Step 8: sync-shop-price-presentation

## 읽어야 할 파일

- /AGENTS.md
- /docs/UI_GUIDE.md
- /app/src/main/java/com/todoquest/feature/shop/ShopScreen.kt
- /app/src/main/java/com/todoquest/feature/shop/ShopViewModel.kt
- /app/src/test/java/com/todoquest/feature/shop/ShopViewModelTest.kt
- /app/src/androidTest/java/com/todoquest/feature/shop/ShopScreenTest.kt
- /app/src/androidTest/java/com/todoquest/feature/shop/InventoryScreenTest.kt
- /phases/050-059/50-notification-and-gameplay-balance/step7.md
- /phases/050-059/50-notification-and-gameplay-balance/index.json

## 작업

Shop presentation test를 먼저 새 catalog 가격으로 갱신해 실패를 확인한다. production Shop은 Repository price를 그대로 표시·확인·차감하므로 별도 가격 계산을 추가하지 않는다. seeded catalog의 실제 ID·이름을 의미하는 preview fixture와 connected assertion만 step 0 표로 동기화한다. arbitrary formatting, insufficient-gold 또는 isolated UI-state test가 의도적으로 쓰는 임의 가격은 catalog 가격으로 오인해 일괄 변경하지 않는다.

상점 목록·상세·구매 확인·부족 안내·구매 성공 잔액이 같은 Repository snapshot 가격을 사용하고, 천 단위 formatting과 한국어 골드 문구를 유지한다. 320dp, font scale 2.0에서도 이름·가격·구매 action이 접근 가능해야 한다. Inventory의 이미 소유한 장비는 가격 변경과 무관하게 소유·장착 상태를 유지한다.

## Acceptance Criteria

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.shop.ShopViewModelTest" --tests "com.todoquest.feature.shop.InventoryViewModelTest" --console=plain
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.shop.ShopScreenTest,com.todoquest.feature.shop.InventoryScreenTest" --console=plain
git diff --check
~~~

## 검증 절차

1. seeded item exact price assertion을 먼저 갱신해 실패를 확인한다.
2. preview와 catalog-specific fixture만 동기화한다.
3. AC를 실행하고 연결 기기 부재 시 blocked로 기록한다.
4. step 8을 completed와 한국어 summary로 갱신한다.

## 금지사항

- ViewModel이나 Compose에서 50% 계산을 다시 하지 마라. 이유: catalog가 가격의 단일 원천이어야 한다.
- 모든 숫자 fixture를 기계적으로 절반으로 바꾸지 마라. 이유: 독립 UI 상태 test의 의도가 달라질 수 있다.
- 구매·장착 UI가 DAO를 직접 호출하게 하지 마라. 이유: Repository/UseCase 경계를 유지해야 한다.
- 기존 테스트를 깨뜨리지 마라.
