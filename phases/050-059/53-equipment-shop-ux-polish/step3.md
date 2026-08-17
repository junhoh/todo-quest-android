# Step 3: render-equipment-unequip-ui

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/feature/shop/ShopUiState.kt`
- `/app/src/main/java/com/todoquest/feature/shop/ShopScreen.kt`
- `/app/src/main/java/com/todoquest/feature/shop/InventoryUiState.kt`
- `/app/src/main/java/com/todoquest/feature/shop/InventoryScreen.kt`
- `/app/src/main/res/values/strings.xml`
- `/app/src/androidTest/java/com/todoquest/feature/shop/ShopScreenTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/shop/InventoryScreenTest.kt`
- `/phases/050-059/53-equipment-shop-ux-polish/step2.md`
- `/phases/050-059/53-equipment-shop-ux-polish/index.json`

## 작업

Compose instrumentation test를 먼저 작성한다. Shop의 각 preview slot 클릭은 즉시 category만 바꾸지 않고 ViewModel의 `OpenSlotManagement(slot)` event를 보낸다. 관리 dialog는 slot명, 장착 장비명·등급 또는 `비어 있음`을 표시하고 최소 48dp의 `이 부위 장비 보기`, 장착 상태일 때만 `해제`, `닫기` action을 제공한다. `이 부위 장비 보기`는 dialog를 닫고 기존 category filter로 이동한다. `해제`는 별도 확인 dialog를 중첩하지 않고 command를 시작하며 processing 중 dialog action과 다른 장비 command를 비활성화한다.

Inventory의 장착 중 item은 기존 비활성 `장착 중` button 대신 최소 48dp의 `해제` button을 제공한다. 미장착 item은 기존 `장착`/`교체` 흐름을 유지한다. Shop과 Inventory 모두 성공 결과를 한국어 `해제 완료` feedback으로 한 번 표시하고, 실패는 재시도 가능한 한국어 오류로 표시한다. `AlreadyEmpty`도 사용자가 안전하게 완료한 것으로 안내한다.

새 문구, content description, TalkBack state는 모두 `strings.xml`에 둔다. 관리 dialog는 slot, 장비명, 장착/빈 상태를 색에 의존하지 않는 merged semantics로 제공한다. Android-test fake `EquipmentRepository`가 새 method 때문에 컴파일되지 않으면 실제 해제 결과를 제어할 수 있게 이 step에서 갱신한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:assembleDebug --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.shop.ShopScreenTest,com.todoquest.feature.shop.InventoryScreenTest" --console=plain
git diff --check
```

## 검증 절차

1. popup·button·semantics Compose test를 먼저 추가해 실패를 확인한 뒤 구현하고 AC를 실행한다.
2. empty/equipped popup action, processing 비활성화, 성공·실패 결과 소비를 확인한다.
3. Android 도구나 connected device가 없으면 임의 설치하지 말고 step을 `blocked`로 기록한다.
4. task index의 step 3을 `completed`로 바꾸고 UI 진입점·접근성·connected 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 좁은 slot card 안에 별도 해제 button을 중첩하지 마라. 이유: 승인된 UX는 고정 크기를 유지하는 slot 관리 popup이다.
- 장착 중 Inventory button을 비활성 상태로 남기지 마라. 이유: 사용자가 해제 진입점을 찾을 수 있어야 한다.
- 48dp보다 작은 핵심 action target을 만들지 마라. 이유: 접근성 규칙을 위반한다.
- 표시용 영문 문장을 Compose나 ViewModel에 하드코딩하지 마라. 이유: 한국어 기본 문자열 정책을 위반한다.
- 기존 테스트를 깨뜨리지 마라.
