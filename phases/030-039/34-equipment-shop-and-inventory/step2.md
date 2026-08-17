# Step 2: persist-equipment-room-v7

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/app/src/main/java/com/todoquest/data/local/TodoQuestDatabase.kt`
- `/app/src/main/java/com/todoquest/data/local/CharacterEquippedItemsEntity.kt`
- `/app/src/main/java/com/todoquest/data/local/CharacterProfileEntity.kt`
- `/app/src/test/java/com/todoquest/data/local/TodoQuestDatabaseMigrationTest.kt`
- `/app/schemas/com.todoquest.data.local.TodoQuestDatabase/6.json`
- `/app/src/main/java/com/todoquest/domain/model/Equipment.kt`
- `/app/src/main/java/com/todoquest/domain/repository/EquipmentRepository.kt`
- `/phases/030-039/34-equipment-shop-and-inventory/index.json`

## 작업

DAO·migration 테스트를 먼저 실패시키고 Room schema를 v7로 확장한다. `data/local/EquipmentEntities.kt`와 `EquipmentDao.kt`를 추가한다. 판매 원본 `equipment`, 정규화 modifier `equipment_modifiers`, 캐릭터별 소유 `owned_equipment`, slot별 장착 `character_equipment`를 사용한다. Entity에는 UI 문자열 대신 `nameKey`·`descriptionKey`, enum storage name, `Long` price와 epoch millis를 저장한다.

`owned_equipment`에는 auto id와 `characterId`, `equipmentId`, `acquiredAtEpochMillis`를 두고 `(characterId, equipmentId)` unique index로 중복 구매를 막는다. quantity와 `isEquipped`를 넣지 않는다. `character_equipment`는 `(characterId, slot)` composite primary key와 `ownedEquipmentId` unique index를 가지며 FK로 존재하는 owned row만 참조한다. equipment modifier는 equipment 삭제 시 함께 정리되는 FK와 안정적인 modifier 순서를 가진다. 필요한 lookup index와 foreign key를 Room entity와 migration SQL에서 동일하게 정의한다.

`TodoQuestDatabase` version을 7로 올리고 `MIGRATION_6_7`을 추가한다. migration은 기존 profile·gold·current HP·appearance·`topId`·`bottomId`·combat/reward/event 데이터를 수정하지 않고 새 테이블만 만든다. v6에 저장된 ARMOR row가 실제로 없음을 migration test fixture로 확인하고, compatibility mapper의 `ARMOR → CHEST`는 별도 unit test로 검증한다. Room schema `7.json`을 export한다.

`EquipmentCatalogSeeder`는 stable explicit id와 `INSERT IGNORE`를 사용해 요청된 낡은 검, 철제 장검, 가죽 모자, 철제 투구, 천 상의, 가죽 갑옷, 철제 흉갑, 천 바지, 가죽 바지, 강철 각반, 가죽 장갑, 여행자의 장화, 마법사의 반지, 수호자의 목걸이를 모두 제공한다. 이름/설명은 resource key로 저장하며 카테고리·가격·레벨·희귀도와 modifier를 다양화한다. 기존 네 기본 스탯만 사용하고 모든 modifier는 step 1 validator를 통과해야 한다. EPIC·LEGENDARY mapping 검증용 판매 장비도 catalog에 포함한다. seeder를 반복 호출해도 row와 modifier가 중복되지 않게 한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.local.EquipmentDaoTest" --tests "com.todoquest.data.local.TodoQuestDatabaseMigrationTest" --console=plain
Test-Path 'app\schemas\com.todoquest.data.local.TodoQuestDatabase\7.json'
git diff --check
```

## 검증 절차

1. v6→v7 보존, CHEST/LEGS 독립 저장, unique/FK와 seed 멱등성 테스트를 먼저 작성한다.
2. 생성된 v7 schema의 table/index/FK가 migration SQL과 일치하는지 확인한다.
3. task index의 step 2를 `completed`로 바꾸고 schema와 migration 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- destructive migration이나 기존 테이블 drop을 사용하지 마라. 이유: 사용자 진행·보상·전투 상태를 보존해야 한다.
- `character_equipped_items`를 소유권 테이블로 재사용하지 마라. 이유: 이 테이블은 기존 외형 fallback이다.
- seed에 auto-generated equipment id를 사용하지 마라. 이유: migration·테스트·구매 결과가 재현 가능해야 한다.
- 기존 테스트를 깨뜨리지 마라.
