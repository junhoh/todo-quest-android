# Step 2: present-shop-selection-state

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/feature/shop/ShopUiState.kt`
- `/app/src/main/java/com/todoquest/feature/shop/ShopViewModel.kt`
- `/app/src/test/java/com/todoquest/feature/shop/ShopViewModelTest.kt`
- `/app/src/main/java/com/todoquest/domain/model/Equipment.kt`
- `/phases/050-059/58-shop-screen-redesign/step1.md`
- `/phases/050-059/58-shop-screen-redesign/index.json`

## 작업

ViewModel unit test를 먼저 작성하고 상품 선택, 상세 열기와 직접 구매 요청을 분리한다.

`ShopUiState`는 `selectedEquipmentId: Long?`를 노출한다. `CharacterStatValueUiModel(currentValue: Int, difference: Int)`을 추가하고 공격력·최대 체력·방어력 summary를 이 타입으로 구성한다. 선택 장비가 있으면 캐릭터 render state는 `previewByEquipmentId`의 임시 외형을 사용하지만 스탯의 `currentValue`는 실제 현재값, `difference`는 preview minus current로 둔다. 차이가 없거나 projection이 없으면 0이다.

`ShopCommandState`의 preview 선택 id와 상세 bottom sheet id를 분리한다. 이벤트 계약은 다음과 같이 확정한다.

- `SelectEquipment(equipmentId)`: preview 선택만 갱신한다.
- `OpenEquipmentDetail(equipmentId)`: 같은 상품을 선택하고 기존 상세 bottom sheet를 연다.
- `CloseEquipmentDetail`: 상세만 닫고 preview 선택은 유지한다.
- `RequestPurchaseConfirmation(equipmentId)`: 상세 상태에 의존하지 않고 최신 snapshot으로 확인 상태를 만들며 같은 상품을 preview 선택한다.

`SelectCategory`는 선택 상품, 상세와 구매 확인을 정리하고 기존 filter를 적용한다. slot 선택 표시는 선택 상품 slot을 최우선으로 하고, 없으면 managed slot, 그다음 category slot을 사용한다. 상품 선택은 목록 위치나 filter를 바꾸지 않는다.

구매·장착·해제의 처리 잠금, 중복 confirm 방지, success consume, retry와 Repository commit 이후 갱신 흐름은 유지한다. 구매 성공만으로 실제 stats와 장착 외형을 바꾸지 않고 `EquipPurchased` commit 이후에만 실제 상태를 갱신한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.shop.ShopViewModelTest" --console=plain
.\gradlew.bat :app:assembleDebug --console=plain
git diff --check
```

## 검증 절차

1. 선택/상세/구매 이벤트 분리와 정확한 stat delta unit test를 먼저 추가해 실패를 확인한다.
2. 카드 선택, 상세 열고 닫기, category 변경, 부족 골드와 구매 성공·장착 commit을 각각 검증한다.
3. Repository/Room 객체가 UI state나 Composable로 누출되지 않는지 확인한다.
4. 성공 시 task index의 step 2를 `completed`로 바꾸고 새 이벤트·선택 projection 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 카드 선택만으로 상세 bottom sheet를 열지 마라. 이유: 사용자가 목록 위치를 유지하며 여러 상품을 선택 비교해야 한다.
- 구매 버튼에서 카드 click과 구매 event를 연속 호출하지 마라. 이유: nested click으로 선택과 command가 중복 실행될 수 있다.
- preview 수치를 실제 장착 상태로 저장하지 마라. 이유: 장착 transaction commit 전에는 gameplay 능력치가 바뀌면 안 된다.
- 기존 테스트를 깨뜨리지 마라.
