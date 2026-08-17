package com.todoquest.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.todoquest.core.AppClock
import com.todoquest.data.local.TaskReminderEntity
import com.todoquest.domain.model.CreateTaskInput
import com.todoquest.domain.model.RecurrenceRule
import com.todoquest.domain.model.ReminderMode
import com.todoquest.domain.model.ReminderOccurrenceKey
import com.todoquest.domain.model.ReminderPlan
import com.todoquest.domain.model.ReminderScheduleState
import com.todoquest.domain.model.ReminderScheduleStatus
import com.todoquest.domain.model.ReminderSetting
import com.todoquest.domain.model.TaskDifficulty
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RoomReminderRepositoryTest {
    private lateinit var database: com.todoquest.data.local.TodoQuestDatabase
    private lateinit var taskRepository: RoomTaskRepository
    private lateinit var repository: RoomReminderRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            com.todoquest.data.local.TodoQuestDatabase::class.java,
        ).allowMainThreadQueries().build()
        taskRepository = RoomTaskRepository(database, FixedClock)
        repository = RoomReminderRepository(database, FixedClock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun configuredTaskIdsIncludeNoneSoftDeletedAndEndedReminderRows() = runTest {
        val noneTaskId = taskRepository.createTask(taskInput(title = "None"))
        val deletedTaskId = taskRepository.createTask(
            taskInput(
                title = "Deleted",
                reminderSetting = customReminder(LocalTime.of(8, 0)),
            ),
        )
        val endedTaskId = taskRepository.createTask(
            taskInput(
                title = "Ended",
                recurrenceRule = RecurrenceRule.DAILY,
                reminderSetting = customReminder(LocalTime.of(9, 0)),
            ),
        )
        taskRepository.deleteTask(deletedTaskId)
        taskRepository.deleteTask(endedTaskId, LocalDate.of(2026, 7, 16))

        assertEquals(listOf(noneTaskId, deletedTaskId, endedTaskId), repository.getConfiguredTaskIds())
        assertNull(repository.getConfiguredTask(deletedTaskId))
        assertEquals(LocalDate.of(2026, 7, 15), repository.getConfiguredTask(endedTaskId)?.endDate)
    }

    @Test
    fun nextPlanSkipsCompletedAndFailedDailyOccurrencesWithoutMaterializingTheSeries() = runTest {
        val taskId = taskRepository.createTask(
            taskInput(
                recurrenceRule = RecurrenceRule.DAILY,
                reminderSetting = customReminder(LocalTime.of(9, 0)),
            ),
        )
        taskRepository.completeOccurrence(taskId, LocalDate.of(2026, 7, 14))
        taskRepository.failOccurrence(taskId, LocalDate.of(2026, 7, 15))

        val plan = repository.findNextDeliverablePlan(
            taskId = taskId,
            now = Instant.parse("2026-07-14T08:00:00Z"),
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(ReminderOccurrenceKey(taskId, LocalDate.of(2026, 7, 16)), plan?.key)
        assertEquals(Instant.parse("2026-07-16T09:00:00Z"), plan?.triggerAt)
    }

    @Test
    fun monthlyNextPlanSkipsMonthsWithoutTheOccurrenceDay() = runTest {
        val taskId = taskRepository.createTask(
            taskInput(
                startDate = LocalDate.of(2026, 1, 31),
                recurrenceRule = RecurrenceRule.MONTHLY,
                reminderSetting = customReminder(LocalTime.of(7, 30)),
            ),
        )

        val plan = repository.findNextDeliverablePlan(
            taskId = taskId,
            now = Instant.parse("2026-02-01T00:00:00Z"),
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(ReminderOccurrenceKey(taskId, LocalDate.of(2026, 3, 31)), plan?.key)
        assertEquals(Instant.parse("2026-03-31T07:30:00Z"), plan?.triggerAt)
    }

    @Test
    fun scheduleStateUpdatesRequireCurrentOccurrenceKeyAndCanClearIt() = runTest {
        val taskId = taskRepository.createTask(
            taskInput(reminderSetting = customReminder(LocalTime.of(9, 0))),
        )
        val key = ReminderOccurrenceKey(taskId, LocalDate.of(2026, 7, 14))
        val plan = ReminderPlan(key, Instant.parse("2026-07-14T09:00:00Z"))

        assertTrue(
            repository.updateScheduleState(
                state = ReminderScheduleState(
                    taskId = taskId,
                    setting = customReminder(LocalTime.of(9, 0)),
                    status = ReminderScheduleStatus.SCHEDULED,
                    scheduledPlan = plan,
                ),
                expectedCurrentKey = null,
            ),
        )
        assertEquals(plan, repository.getScheduleState(taskId)?.scheduledPlan)

        val staleKey = key.copy(occurrenceDate = key.occurrenceDate.minusDays(1))
        assertEquals(
            false,
            repository.updateScheduleState(
                state = repository.getScheduleState(taskId)!!.copy(
                    status = ReminderScheduleStatus.POST_NOTIFICATIONS_REQUIRED,
                ),
                expectedCurrentKey = staleKey,
            ),
        )
        assertEquals(ReminderScheduleStatus.SCHEDULED, repository.getScheduleState(taskId)?.status)

        assertTrue(
            repository.updateScheduleState(
                state = repository.getScheduleState(taskId)!!.copy(
                    status = ReminderScheduleStatus.EXACT_ALARM_ACCESS_REQUIRED,
                ),
                expectedCurrentKey = key,
            ),
        )
        assertTrue(
            repository.updateScheduleState(
                state = repository.getScheduleState(taskId)!!.copy(
                    status = ReminderScheduleStatus.DELIVERED,
                    scheduledPlan = null,
                ),
                expectedCurrentKey = key,
            ),
        )
        assertEquals(ReminderScheduleStatus.DELIVERED, repository.getScheduleState(taskId)?.status)
        assertNull(repository.getScheduleState(taskId)?.scheduledPlan)
    }

    @Test
    fun noFutureAndErrorStatesClearOnlyTheExpectedCurrentKey() = runTest {
        val statuses = listOf(ReminderScheduleStatus.NO_FUTURE_OCCURRENCE, ReminderScheduleStatus.ERROR)
        statuses.forEachIndexed { index, status ->
            val taskId = taskRepository.createTask(
                taskInput(
                    title = status.name,
                    reminderSetting = customReminder(LocalTime.of(9, index)),
                ),
            )
            val key = ReminderOccurrenceKey(taskId, LocalDate.of(2026, 7, 14))
            val state = ReminderScheduleState(
                taskId = taskId,
                setting = customReminder(LocalTime.of(9, index)),
                status = ReminderScheduleStatus.SCHEDULED,
                scheduledPlan = ReminderPlan(key, Instant.parse("2026-07-14T09:00:00Z").plusSeconds(index * 60L)),
            )
            assertTrue(repository.updateScheduleState(state, expectedCurrentKey = null))
            assertTrue(
                repository.updateScheduleState(
                    state = state.copy(status = status, scheduledPlan = null),
                    expectedCurrentKey = key,
                ),
            )
            assertEquals(status, repository.getScheduleState(taskId)?.status)
            assertNull(repository.getScheduleState(taskId)?.scheduledPlan)
        }
    }

    @Test
    fun deliveryRequiresMatchingPendingOrScheduledKeyActiveTaskAndTodoOccurrence() = runTest {
        val pendingTaskId = scheduledDailyTask(
            "Pending",
            LocalTime.of(8, 55),
            ReminderScheduleStatus.PENDING,
        )
        val todoTaskId = scheduledDailyTask("Todo", LocalTime.of(9, 0))
        val completedTaskId = scheduledDailyTask("Completed", LocalTime.of(9, 5))
        val failedTaskId = scheduledDailyTask("Failed", LocalTime.of(9, 10))
        val deletedTaskId = scheduledDailyTask("Deleted", LocalTime.of(9, 15))
        val occurrenceDate = LocalDate.of(2026, 7, 14)
        taskRepository.completeOccurrence(completedTaskId, occurrenceDate)
        taskRepository.failOccurrence(failedTaskId, occurrenceDate)
        taskRepository.deleteTask(deletedTaskId)

        assertNotNull(repository.getActiveTodoTaskForDelivery(ReminderOccurrenceKey(pendingTaskId, occurrenceDate)))
        assertNotNull(repository.getActiveTodoTaskForDelivery(ReminderOccurrenceKey(todoTaskId, occurrenceDate)))
        assertNull(
            repository.getActiveTodoTaskForDelivery(
                ReminderOccurrenceKey(todoTaskId, occurrenceDate.plusDays(1)),
            ),
        )
        assertNull(repository.getActiveTodoTaskForDelivery(ReminderOccurrenceKey(completedTaskId, occurrenceDate)))
        assertNull(repository.getActiveTodoTaskForDelivery(ReminderOccurrenceKey(failedTaskId, occurrenceDate)))
        assertNull(repository.getActiveTodoTaskForDelivery(ReminderOccurrenceKey(deletedTaskId, occurrenceDate)))
    }

    @Test
    fun deliverySuppressesPermissionDeliveredErrorAndOtherNonDeliverableStatuses() = runTest {
        val occurrenceDate = LocalDate.of(2026, 7, 14)
        val statuses = listOf(
            ReminderScheduleStatus.DISABLED,
            ReminderScheduleStatus.POST_NOTIFICATIONS_REQUIRED,
            ReminderScheduleStatus.NOTIFICATION_CHANNEL_DISABLED,
            ReminderScheduleStatus.EXACT_ALARM_ACCESS_REQUIRED,
            ReminderScheduleStatus.DELIVERED,
            ReminderScheduleStatus.NO_FUTURE_OCCURRENCE,
            ReminderScheduleStatus.ERROR,
        )

        statuses.forEachIndexed { index, status ->
            val taskId = scheduledDailyTask(
                title = status.name,
                customTime = LocalTime.of(10, index),
                status = status,
            )

            assertNull(
                "Expected $status callback to be suppressed",
                repository.getActiveTodoTaskForDelivery(ReminderOccurrenceKey(taskId, occurrenceDate)),
            )
        }
    }

    @Test
    fun callbackClaimOfStagedPendingPlanPreventsScheduledOrErrorStateRegression() = runTest {
        val occurrenceDate = LocalDate.of(2026, 7, 14)
        val taskId = scheduledDailyTask(
            title = "Race",
            customTime = LocalTime.of(9, 30),
            status = ReminderScheduleStatus.PENDING,
        )
        val key = ReminderOccurrenceKey(taskId, occurrenceDate)
        val staged = repository.getScheduleState(taskId)!!
        assertNotNull(repository.getActiveTodoTaskForDelivery(key))

        assertTrue(
            repository.updateScheduleState(
                state = staged.copy(
                    status = ReminderScheduleStatus.DELIVERED,
                    scheduledPlan = null,
                ),
                expectedCurrentKey = key,
            ),
        )
        assertFalse(
            repository.updateScheduleState(
                state = staged.copy(status = ReminderScheduleStatus.SCHEDULED),
                expectedCurrentKey = key,
            ),
        )
        assertFalse(
            repository.updateScheduleState(
                state = staged.copy(
                    status = ReminderScheduleStatus.ERROR,
                    scheduledPlan = null,
                ),
                expectedCurrentKey = key,
            ),
        )
        assertEquals(ReminderScheduleStatus.DELIVERED, repository.getScheduleState(taskId)?.status)
        assertNull(repository.getScheduleState(taskId)?.scheduledPlan)
    }

    @Test
    fun corruptReminderRowsFailMappingInsteadOfFallingBackToNone() = runTest {
        val corruptRows = listOf(
            rawReminder(taskId = 1L, mode = "FUTURE_MODE"),
            rawReminder(taskId = 2L, mode = ReminderMode.CUSTOM_TIME.name, customMinute = null),
            rawReminder(taskId = 3L, mode = ReminderMode.CUSTOM_TIME.name, customMinute = 1_440),
            rawReminder(taskId = 4L, scheduleStatus = "FUTURE_STATUS"),
        )
        corruptRows.forEach { row ->
            taskRepository.createTask(taskInput(title = "Task ${row.taskId}"))
            database.taskReminderDao().upsert(row)

            val result = runCatching { repository.getScheduleState(row.taskId) }

            assertTrue("Expected corrupt row ${row.taskId} to fail", result.isFailure)
        }
    }

    private suspend fun scheduledDailyTask(
        title: String,
        customTime: LocalTime,
        status: ReminderScheduleStatus = ReminderScheduleStatus.SCHEDULED,
    ): Long {
        val taskId = taskRepository.createTask(
            taskInput(
                title = title,
                recurrenceRule = RecurrenceRule.DAILY,
                reminderSetting = customReminder(customTime),
            ),
        )
        val key = ReminderOccurrenceKey(taskId, LocalDate.of(2026, 7, 14))
        assertTrue(
            repository.updateScheduleState(
                ReminderScheduleState(
                    taskId = taskId,
                    setting = customReminder(customTime),
                    status = status,
                    scheduledPlan = ReminderPlan(key, LocalDate.of(2026, 7, 14).atTime(customTime).toInstant(java.time.ZoneOffset.UTC)),
                ),
                expectedCurrentKey = null,
            ),
        )
        return taskId
    }

    private fun taskInput(
        title: String = "Quest",
        startDate: LocalDate = LocalDate.of(2026, 7, 14),
        recurrenceRule: RecurrenceRule = RecurrenceRule.NONE,
        reminderSetting: ReminderSetting = ReminderSetting(),
    ) = CreateTaskInput(
        title = title,
        memo = "",
        startDate = startDate,
        time = null,
        difficulty = TaskDifficulty.MEDIUM,
        category = "General",
        recurrenceRule = recurrenceRule,
        reminderSetting = reminderSetting,
    )

    private fun customReminder(time: LocalTime) = ReminderSetting(ReminderMode.CUSTOM_TIME, time)

    private fun rawReminder(
        taskId: Long,
        mode: String = ReminderMode.NONE.name,
        customMinute: Int? = null,
        scheduleStatus: String = ReminderScheduleStatus.DISABLED.name,
    ) = TaskReminderEntity(
        taskId = taskId,
        mode = mode,
        customTimeMinuteOfDay = customMinute,
        scheduleStatus = scheduleStatus,
        scheduledOccurrenceEpochDay = null,
        scheduledTriggerAtEpochMillis = null,
        updatedAtEpochMillis = FixedClock.now().toEpochMilli(),
    )

    private object FixedClock : AppClock {
        override val zoneId: ZoneId = ZoneId.of("UTC")
        override fun now(): Instant = Instant.parse("2026-07-14T08:00:00Z")
        override fun today(): LocalDate = LocalDate.of(2026, 7, 14)
    }
}
