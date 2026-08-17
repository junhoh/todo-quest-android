# Step 3: extend-equipment-domain-contracts

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/game-design/character-stats/modifiers-and-equipment.md`
- `/app/src/main/java/com/todoquest/domain/model/CharacterLoadout.kt`
- `/app/src/main/java/com/todoquest/domain/model/Equipment.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/EquipmentPolicies.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/EquipmentModifierValidator.kt`
- `/app/src/test/java/com/todoquest/domain/CharacterLoadoutCatalogTest.kt`
- `/app/src/test/java/com/todoquest/domain/EquipmentModifierValidatorTest.kt`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/step2.md`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/index.json`

## 작업

순수 Kotlin 테스트를 먼저 작성해 빈 fallback, adventure gloves, 상점 소유 장비 lookup 계약을 도메인 모델에 추가한다.

`CharacterLoadoutCatalog`에 `GLOVES_ADVENTURE = "gloves_adventure"`를 등록한다. `defaultEquippedItems`는 빈 gameplay slot의 외형을 나타내도록 `headId = null`, `topId = TOP_DEFAULT`, `bottomId = BOTTOM_DEFAULT`, `shoesId = SHOES_DEFAULT`, `accessoryId = null`, `weaponId = null`, `glovesId = null`로 바꾼다. `EquipmentUnequipAppearancePolicy`는 이미 이 상태로 수렴하도록 유지하며, adventure layer key 전체는 catalog validation에서 계속 허용한다.

`EquipmentStoreSnapshot`에 `ownedEquipmentByEquipmentId: Map<Long, OwnedEquipment> = emptyMap()`을 추가한다. key는 반드시 `OwnedEquipment.equipmentId`이고 값은 해당 character 소유 항목이어야 하며, 기존 `ownedEquipmentIds`는 구매 eligibility 호환을 위해 유지한다. snapshot `init` 또는 순수 validator로 map key/character/equipment 일관성을 검증한다. 이 map은 이후 Shop의 `장착` action이 DB id인 `ownedEquipmentId`를 안전하게 얻는 유일한 typed source다.

테스트는 새 default loadout, 모든 nullable slot 해제, `gloves_adventure` 허용, 잘못된 owned map key/character 차단, 기존 fixture의 default empty map 호환을 포함한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.CharacterLoadoutCatalogTest" --tests "com.todoquest.domain.EquipmentModifierValidatorTest" --console=plain
.\gradlew.bat :app:assembleDebug --console=plain
git diff --check
```

## 검증 절차

1. 새 default loadout과 owned lookup invariants에 대한 실패 테스트를 먼저 작성한다.
2. 장비 해제 정책이 어떤 slot에서도 adventure layer를 빈 slot fallback으로 되살리지 않는지 확인한다.
3. `ownedEquipmentIds`, `ownedEquipmentByEquipmentId`, `equippedBySlot`의 식별자 의미가 섞이지 않는지 확인한다.
4. 성공 시 task index의 step 3을 `completed`로 바꾸고 도메인 계약 변경을 한국어 `summary` 두 줄로 기록한다.

## 금지사항

- `OwnedEquipment.id`와 `Equipment.id`를 같은 식별자로 취급하지 마라. 이유: 장착 command는 owned row id를 요구한다.
- `ownedEquipmentIds`를 제거하지 마라. 이유: 기존 구매 검증과 fixture 호환을 유지해야 한다.
- default loadout에 adventure 장비를 남기지 마라. 이유: 기존 기본 외형은 상점에서 획득하는 장비로 전환된다.
- domain 모델에서 Room entity나 Android API를 참조하지 마라. 이유: 도메인 계층 경계를 지켜야 한다.
- 기존 테스트를 삭제하거나 완화하지 마라.
