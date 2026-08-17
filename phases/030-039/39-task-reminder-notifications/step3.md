# Step 3: integrate-reminder-repositories

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/app/src/main/java/com/todoquest/domain/model/TodoTask.kt`
- `/app/src/main/java/com/todoquest/domain/repository/TaskRepository.kt`
- `/app/src/main/java/com/todoquest/domain/repository/ReminderRepository.kt`
- `/app/src/main/java/com/todoquest/data/local/TaskReminderEntity.kt`
- `/app/src/main/java/com/todoquest/data/local/TaskReminderDao.kt`
- `/app/src/main/java/com/todoquest/data/mapper/TodoTaskMapper.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomTaskRepository.kt`
- `/app/src/test/java/com/todoquest/data/repository/RoomTaskRepositoryTest.kt`
- `/phases/030-039/39-task-reminder-notifications/step1.md`
- `/phases/030-039/39-task-reminder-notifications/step2.md`
- `/phases/030-039/39-task-reminder-notifications/index.json`

## 작업

Repository test를 production 코드보다 먼저 작성해 task 저장과 reminder 설정의 원자성, 반복 분할, scheduler state 경계를 고정한다. 이 step은 `data/mapper`와 `data/repository` 구현에 한정하고 Android scheduler, UseCase, Compose를 수정하지 않는다.

`RoomTaskRepository.createTask(CreateTaskInput)`는 task insert·recurrenceSeriesId 갱신과 reminder 설정 저장을 하나의 `database.withTransaction`에서 처리한다. `NONE`을 포함해 새 task에는 명시적인 reminder row를 만들고 `NONE`은 `DISABLED`, 나머지는 `PENDING`으로 시작한다. task 저장이 rollback되면 reminder row도 없어야 한다.

`RoomTaskRepository.getTask`는 reminder row를 읽어 `TodoTask.reminderSetting`을 복원하고 legacy row 부재는 `ReminderSetting()`으로 해석한다. `updateTask(UpdateTaskInput)`는 일반 수정에서 같은 task reminder 설정을 갱신한다. 기존 예약 key가 있을 때는 coordinator가 cancel할 수 있도록 `scheduledOccurrenceEpochDay`와 `scheduledTriggerAtEpochMillis`를 config 갱신 직후까지 보존하고 status를 `PENDING` 또는 `DISABLED`로 바꾼다.

미래 반복 분할에서는 기존 segment의 endDate와 과거 completion/failure/reward/combat key를 현재 계약대로 보존한다. 새 task id에는 사용자가 편집한 reminder 설정과 새 `PENDING`/`DISABLED` row를 만들고 기존 segment reminder row 및 예약 key를 임의로 새 id로 이동하지 않는다. UseCase가 반환된 새 id와 기존 id를 각각 reconcile할 수 있어야 한다. soft delete는 reminder row를 즉시 지우지 않아 coordinator가 기존 PendingIntent를 취소할 수 있게 한다.

`RoomReminderRepository`를 구현한다. 최소 동작은 다음과 같다.

- task별 설정과 persisted schedule state 조회
- reminder row가 있는 모든 task id 조회(soft-deleted·ended row도 stale alarm cleanup 대상으로 포함)
- active task와 completion/failure source를 사용한 다음 TODO occurrence candidate 조회
- `(taskId, occurrenceDate)`가 여전히 active TODO이고 현재 persisted key와 일치하는지 발화 직전 검증
- schedule success, permission-required, delivered/no-future/error 상태의 conditional update와 key clear

다음 candidate는 step 1의 `ReminderPlanner`와 기존 `OccurrenceCalculator`를 재사용한다. 완료 또는 실패 occurrence는 건너뛰고 무한 반복 전체를 materialize하지 않는다. Repository는 `AlarmManager`, notification permission, `Context`를 참조하지 않는다. mapper는 알 수 없는 enum, custom minute 범위 밖 값, CUSTOM_TIME의 null을 조용히 NONE으로 대체하지 않고 명확히 실패시킨다.

테스트는 create NONE/custom/preset, transaction rollback, legacy no-row fallback, edit preset→none 및 custom, 반복 split 양쪽 row, soft delete state 보존, 완료·실패 occurrence 건너뛰기, current key conditional update, monthly next candidate를 포함한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.repository.RoomTaskRepositoryTest" --tests "com.todoquest.data.repository.RoomReminderRepositoryTest" --console=plain
git diff --check
```

## 검증 절차

1. Repository test를 먼저 추가하고 reminder persistence 구현 부재로 실패하는지 확인한다.
2. mapper와 Room repository 구현 후 AC를 실행한다.
3. task와 reminder config가 동일 transaction에서 commit/rollback되는지 확인한다.
4. Repository source에 `android.app.AlarmManager`, WorkManager, Compose import가 없는지 확인한다.
5. task index의 step 3을 `completed`로 바꾸고 원자 저장·반복 분할 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- task 저장 뒤 별도 비원자 transaction으로 reminder 설정을 기록하지 마라. 이유: 일정과 사용자가 선택한 설정이 어긋날 수 있다.
- 반복 분할 시 기존 segment reminder row를 새 task id로 이동하지 마라. 이유: 과거 segment와 예약 key의 소유권이 깨진다.
- soft delete와 동시에 reminder state를 제거하지 마라. 이유: 기존 PendingIntent를 식별해 취소해야 한다.
- Repository에서 AlarmManager나 notification permission을 호출하지 마라. 이유: platform scheduling은 전용 scheduler 뒤에 있어야 한다.
- 기존 테스트를 깨뜨리지 마라.
