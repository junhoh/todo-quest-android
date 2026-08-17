# Step 4: migrate-empty-fallback-and-seed-set

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/game-design/character-stats/modifiers-and-equipment.md`
- `/app/src/main/java/com/todoquest/data/local/TodoQuestDatabase.kt`
- `/app/src/main/java/com/todoquest/data/local/CharacterEquippedItemsEntity.kt`
- `/app/src/main/java/com/todoquest/data/local/EquipmentDao.kt`
- `/app/src/main/java/com/todoquest/data/local/EquipmentEntities.kt`
- `/app/src/main/java/com/todoquest/app/TodoQuestApplication.kt`
- `/app/src/main/res/values/strings.xml`
- `/app/src/test/java/com/todoquest/data/local/TodoQuestDatabaseMigrationTest.kt`
- `/app/src/test/java/com/todoquest/data/local/EquipmentDaoTest.kt`
- `/app/src/test/java/com/todoquest/data/repository/RoomEquipmentRepositoryTest.kt`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/step3.md`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/index.json`

## 작업

Room migration 및 catalog 테스트를 먼저 작성하고, 기존 저장 데이터의 appearance fallback을 빈 장비 외형으로 전환하면서 현재 gameplay 소유·장착 상태는 보존한다.

Room schema version을 15로 올리고 `MIGRATION_14_15`를 production migration 목록에 등록한다. migration은 모든 `character_equipped_items` row의 fallback만 `headId = NULL`, `topId = 'top_default'`, `bottomId = 'bottom_default'`, `shoesId = 'shoes_default'`, `accessoryId = NULL`, `weaponId = NULL`, `glovesId = NULL`로 갱신한다. `owned_equipment`, `character_equipment`, character HP/status, 일정·occurrence·보상·combat table은 변경하지 않는다. repository가 실제 `character_equipment`를 fallback 위에 투영하므로 기존 사용자가 소유하고 실제 장착한 장비는 그대로 보여야 한다.

`EquipmentCatalogSeeder`에 다음 UNCOMMON, required level 5인 7개 판매 상품을 고정 ID로 추가한다. 모든 item은 rarity 규칙에 맞게 정확히 modifier 두 개를 가진다.

| ID | key / 표시 이름 | slot / weapon | 가격 | modifier |
|---|---|---|---:|---|
| 1019 | `adventure_sword` / 모험가의 검 | WEAPON / LONGSWORD | 150 | ATTACK FLAT +5, STRENGTH FLAT +1 |
| 1020 | `adventure_hat` / 모험가의 모자 | HELMET | 100 | FOCUS FLAT +1, STATUS_RESISTANCE FLAT +150bp |
| 1021 | `adventure_jacket` / 모험가의 재킷 | CHEST | 120 | VITALITY FLAT +1, DEFENSE FLAT +2 |
| 1022 | `adventure_pants` / 모험가의 바지 | LEGS | 110 | WILLPOWER FLAT +1, DEFENSE FLAT +2 |
| 1023 | `adventure_gloves` / 모험가의 장갑 | GLOVES | 125 | STRENGTH FLAT +1, CRITICAL_CHANCE FLAT +150bp |
| 1024 | `adventure_shoes` / 모험가의 신발 | SHOES | 130 | FOCUS FLAT +1, DEFENSE FLAT +2 |
| 1025 | `adventure_accessory` / 모험가의 장식 | ACCESSORY | 160 | WILLPOWER FLAT +1, GOLD_GAIN_BONUS FLAT +150bp |

visual key는 각각 기존 adventure layer를 사용하고 검은 `WEAPON_DEFAULT_SWORD`, 장갑은 `GLOVES_ADVENTURE`를 사용한다. 한국어 이름·설명은 `strings.xml`에 추가하고 기존 key 기반 문자열 resolver가 찾을 수 있는 naming convention을 따른다. `legacyPrices`, canonical update, visual metadata seed에도 새 ID를 빠짐없이 포함한다.

migration test는 v14 fixture에 fallback adventure 복장, 실제 소유/장착 장비, gold/HP/status/task를 넣은 뒤 v15에서 fallback만 비워지고 나머지 row가 동일함을 검증한다. catalog test는 ID 유일성, 7개 상품, 가격/레벨/rarity/modifier/layer를 검증한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.local.TodoQuestDatabaseMigrationTest" --tests "com.todoquest.data.local.EquipmentDaoTest" --tests "com.todoquest.data.repository.RoomEquipmentRepositoryTest" --console=plain
.\gradlew.bat :app:assembleDebug --console=plain
git diff --check
```

## 검증 절차

1. v14→v15 보존 테스트와 신규 catalog 실패 테스트를 먼저 작성한다.
2. migration 후 실제 `character_equipment` 및 `owned_equipment` row가 변경되지 않았는지 확인한다.
3. 신규 7개 item이 중복 없이 seed되고 `EquipmentModifierValidator`를 통과하는지 확인한다.
4. clean install과 migrated install 모두 default fallback이 빈 slot 외형인지 확인한다.
5. 성공 시 task index의 step 4를 `completed`로 바꾸고 migration 보존 범위와 신규 catalog를 한국어 `summary` 두 줄로 기록한다.

## 금지사항

- `DROP TABLE` 또는 destructive migration을 사용하지 마라. 이유: 기존 사용자 데이터 보존이 필수다.
- `owned_equipment`나 `character_equipment`를 초기화하지 마라. 이유: 사용자가 획득·장착한 gameplay 장비는 유지해야 한다.
- 신규 모험가 세트를 기존 1001~1018 ID에 덮어쓰지 마라. 이유: 저장된 ownership의 item 의미가 바뀐다.
- 모험가 세트를 자동 소유 또는 자동 장착시키지 마라. 이유: 기존 기본 외형을 구매 가능한 신규 상품으로 전환하는 요구다.
- 표시용 한국어 문자열을 Kotlin에 하드코딩하지 마라. 이유: 사용자 노출 문구는 문자열 리소스를 사용해야 한다.
- 기존 테스트를 삭제하거나 완화하지 마라.
