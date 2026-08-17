# Step 3: persist-gloves-loadout-field

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/app/src/main/java/com/todoquest/domain/model/CharacterLoadout.kt`
- `/app/src/main/java/com/todoquest/data/local/CharacterEquippedItemsEntity.kt`
- `/app/src/main/java/com/todoquest/data/local/TodoQuestDatabase.kt`
- `/app/src/main/java/com/todoquest/data/mapper/CharacterMapper.kt`
- `/app/src/main/java/com/todoquest/app/TodoQuestApplication.kt`
- `/app/src/test/java/com/todoquest/data/local/TodoQuestDatabaseMigrationTest.kt`
- `/app/src/test/java/com/todoquest/data/local/CharacterDaoTest.kt`
- `/app/src/test/java/com/todoquest/data/repository/RoomCharacterRepositoryTest.kt`
- `/phases/040-049/48-shop-gloves-shoes-item-art/index.json`

## 작업

Room·mapper 테스트를 먼저 작성해 `glovesId`가 저장되지 않는 예상 실패를 확인한 뒤 appearance fallback persistence를 v11로 확장한다. gameplay 소유·장착 테이블과 equipment catalog는 수정하지 않는다.

- `CharacterEquippedItemsEntity` 마지막에 `val glovesId: String?`를 추가한다.
- `CharacterMapper.toDomain/fromDomain`이 nullable `glovesId`를 양방향 보존한다.
- `TodoQuestDatabase` version을 `11`로 올리고 다음 migration을 공개한다.

```kotlin
val MIGRATION_10_11: Migration = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE character_equipped_items ADD COLUMN glovesId TEXT")
    }
}
```

- production database builder와 migration을 직접 나열하는 test builder에 `MIGRATION_10_11`을 등록한다.
- fresh default와 v10 이하 migration row의 `glovesId`는 `null`이다.
- `updateEquippedItems`로 유효한 장갑 fallback을 저장·관찰할 수 있고 invalid loadout은 기존처럼 무변경이다.
- schema export `/app/schemas/com.todoquest.data.local.TodoQuestDatabase/11.json`을 생성하고 v10의 기존 table·index·data 계약을 보존한다.

테스트는 v10→v11 row 보존과 null backfill, fresh database round trip, 장갑 fallback update/observe, 기존 nullable head/accessory/weapon과 모든 일정·보상·전투 table 보존을 포함한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.local.TodoQuestDatabaseMigrationTest" --tests "com.todoquest.data.local.CharacterDaoTest" --tests "com.todoquest.data.repository.RoomCharacterRepositoryTest"
.\gradlew.bat :app:assembleDebug
git diff --check
```

## 검증 절차

1. migration·mapper 테스트를 먼저 작성하고 v10 schema에서 예상 실패를 확인한다.
2. entity, mapper, v11 migration과 builder 등록 후 AC를 실행한다.
3. destructive migration, appearance 기반 gameplay ownership backfill 또는 기존 row rewrite가 없는지 확인한다.
4. task index의 step 3을 `completed`로 바꾸고 v11 nullable field와 보존 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- `character_equipped_items`를 drop/recreate하지 마라. 이유: 기존 사용자 appearance 진행을 보존해야 한다.
- `owned_equipment`나 `character_equipment`를 migration에서 생성·변경하지 마라. 이유: appearance fallback과 gameplay source는 분리 계약이다.
- 새 장갑을 기존 사용자에게 자동 장착하지 마라. 이유: migration은 nullable fallback만 추가한다.
- 테스트보다 migration 구현을 먼저 작성하지 마라. 이유: AGENTS.md의 테스트 우선 규칙을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
