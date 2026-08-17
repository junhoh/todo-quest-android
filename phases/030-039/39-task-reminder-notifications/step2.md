# Step 2: persist-reminders-room-v10

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/app/src/main/java/com/todoquest/data/local/TodoQuestDatabase.kt`
- `/app/src/main/java/com/todoquest/data/local/TodoTaskEntity.kt`
- `/app/src/main/java/com/todoquest/data/local/TodoTaskDao.kt`
- `/app/src/test/java/com/todoquest/data/local/TodoQuestDatabaseMigrationTest.kt`
- `/app/src/androidTest/java/com/todoquest/app/TodoQuestDatabaseIsolationTest.kt`
- `/app/schemas/com.todoquest.data.local.TodoQuestDatabase/9.json`
- `/phases/030-039/39-task-reminder-notifications/step1.md`
- `/phases/030-039/39-task-reminder-notifications/index.json`

## 작업

Room migration과 DAO test를 production code보다 먼저 작성하고 database를 v10으로 올린다. 이 step은 `data/local` entity·DAO·database migration·schema에 한정하며 production builder wiring, Repository, Android scheduler는 수정하지 않는다.

`task_reminders` table을 추가한다. 최소 column은 `taskId INTEGER PRIMARY KEY`, `mode TEXT NOT NULL`, nullable `customTimeMinuteOfDay INTEGER`, `scheduleStatus TEXT NOT NULL`, nullable `scheduledOccurrenceEpochDay INTEGER`, nullable `scheduledTriggerAtEpochMillis INTEGER`, `updatedAtEpochMillis INTEGER NOT NULL`이다. `taskId`는 `todo_tasks(id)`를 참조하고 실제 row 삭제 시 cascade하되 기존 soft delete 동작은 변경하지 않는다. configured reminder와 scheduler materialized state를 같은 row에 두며 mode/status 조회에 필요한 최소 index만 추가한다.

DAO는 task별 get/observe, upsert, schedule state compare-and-update, configured reminder 목록, state reset을 제공한다. 발생 key 갱신은 taskId·occurrenceDate를 함께 사용해 오래된 alarm callback이 새 예약 상태를 덮어쓰지 못하게 한다. enum 문자열과 minute-of-day 범위는 mapper/repository가 검증할 수 있도록 raw entity에 저장하되 DAO가 알 수 없는 값을 조용히 변환하지 않는다.

`MIGRATION_9_10`은 새 table/index 생성만 수행한다. 기존 todo task에 reminder row를 backfill하지 않으며 row 부재를 `NONE`으로 해석하는 것은 Repository 책임이다. v9의 모든 task, completion/failure, reward, character, combat, equipment row와 값을 그대로 보존한다. 기존 schema `1..9.json`을 수정하지 않고 `10.json`을 생성한다. v1부터 v10 전체 chain, v9→v10 direct migration과 재개방, FK/index/schema identity를 테스트한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.local.TodoQuestDatabaseMigrationTest" --tests "com.todoquest.data.local.TaskReminderDaoTest" --console=plain
Test-Path 'app\schemas\com.todoquest.data.local.TodoQuestDatabase\10.json'
git diff --check
```

## 검증 절차

1. v9 fixture migration과 DAO constraint test를 먼저 작성해 새 table 부재 실패를 확인한다.
2. entity·DAO·MIGRATION_9_10을 구현하고 AC를 실행한다.
3. 기존 v1..v9 schema JSON이 변경되지 않고 10.json만 추가됐는지 확인한다.
4. production Room builder에는 아직 MIGRATION_9_10을 연결하지 않았는지 확인한다.
5. task index의 step 2를 `completed`로 바꾸고 v10 table·migration 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 기존 task에 임의의 기본 알림 row를 backfill하지 마라. 이유: 기존 사용자가 요청하지 않은 알림이 생기면 안 된다.
- 기존 table을 drop하거나 destructive migration을 사용하지 마라. 이유: 일정·보상·전투·장비 상태를 보존해야 한다.
- Repository나 Android scheduler를 구현하지 마라. 이유: 이 step은 Room local 계층에 한정한다.
- 오래된 alarm key가 현재 schedule state를 무조건 덮어쓰게 하지 마라. 이유: 재등록 경합에서 상태가 역행할 수 있다.
- 기존 테스트를 깨뜨리지 마라.
