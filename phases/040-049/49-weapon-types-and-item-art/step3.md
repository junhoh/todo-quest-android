# Step 3: persist-weapon-type-room-v12

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/app/src/main/java/com/todoquest/domain/model/Equipment.kt`
- `/app/src/main/java/com/todoquest/data/local/EquipmentEntities.kt`
- `/app/src/main/java/com/todoquest/data/local/TodoQuestDatabase.kt`
- `/app/src/main/java/com/todoquest/data/mapper/EquipmentMapper.kt`
- `/app/src/main/java/com/todoquest/app/TodoQuestApplication.kt`
- `/app/src/test/java/com/todoquest/data/local/TodoQuestDatabaseMigrationTest.kt`
- `/app/src/test/java/com/todoquest/data/local/EquipmentDaoTest.kt`
- `/app/src/test/java/com/todoquest/data/repository/RoomEquipmentRepositoryTest.kt`
- `/phases/040-049/49-weapon-types-and-item-art/index.json`

## 작업

Room migration·mapper 테스트를 먼저 작성해 subtype이 저장되지 않는 예상 실패를 확인한 뒤 equipment persistence를 v12로 확장한다. catalog 항목과 PNG/UI는 수정하지 않는다.

- `EquipmentEntity`에 `val weaponType: String?`를 추가한다.
- `EquipmentMapper`는 known storage value를 `WeaponType`으로 매핑하고 unknown value는 `EquipmentRepositoryDataException`으로 격리한다.
- `TodoQuestDatabase` version을 `12`로 올리고 다음 공개 migration을 추가한다.

```kotlin
val MIGRATION_11_12: Migration = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE equipment ADD COLUMN weaponType TEXT")
        db.execSQL("UPDATE equipment SET weaponType = 'LONGSWORD' WHERE type = 'WEAPON'")
    }
}
```

- non-weapon row는 null을 유지한다. 기존 v11의 app-owned/custom `WEAPON` row는 호환 기본값 `LONGSWORD`로 읽을 수 있게 한다.
- production database builder와 migration을 직접 나열하는 모든 test builder에 `MIGRATION_11_12`를 등록한다.
- schema export `/app/schemas/com.todoquest.data.local.TodoQuestDatabase/12.json`을 생성한다.
- migration은 기존 equipment ID, visual key, 판매 상태, modifier, owned/character equipment와 일정·보상·전투·알림 row를 보존한다.

테스트는 v11→v12 weapon backfill, non-weapon null, fresh round trip, enum 네 값 mapping, unknown subtype 오류, 전체 migration chain, 기존 소유·장착과 nullable appearance fallback 보존을 포함한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.local.TodoQuestDatabaseMigrationTest" --tests "com.todoquest.data.local.EquipmentDaoTest" --tests "com.todoquest.data.repository.RoomEquipmentRepositoryTest"
.\gradlew.bat :app:assembleDebug
git diff --check
```

## 검증 절차

1. migration·mapper 테스트를 먼저 작성하고 v11 schema에서 예상 실패를 확인한다.
2. entity, mapper, v12 migration과 builder 등록 후 AC를 실행한다.
3. schema 12의 table/index/foreign key가 v11과 새 nullable column 외에는 동일한지 확인한다.
4. task index의 step 3을 `completed`로 바꾸고 v12 backfill과 보존 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- `equipment`를 drop/recreate하지 마라. 이유: 기존 catalog와 사용자 소유·장착 상태를 보존해야 한다.
- migration에서 신규 장비나 ownership row를 생성하지 마라. 이유: catalog seed와 schema migration의 책임을 분리해야 한다.
- non-weapon에 weaponType을 채우지 마라. 이유: domain 불변식을 위반한다.
- 테스트보다 migration 구현을 먼저 작성하지 마라. 이유: AGENTS.md의 테스트 우선 규칙을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
