# Step 4: coordinate-reminder-lifecycle

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/app/src/main/java/com/todoquest/domain/repository/TaskRepository.kt`
- `/app/src/main/java/com/todoquest/domain/repository/ReminderRepository.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/CompleteOccurrenceUseCase.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/FailOccurrenceUseCase.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/UndoCompleteOccurrenceUseCase.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/UndoFailOccurrenceUseCase.kt`
- `/app/src/test/java/com/todoquest/domain/CombatUseCaseTest.kt`
- `/phases/030-039/39-task-reminder-notifications/step1.md`
- `/phases/030-039/39-task-reminder-notifications/step3.md`
- `/phases/030-039/39-task-reminder-notifications/index.json`

## 작업

fake Repository·scheduler·publisher를 사용하는 domain unit test를 production 코드보다 먼저 작성한다. 이 step은 `domain/usecase` orchestration에 한정하고 Android, Room 구현, Compose를 수정하지 않는다.

다음 use case와 typed 결과를 추가한다. 정확한 클래스명은 기존 naming과 일치시킬 수 있지만 UI가 Repository와 scheduler 구현을 직접 조정하지 않도록 같은 경계를 유지한다.

```kotlin
data class TaskMutationResult(
    val taskId: Long,
    val reminderStatus: ReminderScheduleStatus,
)

class ReconcileTaskReminderUseCase { suspend operator fun invoke(taskId: Long): ReminderScheduleStatus }
class ReconcileAllRemindersUseCase { suspend operator fun invoke() }
class DeliverReminderUseCase { suspend operator fun invoke(key: ReminderOccurrenceKey): ReminderDeliveryResult }
class CreateTaskUseCase { suspend operator fun invoke(input: CreateTaskInput): TaskMutationResult }
class UpdateTaskUseCase { suspend operator fun invoke(input: UpdateTaskInput): TaskMutationResult }
class DeleteTaskUseCase { suspend operator fun invoke(taskId: Long, effectiveDate: LocalDate) }
```

`ReconcileTaskReminderUseCase`는 persisted old key가 있으면 deterministic cancel을 먼저 수행한다. NONE/deleted/no future이면 key를 clear하고 상태를 기록한다. configured reminder이면 post-notification capability, exact-alarm capability 순서로 검사하고 부족한 capability status를 기록한다. 모두 허용되면 next plan을 exact schedule하고 `SCHEDULED`와 key/trigger를 저장한다. scheduler 예외는 coroutine cancellation만 다시 던지고 나머지는 `ERROR`와 diagnostic sink로 격리한다.

`CreateTaskUseCase`는 task transaction 성공 후 새 id를 reconcile한다. `UpdateTaskUseCase`는 input의 기존 task id와 Repository가 반환한 새/current id를 중복 없이 모두 reconcile한다. `DeleteTaskUseCase`는 soft delete 성공 후 기존 id를 reconcile해 alarm을 취소한다. scheduler 실패는 완료된 task mutation을 rollback하거나 `SaveFailed`로 바꾸지 않고 typed reminder warning만 반환한다.

기존 완료·실패·각 취소 use case는 성공한 occurrence의 task id를 reconcile한다. 완료·실패는 해당 occurrence 알림을 취소하고 다음 반복 occurrence를 예약한다. undo는 trigger가 아직 미래면 같은 occurrence를 다시 예약하고 이미 지났으면 다음 미래 occurrence로 넘어간다. 기존 combat best-effort, reward ledger, cancellation 전파와 occurrence 멱등성은 그대로 유지한다.

`DeliverReminderUseCase`는 발화 key가 persisted current key인지, task가 active인지, occurrence가 TODO인지 재검증한다. 유효할 때만 Android 비의존 `ReminderPublisher` contract로 title/memo/time/date payload를 전달하고 one-off는 `DELIVERED`, 반복은 즉시 next reconcile로 전환한다. stale·완료·실패·삭제 key는 게시하지 않고 reconcile한다. 같은 deterministic key의 중복 callback이 동일 알림을 중복 게시하지 않도록 repository conditional state를 사용한다.

테스트는 capability 두 종류, scheduler success/error/cancellation, create/update split/delete, complete/fail/undo, stale receiver, duplicate callback, one-off delivered, recurring next schedule, core command success 보존을 포함한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.ReminderUseCaseTest" --tests "com.todoquest.domain.CombatUseCaseTest" --console=plain
git diff --check
```

## 검증 절차

1. fake 기반 lifecycle test를 먼저 작성해 새 orchestration 부재 실패를 확인한다.
2. use case를 구현하고 기존 combat use case test와 함께 AC를 실행한다.
3. scheduler·publisher 실패가 task/occurrence 결과를 되돌리지 않는지 확인한다.
4. UI나 platform 구현이 아니라 use case가 mutation→reconcile 순서를 소유하는지 검토한다.
5. task index의 step 4를 `completed`로 바꾸고 lifecycle·실패 격리 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- scheduler 실패를 create/update/delete/complete/fail transaction 실패로 변환하지 마라. 이유: 알림과 핵심 일정 기능은 독립이다.
- 완료·실패 occurrence를 receiver payload만 믿고 게시하지 마라. 이유: alarm 예약 뒤 상태가 바뀔 수 있다.
- coroutine cancellation을 `ERROR`로 삼키지 마라. 이유: structured concurrency 취소 계약을 유지해야 한다.
- 기존 combat 처리 순서나 RewardLedger 멱등성을 변경하지 마라. 이유: 알림은 전투·경제 이벤트와 독립이다.
- 기존 테스트를 깨뜨리지 마라.
