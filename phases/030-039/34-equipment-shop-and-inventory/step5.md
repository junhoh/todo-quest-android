# Step 5: model-shop-inventory-presentation

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/feature/character/CharacterUiState.kt`
- `/app/src/main/java/com/todoquest/feature/character/CharacterViewModel.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarUiState.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarViewModel.kt`
- `/app/src/main/java/com/todoquest/domain/model/Equipment.kt`
- `/app/src/main/java/com/todoquest/domain/repository/EquipmentRepository.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/EquipmentPolicies.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomEquipmentRepository.kt`
- `/phases/030-039/34-equipment-shop-and-inventory/index.json`

## 작업

`ShopViewModelTest`와 `InventoryViewModelTest`를 먼저 실패시키고 `feature/shop`의 presentation model과 ViewModel을 구현한다. `ShopUiState`는 loading, current gold, character level, nullable selected category(전체), filter된 item 목록, selected detail, 구매 confirmation, `PurchaseState`, equip 결과/오류와 retry 상태를 하나의 immutable state로 노출한다. `InventoryUiState`는 loading, 보유 장비 목록, slot별 장착 상태, 선택/처리 중 owned id와 오류를 노출한다.

ViewModel은 `EquipmentRepository` Flow와 step 1 UseCase만 사용하고 DAO나 Android resource를 알지 않는다. `ShopEvent`는 category 선택, equipment 선택/상세 닫기, 구매 확인 요청/취소/확정, 구매 직후 장착, 성공 결과 소비, retry를 포함한다. Inventory event는 owned 장비 선택, 장착/교체, retry와 오류 소비를 포함한다. type/slot label과 name/description은 enum 또는 resource key로 UI까지 전달하고 ViewModel에서 표시용 영문 문장을 만들지 않는다.

구매 confirmation은 UI snapshot의 예상 gold를 보여주지만 confirm 시 Repository가 다시 검증한다. confirm 직후 `PurchaseState.Processing`으로 바꿔 연속 event를 무시한다. 성공은 owned id, equipment key, type, slot과 최신 gold를 가진 state로 유지해 `바로 장착`, `인벤토리로 이동`, `계속 쇼핑` 중 하나를 고를 때까지 보존한다. recomposition은 command를 다시 실행하지 않으며 result 소비가 명시적으로 한 번만 일어난다. process recreation 뒤 UI command를 자동 재전송하지 않는다.

목록과 상세 item mapping은 현재 같은 slot 장착 장비만 comparison source로 사용한다. CHEST 상세에는 LEGS를, LEGS 상세에는 CHEST를 포함하지 않는다. 구매 불가 이유와 equip 결과는 raw exception 문자열이 아니라 semantic sealed type으로 노출한다. load/DB 예외는 일반 오류 상태와 retry로 격리한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.shop.ShopViewModelTest" --tests "com.todoquest.feature.shop.InventoryViewModelTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.PurchaseEquipmentPolicyTest" --tests "com.todoquest.domain.EquipmentComparisonCalculatorTest" --console=plain
git diff --check
```

## 검증 절차

1. filter, same-slot comparison, confirmation, Processing 중복 방지, success 소비와 retry 테스트를 먼저 작성한다.
2. 화면 회전과 recomposition을 가정해 state 변화만으로 command가 재실행되지 않는지 확인한다.
3. task index의 step 5를 `completed`로 바꾸고 UI state/event 계약을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- ViewModel에 한국어 또는 영문 표시 문장을 하드코딩하지 마라. 이유: 표시 문자열은 resource mapping 책임이다.
- 성공 state 관찰만으로 purchase/equip command를 다시 호출하지 마라. 이유: 재구성과 재수집에서 중복 실행된다.
- CHEST와 LEGS comparison source를 하나의 방어구 그룹으로 합치지 마라. 이유: 두 slot은 독립 계약이다.
- 기존 테스트를 깨뜨리지 마라.
