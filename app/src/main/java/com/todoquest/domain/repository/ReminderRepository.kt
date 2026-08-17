package com.todoquest.domain.repository

import com.todoquest.domain.model.ReminderOccurrenceKey
import com.todoquest.domain.model.ReminderPlan
import com.todoquest.domain.model.ReminderScheduleState
import com.todoquest.domain.model.TodoTask
import java.time.Instant
import java.time.ZoneId

interface ReminderRepository {
    suspend fun getConfiguredTask(taskId: Long): TodoTask?

    suspend fun getConfiguredTaskIds(): List<Long>

    suspend fun getScheduleState(taskId: Long): ReminderScheduleState?

    suspend fun updateScheduleState(
        state: ReminderScheduleState,
        expectedCurrentKey: ReminderOccurrenceKey?,
    ): Boolean

    suspend fun findNextDeliverablePlan(
        taskId: Long,
        now: Instant,
        zoneId: ZoneId,
    ): ReminderPlan?

    /** Returns the task only when [key] is still current, active, and TODO. */
    suspend fun getActiveTodoTaskForDelivery(key: ReminderOccurrenceKey): TodoTask?
}
