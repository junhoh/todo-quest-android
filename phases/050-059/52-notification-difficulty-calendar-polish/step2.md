# Step 2: persist-difficulty-snapshot-room-v14

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/app/src/main/java/com/todoquest/data/local/CombatEntities.kt`
- `/app/src/main/java/com/todoquest/data/local/CombatDao.kt`
- `/app/src/main/java/com/todoquest/data/local/TaskReminderDao.kt`
- `/app/src/main/java/com/todoquest/data/local/TodoQuestDatabase.kt`
- `/app/src/test/java/com/todoquest/data/local/TodoQuestDatabaseMigrationTest.kt`
- `/app/src/test/java/com/todoquest/data/local/CombatDaoTest.kt`
- `/app/src/test/java/com/todoquest/data/local/TaskReminderDaoTest.kt`
- `/app/schemas/com.todoquest.data.local.TodoQuestDatabase/13.json`
- `/phases/050-059/52-notification-difficulty-calendar-polish/step1.md`
- `/phases/050-059/52-notification-difficulty-calendar-polish/index.json`

## 작업

Room migration·DAO 테스트를 먼저 작성하고 `data/local`과 schema만 수정한다. database version을 14로 올리고 `MIGRATION_13_14`를 추가한다.

`player_attack_events`에 다음 additive column을 추가한다.

~~~text
sourceTaskDifficulty TEXT NULL
taskDifficultyBalanceVersion INTEGER NOT NULL DEFAULT 0
~~~

`PlayerAttackEventEntity`에도 source-compatible 기본값을 반영한다. 기존 v13 row는 PENDING/APPLIED 여부와 무관하게 nullable difficulty와 version `0`을 유지한다. migration에서 현재 `todo_tasks.difficulty`를 join하거나 backfill하지 않는다. 기존 combat reward version, source attack, result/reward snapshot, 일정·완료·실패·보상·캐릭터·장비·알림·상태이상 row를 보존한다.

`TaskReminderDao`에 전체 row를 taskId 순으로 관찰하는 `Flow<List<TaskReminderEntity>>` query를 추가한다. 이 query는 mode나 schedule status로 row를 누락하지 않아야 하며 schema 변경을 추가로 만들지 않는다.

테스트는 v13→v14 direct와 v1→v14 full chain, 기존 PENDING/APPLIED row의 null/0 기본값, fresh current schema, 새 entity round-trip, reminder Flow의 insert/update emission과 deterministic order를 포함한다. 기존 schema `1..13.json`은 수정하지 않고 `14.json`만 생성한다. production Room builder 연결은 후속 Android wiring step에서 수행한다.

## Acceptance Criteria

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.local.TodoQuestDatabaseMigrationTest" --tests "com.todoquest.data.local.CombatDaoTest" --tests "com.todoquest.data.local.TaskReminderDaoTest" --console=plain
if (-not (Test-Path 'app\schemas\com.todoquest.data.local.TodoQuestDatabase\14.json')) { exit 1 }
git diff --check
~~~

## 검증 절차

1. v13 fixture와 DAO Flow test를 먼저 추가해 새 column/query 부재 실패를 확인한다.
2. entity·DAO·MIGRATION_13_14를 구현하고 schema 14를 생성한다.
3. 기존 schema JSON이 변경되지 않았는지 확인하고 AC를 실행한다.
4. step 2를 `completed`와 한국어 summary로 갱신한다.

## 금지사항

- 기존 attack row에 현재 task 난이도를 backfill하지 마라. 이유: task가 completion 뒤 편집됐을 수 있어 역사적 source를 복원할 수 없다.
- table을 drop/recreate하거나 destructive migration을 사용하지 마라. 이유: 모든 로컬 원천 상태를 보존해야 한다.
- Repository, Android scheduler 또는 Compose를 수정하지 마라. 이유: 이 step은 Room local 계층에 한정한다.
- 기존 schema `1..13.json`을 수정하지 마라. 이유: migration identity 기록은 불변이다.
- 기존 테스트를 깨뜨리지 마라.
