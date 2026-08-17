# Step 4: bind-outfit-catalog-visuals

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/domain/model/CharacterLoadout.kt`
- `/app/src/main/java/com/todoquest/domain/model/Equipment.kt`
- `/app/src/main/java/com/todoquest/data/local/EquipmentEntities.kt`
- `/app/src/main/java/com/todoquest/data/local/EquipmentDao.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomEquipmentRepository.kt`
- `/app/src/test/java/com/todoquest/data/local/EquipmentDaoTest.kt`
- `/app/src/test/java/com/todoquest/data/repository/RoomEquipmentRepositoryTest.kt`
- `/docs/art/equipment/todo-quest-top-bottom-layers-spec.json`
- `/phases/040-049/47-shop-top-bottom-item-art/index.json`

## 작업

DAO와 Repository 테스트를 먼저 작성해 대상 6종의 visual key가 `null`인 예상 실패를 확인한 뒤 app-owned catalog metadata를 연결한다. Room schema와 database version은 변경하지 않는다.

fresh catalog의 `CLOTH_TOP_ID`, `LEATHER_ARMOR_ID`, `IRON_BREASTPLATE_ID`, `CLOTH_PANTS_ID`, `LEATHER_PANTS_ID`, `STEEL_GREAVES_ID`는 각각 step 2의 대응 `CharacterLoadoutCatalog` 상수를 `imageKey`와 `layerKey` 양쪽에 사용한다.

기존 `seedCatalogIgnoreAndUpdateVisualMetadata` transaction을 재사용한다. stable ID와 예상 `nameKey`, type·slot이 모두 일치할 때 두 visual 컬럼만 갱신하고 price, requiredLevel, rarity, isForSale, modifier, owned/equipped row는 수정하지 않는다. custom/손상 row에는 key를 덮어쓰지 않는다.

테스트는 다음을 포함한다.

- fresh seed에서 기존 투구 2종과 새 상·하의 6종만 non-null visual key를 가지며 나머지 6종은 placeholder 계약을 유지한다.
- 두 번 seed해도 14개 definition, modifier 순서와 visual metadata가 멱등이다.
- 기존 null-key 대상 row가 다음 seed에서 backfill되고 판매 중지·가격·소유·장착·modifier가 보존된다.
- stable identity가 다른 custom/손상 row는 갱신하지 않는다.
- 각 새 장비를 구매·장착하면 `renderedEquippedItems.topId` 또는 `bottomId`만 대응 layer ID로 바뀌고 다른 appearance field는 유지된다.
- 상의와 하의를 연속 장착하면 두 projection이 함께 유지되며 능력치와 HP 비율 보존은 기존 계약과 동일하다.
- 알 수 없는 custom layerKey는 기존 appearance fallback을 유지한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.local.EquipmentDaoTest" --tests "com.todoquest.data.repository.RoomEquipmentRepositoryTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.PurchaseEquipmentPolicyTest" --tests "com.todoquest.domain.EquipmentModifierValidatorTest"
.\gradlew.bat :app:assembleDebug
git diff --check
```

## 검증 절차

1. seeder·projection 테스트를 먼저 작성하고 예상 실패를 확인한다.
2. 대상 catalog key를 추가한 뒤 AC를 실행한다.
3. Room version, exported schema와 migration 목록이 변경되지 않았는지 확인한다.
4. UI가 DAO를 직접 호출하지 않고 Repository Flow를 통해 key를 받는 경계를 확인한다.
5. task index의 step 4를 `completed`로 바꾸고 visual key 6개와 멱등 backfill을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 전체 EquipmentEntity를 REPLACE/UPSERT하지 마라. 이유: 판매 상태와 runtime metadata를 초기화할 수 있다.
- Room version을 올리거나 새 컬럼을 추가하지 마라. 이유: imageKey와 layerKey 컬럼은 이미 존재한다.
- visual key 보정을 소유권 생성으로 해석하지 마라. 이유: 구매 전에는 owned row가 없어야 한다.
- 테스트보다 DAO/Repository 구현을 먼저 작성하지 마라. 이유: AGENTS.md의 테스트 우선 규칙을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
