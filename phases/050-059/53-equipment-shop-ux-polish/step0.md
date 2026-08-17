# Step 0: model-equipment-unequip

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/character-stats/modifiers-and-equipment.md`
- `/app/src/main/java/com/todoquest/domain/model/CharacterLoadout.kt`
- `/app/src/main/java/com/todoquest/domain/model/Equipment.kt`
- `/app/src/test/java/com/todoquest/domain/CharacterLoadoutCatalogTest.kt`
- `/phases/050-059/53-equipment-shop-ux-polish/index.json`

## 작업

테스트를 먼저 작성하고 gameplay 장비 해제의 순수 도메인 계약을 추가한다. `UnequipEquipmentResult`는 `Success(ownedEquipmentId: Long, equipmentId: Long, slot: EquipmentSlot)`와 멱등 재시도를 위한 `AlreadyEmpty(slot: EquipmentSlot)`를 제공한다. 이 step에서는 아직 Repository interface를 변경하지 않는다. 이유: 다음 data step의 실제 구현 없이 abstract method만 추가하면 기존 `RoomEquipmentRepository`와 모든 fake가 컴파일되지 않아 독립 커밋 계약을 깨뜨린다.

`EquipmentUnequipAppearancePolicy.clearSlot(current: EquippedItems, slot: EquipmentSlot): EquippedItems`를 순수 함수로 추가한다. 매핑은 `HELMET → headId = null`, `CHEST → topId = TOP_DEFAULT`, `LEGS → bottomId = BOTTOM_DEFAULT`, `GLOVES → glovesId = null`, `SHOES → shoesId = SHOES_DEFAULT`, `ACCESSORY → accessoryId = null`, `WEAPON → weaponId = null`로 고정한다. 대상 외 모든 appearance field는 byte-for-byte 동일하게 보존한다. 이 정책은 gameplay 소유권을 뜻하지 않으며 이후 명시적 해제 transaction이 shared renderer용 외형 fallback을 기본 복장으로 바꾸는 데만 사용한다.

`CharacterLoadoutCatalogTest` 또는 동등한 순수 Kotlin 테스트에서 일곱 slot 매핑, 다른 slot 보존, 입력 불변성을 먼저 실패시키고 구현한다. 결과 타입의 id와 slot은 양수·canonical source를 전달하는 값으로만 사용하며 Room entity나 UI 문자열을 포함하지 않는다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.CharacterLoadoutCatalogTest" --console=plain
git diff --check
```

## 검증 절차

1. 순수 도메인 테스트를 먼저 추가해 실패를 확인한 뒤 구현하고 AC를 실행한다.
2. 외형 정책이 `EquipmentSlot` 일곱 값 모두를 exhaustive하게 처리하고 다른 slot을 변경하지 않는지 확인한다.
3. AGENTS.md와 ADR-013의 gameplay 소유·장착 source 분리를 확인한다.
4. task index의 step 0을 `completed`로 바꾸고 생성 타입·정책·테스트를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 이 step에서 Room DAO나 Repository 구현을 변경하지 마라. 이유: 순수 도메인 계약과 저장 transaction 경계를 분리해야 한다.
- gameplay slot이 비었다는 이유로 전체 appearance fallback을 초기화하지 마라. 이유: 사용자가 명시적으로 해제한 대상 slot 외 기존 외형을 보존해야 한다.
- 새 Room schema나 migration을 추가하지 마라. 이유: 해제는 기존 `character_equipment` row 삭제로 표현할 수 있다.
- 기존 테스트를 깨뜨리지 마라.
