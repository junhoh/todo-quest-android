# Step 5: project-owned-shop-actions

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/app/src/main/java/com/todoquest/domain/model/Equipment.kt`
- `/app/src/main/java/com/todoquest/domain/repository/EquipmentRepository.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomEquipmentRepository.kt`
- `/app/src/main/java/com/todoquest/data/local/EquipmentDao.kt`
- `/app/src/test/java/com/todoquest/data/repository/RoomEquipmentRepositoryTest.kt`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/step4.md`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/index.json`

## 작업

repository 테스트를 먼저 작성하고 Shop snapshot에 소유 장비의 typed action lookup을 투영한다.

`RoomEquipmentRepository.loadStoreSnapshot(...)`은 현재 character의 owned rows 전체를 읽어 `ownedEquipmentByEquipmentId`를 `equipmentId -> OwnedEquipment`로 만든다. 기존 `ownedEquipmentIds`는 이 map의 key set과 같아야 한다. `equippedBySlot`의 각 값은 동일한 map에 있는 같은 owned row를 참조해야 한다. 잘못된/중복 catalog row는 기존 repository 격리 정책을 따르며 임의의 owned id를 선택하지 않는다.

projection은 신규 모험가 7부위를 포함해 판매 catalog 전체에 대해 구매 전 preview를 계속 만들고, 실제 gameplay 장착이 있는 slot은 fallback보다 우선한다. 빈 slot에서는 step 3의 중립 default loadout을 사용한다. 구매·장착·해제 transaction 자체는 기존 use case/repository 경계를 유지한다.

테스트는 미소유, 소유했지만 미장착, 소유 및 장착 상태를 각각 구성해 lookup key/value, `ownedEquipmentIds`, `equippedBySlot`, rendered loadout이 일치하는지 검증한다. 신규 7개 item을 seed한 store snapshot에서도 같은 계약을 확인한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.repository.RoomEquipmentRepositoryTest" --console=plain
.\gradlew.bat :app:assembleDebug --console=plain
git diff --check
```

## 검증 절차

1. owned equipment lookup이 비어 있어 장착 action을 만들 수 없는 실패 테스트를 먼저 작성한다.
2. equipment id와 owned row id가 다른 fixture로 정확한 lookup을 검증한다.
3. 실제 장착 projection이 빈 appearance fallback보다 우선하는지 확인한다.
4. 성공 시 task index의 step 5를 `completed`로 바꾸고 repository projection 계약을 한국어 `summary` 두 줄로 기록한다.

## 금지사항

- UI 또는 ViewModel에서 DAO를 조회하게 하지 마라. 이유: 소유 장비 lookup은 Repository snapshot이 제공해야 한다.
- equipment id를 owned equipment id로 전달하지 마라. 이유: 장착 transaction이 잘못된 row를 찾거나 실패한다.
- snapshot 생성 중 소유권이나 장착 상태를 변경하지 마라. 이유: observe projection은 읽기 전용이어야 한다.
- 기존 테스트를 삭제하거나 완화하지 마라.
