package com.todoquest.domain

import com.todoquest.core.AppClock
import com.todoquest.domain.model.CombatReconciliationResult
import com.todoquest.domain.model.CombatSnapshot
import com.todoquest.domain.model.CompletionResult
import com.todoquest.domain.model.CreateTaskInput
import com.todoquest.domain.model.FailureResult
import com.todoquest.domain.model.PlayerAttackResult
import com.todoquest.domain.model.RecurrenceRule
import com.todoquest.domain.model.ReminderDeliveryResult
import com.todoquest.domain.model.ReminderMode
import com.todoquest.domain.model.ReminderNotificationPayload
import com.todoquest.domain.model.ReminderOccurrenceKey
import com.todoquest.domain.model.ReminderPlan
import com.todoquest.domain.model.ReminderScheduleState
import com.todoquest.domain.model.ReminderScheduleStatus
import com.todoquest.domain.model.ReminderSetting
import com.todoquest.domain.model.TaskDifficulty
import com.todoquest.domain.model.TaskOccurrence
import com.todoquest.domain.model.TodoTask
import com.todoquest.domain.model.UpdateTaskInput
import com.todoquest.domain.model.MonsterAttackResult
import com.todoquest.domain.repository.CombatRepository
import com.todoquest.domain.repository.FirstLaunchNotificationPromptStore
import com.todoquest.domain.repository.ReminderCapability
import com.todoquest.domain.repository.ReminderCapabilityStatus
import com.todoquest.domain.repository.ReminderPublisher
import com.todoquest.domain.repository.ReminderRepository
import com.todoquest.domain.repository.ReminderScheduler
import com.todoquest.domain.repository.TaskRepository
import com.todoquest.domain.usecase.CombatProcessingDiagnosticSink
import com.todoquest.domain.usecase.CompleteOccurrenceUseCase
import com.todoquest.domain.usecase.CreateTaskUseCase
import com.todoquest.domain.usecase.DeleteTaskUseCase
import com.todoquest.domain.usecase.DeliverReminderUseCase
import com.todoquest.domain.usecase.FailOccurrenceUseCase
import com.todoquest.domain.usecase.PrepareFirstLaunchNotificationPromptUseCase
import com.todoquest.domain.usecase.ReconcileAllRemindersUseCase
import com.todoquest.domain.usecase.ReconcileTaskReminderUseCase
import com.todoquest.domain.usecase.ReminderDiagnosticOperation
import com.todoquest.domain.usecase.ReminderDiagnosticSink
import com.todoquest.domain.usecase.UndoCompleteOccurrenceUseCase
import com.todoquest.domain.usecase.UndoFailOccurrenceUseCase
import com.todoquest.domain.usecase.UpdateTaskUseCase
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ReminderUseCaseTest {
    private val occurrenceDate = LocalDate.of(2026, 7, 14)
    private val oldKey = ReminderOccurrenceKey(taskId = 7L, occurrenceDate = occurrenceDate)
    private val oldPlan = ReminderPlan(oldKey, Instant.parse("2026-07-14T09:00:00Z"))
    private val nextPlan = ReminderPlan(
        ReminderOccurrenceKey(taskId = 7L, occurrenceDate = occurrenceDate.plusDays(1)),
        Instant.parse("2026-07-15T09:00:00Z"),
    )

    @Test
    fun reconcileCancelsPersistedKeyBeforeCapabilitiesAndSchedulesNextPlan() = runTest {
        val repository = FakeReminderRepository().apply {
            putTask(task(taskId = 7L, recurrenceRule = RecurrenceRule.DAILY))
            putState(scheduledState(taskId = 7L, plan = oldPlan))
            enqueuePlan(taskId = 7L, nextPlan)
        }
        val scheduler = FakeReminderScheduler(
            onSchedule = { plan ->
                val stagedState = repository.states.getValue(7L)
                assertEquals(ReminderScheduleStatus.PENDING, stagedState.status)
                assertEquals(plan, stagedState.scheduledPlan)
            },
        )
        val useCase = reconcileUseCase(repository, scheduler)

        val status = useCase(7L)

        assertEquals(ReminderScheduleStatus.SCHEDULED, status)
        assertEquals(
            listOf(
                "cancel:$oldKey",
                "capability:${ReminderCapability.POST_NOTIFICATIONS}",
                "capability:${ReminderCapability.EXACT_ALARM}",
                "schedule:$nextPlan",
            ),
            scheduler.calls,
        )
        assertEquals(nextPlan, repository.states.getValue(7L).scheduledPlan)
        assertEquals(ReminderScheduleStatus.SCHEDULED, repository.states.getValue(7L).status)
        assertEquals(
            listOf(oldKey, null, nextPlan.key),
            repository.updateInputs.map { it.expectedCurrentKey },
        )
    }

    @Test
    fun reconcileRecordsNotificationCapabilityBeforeCheckingExactAlarm() = runTest {
        val repository = configuredRepository(taskId = 7L)
        val scheduler = FakeReminderScheduler(
            capabilityStatuses = mutableMapOf(
                ReminderCapability.POST_NOTIFICATIONS to ReminderCapabilityStatus.REQUIRED,
                ReminderCapability.EXACT_ALARM to ReminderCapabilityStatus.AVAILABLE,
            ),
        )

        val status = reconcileUseCase(repository, scheduler)(7L)

        assertEquals(ReminderScheduleStatus.POST_NOTIFICATIONS_REQUIRED, status)
        assertEquals(
            listOf("capability:${ReminderCapability.POST_NOTIFICATIONS}"),
            scheduler.calls,
        )
        assertEquals(status, repository.states.getValue(7L).status)
    }

    @Test
    fun channelDisabledIsPersistedSeparatelyWithoutCheckingExactAlarm() = runTest {
        val repository = configuredRepository(taskId = 7L)
        val scheduler = FakeReminderScheduler(
            capabilityStatuses = mutableMapOf(
                ReminderCapability.POST_NOTIFICATIONS to
                    ReminderCapabilityStatus.CHANNEL_DISABLED,
                ReminderCapability.EXACT_ALARM to ReminderCapabilityStatus.AVAILABLE,
            ),
        )

        val status = reconcileUseCase(repository, scheduler)(7L)

        assertEquals(ReminderScheduleStatus.NOTIFICATION_CHANNEL_DISABLED, status)
        assertEquals(
            listOf("capability:${ReminderCapability.POST_NOTIFICATIONS}"),
            scheduler.calls,
        )
        assertEquals(status, repository.states.getValue(7L).status)
    }

    @Test
    fun firstLaunchPromptsForChannelDisabledWithoutCheckingExactAlarm() = runTest {
        val store = FakeFirstLaunchNotificationPromptStore()
        val scheduler = FakeReminderScheduler(
            capabilityStatuses = mutableMapOf(
                ReminderCapability.POST_NOTIFICATIONS to
                    ReminderCapabilityStatus.CHANNEL_DISABLED,
                ReminderCapability.EXACT_ALARM to ReminderCapabilityStatus.REQUIRED,
            ),
        )

        val shouldPrompt = PrepareFirstLaunchNotificationPromptUseCase(store, scheduler)()

        assertTrue(shouldPrompt)
        assertEquals(
            listOf("capability:${ReminderCapability.POST_NOTIFICATIONS}"),
            scheduler.calls,
        )
    }

    @Test
    fun reconcileChecksExactAlarmAfterNotificationCapability() = runTest {
        val repository = configuredRepository(taskId = 7L)
        val scheduler = FakeReminderScheduler(
            capabilityStatuses = mutableMapOf(
                ReminderCapability.POST_NOTIFICATIONS to ReminderCapabilityStatus.AVAILABLE,
                ReminderCapability.EXACT_ALARM to ReminderCapabilityStatus.REQUIRED,
            ),
        )

        val status = reconcileUseCase(repository, scheduler)(7L)

        assertEquals(ReminderScheduleStatus.EXACT_ALARM_ACCESS_REQUIRED, status)
        assertEquals(
            listOf(
                "capability:${ReminderCapability.POST_NOTIFICATIONS}",
                "capability:${ReminderCapability.EXACT_ALARM}",
            ),
            scheduler.calls,
        )
        assertEquals(status, repository.states.getValue(7L).status)
    }

    @Test
    fun noneDeletedAndNoFutureClearOldKeysWithTypedStatuses() = runTest {
        val repository = FakeReminderRepository()
        val scheduler = FakeReminderScheduler()
        val noneTask = task(taskId = 1L, reminderSetting = ReminderSetting())
        val deletedTask = task(taskId = 2L)
        val noFutureTask = task(taskId = 3L)
        repository.putTask(noneTask)
        repository.putTask(noFutureTask)
        repository.putState(scheduledState(1L, planFor(1L)))
        repository.putState(scheduledState(2L, planFor(2L)))
        repository.putState(scheduledState(3L, planFor(3L)))
        repository.enqueuePlan(3L, null)
        val useCase = reconcileUseCase(repository, scheduler)

        assertEquals(ReminderScheduleStatus.DISABLED, useCase(1L))
        assertEquals(ReminderScheduleStatus.DISABLED, useCase(2L))
        assertEquals(ReminderScheduleStatus.NO_FUTURE_OCCURRENCE, useCase(3L))

        assertEquals(
            listOf(
                "cancel:${planFor(1L).key}",
                "cancel:${planFor(2L).key}",
                "cancel:${planFor(3L).key}",
                "capability:${ReminderCapability.POST_NOTIFICATIONS}",
                "capability:${ReminderCapability.EXACT_ALARM}",
            ),
            scheduler.calls,
        )
        assertTrue(repository.states.values.all { it.scheduledPlan == null })
    }

    @Test
    fun schedulerErrorIsDiagnosedAndDoesNotChangeSuccessfulCreateMutation() = runTest {
        val taskRepository = FakeTaskRepository(createdTaskId = 41L)
        val reminderRepository = configuredRepository(taskId = 41L)
        val schedulerFailure = IllegalStateException("alarm service unavailable")
        val scheduler = FakeReminderScheduler(scheduleFailure = schedulerFailure)
        val diagnostics = RecordingReminderDiagnostics()
        val reconcile = reconcileUseCase(reminderRepository, scheduler, diagnostics)
        val useCase = CreateTaskUseCase(taskRepository, reconcile)
        val input = createInput()

        val result = useCase(input)

        assertEquals(41L, result.taskId)
        assertEquals(ReminderScheduleStatus.ERROR, result.reminderStatus)
        assertEquals(listOf(input), taskRepository.createInputs)
        assertEquals(ReminderScheduleStatus.ERROR, reminderRepository.states.getValue(41L).status)
        assertEquals(
            listOf(
                RecordedReminderFailure(
                    taskId = 41L,
                    operation = ReminderDiagnosticOperation.SCHEDULE,
                    failure = schedulerFailure,
                ),
            ),
            diagnostics.failures,
        )
    }

    @Test
    fun schedulerCancellationPropagatesWithoutBecomingError() = runTest {
        val repository = configuredRepository(taskId = 7L)
        val cancellation = CancellationException("cancel reminder reconciliation")
        val diagnostics = RecordingReminderDiagnostics()
        val useCase = reconcileUseCase(
            repository = repository,
            scheduler = FakeReminderScheduler(scheduleFailure = cancellation),
            diagnostics = diagnostics,
        )

        try {
            useCase(7L)
            fail("Expected cancellation")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }

        assertEquals(emptyList<RecordedReminderFailure>(), diagnostics.failures)
        assertEquals(ReminderScheduleStatus.PENDING, repository.states.getValue(7L).status)
    }

    @Test
    fun typedSchedulerErrorIsRecordedWithoutPersistingAnUnconfirmedPlan() = runTest {
        val repository = configuredRepository(taskId = 7L)
        val diagnostics = RecordingReminderDiagnostics()
        val useCase = reconcileUseCase(
            repository = repository,
            scheduler = FakeReminderScheduler(
                scheduleResult = ReminderScheduleStatus.ERROR,
            ),
            diagnostics = diagnostics,
        )

        val status = useCase(7L)

        assertEquals(ReminderScheduleStatus.ERROR, status)
        assertEquals(ReminderScheduleStatus.ERROR, repository.states.getValue(7L).status)
        assertEquals(null, repository.states.getValue(7L).scheduledPlan)
        assertEquals(ReminderDiagnosticOperation.SCHEDULE, diagnostics.failures.single().operation)
    }

    @Test
    fun callbackClaimDuringSuccessfulSchedulePreservesDeliveredState() = runTest {
        val repository = configuredRepository(taskId = 7L)
        val scheduler = FakeReminderScheduler(
            onSchedule = { plan ->
                val staged = repository.states.getValue(7L)
                assertEquals(ReminderScheduleStatus.PENDING, staged.status)
                assertEquals(plan, staged.scheduledPlan)
                assertTrue(
                    repository.updateScheduleState(
                        state = staged.copy(
                            status = ReminderScheduleStatus.DELIVERED,
                            scheduledPlan = null,
                        ),
                        expectedCurrentKey = plan.key,
                    ),
                )
            },
        )

        val status = reconcileUseCase(repository, scheduler)(7L)

        assertEquals(ReminderScheduleStatus.DELIVERED, status)
        assertEquals(ReminderScheduleStatus.DELIVERED, repository.states.getValue(7L).status)
        assertEquals(null, repository.states.getValue(7L).scheduledPlan)
    }

    @Test
    fun callbackClaimDuringFailedScheduleIsNotOverwrittenByErrorCleanup() = runTest {
        val repository = configuredRepository(taskId = 7L)
        val scheduler = FakeReminderScheduler(
            scheduleResult = ReminderScheduleStatus.ERROR,
            onSchedule = { plan ->
                val staged = repository.states.getValue(7L)
                assertTrue(
                    repository.updateScheduleState(
                        state = staged.copy(
                            status = ReminderScheduleStatus.DELIVERED,
                            scheduledPlan = null,
                        ),
                        expectedCurrentKey = plan.key,
                    ),
                )
            },
        )

        val status = reconcileUseCase(repository, scheduler)(7L)

        assertEquals(ReminderScheduleStatus.DELIVERED, status)
        assertEquals(ReminderScheduleStatus.DELIVERED, repository.states.getValue(7L).status)
        assertEquals(null, repository.states.getValue(7L).scheduledPlan)
        assertTrue(scheduler.calls.contains("cancel:${planFor(7L).key}"))
    }

    @Test
    fun updateSplitReconcilesOldAndReturnedTaskIdsOnceAndDeleteReconcilesAfterMutation() = runTest {
        val operationLog = mutableListOf<String>()
        val taskRepository = FakeTaskRepository(
            updatedTaskId = 102L,
            operationLog = operationLog,
        )
        val reminderRepository = FakeReminderRepository(operationLog).apply {
            putTask(task(taskId = 101L, reminderSetting = ReminderSetting()))
            putTask(task(taskId = 102L, reminderSetting = ReminderSetting()))
            putState(disabledState(101L))
            putState(disabledState(102L))
        }
        val reconcile = reconcileUseCase(reminderRepository, FakeReminderScheduler())
        val updateUseCase = UpdateTaskUseCase(taskRepository, reconcile)
        val deleteUseCase = DeleteTaskUseCase(taskRepository, reconcile)
        val updateInput = updateInput(taskId = 101L)

        val updateResult = updateUseCase(updateInput)
        deleteUseCase(taskId = 102L, effectiveDate = occurrenceDate)

        assertEquals(102L, updateResult.taskId)
        assertEquals(ReminderScheduleStatus.DISABLED, updateResult.reminderStatus)
        assertEquals(listOf(updateInput), taskRepository.updateInputs)
        assertEquals(listOf(102L to occurrenceDate), taskRepository.deleteEffectiveInputs)
        assertEquals(listOf(101L, 102L, 102L), reminderRepository.scheduleStateReads)
        assertEquals(
            listOf(
                "update:101",
                "reconcile:101",
                "reconcile:102",
                "delete:102",
                "reconcile:102",
            ),
            operationLog,
        )
    }

    @Test
    fun updateWithoutSplitDoesNotReconcileSameTaskTwice() = runTest {
        val taskRepository = FakeTaskRepository(updatedTaskId = 101L)
        val reminderRepository = FakeReminderRepository().apply {
            putTask(task(taskId = 101L, reminderSetting = ReminderSetting()))
            putState(disabledState(101L))
        }

        UpdateTaskUseCase(
            taskRepository,
            reconcileUseCase(reminderRepository, FakeReminderScheduler()),
        )(updateInput(taskId = 101L))

        assertEquals(listOf(101L), reminderRepository.scheduleStateReads)
    }

    @Test
    fun reconcileAllVisitsEveryPersistedReminderTask() = runTest {
        val repository = FakeReminderRepository().apply {
            (1L..3L).forEach { taskId ->
                putTask(task(taskId, reminderSetting = ReminderSetting()))
                putState(disabledState(taskId))
            }
        }
        val useCase = ReconcileAllRemindersUseCase(
            repository,
            reconcileUseCase(repository, FakeReminderScheduler()),
        )

        useCase()

        assertEquals(listOf(1L, 2L, 3L), repository.scheduleStateReads)
    }

    @Test
    fun occurrenceCommandsReconcileAfterExistingCompletionFailureAndUndoFlows() = runTest {
        val taskRepository = FakeTaskRepository()
        val reminderRepository = FakeReminderRepository().apply {
            (11L..14L).forEach { taskId ->
                putTask(task(taskId, reminderSetting = ReminderSetting()))
                putState(disabledState(taskId))
            }
        }
        val reconcile = reconcileUseCase(reminderRepository, FakeReminderScheduler())
        val combatRepository = FakeCombatRepository()
        val combatDiagnostics = NoOpCombatDiagnostics

        CompleteOccurrenceUseCase(
            taskRepository,
            combatRepository,
            combatDiagnostics,
            reconcile,
        )(11L, occurrenceDate)
        FailOccurrenceUseCase(
            taskRepository,
            combatRepository,
            combatDiagnostics,
            reconcile,
        )(12L, occurrenceDate)
        UndoCompleteOccurrenceUseCase(taskRepository, reconcile)(13L, occurrenceDate)
        UndoFailOccurrenceUseCase(taskRepository, reconcile)(14L, occurrenceDate)

        assertEquals(listOf(11L, 12L, 13L, 14L), reminderRepository.scheduleStateReads)
        assertEquals(listOf(11L to occurrenceDate), combatRepository.playerAttackInputs)
        assertEquals(listOf(12L to occurrenceDate), combatRepository.monsterAttackInputs)
        assertEquals(listOf(13L to occurrenceDate), taskRepository.undoCompletionInputs)
        assertEquals(listOf(14L to occurrenceDate), taskRepository.undoFailureInputs)
    }

    @Test
    fun reminderSchedulerFailureDoesNotChangeSuccessfulOccurrenceResults() = runTest {
        val taskRepository = FakeTaskRepository()
        val reminderRepository = FakeReminderRepository().apply {
            (21L..22L).forEach { taskId ->
                putTask(task(taskId))
                putState(
                    ReminderScheduleState(
                        taskId = taskId,
                        setting = configuredReminder,
                        status = ReminderScheduleStatus.PENDING,
                    ),
                )
                enqueuePlan(taskId, planFor(taskId))
            }
        }
        val schedulerFailure = IllegalStateException("scheduler failed")
        val reconcile = reconcileUseCase(
            repository = reminderRepository,
            scheduler = FakeReminderScheduler(scheduleFailure = schedulerFailure),
        )
        val combatRepository = FakeCombatRepository()

        val completion = CompleteOccurrenceUseCase(
            taskRepository,
            combatRepository,
            NoOpCombatDiagnostics,
            reconcile,
        )(21L, occurrenceDate)
        val failure = FailOccurrenceUseCase(
            taskRepository,
            combatRepository,
            NoOpCombatDiagnostics,
            reconcile,
        )(22L, occurrenceDate)

        assertEquals(false, completion.alreadyRewarded)
        assertEquals(false, failure.wasAlreadyFailed)
        assertEquals(listOf(21L to occurrenceDate), combatRepository.playerAttackInputs)
        assertEquals(listOf(22L to occurrenceDate), combatRepository.monsterAttackInputs)
        assertEquals(ReminderScheduleStatus.ERROR, reminderRepository.states.getValue(21L).status)
        assertEquals(ReminderScheduleStatus.ERROR, reminderRepository.states.getValue(22L).status)
    }

    @Test
    fun staleCompletedOrDeletedDeliveryDoesNotPublishAndReconcilesSourceState() = runTest {
        val repository = FakeReminderRepository().apply {
            putTask(task(taskId = 7L, recurrenceRule = RecurrenceRule.DAILY))
            putState(scheduledState(taskId = 7L, plan = oldPlan))
            deliveryEligibility[oldKey] = false
            enqueuePlan(7L, nextPlan)
        }
        val publisher = FakeReminderPublisher()
        val useCase = deliveryUseCase(repository, FakeReminderScheduler(), publisher)

        val result = useCase(oldKey)

        assertFalse(result.published)
        assertEquals(ReminderScheduleStatus.SCHEDULED, result.reminderStatus)
        assertEquals(emptyList<ReminderNotificationPayload>(), publisher.payloads)
        assertEquals(nextPlan, repository.states.getValue(7L).scheduledPlan)
    }

    @Test
    fun oneOffDeliveryClaimsKeyBeforePublishingAndDuplicateCallbackDoesNotRepublish() = runTest {
        val task = task(taskId = 7L, recurrenceRule = RecurrenceRule.NONE)
        val repository = FakeReminderRepository().apply {
            putTask(task)
            putState(scheduledState(taskId = 7L, plan = oldPlan))
            deliveryEligibility[oldKey] = true
        }
        val publisher = FakeReminderPublisher()
        val useCase = deliveryUseCase(repository, FakeReminderScheduler(), publisher)

        val first = useCase(oldKey)
        val duplicate = useCase(oldKey)

        assertEquals(
            ReminderDeliveryResult(
                published = true,
                reminderStatus = ReminderScheduleStatus.DELIVERED,
            ),
            first,
        )
        assertEquals(
            ReminderDeliveryResult(
                published = false,
                reminderStatus = ReminderScheduleStatus.DELIVERED,
            ),
            duplicate,
        )
        assertEquals(
            listOf(
                ReminderNotificationPayload(
                    key = oldKey,
                    title = task.title,
                    memo = task.memo,
                    occurrenceDate = occurrenceDate,
                    taskTime = task.time,
                ),
            ),
            publisher.payloads,
        )
        assertEquals(ReminderScheduleStatus.DELIVERED, repository.states.getValue(7L).status)
        assertEquals(null, repository.states.getValue(7L).scheduledPlan)
    }

    @Test
    fun recurringDeliveryPublishesOnceThenImmediatelySchedulesNextOccurrence() = runTest {
        val repository = FakeReminderRepository().apply {
            putTask(task(taskId = 7L, recurrenceRule = RecurrenceRule.DAILY))
            putState(scheduledState(taskId = 7L, plan = oldPlan))
            deliveryEligibility[oldKey] = true
            enqueuePlan(7L, nextPlan)
        }
        val publisher = FakeReminderPublisher()
        val scheduler = FakeReminderScheduler()

        val result = deliveryUseCase(repository, scheduler, publisher)(oldKey)

        assertTrue(result.published)
        assertEquals(ReminderScheduleStatus.SCHEDULED, result.reminderStatus)
        assertEquals(1, publisher.payloads.size)
        assertEquals(nextPlan, repository.states.getValue(7L).scheduledPlan)
        assertEquals(
            listOf(
                "capability:${ReminderCapability.POST_NOTIFICATIONS}",
                "capability:${ReminderCapability.EXACT_ALARM}",
                "schedule:$nextPlan",
            ),
            scheduler.calls,
        )
    }

    @Test
    fun publisherFailureIsDiagnosedAndReturnsErrorWithoutRepublishing() = runTest {
        val repository = FakeReminderRepository().apply {
            putTask(task(taskId = 7L))
            putState(scheduledState(taskId = 7L, plan = oldPlan))
            deliveryEligibility[oldKey] = true
        }
        val failure = IllegalStateException("notification manager unavailable")
        val diagnostics = RecordingReminderDiagnostics()
        val publisher = FakeReminderPublisher(failure)
        val useCase = deliveryUseCase(
            repository = repository,
            scheduler = FakeReminderScheduler(),
            publisher = publisher,
            diagnostics = diagnostics,
        )

        val result = useCase(oldKey)
        val duplicate = useCase(oldKey)

        assertEquals(
            ReminderDeliveryResult(
                published = false,
                reminderStatus = ReminderScheduleStatus.ERROR,
            ),
            result,
        )
        assertFalse(duplicate.published)
        assertEquals(1, publisher.attempts)
        assertEquals(ReminderScheduleStatus.ERROR, repository.states.getValue(7L).status)
        assertEquals(
            listOf(
                RecordedReminderFailure(
                    taskId = 7L,
                    operation = ReminderDiagnosticOperation.PUBLISH,
                    failure = failure,
                ),
            ),
            diagnostics.failures,
        )
    }

    private fun configuredRepository(taskId: Long): FakeReminderRepository =
        FakeReminderRepository().apply {
            putTask(task(taskId = taskId))
            putState(
                ReminderScheduleState(
                    taskId = taskId,
                    setting = configuredReminder,
                    status = ReminderScheduleStatus.PENDING,
                ),
            )
            enqueuePlan(taskId, planFor(taskId))
        }

    private fun reconcileUseCase(
        repository: FakeReminderRepository,
        scheduler: FakeReminderScheduler,
        diagnostics: RecordingReminderDiagnostics = RecordingReminderDiagnostics(),
    ) = ReconcileTaskReminderUseCase(
        repository = repository,
        scheduler = scheduler,
        clock = FixedClock,
        diagnosticSink = diagnostics,
    )

    private fun deliveryUseCase(
        repository: FakeReminderRepository,
        scheduler: FakeReminderScheduler,
        publisher: FakeReminderPublisher,
        diagnostics: RecordingReminderDiagnostics = RecordingReminderDiagnostics(),
    ): DeliverReminderUseCase {
        val reconcile = reconcileUseCase(repository, scheduler, diagnostics)
        return DeliverReminderUseCase(
            repository = repository,
            publisher = publisher,
            reconcileTaskReminder = reconcile,
            diagnosticSink = diagnostics,
        )
    }

    private fun task(
        taskId: Long,
        recurrenceRule: RecurrenceRule = RecurrenceRule.NONE,
        reminderSetting: ReminderSetting = configuredReminder,
    ) = TodoTask(
        id = taskId,
        title = "물 마시기",
        memo = "한 컵",
        startDate = occurrenceDate,
        time = LocalTime.of(9, 30),
        difficulty = TaskDifficulty.EASY,
        category = "건강",
        recurrenceRule = recurrenceRule,
        reminderSetting = reminderSetting,
    )

    private fun createInput() = CreateTaskInput(
        title = "물 마시기",
        memo = "한 컵",
        startDate = occurrenceDate,
        time = LocalTime.of(9, 30),
        difficulty = TaskDifficulty.EASY,
        category = "건강",
        recurrenceRule = RecurrenceRule.NONE,
        reminderSetting = configuredReminder,
    )

    private fun updateInput(taskId: Long) = UpdateTaskInput(
        taskId = taskId,
        effectiveDate = occurrenceDate,
        title = "물 마시기",
        memo = "두 컵",
        time = LocalTime.of(10, 0),
        difficulty = TaskDifficulty.EASY,
        category = "건강",
        recurrenceRule = RecurrenceRule.DAILY,
        reminderSetting = configuredReminder,
    )

    private fun scheduledState(taskId: Long, plan: ReminderPlan) = ReminderScheduleState(
        taskId = taskId,
        setting = if (taskId == 1L) ReminderSetting() else configuredReminder,
        status = ReminderScheduleStatus.SCHEDULED,
        scheduledPlan = plan,
    )

    private fun disabledState(taskId: Long) = ReminderScheduleState(
        taskId = taskId,
        setting = ReminderSetting(),
        status = ReminderScheduleStatus.DISABLED,
    )

    private fun planFor(taskId: Long) = ReminderPlan(
        key = ReminderOccurrenceKey(taskId, occurrenceDate),
        triggerAt = Instant.parse("2026-07-14T09:00:00Z"),
    )

    private class FakeReminderRepository(
        private val operationLog: MutableList<String>? = null,
    ) : ReminderRepository {
        val tasks = mutableMapOf<Long, TodoTask>()
        val states = mutableMapOf<Long, ReminderScheduleState>()
        val updateInputs = mutableListOf<ScheduleUpdateInput>()
        val scheduleStateReads = mutableListOf<Long>()
        val deliveryEligibility = mutableMapOf<ReminderOccurrenceKey, Boolean>()
        private val plans = mutableMapOf<Long, MutableList<ReminderPlan?>>()

        fun putTask(task: TodoTask) {
            tasks[task.id] = task
        }

        fun putState(state: ReminderScheduleState) {
            states[state.taskId] = state
        }

        fun enqueuePlan(taskId: Long, plan: ReminderPlan?) {
            plans.getOrPut(taskId) { mutableListOf() }.add(plan)
        }

        override suspend fun getConfiguredTask(taskId: Long): TodoTask? = tasks[taskId]

        override suspend fun getConfiguredTaskIds(): List<Long> = states.keys.sorted()

        override suspend fun getScheduleState(taskId: Long): ReminderScheduleState? {
            scheduleStateReads += taskId
            operationLog?.add("reconcile:$taskId")
            return states[taskId]
        }

        override suspend fun updateScheduleState(
            state: ReminderScheduleState,
            expectedCurrentKey: ReminderOccurrenceKey?,
        ): Boolean {
            updateInputs += ScheduleUpdateInput(state, expectedCurrentKey)
            val current = states[state.taskId] ?: return false
            if (
                current.setting != state.setting ||
                current.scheduledPlan?.key != expectedCurrentKey
            ) {
                return false
            }
            states[state.taskId] = state
            return true
        }

        override suspend fun findNextDeliverablePlan(
            taskId: Long,
            now: Instant,
            zoneId: ZoneId,
        ): ReminderPlan? {
            val taskPlans = plans[taskId] ?: return null
            return if (taskPlans.isEmpty()) null else taskPlans.removeAt(0)
        }

        override suspend fun getActiveTodoTaskForDelivery(key: ReminderOccurrenceKey): TodoTask? {
            val state = states[key.taskId] ?: return null
            if (
                state.status != ReminderScheduleStatus.SCHEDULED ||
                state.scheduledPlan?.key != key ||
                deliveryEligibility[key] == false
            ) {
                return null
            }
            return tasks[key.taskId]
        }
    }

    private class FakeReminderScheduler(
        private val capabilityStatuses: MutableMap<ReminderCapability, ReminderCapabilityStatus> =
            mutableMapOf(
                ReminderCapability.POST_NOTIFICATIONS to ReminderCapabilityStatus.AVAILABLE,
                ReminderCapability.EXACT_ALARM to ReminderCapabilityStatus.AVAILABLE,
            ),
        private val scheduleResult: ReminderScheduleStatus = ReminderScheduleStatus.SCHEDULED,
        private val cancelResult: ReminderScheduleStatus = ReminderScheduleStatus.DISABLED,
        private val scheduleFailure: Throwable? = null,
        private val cancelFailure: Throwable? = null,
        private val onSchedule: (suspend (ReminderPlan) -> Unit)? = null,
    ) : ReminderScheduler {
        val calls = mutableListOf<String>()

        override suspend fun capabilityStatus(
            capability: ReminderCapability,
        ): ReminderCapabilityStatus {
            calls += "capability:$capability"
            return capabilityStatuses.getValue(capability)
        }

        override suspend fun scheduleExact(plan: ReminderPlan): ReminderScheduleStatus {
            calls += "schedule:$plan"
            onSchedule?.invoke(plan)
            scheduleFailure?.let { throw it }
            return scheduleResult
        }

        override suspend fun cancel(key: ReminderOccurrenceKey): ReminderScheduleStatus {
            calls += "cancel:$key"
            cancelFailure?.let { throw it }
            return cancelResult
        }
    }

    private class FakeFirstLaunchNotificationPromptStore : FirstLaunchNotificationPromptStore {
        private var firstCheck = true

        override fun consumeFirstLaunchCheck(): Boolean = firstCheck.also { firstCheck = false }
    }

    private class FakeReminderPublisher(
        private val failure: Throwable? = null,
    ) : ReminderPublisher {
        val payloads = mutableListOf<ReminderNotificationPayload>()
        var attempts = 0

        override suspend fun publish(payload: ReminderNotificationPayload) {
            attempts += 1
            failure?.let { throw it }
            payloads += payload
        }
    }

    private class FakeTaskRepository(
        private val createdTaskId: Long = 1L,
        private val updatedTaskId: Long = 1L,
        private val operationLog: MutableList<String>? = null,
    ) : TaskRepository {
        val createInputs = mutableListOf<CreateTaskInput>()
        val updateInputs = mutableListOf<UpdateTaskInput>()
        val deleteEffectiveInputs = mutableListOf<Pair<Long, LocalDate>>()
        val undoCompletionInputs = mutableListOf<Pair<Long, LocalDate>>()
        val undoFailureInputs = mutableListOf<Pair<Long, LocalDate>>()
        val mutationOrder = mutableListOf<String>()

        override fun observeOccurrences(
            rangeStart: LocalDate,
            rangeEnd: LocalDate,
        ): Flow<List<TaskOccurrence>> = emptyFlow()

        override suspend fun createTask(input: CreateTaskInput): Long {
            createInputs += input
            mutationOrder += "create:$createdTaskId"
            operationLog?.add("create:$createdTaskId")
            return createdTaskId
        }

        override suspend fun updateTask(task: TodoTask) = error("not used")

        override suspend fun updateTask(input: UpdateTaskInput): Long {
            updateInputs += input
            mutationOrder += "update:${input.taskId}"
            operationLog?.add("update:${input.taskId}")
            return updatedTaskId
        }

        override suspend fun deleteTask(taskId: Long) = error("not used")

        override suspend fun deleteTask(taskId: Long, effectiveDate: LocalDate) {
            deleteEffectiveInputs += taskId to effectiveDate
            mutationOrder += "delete:$taskId"
            operationLog?.add("delete:$taskId")
        }

        override suspend fun completeOccurrence(
            taskId: Long,
            occurrenceDate: LocalDate,
        ): CompletionResult = CompletionResult(
            awardedXp = 0,
            awardedGold = 0,
            alreadyRewarded = false,
        )

        override suspend fun undoCompleteOccurrence(taskId: Long, occurrenceDate: LocalDate) {
            undoCompletionInputs += taskId to occurrenceDate
        }

        override suspend fun failOccurrence(
            taskId: Long,
            occurrenceDate: LocalDate,
        ): FailureResult = FailureResult(wasAlreadyFailed = false)

        override suspend fun undoFailOccurrence(taskId: Long, occurrenceDate: LocalDate) {
            undoFailureInputs += taskId to occurrenceDate
        }
    }

    private class FakeCombatRepository : CombatRepository {
        val playerAttackInputs = mutableListOf<Pair<Long, LocalDate>>()
        val monsterAttackInputs = mutableListOf<Pair<Long, LocalDate>>()

        override val events = emptyFlow<com.todoquest.domain.model.CombatTransition>()

        override fun observeCombat(): Flow<CombatSnapshot> = emptyFlow()

        override suspend fun processPlayerAttack(
            taskId: Long,
            occurrenceDate: LocalDate,
        ): PlayerAttackResult {
            playerAttackInputs += taskId to occurrenceDate
            return PlayerAttackResult.NotFound
        }

        override suspend fun processPendingPlayerAttacks(): Int = 0

        override suspend fun processFailedOccurrenceAttack(
            taskId: Long,
            occurrenceDate: LocalDate,
        ): MonsterAttackResult {
            monsterAttackInputs += taskId to occurrenceDate
            return MonsterAttackResult.NotFound
        }

        override suspend fun processPendingFailureAttacks(): Int = 0

        override suspend fun reconcileOverdue(now: Instant): CombatReconciliationResult =
            CombatReconciliationResult(playerAttacksProcessed = 0)
    }

    private class RecordingReminderDiagnostics : ReminderDiagnosticSink {
        val failures = mutableListOf<RecordedReminderFailure>()

        override fun report(
            taskId: Long,
            operation: ReminderDiagnosticOperation,
            failure: Throwable,
        ) {
            failures += RecordedReminderFailure(taskId, operation, failure)
        }
    }

    private data class RecordedReminderFailure(
        val taskId: Long,
        val operation: ReminderDiagnosticOperation,
        val failure: Throwable,
    )

    private data class ScheduleUpdateInput(
        val state: ReminderScheduleState,
        val expectedCurrentKey: ReminderOccurrenceKey?,
    )

    private object NoOpCombatDiagnostics : CombatProcessingDiagnosticSink {
        override fun reportPlayerAttackFailure(
            taskId: Long,
            occurrenceDate: LocalDate,
            failure: Throwable,
        ) = Unit
    }

    private object FixedClock : AppClock {
        override val zoneId: ZoneId = ZoneId.of("UTC")

        override fun now(): Instant = Instant.parse("2026-07-14T08:00:00Z")

        override fun today(): LocalDate = LocalDate.of(2026, 7, 14)
    }

    private companion object {
        val configuredReminder = ReminderSetting(
            mode = ReminderMode.CUSTOM_TIME,
            customTime = LocalTime.of(9, 0),
        )
    }
}
