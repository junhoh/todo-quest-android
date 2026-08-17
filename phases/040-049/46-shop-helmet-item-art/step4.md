# Step 4: bind-helmet-catalog-visuals

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
- `/docs/art/equipment/todo-quest-helmet-layers-spec.json`
- `/phases/040-049/46-shop-helmet-item-art/index.json`

## 작업

DAO와 Repository 테스트를 먼저 작성해 두 seeded helmet의 visual key가 `null`인 예상 실패를 확인한 뒤 app-owned catalog metadata를 멱등하게 연결한다. Room schema와 database version은 변경하지 않는다.

fresh catalog 정의는 다음 값을 사용한다.

```text
LEATHER_HAT_ID: imageKey=headgear_leather_hat, layerKey=headgear_leather_hat
IRON_HELMET_ID: imageKey=headgear_iron_helmet, layerKey=headgear_iron_helmet
```

기존 설치에서는 `INSERT OR IGNORE`만으로 기존 row가 갱신되지 않으므로 `EquipmentDao`에 visual metadata 전용 update와 transaction 경계를 추가한다. update는 stable id와 예상 `nameKey`, `type=HELMET`, `slot=HELMET`이 모두 일치할 때 `imageKey`, `layerKey` 두 컬럼만 변경한다. price, requiredLevel, rarity, `isForSale`, modifier, owned row와 equipped row를 수정하지 않는다.

`EquipmentCatalogSeeder.seed()`는 catalog insert와 두 visual metadata 보정을 하나의 DAO transaction으로 실행한다. 같은 값을 반복 적용해도 row 수, 소유권과 판매 상태가 바뀌지 않아야 한다. stable id가 다른 custom row와 예상 name/type/slot이 다른 손상 row에는 visual key를 덮어쓰지 않고 기존 repository data-error/fallback 경계를 유지한다.

테스트는 다음을 포함한다.

- fresh seed에서 두 helmet만 non-null visual key를 가지며 값이 해당 `CharacterLoadoutCatalog` 상수와 같다.
- 두 번 seed해도 14개 definition과 modifier 순서가 유지된다.
- 기존 null-key helmet row는 다음 seed에서 backfill된다.
- 기존 helmet의 판매 중지, 가격, 소유·장착 row와 modifier는 backfill 후 그대로다.
- custom/불일치 row는 보정하지 않는다.
- 가죽 모자와 철 투구를 각각 구매·장착하면 `renderedEquippedItems.headId`가 해당 ID가 되고 다른 appearance field는 유지된다.
- 알 수 없는 custom layerKey는 기존 appearance fallback을 유지한다. seeded helmet을 손상시키는 fixture 대신 별도 custom definition 또는 순수 projection fixture를 사용한다.
- 구매·장착 능력치와 HP 비율 보존은 기존 값과 동일하다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.local.EquipmentDaoTest" --tests "com.todoquest.data.repository.RoomEquipmentRepositoryTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.PurchaseEquipmentPolicyTest" --tests "com.todoquest.domain.EquipmentModifierValidatorTest"
.\gradlew.bat :app:assembleDebug
git diff --check
```

## 검증 절차

1. seeder·projection 테스트를 먼저 작성하고 예상 실패를 확인한다.
2. visual 전용 보정 transaction과 catalog 값을 구현한 뒤 AC를 실행한다.
3. `TodoQuestDatabase.version`, exported schema와 migration 목록이 변경되지 않았는지 확인한다.
4. UI가 DAO를 직접 호출하지 않고 repository Flow를 통해 key를 받는 기존 경계를 확인한다.
5. task index의 step 4를 `completed`로 바꾸고 visual key, 멱등 backfill과 무 migration 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 전체 EquipmentEntity를 REPLACE/UPSERT하지 마라. 이유: 판매 상태와 다른 runtime metadata를 뜻하지 않게 초기화할 수 있다.
- Room version을 올리거나 새 컬럼을 추가하지 마라. 이유: 필요한 `imageKey`와 `layerKey` 컬럼은 이미 존재한다.
- visual key 보정을 소유권 생성으로 해석하지 마라. 이유: 구매 전에는 gameplay owned row가 없어야 한다.
- 테스트보다 DAO/Repository 구현을 먼저 작성하지 마라. 이유: AGENTS.md의 테스트 우선 규칙을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
