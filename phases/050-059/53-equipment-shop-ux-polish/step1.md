# Step 1: persist-equipment-unequip

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/character-stats/modifiers-and-equipment.md`
- `/app/src/main/java/com/todoquest/domain/model/Equipment.kt`
- `/app/src/main/java/com/todoquest/domain/model/CharacterLoadout.kt`
- `/app/src/main/java/com/todoquest/domain/repository/EquipmentRepository.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/EquipmentPolicies.kt`
- `/app/src/main/java/com/todoquest/data/local/EquipmentDao.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomEquipmentRepository.kt`
- `/app/src/test/java/com/todoquest/data/local/EquipmentDaoTest.kt`
- `/app/src/test/java/com/todoquest/data/repository/RoomEquipmentRepositoryTest.kt`
- `/phases/050-059/53-equipment-shop-ux-polish/step0.md`
- `/phases/050-059/53-equipment-shop-ux-polish/index.json`

## 작업

Repository/Room 테스트를 먼저 작성한다. `EquipmentRepository`에 다음 contract를 추가하고 `UnequipEquipmentUseCase`가 그대로 위임하게 한다.

```kotlin
suspend fun unequipEquipment(
    characterId: Long,
    targetSlot: EquipmentSlot,
): UnequipEquipmentResult
```

`EquipmentDao`에는 `(characterId, slot)`의 `character_equipment` row 하나만 삭제하고 영향 row 수를 반환하는 query를 추가한다. `RoomEquipmentRepository.unequipEquipment`는 하나의 `database.withTransaction`에서 catalog/character source를 준비하고 최신 slot row를 읽는다. row가 없으면 write 없이 `AlreadyEmpty(targetSlot)`를 반환한다. row가 있으면 기존 modifier·파생 능력치·현재 HP를 읽고, 대상 장착 row만 삭제하고, 최신 `character_equipped_items`에 `EquipmentUnequipAppearancePolicy.clearSlot`을 적용해 저장한 뒤 새 modifier·파생 능력치를 계산한다. `MAX_HP`가 바뀌면 기존 `CombatCalculator.preserveHpRatio`로 current HP를 갱신하고 `0 HP`는 `0`으로 유지한다. 성공 결과는 삭제한 owned/equipment id와 slot을 반환한다.

삭제는 `owned_equipment`를 건드리지 않고 다른 slot, 골드, catalog와 외형 field를 보존해야 한다. 장착 삭제·fallback 갱신·current state 갱신 중 하나라도 실패하면 모두 rollback해야 한다. 실제 장착 modifier가 제거되므로 기존 Character·Task·Combat 관찰 경로는 다음 source emission에서 새 값을 사용하되 과거 RewardLedger나 확정 attack snapshot을 수정하지 않는다.

새 abstract Repository method로 인해 unit-test fake가 컴파일되지 않으면 이 step에서 모든 unit-test `EquipmentRepository` fake에 명시적 stub 또는 실제 fake 동작을 추가한다. production 구현을 default no-op으로 숨기지 않는다. Room schema와 entity는 바꾸지 않는다.

테스트에는 단일 slot 성공, 소유권과 다른 slot 보존, 외형 기본화, derived stats 감소, MAX_HP 비율과 `0 HP`, `AlreadyEmpty` 멱등성, active status modifier와의 합성, transaction 실패 rollback을 포함한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.local.EquipmentDaoTest" --tests "com.todoquest.data.repository.RoomEquipmentRepositoryTest" --console=plain
git diff --check
```

## 검증 절차

1. DAO와 Repository 테스트를 먼저 추가해 실패를 확인한 뒤 구현하고 AC를 실행한다.
2. UI나 ViewModel이 DAO를 직접 호출하지 않고 Repository transaction이 삭제·fallback·HP를 함께 소유하는지 확인한다.
3. Room schema export가 변경되지 않았고 `owned_equipment` row가 보존되는지 확인한다.
4. task index의 step 1을 `completed`로 바꾸고 transaction·멱등성·HP 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- `owned_equipment`를 삭제하지 마라. 이유: 해제는 판매나 폐기가 아니며 인벤토리 소유권을 보존해야 한다.
- 장착 row 삭제와 외형/HP 갱신을 서로 다른 transaction으로 나누지 마라. 이유: crash나 write 실패에서 source가 불일치할 수 있다.
- 이미 빈 slot을 오류로 처리하지 마라. 이유: retry와 중복 command는 멱등 성공이어야 한다.
- Room version이나 migration을 올리지 마라. 이유: 기존 table의 row 삭제만 필요하다.
- 기존 테스트를 깨뜨리지 마라.
