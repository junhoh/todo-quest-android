package com.todoquest.data.local

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TaskReminderDaoTest {
    private lateinit var database: TodoQuestDatabase
    private lateinit var dao: TaskReminderDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            TodoQuestDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.taskReminderDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun getObserveUpsertAndConfiguredQueryKeepRawPersistenceValues() = runTest {
        insertTask(taskId = 1L)
        insertTask(taskId = 2L)
        val rawReminder = reminder(
            taskId = 1L,
            mode = "FUTURE_MODE",
            customTimeMinuteOfDay = 1_500,
            scheduleStatus = "FUTURE_STATUS",
        )
        val disabledReminder = reminder(
            taskId = 2L,
            mode = "NONE",
            customTimeMinuteOfDay = null,
            scheduleStatus = "DISABLED",
        )

        dao.upsert(rawReminder)
        dao.upsert(disabledReminder)

        assertEquals(rawReminder, dao.getByTaskId(1L))
        assertEquals(rawReminder, dao.observeByTaskId(1L).first())
        assertEquals(listOf(rawReminder), dao.getConfiguredReminders())
        assertNull(dao.getByTaskId(99L))
    }

    @Test
    fun observeAllEmitsInsertAndUpdateWithEveryModeAndStatusInTaskIdOrder() = runTest {
        insertTask(taskId = 30L)
        insertTask(taskId = 10L)
        insertTask(taskId = 20L)
        val disabled = reminder(
            taskId = 30L,
            mode = "NONE",
            scheduleStatus = "DISABLED",
            scheduledOccurrenceEpochDay = null,
            scheduledTriggerAtEpochMillis = null,
        )
        val channelDisabled = reminder(
            taskId = 10L,
            mode = "CUSTOM_TIME",
            scheduleStatus = "NOTIFICATION_CHANNEL_DISABLED",
        )
        dao.upsert(disabled)
        dao.upsert(channelDisabled)

        val emissions = dao.observeAll().produceIn(backgroundScope)
        assertEquals(listOf(channelDisabled, disabled), emissions.receive())
        val updatedDisabled = disabled.copy(
            scheduleStatus = "PENDING",
            updatedAtEpochMillis = 600L,
        )
        dao.upsert(updatedDisabled)
        assertEquals(listOf(channelDisabled, updatedDisabled), emissions.receive())
        val scheduled = reminder(
            taskId = 20L,
            mode = "TEN_MINUTES_BEFORE",
            scheduleStatus = "SCHEDULED",
        )
        dao.upsert(scheduled)
        assertEquals(
            listOf(channelDisabled, scheduled, updatedDisabled),
            emissions.receive(),
        )
        emissions.cancel()
    }

    @Test
    fun scheduleStateUpdateRequiresTheCurrentTaskAndOccurrenceKey() = runTest {
        insertTask(taskId = 1L)
        dao.upsert(
            reminder(
                taskId = 1L,
                scheduledOccurrenceEpochDay = 20_000L,
                scheduledTriggerAtEpochMillis = 1_000L,
            ),
        )

        assertEquals(
            0,
            dao.compareAndUpdateScheduleState(
                taskId = 1L,
                expectedOccurrenceEpochDay = 19_999L,
                scheduleStatus = "DELIVERED",
                scheduledOccurrenceEpochDay = null,
                scheduledTriggerAtEpochMillis = null,
                updatedAtEpochMillis = 2_000L,
            ),
        )
        assertEquals("SCHEDULED", dao.getByTaskId(1L)?.scheduleStatus)
        assertEquals(20_000L, dao.getByTaskId(1L)?.scheduledOccurrenceEpochDay)

        assertEquals(
            1,
            dao.compareAndUpdateScheduleState(
                taskId = 1L,
                expectedOccurrenceEpochDay = 20_000L,
                scheduleStatus = "DELIVERED",
                scheduledOccurrenceEpochDay = null,
                scheduledTriggerAtEpochMillis = null,
                updatedAtEpochMillis = 2_000L,
            ),
        )
        assertEquals(
            reminder(
                taskId = 1L,
                scheduleStatus = "DELIVERED",
                scheduledOccurrenceEpochDay = null,
                scheduledTriggerAtEpochMillis = null,
                updatedAtEpochMillis = 2_000L,
            ),
            dao.getByTaskId(1L),
        )
    }

    @Test
    fun resetScheduleStateClearsOnlyTheSelectedTaskMaterializedKey() = runTest {
        insertTask(taskId = 1L)
        insertTask(taskId = 2L)
        dao.upsert(reminder(taskId = 1L))
        dao.upsert(reminder(taskId = 2L, scheduledOccurrenceEpochDay = 20_001L))

        assertEquals(
            1,
            dao.resetScheduleState(
                taskId = 1L,
                scheduleStatus = "PENDING",
                updatedAtEpochMillis = 3_000L,
            ),
        )

        assertEquals("PENDING", dao.getByTaskId(1L)?.scheduleStatus)
        assertNull(dao.getByTaskId(1L)?.scheduledOccurrenceEpochDay)
        assertNull(dao.getByTaskId(1L)?.scheduledTriggerAtEpochMillis)
        assertEquals(20_001L, dao.getByTaskId(2L)?.scheduledOccurrenceEpochDay)
    }

    @Test
    fun reminderRequiresAnExistingTaskAndHardDeleteCascades() = runTest {
        assertConstraintFailure {
            dao.upsert(reminder(taskId = 99L))
        }

        insertTask(taskId = 1L)
        dao.upsert(reminder(taskId = 1L))
        database.openHelper.writableDatabase.execSQL("DELETE FROM todo_tasks WHERE id = 1")

        assertNull(dao.getByTaskId(1L))
    }

    private suspend fun insertTask(taskId: Long) {
        database.todoTaskDao().insert(
            TodoTaskEntity(
                id = taskId,
                recurrenceSeriesId = taskId,
                title = "task-$taskId",
                memo = "",
                startDateEpochDay = 20_000L,
                endDateEpochDay = null,
                timeMinuteOfDay = null,
                difficulty = "MEDIUM",
                category = "DEFAULT",
                recurrenceRule = "NONE",
                createdAtEpochMillis = 0L,
                updatedAtEpochMillis = 0L,
                deletedAtEpochMillis = null,
            ),
        )
    }

    private fun reminder(
        taskId: Long,
        mode: String = "CUSTOM_TIME",
        customTimeMinuteOfDay: Int? = 540,
        scheduleStatus: String = "SCHEDULED",
        scheduledOccurrenceEpochDay: Long? = 20_000L,
        scheduledTriggerAtEpochMillis: Long? = 1_000L,
        updatedAtEpochMillis: Long = 500L,
    ) = TaskReminderEntity(
        taskId = taskId,
        mode = mode,
        customTimeMinuteOfDay = customTimeMinuteOfDay,
        scheduleStatus = scheduleStatus,
        scheduledOccurrenceEpochDay = scheduledOccurrenceEpochDay,
        scheduledTriggerAtEpochMillis = scheduledTriggerAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

    private suspend fun assertConstraintFailure(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected SQLiteConstraintException")
        } catch (_: SQLiteConstraintException) {
            Unit
        }
    }
}
