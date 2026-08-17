# Step 1: task-edit-room-storage

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/DEVELOPMENT.md`
- `/phases/000-009/7-task-entry-editing/index.json`
- `/phases/000-009/7-task-entry-editing/step0.md`
- `/app/src/main/java/com/todoquest/domain/model/TodoTask.kt`
- `/app/src/main/java/com/todoquest/domain/model/TaskCategory.kt`
- `/app/src/main/java/com/todoquest/data/local/TodoTaskEntity.kt`
- `/app/src/main/java/com/todoquest/data/local/TodoTaskDao.kt`
- `/app/src/main/java/com/todoquest/data/local/TodoQuestDatabase.kt`
- `/app/src/main/java/com/todoquest/data/local/CompletionLogDao.kt`
- `/app/src/main/java/com/todoquest/data/local/RewardLedgerDao.kt`
- `/app/src/main/java/com/todoquest/data/mapper/TodoTaskMapper.kt`
- `/app/src/main/java/com/todoquest/app/TodoQuestApp.kt`
- `/app/src/test/java/com/todoquest/data/repository/RoomTaskRepositoryTest.kt`
- `/app/schemas/com.todoquest.data.local.TodoQuestDatabase/1.json`

## 작업

테스트를 먼저 작성한 뒤 반복 원본 종료일과 미래 완료/보상 기록 재연결에 필요한 Room 저장소 계약을 추가한다. Repository의 수정/삭제 비즈니스 로직은 이 step에서 구현하지 않는다.

Room schema를 version 2로 올린다.

- `todo_tasks.endDateEpochDay INTEGER` nullable column을 추가한다.
- `TodoTaskEntity`에 `endDateEpochDay: Long?`를 추가한다.
- `TodoTaskMapper.toDomain`, `fromInput`, `fromDomain`이 `TodoTask.endDate`를 매핑하게 한다.
- `CreateTaskInput`으로 만든 새 일정의 `endDate`는 항상 `null`이다.
- `TaskCategory.normalize()`를 저장 매핑에서 사용해 빈 값과 프리셋 외 값을 `일반`로 저장한다.

`TodoQuestDatabase`에 명시적인 migration을 추가한다.

- `version = 2`로 변경한다.
- `MIGRATION_1_2`는 `ALTER TABLE todo_tasks ADD COLUMN endDateEpochDay INTEGER`만 수행한다.
- destructive migration이나 schema 삭제를 사용하지 않는다.
- `TodoQuestApp`의 `Room.databaseBuilder`에 migration을 등록한다.
- schema export 결과로 `/app/schemas/com.todoquest.data.local.TodoQuestDatabase/2.json`이 생성되어야 한다.

DAO에는 후속 Repository step에서 사용할 최소 쿼리를 추가한다.

- `CompletionLogDao`:
  - `suspend fun findFrom(taskId: Long, fromOccurrenceDateEpochDay: Long): List<CompletionLogEntity>`
  - `suspend fun reassignFrom(taskId: Long, fromOccurrenceDateEpochDay: Long, newTaskId: Long)`
- `RewardLedgerDao`:
  - `suspend fun findFrom(taskId: Long, fromOccurrenceDateEpochDay: Long): List<RewardLedgerEntity>`
  - `suspend fun reassignFrom(taskId: Long, fromOccurrenceDateEpochDay: Long, newTaskId: Long)`

Room/Robolectric 테스트를 추가하거나 확장한다.

- `endDate`가 저장 후 domain model로 복원된다.
- 기존 v1 schema에서 v2 migration이 성공한다.
- 기존 영어 카테고리 `General`, `Work`, `Personal`은 각각 `일반`, `업무`, `개인`으로 normalize된다. 나머지 미지정 값은 `일반`로 normalize한다.

## Acceptance Criteria

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
git diff --check
```

## 검증 절차

1. Acceptance Criteria 명령을 실행한다.
2. `/app/schemas/com.todoquest.data.local.TodoQuestDatabase/2.json`이 생성되었는지 확인한다.
3. `fallbackToDestructiveMigration`이 추가되지 않았는지 확인한다.
4. `/phases/000-009/7-task-entry-editing/index.json`의 step 1 상태와 결과 필드를 업데이트한다.

## 금지사항

- destructive migration을 사용하지 마라. 이유: 사용자의 기존 로컬 할일 데이터를 삭제할 수 있다.
- completion log나 reward ledger 데이터를 migration에서 삭제하지 마라. 이유: 완료/보상 이력은 데이터 무결성의 기준이다.
- Repository 수정/삭제 split 로직을 구현하지 마라. 이유: 이 step은 Room 저장 계약과 migration만 담당한다.
- UI 파일을 수정하지 마라. 이유: 저장소 변경과 화면 변경 범위를 분리해야 한다.
- 기존 테스트를 깨뜨리지 마라.
