# Step 2: task-edit-repository

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/000-009/7-task-entry-editing/index.json`
- `/phases/000-009/7-task-entry-editing/step0.md`
- `/phases/000-009/7-task-entry-editing/step1.md`
- `/app/src/main/java/com/todoquest/domain/model/TodoTask.kt`
- `/app/src/main/java/com/todoquest/domain/model/UpdateTaskInput.kt`
- `/app/src/main/java/com/todoquest/domain/model/TaskCategory.kt`
- `/app/src/main/java/com/todoquest/domain/repository/TaskRepository.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomTaskRepository.kt`
- `/app/src/main/java/com/todoquest/data/local/TodoTaskDao.kt`
- `/app/src/main/java/com/todoquest/data/local/CompletionLogDao.kt`
- `/app/src/main/java/com/todoquest/data/local/RewardLedgerDao.kt`
- `/app/src/test/java/com/todoquest/data/repository/RoomTaskRepositoryTest.kt`

## 작업

테스트를 먼저 작성한 뒤 기존 Repository 계약을 수정/삭제 UI가 사용할 수 있게 확장한다. UI와 ViewModel은 이 step에서 수정하지 않는다.

`TaskRepository`에 다음 메서드를 추가한다. 기존 호출부 빌드를 위해 기존 `updateTask(task: TodoTask)`와 `deleteTask(taskId: Long)`는 남기되, 새 UI는 아래 메서드만 사용하게 한다.

```kotlin
suspend fun getTask(taskId: Long): TodoTask?

suspend fun updateTask(input: UpdateTaskInput): Long

suspend fun deleteTask(taskId: Long, effectiveDate: LocalDate)
```

`RoomTaskRepository` 구현 규칙은 다음과 같다.

- `getTask()`는 active task만 반환한다.
- `updateTask(input)`은 `input.title.trim()`이 비어 있으면 실패한다.
- `input.category`는 `TaskCategory.normalize()`로 저장한다.
- `effectiveDate`가 기존 task의 occurrence가 아니면 실패한다.
- 비반복 일정 또는 `effectiveDate <= existing.startDate`인 경우 기존 task row를 직접 update하고 기존 task id를 반환한다.
- 반복 일정이고 `effectiveDate > existing.startDate`인 경우 하나의 Room transaction에서 처리한다.
  - 기존 task의 `endDate`를 `effectiveDate.minusDays(1)`로 update한다.
  - `effectiveDate`부터 시작하는 새 task를 insert한다.
  - 새 task는 input의 title, memo, time, difficulty, category, recurrenceRule을 가진다.
  - `effectiveDate` 이후 completion/reward ledger는 새 task id로 reassign한다.
  - 이미 지급된 reward ledger가 있던 occurrence는 새 task id에서도 중복 보상이 발생하지 않아야 한다.
- `deleteTask(taskId, effectiveDate)`는 active task만 대상으로 한다.
  - 비반복 일정 또는 `effectiveDate <= existing.startDate`이면 기존 soft delete를 사용한다.
  - 반복 일정이고 `effectiveDate > existing.startDate`이면 기존 task의 `endDate`를 `effectiveDate.minusDays(1)`로 update한다.
  - 삭제는 completion/reward ledger와 character profile을 삭제하거나 회수하지 않는다.

`RoomTaskRepositoryTest`에 다음 테스트를 추가한다.

- 비반복 일정 수정은 같은 task id를 유지하고 occurrence 내용이 바뀐다.
- 반복 일정 미래분 수정은 기존 task가 effective date 전날까지만 발생하고 새 task가 effective date부터 발생한다.
- 반복 일정 미래분 수정 후 이미 완료/보상된 future occurrence를 다시 완료해도 XP와 gold가 중복 지급되지 않는다.
- 반복 일정 미래분 삭제는 effective date 이전 occurrence만 남긴다.
- 삭제는 기존 completion/reward ledger나 character profile을 회수하지 않는다.

## Acceptance Criteria

```powershell
.\gradlew.bat test
git diff --check
```

## 검증 절차

1. Acceptance Criteria 명령을 실행한다.
2. Repository가 Room transaction 경계 안에서 반복 split과 ledger reassign을 처리하는지 확인한다.
3. UI, ViewModel, Compose 파일을 수정하지 않았는지 확인한다.
4. `/phases/000-009/7-task-entry-editing/index.json`의 step 2 상태와 결과 필드를 업데이트한다.

## 금지사항

- UI 레이어에서 사용할 임시 DAO 접근 API를 만들지 마라. 이유: UI는 Repository 또는 UseCase를 통해서만 데이터 변경을 수행해야 한다.
- reward ledger를 삭제하거나 character profile 보상을 회수하지 마라. 이유: MVP 완료 취소 정책과 보상 이력 정책을 혼동한다.
- 반복 원본 전체를 무조건 덮어쓰지 마라. 이유: 사용자가 선택한 날짜부터 미래분만 수정하기로 결정했다.
- Google Calendar 연동을 추가하지 마라. 이유: MVP 제외 사항이다.
- 기존 테스트를 깨뜨리지 마라.
