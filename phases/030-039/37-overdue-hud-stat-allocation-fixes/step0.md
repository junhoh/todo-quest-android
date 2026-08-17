# Step 0: backfill-overdue-failures-room-v8

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/app/src/main/java/com/todoquest/data/local/TodoQuestDatabase.kt`
- `/app/src/main/java/com/todoquest/data/local/FailureLogEntity.kt`
- `/app/src/main/java/com/todoquest/data/local/CombatEntities.kt`
- `/app/src/test/java/com/todoquest/data/local/TodoQuestDatabaseMigrationTest.kt`
- `/app/schemas/com.todoquest.data.local.TodoQuestDatabase/7.json`
- `/phases/030-039/37-overdue-hud-stat-allocation-fixes/index.json`

## 작업

Room migration 테스트를 먼저 작성해 v7의 자동 마감 실패 전투 이벤트가 `failure_logs`로 복구되지 않는 현재 동작을 실패로 확인한 뒤 database version을 8로 올리고 `MIGRATION_7_8`을 구현한다. table이나 column 구조는 변경하지 않는 data-only migration으로 유지하고 Room schema `8.json`을 export한다.

`MIGRATION_7_8`은 `monster_attack_events.trigger = 'MISSED_DEADLINE'`인 row 중 같은 `(taskId, occurrenceDateEpochDay)`의 `failure_logs`와 `completion_logs`가 모두 없는 row만 `failure_logs`로 삽입한다. `recurrenceSeriesId`는 event의 값을, `failedAtEpochMillis`는 `processedAtEpochMillis`를 사용한다. `APPLIED`와 `SKIPPED` 자동 마감 이벤트는 모두 실패 상태로 복구하지만 `MANUAL_FAILURE`, 이미 완료된 occurrence, 이미 실패 기록이 있는 occurrence는 건드리지 않는다. unique key 충돌 없이 반복 실행해도 결과가 같은 SQL을 사용한다.

v7 fixture에 자동 적용 이벤트, reconciliation cap으로 건너뛴 자동 이벤트, 수동 실패 이벤트, 완료 기록과 충돌하는 자동 이벤트, 기존 실패 기록을 각각 넣고 기대 backfill만 생기는지 검증한다. v1부터 v8까지 기존 migration chain이 일정·보상·캐릭터·장비·전투 데이터를 보존하는지도 유지한다. 이 step에서는 application builder나 reconciliation runtime 코드를 수정하지 않는다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.local.TodoQuestDatabaseMigrationTest" --console=plain
Test-Path 'app\schemas\com.todoquest.data.local.TodoQuestDatabase\8.json'
git diff --check
```

## 검증 절차

1. v7 fixture와 backfill 기대값 테스트를 production migration보다 먼저 작성하고 실패를 확인한다.
2. AC를 실행하고 schema 7과 8의 entity 구조가 동일하며 version과 identity hash만 정상 갱신됐는지 확인한다.
3. ARCHITECTURE, ADR, AGENTS.md의 데이터 보존·멱등성 규칙을 확인한다.
4. task index의 step 0을 `completed`로 바꾸고 migration과 backfill 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 기존 table을 drop하거나 destructive migration을 사용하지 마라. 이유: 일정·보상·캐릭터·장비·전투 원천 상태를 보존해야 한다.
- 완료 occurrence에 failure log를 만들지 마라. 이유: 한 occurrence가 완료와 실패를 동시에 가지면 상태 무결성이 깨진다.
- `MANUAL_FAILURE` event를 migration으로 다시 실패 처리하지 마라. 이유: 수동 실패 취소 뒤 event만 남아 있을 수 있다.
- 기존 테스트를 깨뜨리지 마라.
