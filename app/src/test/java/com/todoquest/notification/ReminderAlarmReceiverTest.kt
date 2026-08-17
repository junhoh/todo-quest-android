package com.todoquest.notification

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.todoquest.core.AppClock
import com.todoquest.domain.model.RecurrenceRule
import com.todoquest.domain.model.ReminderNotificationPayload
import com.todoquest.domain.model.ReminderOccurrenceKey
import com.todoquest.domain.model.ReminderPlan
import com.todoquest.domain.model.ReminderScheduleState
import com.todoquest.domain.model.ReminderScheduleStatus
import com.todoquest.domain.model.ReminderSetting
import com.todoquest.domain.model.TaskCategory
import com.todoquest.domain.model.TaskDifficulty
import com.todoquest.domain.model.TodoTask
import com.todoquest.domain.repository.ReminderCapability
import com.todoquest.domain.repository.ReminderCapabilityStatus
import com.todoquest.domain.repository.ReminderPublisher
import com.todoquest.domain.repository.ReminderRepository
import com.todoquest.domain.repository.ReminderScheduler
import com.todoquest.domain.usecase.DeliverReminderUseCase
import com.todoquest.domain.usecase.ReconcileAllRemindersUseCase
import com.todoquest.domain.usecase.ReconcileTaskReminderUseCase
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = ReminderReceiverTestApplication::class, sdk = [35])
class ReminderAlarmReceiverTest {
    private lateinit var application: ReminderReceiverTestApplication

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        application.runtime = null
    }

    @Test
    fun validAlarmDelegatesTypedKeyAndPublishesPersistedTaskPayload() = runTest {
        val key = ReminderOccurrenceKey(7L, LocalDate.of(2026, 7, 29))
        val repository = RecordingReminderRepository(
            state = scheduledState(key),
            activeTask = task(id = key.taskId),
        )
        val publisher = RecordingPublisher()
        application.runtime = runtime(repository, publisher)
        val receiver = ReminderAlarmReceiver(this)

        receiver.onReceive(application, ReminderAlarmIntents.alarmIntent(application, key))
        testScheduler.runCurrent()

        assertEquals(listOf(key), repository.deliveryChecks)
        assertEquals(listOf(key), publisher.payloads.map { it.key })
        assertEquals("아침 회고", publisher.payloads.single().title)
        assertEquals(ReminderScheduleStatus.DELIVERED, repository.state?.status)
    }

    @Test
    fun stagedPendingAlarmCanBeClaimedAndPublishedBeforeScheduledUpdate() = runTest {
        val key = ReminderOccurrenceKey(70L, LocalDate.of(2026, 7, 29))
        val repository = RecordingReminderRepository(
            state = scheduledState(key, ReminderScheduleStatus.PENDING),
            activeTask = task(id = key.taskId),
        )
        val publisher = RecordingPublisher()
        application.runtime = runtime(repository, publisher)
        val receiver = ReminderAlarmReceiver(this)

        receiver.onReceive(application, ReminderAlarmIntents.alarmIntent(application, key))
        testScheduler.runCurrent()

        assertEquals(listOf(key), repository.deliveryChecks)
        assertEquals(listOf(key), publisher.payloads.map { it.key })
        assertEquals(ReminderScheduleStatus.DELIVERED, repository.state?.status)
    }

    @Test
    fun staleAlarmStillDelegatesForSourceRevalidationWithoutPublishing() = runTest {
        val currentKey = ReminderOccurrenceKey(8L, LocalDate.of(2026, 7, 30))
        val staleKey = currentKey.copy(occurrenceDate = LocalDate.of(2026, 7, 29))
        val repository = RecordingReminderRepository(
            state = scheduledState(currentKey),
            activeTask = null,
        )
        val publisher = RecordingPublisher()
        application.runtime = runtime(repository, publisher)
        val receiver = ReminderAlarmReceiver(this)

        receiver.onReceive(application, ReminderAlarmIntents.alarmIntent(application, staleKey))
        testScheduler.runCurrent()

        assertEquals(listOf(staleKey), repository.deliveryChecks)
        assertTrue(publisher.payloads.isEmpty())
        assertEquals(listOf(currentKey), repository.cancelledKeys)
        assertEquals(ReminderScheduleStatus.DISABLED, repository.state?.status)
    }

    @Test
    fun malformedAlarmAndMissingProviderDoNotCrashOrInvokeDelivery() = runTest {
        val repository = RecordingReminderRepository(state = null, activeTask = null)
        application.runtime = runtime(repository, RecordingPublisher())
        val receiver = ReminderAlarmReceiver(this)

        receiver.onReceive(application, android.content.Intent(ReminderAlarmIntents.ACTION_DELIVER_REMINDER))
        application.runtime = null
        receiver.onReceive(
            application,
            ReminderAlarmIntents.alarmIntent(
                application,
                ReminderOccurrenceKey(9L, LocalDate.of(2026, 7, 29)),
            ),
        )
        testScheduler.runCurrent()

        assertTrue(repository.deliveryChecks.isEmpty())
    }

    private fun runtime(
        repository: RecordingReminderRepository,
        publisher: RecordingPublisher,
    ): ReminderRuntime {
        val scheduler = RecordingScheduler(repository)
        val reconcileTask = ReconcileTaskReminderUseCase(
            repository = repository,
            scheduler = scheduler,
            clock = FixedClock,
            diagnosticSink = { _, _, _ -> },
        )
        return ReminderRuntime(
            deliverReminderUseCase = DeliverReminderUseCase(
                repository = repository,
                publisher = publisher,
                reconcileTaskReminder = reconcileTask,
                diagnosticSink = { _, _, _ -> },
            ),
            reconcileAllRemindersUseCase = ReconcileAllRemindersUseCase(
                repository = repository,
                reconcileTaskReminder = reconcileTask,
            ),
        )
    }

    private fun scheduledState(
        key: ReminderOccurrenceKey,
        status: ReminderScheduleStatus = ReminderScheduleStatus.SCHEDULED,
    ) = ReminderScheduleState(
        taskId = key.taskId,
        setting = ReminderSetting(),
        status = status,
        scheduledPlan = ReminderPlan(key, Instant.parse("2026-07-29T00:00:00Z")),
    )

    private fun task(id: Long) = TodoTask(
        id = id,
        title = "아침 회고",
        memo = "어제 기록 확인",
        startDate = LocalDate.of(2026, 7, 29),
        time = LocalTime.of(9, 0),
        difficulty = TaskDifficulty.EASY,
        category = TaskCategory.DEFAULT,
        recurrenceRule = RecurrenceRule.NONE,
    )

    private class RecordingReminderRepository(
        var state: ReminderScheduleState?,
        private val activeTask: TodoTask?,
    ) : ReminderRepository {
        val deliveryChecks = mutableListOf<ReminderOccurrenceKey>()
        val cancelledKeys = mutableListOf<ReminderOccurrenceKey>()

        override suspend fun getConfiguredTask(taskId: Long): TodoTask? = null

        override suspend fun getConfiguredTaskIds(): List<Long> = emptyList()

        override suspend fun getScheduleState(taskId: Long): ReminderScheduleState? = state

        override suspend fun updateScheduleState(
            state: ReminderScheduleState,
            expectedCurrentKey: ReminderOccurrenceKey?,
        ): Boolean {
            val currentKey = this.state?.scheduledPlan?.key
            if (currentKey != expectedCurrentKey) return false
            this.state = state
            return true
        }

        override suspend fun findNextDeliverablePlan(
            taskId: Long,
            now: Instant,
            zoneId: ZoneId,
        ): ReminderPlan? = null

        override suspend fun getActiveTodoTaskForDelivery(key: ReminderOccurrenceKey): TodoTask? {
            deliveryChecks += key
            return activeTask?.takeIf { state?.scheduledPlan?.key == key }
        }
    }

    private class RecordingScheduler(
        private val repository: RecordingReminderRepository,
    ) : ReminderScheduler {
        override suspend fun capabilityStatus(
            capability: ReminderCapability,
        ): ReminderCapabilityStatus = ReminderCapabilityStatus.AVAILABLE

        override suspend fun scheduleExact(
            plan: ReminderPlan,
        ): ReminderScheduleStatus = ReminderScheduleStatus.SCHEDULED

        override suspend fun cancel(key: ReminderOccurrenceKey): ReminderScheduleStatus {
            repository.cancelledKeys += key
            return ReminderScheduleStatus.DISABLED
        }
    }

    private class RecordingPublisher : ReminderPublisher {
        val payloads = mutableListOf<ReminderNotificationPayload>()

        override suspend fun publish(payload: ReminderNotificationPayload) {
            payloads += payload
        }
    }

    private object FixedClock : AppClock {
        override val zoneId: ZoneId = ZoneId.of("Asia/Seoul")

        override fun now(): Instant = Instant.parse("2026-07-28T00:00:00Z")

        override fun today(): LocalDate = LocalDate.of(2026, 7, 28)
    }
}

class ReminderReceiverTestApplication : Application(), ReminderRuntimeProvider {
    var runtime: ReminderRuntime? = null

    override val deliverReminderUseCase: DeliverReminderUseCase
        get() = requireNotNull(runtime).deliverReminderUseCase

    override val reconcileAllRemindersUseCase: ReconcileAllRemindersUseCase
        get() = requireNotNull(runtime).reconcileAllRemindersUseCase
}

data class ReminderRuntime(
    val deliverReminderUseCase: DeliverReminderUseCase,
    val reconcileAllRemindersUseCase: ReconcileAllRemindersUseCase,
)
