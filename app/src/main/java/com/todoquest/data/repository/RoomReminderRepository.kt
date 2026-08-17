package com.todoquest.data.repository

import androidx.room.withTransaction
import com.todoquest.core.AppClock
import com.todoquest.data.local.TodoQuestDatabase
import com.todoquest.data.mapper.TaskReminderMapper
import com.todoquest.data.mapper.TodoTaskMapper
import com.todoquest.domain.model.ReminderMode
import com.todoquest.domain.model.ReminderOccurrenceKey
import com.todoquest.domain.model.ReminderPlan
import com.todoquest.domain.model.ReminderScheduleState
import com.todoquest.domain.model.ReminderScheduleStatus
import com.todoquest.domain.model.TodoTask
import com.todoquest.domain.repository.ReminderRepository
import com.todoquest.domain.usecase.OccurrenceCalculator
import com.todoquest.domain.usecase.ReminderPlanner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class RoomReminderRepository(
    private val database: TodoQuestDatabase,
    private val clock: AppClock,
    private val occurrenceCalculator: OccurrenceCalculator = OccurrenceCalculator(),
    private val reminderPlanner: ReminderPlanner = ReminderPlanner(occurrenceCalculator),
) : ReminderRepository {
    private val taskDao = database.todoTaskDao()
    private val reminderDao = database.taskReminderDao()
    private val completionLogDao = database.completionLogDao()
    private val failureLogDao = database.failureLogDao()

    override suspend fun getConfiguredTask(taskId: Long): TodoTask? = database.withTransaction {
        val taskEntity = taskDao.getActiveById(taskId) ?: return@withTransaction null
        TodoTaskMapper.toDomain(taskEntity, reminderDao.getByTaskId(taskId))
    }

    override suspend fun getConfiguredTaskIds(): List<Long> = reminderDao.getAllTaskIds()

    override suspend fun getScheduleState(taskId: Long): ReminderScheduleState? =
        reminderDao.getByTaskId(taskId)?.let(TaskReminderMapper::toScheduleState)

    override suspend fun updateScheduleState(
        state: ReminderScheduleState,
        expectedCurrentKey: ReminderOccurrenceKey?,
    ): Boolean = database.withTransaction {
        require(expectedCurrentKey == null || expectedCurrentKey.taskId == state.taskId) {
            "Expected reminder key must belong to task ${state.taskId}"
        }
        require(state.scheduledPlan == null || state.scheduledPlan.key.taskId == state.taskId) {
            "Scheduled reminder plan must belong to task ${state.taskId}"
        }

        val currentEntity = reminderDao.getByTaskId(state.taskId) ?: return@withTransaction false
        val currentState = TaskReminderMapper.toScheduleState(currentEntity)
        if (currentState.setting != state.setting || currentState.scheduledPlan?.key != expectedCurrentKey) {
            return@withTransaction false
        }

        val updatedPlan = state.scheduledPlan
        reminderDao.compareAndUpdateScheduleState(
            taskId = state.taskId,
            expectedOccurrenceEpochDay = expectedCurrentKey?.occurrenceDate?.toEpochDay(),
            scheduleStatus = state.status.name,
            scheduledOccurrenceEpochDay = updatedPlan?.key?.occurrenceDate?.toEpochDay(),
            scheduledTriggerAtEpochMillis = updatedPlan?.triggerAt?.toEpochMilli(),
            updatedAtEpochMillis = clock.now().toEpochMilli(),
        ) == 1
    }

    override suspend fun findNextDeliverablePlan(
        taskId: Long,
        now: Instant,
        zoneId: ZoneId,
    ): ReminderPlan? = database.withTransaction {
        val taskEntity = taskDao.getActiveById(taskId) ?: return@withTransaction null
        val reminderEntity = reminderDao.getByTaskId(taskId) ?: return@withTransaction null
        val task = TodoTaskMapper.toDomain(taskEntity, reminderEntity)
        val fromEpochDay = task.startDate.toEpochDay()
        val ineligibleDates = buildSet {
            completionLogDao.findFrom(taskId, fromEpochDay).forEach {
                add(LocalDate.ofEpochDay(it.occurrenceDateEpochDay))
            }
            failureLogDao.findFrom(taskId, fromEpochDay).forEach {
                add(LocalDate.ofEpochDay(it.occurrenceDateEpochDay))
            }
        }
        reminderPlanner.nextFuturePlan(
            task = task,
            now = now,
            zoneId = zoneId,
            ineligibleOccurrenceDates = ineligibleDates,
        )
    }

    override suspend fun getActiveTodoTaskForDelivery(key: ReminderOccurrenceKey): TodoTask? =
        database.withTransaction {
            val taskEntity = taskDao.getActiveById(key.taskId) ?: return@withTransaction null
            val reminderEntity = reminderDao.getByTaskId(key.taskId) ?: return@withTransaction null
            val scheduleState = TaskReminderMapper.toScheduleState(reminderEntity)
            if (
                scheduleState.status !in DELIVERABLE_STATUSES ||
                scheduleState.setting.mode == ReminderMode.NONE ||
                scheduleState.scheduledPlan?.key != key
            ) {
                return@withTransaction null
            }

            val task = TodoTaskMapper.toDomain(taskEntity, reminderEntity)
            val occurrenceEpochDay = key.occurrenceDate.toEpochDay()
            if (
                !occurrenceCalculator.occursOn(task, key.occurrenceDate) ||
                completionLogDao.find(key.taskId, occurrenceEpochDay) != null ||
                failureLogDao.find(key.taskId, occurrenceEpochDay) != null
            ) {
                return@withTransaction null
            }
            task
        }

    private companion object {
        private val DELIVERABLE_STATUSES = setOf(
            ReminderScheduleStatus.PENDING,
            ReminderScheduleStatus.SCHEDULED,
        )
    }
}
