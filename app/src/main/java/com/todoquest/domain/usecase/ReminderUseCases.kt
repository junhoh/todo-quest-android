package com.todoquest.domain.usecase

import com.todoquest.core.AppClock
import com.todoquest.domain.model.CreateTaskInput
import com.todoquest.domain.model.RecurrenceRule
import com.todoquest.domain.model.ReminderDeliveryResult
import com.todoquest.domain.model.ReminderMode
import com.todoquest.domain.model.ReminderNotificationPayload
import com.todoquest.domain.model.ReminderOccurrenceKey
import com.todoquest.domain.model.ReminderPlan
import com.todoquest.domain.model.ReminderScheduleState
import com.todoquest.domain.model.ReminderScheduleStatus
import com.todoquest.domain.model.TaskMutationResult
import com.todoquest.domain.model.UpdateTaskInput
import com.todoquest.domain.repository.FirstLaunchNotificationPromptStore
import com.todoquest.domain.repository.ReminderCapability
import com.todoquest.domain.repository.ReminderCapabilityStatus
import com.todoquest.domain.repository.ReminderPublisher
import com.todoquest.domain.repository.ReminderRepository
import com.todoquest.domain.repository.ReminderScheduler
import com.todoquest.domain.repository.TaskRepository
import java.time.LocalDate
import kotlinx.coroutines.CancellationException

class PrepareFirstLaunchNotificationPromptUseCase(
    private val store: FirstLaunchNotificationPromptStore,
    private val scheduler: ReminderScheduler,
) {
    suspend operator fun invoke(): Boolean {
        if (!store.consumeFirstLaunchCheck()) return false

        return try {
            scheduler.capabilityStatus(ReminderCapability.POST_NOTIFICATIONS) !=
                ReminderCapabilityStatus.AVAILABLE
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            false
        }
    }
}

enum class ReminderDiagnosticOperation {
    CANCEL,
    CAPABILITY_CHECK,
    SCHEDULE,
    PUBLISH,
}

fun interface ReminderDiagnosticSink {
    fun report(
        taskId: Long,
        operation: ReminderDiagnosticOperation,
        failure: Throwable,
    )
}

class ReconcileTaskReminderUseCase(
    private val repository: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val clock: AppClock,
    private val diagnosticSink: ReminderDiagnosticSink,
) {
    suspend operator fun invoke(taskId: Long): ReminderScheduleStatus {
        var state = repository.getScheduleState(taskId)
            ?: return ReminderScheduleStatus.DISABLED
        val oldPlan = state.scheduledPlan

        if (oldPlan != null) {
            val cancelStatus = schedulerCall(
                taskId = taskId,
                operation = ReminderDiagnosticOperation.CANCEL,
                stateOnFailure = state,
            ) {
                scheduler.cancel(oldPlan.key)
            } ?: return ReminderScheduleStatus.ERROR

            if (cancelStatus == ReminderScheduleStatus.ERROR) {
                recordSchedulerResultError(
                    taskId = taskId,
                    operation = ReminderDiagnosticOperation.CANCEL,
                    status = cancelStatus,
                )
                persistError(state, expectedCurrentKey = oldPlan.key)
                return ReminderScheduleStatus.ERROR
            }

            val clearedState = state.copy(
                status = ReminderScheduleStatus.PENDING,
                scheduledPlan = null,
            )
            if (!repository.updateScheduleState(clearedState, expectedCurrentKey = oldPlan.key)) {
                return currentStatus(taskId)
            }
            state = clearedState
        }

        val task = repository.getConfiguredTask(taskId)
        if (task == null || state.setting.mode == ReminderMode.NONE) {
            return persistStatus(
                state = state,
                status = ReminderScheduleStatus.DISABLED,
            )
        }

        val postNotificationsStatus = capabilityStatus(
            taskId = taskId,
            capability = ReminderCapability.POST_NOTIFICATIONS,
            state = state,
        ) ?: return ReminderScheduleStatus.ERROR
        when (postNotificationsStatus) {
            ReminderCapabilityStatus.AVAILABLE -> Unit
            ReminderCapabilityStatus.REQUIRED -> return persistStatus(
                state = state,
                status = ReminderScheduleStatus.POST_NOTIFICATIONS_REQUIRED,
            )
            ReminderCapabilityStatus.CHANNEL_DISABLED -> return persistStatus(
                state = state,
                status = ReminderScheduleStatus.NOTIFICATION_CHANNEL_DISABLED,
            )
        }

        val exactAlarmStatus = capabilityStatus(
            taskId = taskId,
            capability = ReminderCapability.EXACT_ALARM,
            state = state,
        ) ?: return ReminderScheduleStatus.ERROR
        when (exactAlarmStatus) {
            ReminderCapabilityStatus.AVAILABLE -> Unit
            ReminderCapabilityStatus.REQUIRED -> return persistStatus(
                state = state,
                status = ReminderScheduleStatus.EXACT_ALARM_ACCESS_REQUIRED,
            )
            ReminderCapabilityStatus.CHANNEL_DISABLED -> {
                diagnosticSink.report(
                    taskId = taskId,
                    operation = ReminderDiagnosticOperation.CAPABILITY_CHECK,
                    failure = IllegalStateException(
                        "Exact alarm capability returned CHANNEL_DISABLED",
                    ),
                )
                return persistStatus(
                    state = state,
                    status = ReminderScheduleStatus.ERROR,
                )
            }
        }

        val nextPlan = repository.findNextDeliverablePlan(
            taskId = taskId,
            now = clock.now(),
            zoneId = clock.zoneId,
        ) ?: return persistStatus(
            state = state,
            status = ReminderScheduleStatus.NO_FUTURE_OCCURRENCE,
        )

        val pendingState = state.copy(
            status = ReminderScheduleStatus.PENDING,
            scheduledPlan = nextPlan,
        )
        if (!repository.updateScheduleState(pendingState, expectedCurrentKey = null)) {
            return currentStatus(taskId)
        }
        state = pendingState

        val scheduleStatus = try {
            scheduler.scheduleExact(nextPlan)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            diagnosticSink.report(
                taskId = taskId,
                operation = ReminderDiagnosticOperation.SCHEDULE,
                failure = failure,
            )
            return cleanUpFailedStagedPlan(state)
        }
        if (scheduleStatus != ReminderScheduleStatus.SCHEDULED) {
            recordSchedulerResultError(
                taskId = taskId,
                operation = ReminderDiagnosticOperation.SCHEDULE,
                status = scheduleStatus,
            )
            return cleanUpFailedStagedPlan(state)
        }

        val scheduledState = state.copy(
            status = ReminderScheduleStatus.SCHEDULED,
        )
        if (repository.updateScheduleState(scheduledState, expectedCurrentKey = nextPlan.key)) {
            return ReminderScheduleStatus.SCHEDULED
        }

        cancelOrReportOrphanedPlan(taskId, nextPlan)
        return currentStatus(taskId)
    }

    private suspend fun cleanUpFailedStagedPlan(
        stagedState: ReminderScheduleState,
    ): ReminderScheduleStatus {
        val stagedPlan = requireNotNull(stagedState.scheduledPlan)
        cancelOrReportOrphanedPlan(stagedState.taskId, stagedPlan)
        val errorState = stagedState.copy(
            status = ReminderScheduleStatus.ERROR,
            scheduledPlan = null,
        )
        return if (
            repository.updateScheduleState(
                state = errorState,
                expectedCurrentKey = stagedPlan.key,
            )
        ) {
            ReminderScheduleStatus.ERROR
        } else {
            currentStatus(stagedState.taskId)
        }
    }

    private suspend fun capabilityStatus(
        taskId: Long,
        capability: ReminderCapability,
        state: ReminderScheduleState,
    ): ReminderCapabilityStatus? = schedulerCall(
        taskId = taskId,
        operation = ReminderDiagnosticOperation.CAPABILITY_CHECK,
        stateOnFailure = state,
    ) {
        scheduler.capabilityStatus(capability)
    }

    private suspend fun persistStatus(
        state: ReminderScheduleState,
        status: ReminderScheduleStatus,
    ): ReminderScheduleStatus {
        val updated = state.copy(status = status, scheduledPlan = null)
        return if (repository.updateScheduleState(updated, expectedCurrentKey = null)) {
            status
        } else {
            currentStatus(state.taskId)
        }
    }

    private suspend fun persistError(
        state: ReminderScheduleState,
        expectedCurrentKey: ReminderOccurrenceKey?,
    ) {
        repository.updateScheduleState(
            state = state.copy(status = ReminderScheduleStatus.ERROR),
            expectedCurrentKey = expectedCurrentKey,
        )
    }

    private suspend fun currentStatus(taskId: Long): ReminderScheduleStatus =
        repository.getScheduleState(taskId)?.status ?: ReminderScheduleStatus.DISABLED

    private suspend fun cancelOrReportOrphanedPlan(taskId: Long, plan: ReminderPlan) {
        try {
            val status = scheduler.cancel(plan.key)
            if (status == ReminderScheduleStatus.ERROR) {
                recordSchedulerResultError(
                    taskId = taskId,
                    operation = ReminderDiagnosticOperation.CANCEL,
                    status = status,
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            diagnosticSink.report(
                taskId = taskId,
                operation = ReminderDiagnosticOperation.CANCEL,
                failure = failure,
            )
        }
    }

    private suspend fun <T> schedulerCall(
        taskId: Long,
        operation: ReminderDiagnosticOperation,
        stateOnFailure: ReminderScheduleState,
        block: suspend () -> T,
    ): T? = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        diagnosticSink.report(taskId, operation, failure)
        persistError(
            state = stateOnFailure,
            expectedCurrentKey = stateOnFailure.scheduledPlan?.key,
        )
        null
    }

    private fun recordSchedulerResultError(
        taskId: Long,
        operation: ReminderDiagnosticOperation,
        status: ReminderScheduleStatus,
    ) {
        diagnosticSink.report(
            taskId = taskId,
            operation = operation,
            failure = IllegalStateException("Reminder scheduler returned $status"),
        )
    }
}

class ReconcileAllRemindersUseCase(
    private val repository: ReminderRepository,
    private val reconcileTaskReminder: ReconcileTaskReminderUseCase,
) {
    suspend operator fun invoke() {
        repository.getConfiguredTaskIds().forEach { taskId ->
            reconcileTaskReminder(taskId)
        }
    }
}

class DeliverReminderUseCase(
    private val repository: ReminderRepository,
    private val publisher: ReminderPublisher,
    private val reconcileTaskReminder: ReconcileTaskReminderUseCase,
    private val diagnosticSink: ReminderDiagnosticSink,
) {
    suspend operator fun invoke(key: ReminderOccurrenceKey): ReminderDeliveryResult {
        val state = repository.getScheduleState(key.taskId)
            ?: return ReminderDeliveryResult(
                published = false,
                reminderStatus = ReminderScheduleStatus.DISABLED,
            )
        if (
            state.status in setOf(
                ReminderScheduleStatus.DELIVERED,
                ReminderScheduleStatus.ERROR,
            ) &&
            state.scheduledPlan == null
        ) {
            return ReminderDeliveryResult(
                published = false,
                reminderStatus = state.status,
            )
        }

        val task = repository.getActiveTodoTaskForDelivery(key)
        if (task == null) {
            val latestState = repository.getScheduleState(key.taskId)
            if (
                latestState != null &&
                latestState.scheduledPlan == null &&
                latestState.status in setOf(
                    ReminderScheduleStatus.DELIVERED,
                    ReminderScheduleStatus.ERROR,
                )
            ) {
                return ReminderDeliveryResult(
                    published = false,
                    reminderStatus = latestState.status,
                )
            }
            return ReminderDeliveryResult(
                published = false,
                reminderStatus = reconcileTaskReminder(key.taskId),
            )
        }

        val claimedState = state.copy(
            status = ReminderScheduleStatus.DELIVERED,
            scheduledPlan = null,
        )
        if (!repository.updateScheduleState(claimedState, expectedCurrentKey = key)) {
            return ReminderDeliveryResult(
                published = false,
                reminderStatus = repository.getScheduleState(key.taskId)?.status
                    ?: ReminderScheduleStatus.DISABLED,
            )
        }

        val payload = ReminderNotificationPayload(
            key = key,
            title = task.title,
            memo = task.memo,
            occurrenceDate = key.occurrenceDate,
            taskTime = task.time,
        )
        try {
            publisher.publish(payload)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            diagnosticSink.report(
                taskId = key.taskId,
                operation = ReminderDiagnosticOperation.PUBLISH,
                failure = failure,
            )
            val errorState = claimedState.copy(status = ReminderScheduleStatus.ERROR)
            repository.updateScheduleState(errorState, expectedCurrentKey = null)
            return ReminderDeliveryResult(
                published = false,
                reminderStatus = ReminderScheduleStatus.ERROR,
            )
        }

        val reminderStatus = if (task.recurrenceRule == RecurrenceRule.NONE) {
            ReminderScheduleStatus.DELIVERED
        } else {
            reconcileTaskReminder(key.taskId)
        }
        return ReminderDeliveryResult(
            published = true,
            reminderStatus = reminderStatus,
        )
    }
}

class CreateTaskUseCase(
    private val repository: TaskRepository,
    private val reconcileTaskReminder: ReconcileTaskReminderUseCase,
) {
    suspend operator fun invoke(input: CreateTaskInput): TaskMutationResult {
        val taskId = repository.createTask(input)
        return TaskMutationResult(
            taskId = taskId,
            reminderStatus = reconcileTaskReminder(taskId),
        )
    }
}

class UpdateTaskUseCase(
    private val repository: TaskRepository,
    private val reconcileTaskReminder: ReconcileTaskReminderUseCase,
) {
    suspend operator fun invoke(input: UpdateTaskInput): TaskMutationResult {
        val currentTaskId = repository.updateTask(input)
        val taskIds = linkedSetOf(input.taskId, currentTaskId)
        var currentStatus = ReminderScheduleStatus.DISABLED
        taskIds.forEach { taskId ->
            val status = reconcileTaskReminder(taskId)
            if (taskId == currentTaskId) {
                currentStatus = status
            }
        }
        return TaskMutationResult(
            taskId = currentTaskId,
            reminderStatus = currentStatus,
        )
    }
}

class DeleteTaskUseCase(
    private val repository: TaskRepository,
    private val reconcileTaskReminder: ReconcileTaskReminderUseCase,
) {
    suspend operator fun invoke(taskId: Long, effectiveDate: LocalDate) {
        repository.deleteTask(taskId, effectiveDate)
        reconcileTaskReminder(taskId)
    }
}
