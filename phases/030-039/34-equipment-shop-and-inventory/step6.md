# Step 6: render-shop-inventory-screens

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/res/values/strings.xml`
- `/app/src/main/java/com/todoquest/ui/theme/Theme.kt`
- `/app/src/main/java/com/todoquest/feature/character/CharacterScreen.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarScreen.kt`
- `/app/src/main/java/com/todoquest/feature/shop/ShopUiState.kt`
- `/app/src/main/java/com/todoquest/feature/shop/ShopViewModel.kt`
- `/app/src/main/java/com/todoquest/feature/shop/InventoryViewModel.kt`
- `/phases/030-039/34-equipment-shop-and-inventory/index.json`

## 작업

Compose UI test를 먼저 작성하고 `ShopScreen`과 별도 `InventoryScreen`을 Material 3와 기존 theme로 구현한다. Shop 상단 app bar에는 최소 48dp 뒤로 가기, `상점`, 인접한 gold 아이콘/값과 Inventory 이동 action을 둔다. gold 아이콘과 값 사이는 기존 HUD와 동일한 3dp를 사용하고 하나의 한국어 접근성 설명으로 묶는다. Shop이 하단 destination이어도 명시된 back action을 제공하며 navigation callback 자체는 step 7에서 연결한다.

category는 `전체, 무기, 투구, 상의, 하의, 장갑, 신발, 액세서리` 순서의 horizontal scroll filter로 렌더링하고 선택 상태를 색상 외 shape·selected semantics로 표시한다. 목록은 작은 화면과 큰 font scale에서 이름·가격이 잘리지 않는 카드형 `LazyColumn`을 사용한다. 각 카드는 distinct type placeholder icon, name, type, rarity, 0보다 큰 주요 modifier, price, 판매 가능 상태와 `보유 중`·`장착 중` badge를 보여준다. CHEST와 LEGS 아이콘은 서로 달라야 한다. 알려진 image key를 표시하지 못하면 type placeholder와 한국어 설명으로 nonfatal fallback한다.

카드 클릭은 `ModalBottomSheet`를 열어 image/fallback, name, rarity, type, description, required level, 모든 modifier, 동일 slot 현재 장비와 signed comparison, price, current gold와 구매 button을 표시한다. 비교는 색상뿐 아니라 `+`, `-`, `변화 없음` 텍스트를 사용한다. 구매 불가 button은 비활성화하고 골드 부족, 요구 레벨, 이미 보유, 판매 중지, 지원하지 않는 slot 이유를 구체적인 resource 문자열로 표시한다.

구매 요청은 `AlertDialog`로 이름·가격·slot·현재/구매 후 gold와 취소/구매를 보여준다. Processing 동안 confirm과 card action을 중복 실행할 수 없고 진행 설명을 제공한다. 성공 dialog는 `바로 장착`, `인벤토리로 이동`, `계속 쇼핑`을 제공한다. 장착 성공/실패와 일반 오류는 snackbar 또는 dialog로 명확하게 표시하고 재시도를 제공한다. InventoryScreen은 뒤로 가기, owned empty/error/loading, 보유 카드와 장착/교체 button 및 장착 badge를 제공한다.

모든 사용자 노출 문구와 equipment name/description key mapping을 `strings.xml`의 한국어 resource로 추가한다. rarity와 type 표시도 enum→resource mapping으로 구현하고 UI 문자열을 역으로 domain 값과 비교하지 않는다. Preview/sample은 Repository나 DAO 없이 UI state fixture만 사용한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:assembleDebug --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.shop.ShopScreenTest,com.todoquest.feature.shop.InventoryScreenTest" --console=plain
git diff --check
```

## 검증 절차

1. category, 카드 라벨, same-slot 상세, 구매 dialog/disabled/Processing, 성공 선택과 image fallback 테스트를 먼저 작성한다.
2. 320dp 폭과 큰 font scale에서 터치 영역, gold 인접 배치, 이름·가격·dialog action이 접근 가능한지 연결 test로 확인한다.
3. task index의 step 6을 `completed`로 바꾸고 구현한 화면·상태·접근성을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 장비 목록·가격·gold를 Composable에 하드코딩하지 마라. 이유: Room/StateFlow 원천을 렌더링해야 한다.
- rarity 색상만으로 상태를 전달하지 마라. 이유: 접근성 계약상 badge·텍스트가 함께 필요하다.
- 새 bitmap asset을 임의 생성하지 마라. 이유: 이번 phase는 기존 Material icon fallback을 승인했다.
- 기존 테스트를 깨뜨리지 마라.
