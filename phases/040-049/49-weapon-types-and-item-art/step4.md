# Step 4: seed-and-bind-weapon-catalog

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/character-stats/modifiers-and-equipment.md`
- `/app/src/main/java/com/todoquest/domain/model/Equipment.kt`
- `/app/src/main/java/com/todoquest/domain/model/CharacterLoadout.kt`
- `/app/src/main/java/com/todoquest/data/local/EquipmentDao.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomEquipmentRepository.kt`
- `/app/src/test/java/com/todoquest/data/local/EquipmentDaoTest.kt`
- `/app/src/test/java/com/todoquest/data/repository/RoomEquipmentRepositoryTest.kt`
- `/app/src/test/java/com/todoquest/domain/EquipmentModifierValidatorTest.kt`
- `/docs/art/equipment/todo-quest-weapon-layers-spec.json`
- `/phases/040-049/49-weapon-types-and-item-art/index.json`

## 작업

DAO·Repository 테스트를 먼저 작성해 기존 두 검의 visual key가 null이고 신규 창·철퇴가 없는 예상 실패를 확인한 뒤 app-owned catalog와 gameplay projection을 확장한다. Room version과 schema는 이 step에서 변경하지 않는다.

기존 두 검은 가격·레벨·modifier를 바꾸지 않고 다음 metadata만 추가한다.

```text
1001 worn_sword: weaponType=LONGSWORD, imageKey/layerKey=weapon_worn_sword
1002 iron_longsword: weaponType=LONGSWORD, imageKey/layerKey=weapon_iron_longsword
```

신규 entry는 다음 값으로 정확히 고정한다.

```text
ASH_SPEAR_ID = 1017
key = ash_spear
type/slot = WEAPON
weaponType = SPEAR
rarity = COMMON
price = 50
requiredLevel = 1
imageKey/layerKey = weapon_ash_spear
modifiers = ATTACK FLAT +4

STEEL_MACE_ID = 1018
key = steel_mace
type/slot = WEAPON
weaponType = BLUNT
rarity = RARE
price = 780
requiredLevel = 12
imageKey/layerKey = weapon_steel_mace
modifiers = ATTACK FLAT +12, STRENGTH +4, CRITICAL_DAMAGE FLAT +400bp
```

`EquipmentCatalogSeeder`는 ID `1001..1018`의 18종을 멱등 seed한다. 기존 `seedCatalogIgnoreAndUpdateVisualMetadata` transaction을 재사용해 신규 definition/modifier는 `INSERT OR IGNORE`로 추가하고, 기존 두 검은 stable ID·nameKey·type·slot이 일치할 때 visual 두 컬럼만 backfill한다. 사용자 판매 상태, 가격 override, 기존 modifier, owned row와 equipped row를 덮어쓰지 않는다.

`projectEquippedCharacterLayers`는 기존 WEAPON 분기에서 유효한 새 `layerKey`를 `weaponId`에 투영하고 `CharacterLoadoutCatalog.contains`를 통과한 결과만 채택한다.

테스트는 다음을 포함한다.

- fresh seed는 18개 definition과 정확한 subtype/modifier 순서를 가진다.
- 기존 12개 visual 장비와 무기 4개를 합친 16개만 non-null image/layer key를 가지며 액세서리 2개만 placeholder다.
- 두 번 seed해도 definition, modifier와 visual metadata가 멱등이다.
- 기존 16종 DB에 seed하면 1017·1018만 추가되고 runtime override와 소유·장착 상태가 보존된다.
- stable identity가 다른 custom/손상 row는 visual metadata를 갱신하지 않는다.
- 네 무기의 구매·장착·교체는 `weaponId`만 바꾸며 다른 appearance와 HP 비율을 보존한다.
- 신규 modifier는 rarity/WEAPON slot validator를 통과하고 소유만으로는 능력치에 적용되지 않는다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.local.EquipmentDaoTest" --tests "com.todoquest.data.repository.RoomEquipmentRepositoryTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.PurchaseEquipmentPolicyTest" --tests "com.todoquest.domain.EquipmentModifierValidatorTest" --tests "com.todoquest.domain.EquipmentComparisonCalculatorTest"
.\gradlew.bat :app:assembleDebug
git diff --check
```

## 검증 절차

1. seeder·projection 테스트를 먼저 작성하고 예상 실패를 확인한다.
2. subtype, 신규 catalog와 네 visual key를 추가한 뒤 AC를 실행한다.
3. Room version, exported schema와 migration 목록이 이 step에서 변경되지 않았는지 확인한다.
4. UI가 DAO를 직접 호출하지 않고 Repository Flow를 통해 subtype과 key를 받는 경계를 확인한다.
5. task index의 step 4를 `completed`로 바꾸고 18종 catalog·16개 visual·신규 밸런스를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 전체 EquipmentEntity를 REPLACE/UPSERT하지 마라. 이유: 사용자 runtime 상태를 초기화할 수 있다.
- 기존 두 검의 수치나 가격을 바꾸지 마라. 이유: 승인된 범위는 visual/subtype 연결이다.
- subtype별 전투 공식이나 세트 효과를 추가하지 마라. 이유: 현재 승인 범위가 아니다.
- visual key 보정을 소유권 생성으로 해석하지 마라. 이유: 구매 전에는 owned row가 없어야 한다.
- 테스트보다 구현을 먼저 작성하지 마라. 이유: AGENTS.md의 테스트 우선 규칙을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
