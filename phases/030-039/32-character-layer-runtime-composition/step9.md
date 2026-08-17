# Step 9: persist-character-loadout

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/PRD.md`
- `/app/src/main/java/com/todoquest/domain/model/CharacterSnapshot.kt`
- `/app/src/main/java/com/todoquest/domain/repository/CharacterRepository.kt`
- `/app/src/main/java/com/todoquest/data/local/CharacterProfileDao.kt`
- `/app/src/main/java/com/todoquest/data/local/TodoQuestDatabase.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomCharacterRepository.kt`
- `/app/src/test/java/com/todoquest/data/local/TodoQuestDatabaseMigrationTest.kt`
- `/app/src/test/java/com/todoquest/data/repository/RoomCharacterRepositoryTest.kt`
- `/phases/030-039/32-character-layer-runtime-composition/index.json`

## 작업

테스트를 먼저 작성한다. 도메인에 `CharacterAppearance(hairId: String)`, `EquippedItems(headId: String?, topId: String, bottomId: String, shoesId: String, accessoryId: String?, weaponId: String?)`, `CharacterLoadoutUpdateResult.Success|InvalidItem`을 추가하고 `CharacterSnapshot`에 두 상태를 포함한다.

고정 catalog ID는 `hair_default`, `headgear_adventure`, `top_default`, `top_adventure`, `bottom_default`, `bottom_adventure`, `shoes_default`, `shoes_adventure`, `accessory_adventure`, `weapon_default_sword`다. Repository signature를 다음처럼 확장한다.

```kotlin
suspend fun updateAppearance(appearance: CharacterAppearance): CharacterLoadoutUpdateResult
suspend fun updateEquippedItems(items: EquippedItems): CharacterLoadoutUpdateResult
```

DB version을 5로 올리고 `CharacterAppearanceEntity`/`character_appearance`, `CharacterEquippedItemsEntity`/`character_equipped_items`와 DAO observe/get/upsert를 추가한다. `MIGRATION_4_5`는 기존 `character_profile`의 모든 id에 기본 머리와 adventure head/top/bottom/shoes/accessory/default sword를 삽입한다. 기존 테이블을 drop/rebuild하지 않는다. fresh initialization도 profile/current/appearance/equipped 네 row를 하나의 transaction에서 생성한다.

RoomCharacterRepository는 profile/current/reward/appearance/equipped flow를 결합한다. update 전에 모든 ID를 slot catalog로 검증하고 하나라도 invalid면 어떤 row도 변경하지 않는다. 인벤토리·소유권·stat modifier persistence는 추가하지 않는다.

## Acceptance Criteria

```powershell
.\gradlew.bat test --tests "com.todoquest.data.local.TodoQuestDatabaseMigrationTest" --tests "com.todoquest.data.repository.RoomCharacterRepositoryTest" --tests "com.todoquest.feature.character.CharacterViewModelTest"
Test-Path app\schemas\com.todoquest.data.local.TodoQuestDatabase\5.json
git diff --check
```

## 검증 절차

1. v4→v5 migration과 fresh DB가 같은 default loadout을 만드는지 확인한다.
2. valid mixed update, weapon equip/unequip, invalid ID no-partial-write와 flow emission을 확인한다.
3. task index의 step 9를 `completed`로 바꾸고 schema v5와 Repository API를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- UI에서 DAO를 호출하지 마라. 이유: 모든 변경은 CharacterRepository 경계를 통과해야 한다.
- 기존 profile/current table을 drop하지 마라. 이유: 사용자 성장·전투 원천 상태를 보존해야 한다.
- 장비 능력치나 소유권을 암묵적으로 구현하지 마라. 이유: 이번 범위는 외형 loadout persistence다.
- Android 도구가 없으면 설치하지 마라. 이유: AGENTS.md에 따라 blocked로 기록해야 한다.
- 기존 테스트를 깨뜨리지 마라.
