# Step 5: seed-and-bind-gloves-shoes-catalog

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/character-stats/modifiers-and-equipment.md`
- `/app/src/main/java/com/todoquest/domain/model/CharacterLoadout.kt`
- `/app/src/main/java/com/todoquest/domain/model/Equipment.kt`
- `/app/src/main/java/com/todoquest/data/local/EquipmentEntities.kt`
- `/app/src/main/java/com/todoquest/data/local/EquipmentDao.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomEquipmentRepository.kt`
- `/app/src/test/java/com/todoquest/data/local/EquipmentDaoTest.kt`
- `/app/src/test/java/com/todoquest/data/repository/RoomEquipmentRepositoryTest.kt`
- `/app/src/test/java/com/todoquest/domain/EquipmentModifierValidatorTest.kt`
- `/docs/art/equipment/todo-quest-gloves-shoes-layers-spec.json`
- `/phases/040-049/48-shop-gloves-shoes-item-art/index.json`

## 작업

DAO·Repository 테스트를 먼저 작성해 기존 두 장비의 visual key가 null이고 신규 두 장비가 catalog에 없는 예상 실패를 확인한 뒤 app-owned catalog를 16종으로 확장하고 네 visual mapping을 연결한다. Room schema는 변경하지 않는다.

`EquipmentCatalogSeeder`에 다음 stable ID와 정확한 catalog entry를 추가한다.

```text
STEEL_GAUNTLETS_ID = 1015
key = steel_gauntlets
type/slot = GLOVES
rarity = RARE
price = 820
requiredLevel = 14
modifiers = STRENGTH +4, CRITICAL_CHANCE +400bp, CRITICAL_DAMAGE +400bp

WINDWALKER_BOOTS_ID = 1016
key = windwalker_boots
type/slot = SHOES
rarity = RARE
price = 860
requiredLevel = 15
modifiers = FOCUS +4, DEFENSE +5, HP_RECOVERY PERCENT_ADD +800bp
```

기존 `LEATHER_GLOVES_ID`, `TRAVELERS_BOOTS_ID`와 신규 두 entry는 각각 step 2의 대응 `CharacterLoadoutCatalog` 상수를 `imageKey`와 `layerKey` 양쪽에 사용한다. 기존 가격·레벨·modifier는 변경하지 않는다.

기존 `seedCatalogIgnoreAndUpdateVisualMetadata` transaction을 재사용한다. 신규 definition/modifier는 `INSERT OR IGNORE`로 추가하고 stable ID와 예상 `nameKey`, type, slot이 일치하는 row의 visual 두 컬럼만 backfill한다. 기존 사용자의 판매 상태, 가격 override, 소유·장착 row와 modifier를 덮어쓰지 않는다.

`projectEquippedCharacterLayers`는 `GLOVES`를 `current.copy(glovesId = layerId)`, `SHOES`를 기존 `current.copy(shoesId = layerId)`로 투영하고 `CharacterLoadoutCatalog.contains`를 통과한 결과만 채택한다.

테스트는 다음을 포함한다.

- fresh seed는 ID `1001..1016`의 16개 definition과 정확한 modifier를 가진다.
- 기존 8개 visual 장비와 장갑·신발 4개를 합친 12개만 non-null key를 가지며 무기·액세서리 4개는 placeholder다.
- 두 번 seed해도 definition, modifier 순서와 visual metadata가 멱등이다.
- 기존 14종 DB에 seed하면 신규 두 definition만 추가되고 기존 소유·장착·판매·가격·modifier는 보존된다.
- stable identity가 다른 custom/손상 row는 갱신하지 않는다.
- 네 장비의 구매·장착·교체가 해당 `glovesId` 또는 `shoesId`만 바꾸고 다른 appearance field와 HP 비율을 보존한다.
- 신규 modifier는 희귀 3-affix 범위와 slot 허용 target을 통과하고 소유만으로는 능력치에 적용되지 않는다.
- unknown custom layerKey는 기존 appearance fallback을 유지한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.local.EquipmentDaoTest" --tests "com.todoquest.data.repository.RoomEquipmentRepositoryTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.PurchaseEquipmentPolicyTest" --tests "com.todoquest.domain.EquipmentModifierValidatorTest" --tests "com.todoquest.domain.EquipmentComparisonCalculatorTest"
.\gradlew.bat :app:assembleDebug
git diff --check
```

## 검증 절차

1. seeder·projection 테스트를 먼저 작성하고 예상 실패를 확인한다.
2. 신규 catalog와 네 visual key를 추가한 뒤 AC를 실행한다.
3. Room version, exported schema와 migration 목록이 이 step에서 변경되지 않았는지 확인한다.
4. UI가 DAO를 직접 호출하지 않고 Repository Flow를 통해 key를 받는 경계를 확인한다.
5. task index의 step 5를 `completed`로 바꾸고 16종 catalog·12개 visual·신규 밸런스를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 전체 EquipmentEntity를 REPLACE/UPSERT하지 마라. 이유: 사용자 판매 상태와 runtime metadata를 초기화할 수 있다.
- 기존 가죽 장갑·여행자의 장화의 수치나 가격을 바꾸지 마라. 이유: 신규 장비는 희귀 선택지를 추가하는 범위다.
- 속도 스탯이나 세트 효과를 추가하지 마라. 이유: 현재 domain과 사용자 승인 범위에 없다.
- visual key 보정을 소유권 생성으로 해석하지 마라. 이유: 구매 전에는 owned row가 없어야 한다.
- 테스트보다 구현을 먼저 작성하지 마라. 이유: AGENTS.md의 테스트 우선 규칙을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
