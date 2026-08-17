# Step 1: load-shop-preview-projection

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/app/src/main/java/com/todoquest/data/repository/RoomEquipmentRepository.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomCharacterRepository.kt`
- `/app/src/test/java/com/todoquest/data/repository/RoomEquipmentRepositoryTest.kt`
- `/app/src/main/java/com/todoquest/domain/model/Equipment.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/EquipmentPreviewProjectionCalculator.kt`
- `/phases/050-059/58-shop-screen-redesign/step0.md`
- `/phases/050-059/58-shop-screen-redesign/index.json`

## 작업

Repository test를 먼저 작성하고 `RoomEquipmentRepository.loadStoreSnapshot`이 판매 정의별 구매 전 projection을 같은 snapshot에서 방출하게 한다.

현재 `PlayerCharacter`의 level·base stats, 실제 장착 modifier, 활성 status modifier, balance config와 검증된 `renderedEquippedItems`를 step 0 계산기에 전달한다. 모든 `equipment` 정의에 대해 `previewByEquipmentId[equipment.id]`를 생성하며, 실제 `derivedStats`는 현재 장착 결과로 유지한다. preview 계산을 위해 Room row를 추가·수정하거나 구매·장착 UseCase를 호출하지 않는다.

테스트는 미보유 seed 장비도 선택 전 projection을 가지는지, 같은 slot 교체가 다른 slot 외형을 보존하는지, 중상 상태 효과 아래에서도 실제 공식 차이가 계산되는지, null/unknown layer가 현재 외형을 유지하는지 확인한다. `observeStore`만으로 gold, owned rows, equipped rows, current HP가 변하지 않아야 한다.

기존 test fake와 `EquipmentStoreSnapshot` 생성부는 기본 map을 이용하거나 의도가 있는 projection fixture를 명시해 컴파일을 유지한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.repository.RoomEquipmentRepositoryTest" --console=plain
.\gradlew.bat :app:assembleDebug --console=plain
git diff --check
```

## 검증 절차

1. Repository test를 먼저 추가해 preview map 부재로 실패하는지 확인한 뒤 구현한다.
2. projection 전후 DB gold·ownership·equipment·HP row가 동일하고 snapshot의 실제 stats/render state가 바뀌지 않는지 확인한다.
3. UI가 DAO를 직접 사용하지 않고 Repository Flow만 관찰하는지 확인한다.
4. 성공 시 task index의 step 1을 `completed`로 바꾸고 Repository projection source와 무변경 보장을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- preview 선택을 Room에 저장하지 마라. 이유: 상품 선택은 화면 수명의 비영속 UI 상태다.
- 장비마다 별도 DAO query를 실행하지 마라. 이유: 이미 읽은 snapshot source로 결정론적으로 계산할 수 있다.
- 구매 가능 여부 정책을 preview 계산으로 대체하지 마라. 이유: 실제 구매는 transaction 안에서 최신 상태를 다시 검증해야 한다.
- 기존 테스트를 깨뜨리지 마라.
