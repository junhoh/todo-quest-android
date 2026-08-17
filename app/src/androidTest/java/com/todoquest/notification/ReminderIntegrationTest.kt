package com.todoquest.notification

import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.todoquest.app.TodoQuestAppContainer
import com.todoquest.app.TodoQuestInstrumentedTestApplication
import com.todoquest.core.AppClock
import com.todoquest.data.local.TodoQuestDatabase
import com.todoquest.domain.model.CreateTaskInput
import com.todoquest.domain.model.RecurrenceRule
import com.todoquest.domain.model.ReminderMode
import com.todoquest.domain.model.ReminderNotificationPayload
import com.todoquest.domain.model.ReminderOccurrenceKey
import com.todoquest.domain.model.ReminderPlan
import com.todoquest.domain.model.ReminderScheduleStatus
import com.todoquest.domain.model.ReminderSetting
import com.todoquest.domain.model.TaskCategory
import com.todoquest.domain.model.TaskDifficulty
import com.todoquest.domain.model.UpdateTaskInput
import com.todoquest.domain.repository.ReminderCapability
import com.todoquest.domain.repository.ReminderCapabilityStatus
import com.todoquest.domain.repository.ReminderPublisher
import com.todoquest.domain.repository.ReminderScheduler
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderIntegrationTest {
    private val application =
        ApplicationProvider.getApplicationContext<TodoQuestInstrumentedTestApplication>()
    private var testDatabase: TodoQuestDatabase? = null

    @After
    fun tearDown() {
        application.installTestContainer(null)
        testDatabase?.close()
    }

    @Test
    fun productionGraphPersistsMutationAndUsesCurrentPlatformCapabilityWithoutLosingTask() {
        val container = application.todoQuestContainer
        val runtime = application as ReminderRuntimeProvider
        assertSame(container.deliverReminderUseCase, runtime.deliverReminderUseCase)
        assertSame(
            container.reconcileAllRemindersUseCase,
            runtime.reconcileAllRemindersUseCase,
        )

        val trigger = ZonedDateTime.now(container.clock.zoneId)
            .plusMinutes(10)
            .withSecond(0)
            .withNano(0)
        val setting = ReminderSetting(ReminderMode.CUSTOM_TIME, trigger.toLocalTime())
        val created = runBlocking {
            container.createTaskUseCase(
                CreateTaskInput(
                    title = "실제 알림 연결 일정",
                    memo = "",
                    startDate = trigger.toLocalDate(),
                    time = null,
                    difficulty = TaskDifficulty.MEDIUM,
                    category = TaskCategory.DEFAULT,
                    recurrenceRule = RecurrenceRule.NONE,
                    reminderSetting = setting,
                ),
            )
        }

        assertNotNull(runBlocking { container.taskRepository.getTask(created.taskId) })
        val createdState = runBlocking {
            container.reminderRepository.getScheduleState(created.taskId)
        }
        assertEquals(created.reminderStatus, createdState?.status)
        val expectedStatus = when (
            container.reminderCapabilityAdapter!!.status(ReminderCapability.POST_NOTIFICATIONS)
        ) {
            ReminderCapabilityStatus.REQUIRED ->
                ReminderScheduleStatus.POST_NOTIFICATIONS_REQUIRED
            ReminderCapabilityStatus.CHANNEL_DISABLED ->
                ReminderScheduleStatus.NOTIFICATION_CHANNEL_DISABLED
            ReminderCapabilityStatus.AVAILABLE -> when (
                container.reminderCapabilityAdapter.status(ReminderCapability.EXACT_ALARM)
            ) {
                ReminderCapabilityStatus.REQUIRED ->
                    ReminderScheduleStatus.EXACT_ALARM_ACCESS_REQUIRED
                ReminderCapabilityStatus.AVAILABLE -> ReminderScheduleStatus.SCHEDULED
                ReminderCapabilityStatus.CHANNEL_DISABLED -> ReminderScheduleStatus.ERROR
            }
        }
        assertEquals(expectedStatus, created.reminderStatus)
        if (created.reminderStatus == ReminderScheduleStatus.SCHEDULED) {
            assertNotNull(findExistingAlarmPendingIntent(application, createdState!!.scheduledPlan!!.key))
        }

        val updated = runBlocking {
            container.updateTaskUseCase(
                UpdateTaskInput(
                    taskId = created.taskId,
                    effectiveDate = trigger.toLocalDate(),
                    title = "알림 해제 일정",
                    memo = "",
                    time = null,
                    difficulty = TaskDifficulty.MEDIUM,
                    category = TaskCategory.DEFAULT,
                    recurrenceRule = RecurrenceRule.NONE,
                    reminderSetting = ReminderSetting(),
                ),
            )
        }

        assertEquals(ReminderScheduleStatus.DISABLED, updated.reminderStatus)
        assertEquals(ReminderMode.NONE, runBlocking {
            container.taskRepository.getTask(created.taskId)?.reminderSetting?.mode
        })
        assertNull(runBlocking {
            container.reminderRepository.getScheduleState(created.taskId)?.scheduledPlan
        })
    }

    @Test
    fun productionDatabaseMigrationChainIncludesVersion14To15() {
        val migration = TodoQuestAppContainer.PRODUCTION_MIGRATIONS.single {
            it.startVersion == 14 && it.endVersion == 15
        }

        assertSame(TodoQuestDatabase.MIGRATION_14_15, migration)
    }

    @Test
    fun receiverPublishesCurrentOccurrenceOnceAndIgnoresDuplicateCallback() {
        val database = Room.inMemoryDatabaseBuilder(application, TodoQuestDatabase::class.java)
            .build()
        testDatabase = database
        val scheduler = RecordingScheduler()
        val publisher = RecordingPublisher()
        val container = TodoQuestAppContainer(
            database = database,
            clock = FixedClock,
            reminderScheduler = scheduler,
            reminderPublisher = publisher,
        )
        application.installTestContainer(container)
        val created = runBlocking {
            container.createTaskUseCase(
                CreateTaskInput(
                    title = "수신기 통합 일정",
                    memo = "메모",
                    startDate = FixedClock.today(),
                    time = null,
                    difficulty = TaskDifficulty.MEDIUM,
                    category = TaskCategory.DEFAULT,
                    recurrenceRule = RecurrenceRule.NONE,
                    reminderSetting = ReminderSetting(
                        ReminderMode.CUSTOM_TIME,
                        LocalTime.of(10, 0),
                    ),
                ),
            )
        }
        assertEquals(ReminderScheduleStatus.SCHEDULED, created.reminderStatus)
        val key = requireNotNull(scheduler.scheduledPlan).key
        val receiver = ReminderAlarmReceiver()

        receiver.onReceive(application, ReminderAlarmIntents.alarmIntent(application, key))

        assertTrue(publisher.firstPublish.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(key), publisher.payloads.map { it.key })
        assertEquals(ReminderScheduleStatus.DELIVERED, runBlocking {
            container.reminderRepository.getScheduleState(created.taskId)?.status
        })

        receiver.onReceive(application, ReminderAlarmIntents.alarmIntent(application, key))
        Thread.sleep(300)
        assertEquals(1, publisher.payloads.size)
    }

    @Test
    fun receiverClaimsStagedPendingPlanBeforeSchedulerFinalizesWithoutDuplicateDelivery() {
        val database = Room.inMemoryDatabaseBuilder(application, TodoQuestDatabase::class.java)
            .build()
        testDatabase = database
        val publisher = RecordingPublisher()
        val scheduler = CallbackDuringScheduleScheduler(application, publisher)
        val container = TodoQuestAppContainer(
            database = database,
            clock = FixedClock,
            reminderScheduler = scheduler,
            reminderPublisher = publisher,
        )
        scheduler.statusProvider = { taskId ->
            container.reminderRepository.getScheduleState(taskId)?.status
        }
        application.installTestContainer(container)

        val created = runBlocking {
            container.createTaskUseCase(
                CreateTaskInput(
                    title = "즉시 발화 통합 일정",
                    memo = "",
                    startDate = FixedClock.today(),
                    time = null,
                    difficulty = TaskDifficulty.MEDIUM,
                    category = TaskCategory.DEFAULT,
                    recurrenceRule = RecurrenceRule.NONE,
                    reminderSetting = ReminderSetting(
                        ReminderMode.CUSTOM_TIME,
                        LocalTime.of(10, 0),
                    ),
                ),
            )
        }

        assertEquals(ReminderScheduleStatus.PENDING, scheduler.observedStatus)
        assertTrue(scheduler.deliveryFinishedDuringSchedule)
        assertEquals(ReminderScheduleStatus.DELIVERED, created.reminderStatus)
        assertEquals(ReminderScheduleStatus.DELIVERED, runBlocking {
            container.reminderRepository.getScheduleState(created.taskId)?.status
        })
        assertEquals(1, publisher.payloads.size)
        assertEquals(listOf(requireNotNull(scheduler.scheduledPlan).key), scheduler.cancelledKeys)

        ReminderAlarmReceiver().onReceive(
            application,
            ReminderAlarmIntents.alarmIntent(
                application,
                requireNotNull(scheduler.scheduledPlan).key,
            ),
        )
        Thread.sleep(300)
        assertEquals(1, publisher.payloads.size)
    }

    private fun findExistingAlarmPendingIntent(
        context: Context,
        key: ReminderOccurrenceKey,
    ): PendingIntent? = PendingIntent.getBroadcast(
        context,
        0,
        ReminderAlarmIntents.alarmIntent(context, key),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )

    private class RecordingScheduler : ReminderScheduler {
        @Volatile
        var scheduledPlan: ReminderPlan? = null

        override suspend fun capabilityStatus(
            capability: ReminderCapability,
        ): ReminderCapabilityStatus = ReminderCapabilityStatus.AVAILABLE

        override suspend fun scheduleExact(plan: ReminderPlan): ReminderScheduleStatus {
            scheduledPlan = plan
            return ReminderScheduleStatus.SCHEDULED
        }

        override suspend fun cancel(key: ReminderOccurrenceKey): ReminderScheduleStatus {
            scheduledPlan = null
            return ReminderScheduleStatus.DISABLED
        }
    }

    private class CallbackDuringScheduleScheduler(
        private val context: Context,
        private val publisher: RecordingPublisher,
    ) : ReminderScheduler {
        lateinit var statusProvider: suspend (Long) -> ReminderScheduleStatus?

        @Volatile
        var observedStatus: ReminderScheduleStatus? = null

        @Volatile
        var deliveryFinishedDuringSchedule: Boolean = false

        @Volatile
        var scheduledPlan: ReminderPlan? = null

        val cancelledKeys = mutableListOf<ReminderOccurrenceKey>()

        override suspend fun capabilityStatus(
            capability: ReminderCapability,
        ): ReminderCapabilityStatus = ReminderCapabilityStatus.AVAILABLE

        override suspend fun scheduleExact(plan: ReminderPlan): ReminderScheduleStatus {
            scheduledPlan = plan
            observedStatus = statusProvider(plan.key.taskId)
            ReminderAlarmReceiver().onReceive(
                context,
                ReminderAlarmIntents.alarmIntent(context, plan.key),
            )
            deliveryFinishedDuringSchedule = publisher.firstPublish.await(5, TimeUnit.SECONDS)
            return ReminderScheduleStatus.SCHEDULED
        }

        override suspend fun cancel(key: ReminderOccurrenceKey): ReminderScheduleStatus {
            cancelledKeys += key
            return ReminderScheduleStatus.DISABLED
        }
    }

    private class RecordingPublisher : ReminderPublisher {
        val firstPublish = CountDownLatch(1)
        val payloads = mutableListOf<ReminderNotificationPayload>()

        override suspend fun publish(payload: ReminderNotificationPayload) {
            synchronized(payloads) { payloads += payload }
            firstPublish.countDown()
        }
    }

    private object FixedClock : AppClock {
        override val zoneId: ZoneId = ZoneId.of("Asia/Seoul")

        override fun now(): Instant = Instant.parse("2026-07-28T00:00:00Z")

        override fun today(): LocalDate = LocalDate.of(2026, 7, 28)
    }
}

/**
 * Seeds the production graph for the external process-absent smoke flow.
 *
 * Run this fixture only after verifying that notification permission and exact-alarm access are
 * already available. After it exits, stop the target with `cmd activity stop-app com.todoquest`
 * (or a normal process kill), then verify that the reminder notification appears. Do not use
 * force-stop because Android intentionally cancels the package's PendingIntents.
 */
@RunWith(AndroidJUnit4::class)
@ExternalReminderSmokeFixture
class ReminderBackgroundSmokeFixtureTest {
    private val application =
        ApplicationProvider.getApplicationContext<TodoQuestInstrumentedTestApplication>()

    @Test
    fun seedCustomReminderFifteenToSixtySecondsAhead() {
        val container = application.todoQuestContainer
        val capabilityAdapter = requireNotNull(container.reminderCapabilityAdapter)
        assertEquals(
            ReminderCapabilityStatus.AVAILABLE,
            capabilityAdapter.status(ReminderCapability.POST_NOTIFICATIONS),
        )
        assertEquals(
            ReminderCapabilityStatus.AVAILABLE,
            capabilityAdapter.status(ReminderCapability.EXACT_ALARM),
        )

        var now = ZonedDateTime.now(container.clock.zoneId)
        while (now.second > MAX_SECONDS_FOR_NEXT_MINUTE_TRIGGER) {
            Thread.sleep(SMOKE_ALIGNMENT_POLL_MILLIS)
            now = ZonedDateTime.now(container.clock.zoneId)
        }
        val occurrenceDateTime = now
            .plusMinutes(1)
            .withSecond(0)
            .withNano(0)
        val created = runBlocking {
            container.createTaskUseCase(
                CreateTaskInput(
                    title = "백그라운드 알림 스모크",
                    memo = "프로세스 종료 뒤 exact alarm 게시 확인",
                    startDate = occurrenceDateTime.toLocalDate(),
                    time = null,
                    difficulty = TaskDifficulty.MEDIUM,
                    category = TaskCategory.DEFAULT,
                    recurrenceRule = RecurrenceRule.NONE,
                    reminderSetting = ReminderSetting(
                        mode = ReminderMode.CUSTOM_TIME,
                        customTime = occurrenceDateTime.toLocalTime(),
                    ),
                ),
            )
        }
        assertEquals(ReminderScheduleStatus.SCHEDULED, created.reminderStatus)
        val scheduledPlan = requireNotNull(
            runBlocking {
                container.reminderRepository.getScheduleState(created.taskId)?.scheduledPlan
            },
        )
        val delaySeconds = scheduledPlan.triggerAt.epochSecond - Instant.now().epochSecond
        assertTrue(delaySeconds in MIN_SMOKE_DELAY_SECONDS..MAX_SMOKE_DELAY_SECONDS)
        Log.i(
            LOG_TAG,
            "taskId=${created.taskId} occurrence=${scheduledPlan.key.occurrenceDate} " +
                "triggerAt=${scheduledPlan.triggerAt} delaySeconds=$delaySeconds",
        )
    }

    private companion object {
        const val LOG_TAG = "TodoQuestReminderSmoke"
        const val MAX_SECONDS_FOR_NEXT_MINUTE_TRIGGER = 45
        const val SMOKE_ALIGNMENT_POLL_MILLIS = 250L
        const val MIN_SMOKE_DELAY_SECONDS = 15L
        const val MAX_SMOKE_DELAY_SECONDS = 60L
    }
}

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class ExternalReminderSmokeFixture
