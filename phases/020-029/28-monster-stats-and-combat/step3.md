# Step 3: Room v4 전투 저장소 마이그레이션

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/monster-stats-and-growth.md`
- `/app/src/main/java/com/todoquest/domain/model/MonsterStats.kt`
- `/app/src/main/java/com/todoquest/data/local/TodoQuestDatabase.kt`
- `/app/src/main/java/com/todoquest/data/local/RewardLedgerEntity.kt`
- `/app/src/main/java/com/todoquest/data/local/CharacterCurrentStateEntity.kt`
- `/app/src/test/java/com/todoquest/data/local/TodoQuestDatabaseMigrationTest.kt`
- `/app/schemas/com.todoquest.data.local.TodoQuestDatabase/3.json`
- `/phases/020-029/28-monster-stats-and-combat/index.json`

## 작업

CRITICAL 테스트 우선으로 Room v4 migration/DAO 테스트를 먼저 작성한다. 도메인 모델의 실제 파일명이 다르면 생성된 경로를 찾아 사용한다.

`TodoQuestDatabase`를 version 4로 올리고 `MIGRATION_3_4`에서 기존 테이블이나 사용자 행을 수정하지 않은 채 다음 신규 테이블과 필요한 index를 만든다.

1. `monster_instances`
   - `id: Long` auto primary key
   - `definitionId: String`, `grade: String`
   - `stageNumber: Int`, `encounterNumber: Int`, `level: Int`
   - `currentHp: Int`, `balanceVersion: Int`
   - `(stageNumber, encounterNumber)` unique index

2. `combat_progress`
   - singleton `id = 1`
   - `stageNumber`, `stageLevel`, `activeMonsterInstanceId`
   - `lastReconciledAtEpochMillis`, `balanceVersion`
   - migration에서는 행을 만들지 않는다. 첫 runtime 초기화가 주입된 clock의 현재 시각을 cursor로 저장해 과거 피해를 소급하지 않는다.

3. `player_attack_events`
   - `(taskId, occurrenceDateEpochDay)` composite primary key와 `recurrenceSeriesId`
   - 생성 시 snapshot: status, source level/attack/critical chance/critical damage/MOMENTUM, character·monster balance version, created time
   - 처리 결과 nullable fields: target instance, seed, roll, critical 여부, raw damage, target defense, final damage, target HP 전후, processed time
   - pending 조회 index

4. `monster_attack_events`
   - `(taskId, occurrenceDateEpochDay)` composite primary key와 `recurrenceSeriesId`
   - status와 skip reason, source monster·level·raw damage, player defense/max HP, final damage, HP 전후, `wasLethal`, revived HP, balance versions, processed time

entity의 status/grade는 안정적인 enum `name` 문자열로 저장하고 mapper 경계에서 알 수 없는 값을 거부한다. 파생 `MonsterStats`와 `isDefeated` column은 만들지 않는다. 필요한 DAO에는 observe/get/insert-ignore/pending/update-result/reassign와 transaction에서 사용할 명확한 query를 제공한다. UI가 DAO를 직접 볼 수 없게 한다.

Room schema export `4.json`을 생성한다. migration test는 v1→2→3→4, v2→3→4, v3→4를 검증하고 기존 일정, completion, reward, 캐릭터 원천 상태가 보존되며 신규 combat table이 비어 있고 기존 profile/monster instance에 파생 column이 없는지 확인한다. DAO test는 중복 event key, pending 상태 전이, 인스턴스 unique stage slot, task id 재귀속을 검증한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.local.TodoQuestDatabaseMigrationTest" --tests "com.todoquest.data.local.Combat*DaoTest"
if (-not (Test-Path -LiteralPath 'app\schemas\com.todoquest.data.local.TodoQuestDatabase\4.json')) { throw 'Room v4 schema export missing' }
git diff --check
```

## 검증 절차

1. migration/DAO 테스트를 실행한다.
2. v4 schema가 파생 능력치·`isDefeated`를 저장하지 않는지 확인한다.
3. phase index의 step 3을 완료 처리하고 테이블·migration 경계를 한국어 `summary`로 기록한다.

## 금지사항

- 기존 v1/v2/v3 schema JSON을 수정하지 마라. 이유: migration 기준 artifact다.
- migration 시 combat progress나 과거 실패 event를 생성하지 마라. 이유: 기존 사용자에게 피해를 소급하면 안 된다.
- DAO를 UI에 노출하지 마라. 이유: AGENTS 레이어 규칙을 위반한다.
- 기존 테스트를 깨뜨리지 마라.
