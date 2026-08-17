# Step 2: persist-failure-state-room-v6

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/app/src/main/java/com/todoquest/data/local/TodoQuestDatabase.kt`
- `/app/src/main/java/com/todoquest/data/local/CompletionLogEntity.kt`
- `/app/src/main/java/com/todoquest/data/local/CompletionLogDao.kt`
- `/app/src/main/java/com/todoquest/data/local/CombatEntities.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomTaskRepository.kt`
- `/app/src/main/java/com/todoquest/domain/model/TaskOccurrence.kt`
- `/app/src/main/java/com/todoquest/domain/repository/TaskRepository.kt`
- `/app/src/test/java/com/todoquest/data/local/TodoQuestDatabaseMigrationTest.kt`
- `/app/src/test/java/com/todoquest/data/repository/RoomTaskRepositoryTest.kt`
- `/app/schemas/com.todoquest.data.local.TodoQuestDatabase/5.json`
- `/phases/030-039/33-calendar-combat-feedback/index.json`

## 작업

Room과 Task Repository 테스트를 먼저 작성한다. `failure_logs` table은 completion log와 같은 `(taskId, occurrenceDateEpochDay)` unique identity, 실패 시각과 recurrence 재귀속에 필요한 값을 저장한다. DAO는 범위 관찰, 단건 조회, insert-ignore, delete, 반복 일정 split 재귀속, monster event가 아직 없는 pending failure 조회를 제공한다.

database version을 6으로 올리고 `MIGRATION_5_6`에서 failure table과 index를 생성한다. `monster_attack_events`에는 기본값 `MISSED_DEADLINE`인 non-null trigger column을 `ALTER TABLE`로 추가한다. 기존 completion table을 재작성하거나 삭제하지 않는다. fresh v6 schema export를 생성하고 v1/v2/v3/v4/v5에서 v6까지의 migration test가 task, completion, reward, character, combat source state를 보존하는지 확인한다.

RoomTaskRepository는 tasks, completion logs, failure logs를 combine해 날짜별 `TaskOccurrenceStatus`를 만든다. `completeOccurrence`와 `failOccurrence`는 같은 Room transaction 안에서 반대 상태 존재 여부를 확인하고 conflict 시 어떤 reward, profile, outbox도 변경하지 않는다. 중복 fail은 `FailureResult`로 반환한다. `undoFailOccurrence`는 failure row만 삭제한다. 반복 일정 미래 split 시 completion/reward/player attack/monster attack과 함께 failure log도 새 task id로 재귀속한다.

완료 count는 `COMPLETED`만 포함하고 FAILED는 완료로 세지 않는다. database reopen 뒤 FAILED가 복원되는 repository test를 추가한다.

## Acceptance Criteria

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.todoquest.data.local.TodoQuestDatabaseMigrationTest" --tests "com.todoquest.data.repository.RoomTaskRepositoryTest" --console=plain
.\gradlew.bat :app:kaptDebugKotlin --console=plain
Test-Path app\schemas\com.todoquest.data.local.TodoQuestDatabase\6.json; if (-not $?) { throw 'Room v6 schema missing' }
git diff --check
```

## 검증 절차

1. migration과 Repository 테스트를 먼저 실패시킨 뒤 구현한다.
2. 기존 completion이 COMPLETED, 새 failure가 FAILED, 무기록 occurrence가 TODO인지 확인한다.
3. task index의 step 2를 `completed`로 바꾸고 Room v6와 repository 원자성 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 기존 `completion_logs`를 drop하거나 destructive migration을 사용하지 마라. 이유: 기존 완료 이력을 보존해야 하며 AGENTS.md가 파괴 작업을 금지한다.
- 실패 저장 transaction에서 HP를 직접 변경하지 마라. 이유: combat 계산과 영속은 CombatRepository가 소유한다.
- completed occurrence를 FAILED로 덮어쓰지 마라. 이유: 양쪽 상태의 상호 배제와 보상 무결성을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
