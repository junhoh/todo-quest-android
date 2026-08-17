package com.todoquest.notification

import android.app.AlarmManager
import android.app.Application
import android.content.Intent
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.todoquest.app.TodoQuestAppContainer
import com.todoquest.core.AppClock
import com.todoquest.data.local.TodoQuestDatabase
import com.todoquest.domain.model.ReminderOccurrenceKey
import com.todoquest.domain.model.ReminderPlan
import com.todoquest.domain.model.ReminderScheduleState
import com.todoquest.domain.model.ReminderScheduleStatus
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
import java.time.ZoneId
import java.util.concurrent.Executor
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = ReminderWorkerTestApplication::class, sdk = [35])
class ReminderReconciliationWorkerTest {
    private lateinit var application: ReminderWorkerTestApplication

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        application.deliver = null
        application.reconcileAll = null
    }

    @Test
    fun workerInvokesApplicationUseCaseAndReturnsSuccess() = runTest {
        val repository = ReconciliationRepository()
        installRuntime(repository)

        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(1, repository.reconciliationAttempts)
    }

    @Test
    fun transientDatabaseFailureRetriesAtMostThreeTimes() = runTest {
        installRuntime(
            ReconciliationRepository(
                failure = SQLiteDatabaseLockedException("database is locked"),
            ),
        )

        assertTrue(buildWorker(runAttemptCount = 0).doWork() is ListenableWorker.Result.Retry)
        assertTrue(
            buildWorker(runAttemptCount = ReminderReconciliationWorker.MAX_RETRY_ATTEMPTS)
                .doWork() is ListenableWorker.Result.Failure,
        )
    }

    @Test
    fun deterministicFailureDoesNotRetry() = runTest {
        installRuntime(
            ReconciliationRepository(
                failure = SQLiteException("no such table: task_reminders"),
            ),
        )

        assertTrue(buildWorker().doWork() is ListenableWorker.Result.Failure)
    }

    @Test
    fun restoreBroadcastsAndStartupHelperKeepOneIndependentUniqueWork() {
        installRuntime(
            ReconciliationRepository(
                failure = SQLiteDatabaseLockedException("keep restore work pending"),
            ),
        )
        val directExecutor = Executor(Runnable::run)
        WorkManagerTestInitHelper.initializeTestWorkManager(
            application,
            Configuration.Builder()
                .setExecutor(directExecutor)
                .setTaskExecutor(directExecutor)
                .build(),
        )
        val workManager = WorkManager.getInstance(application)
        val receiver = ReminderRestoreReceiver()

        ReminderReconciliationWork.enqueueStartup(workManager)
        listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        ).forEach { action ->
            receiver.onReceive(application, Intent(action))
        }

        val reminderWork = workManager
            .getWorkInfosForUniqueWork(ReminderReconciliationWork.UNIQUE_WORK_NAME)
            .get()
        assertEquals(1, reminderWork.count { !it.state.isFinished })
        assertTrue(reminderWork.single().state == WorkInfo.State.ENQUEUED)
        assertTrue(
            workManager
                .getWorkInfosForUniqueWork("combat-reconciliation-startup")
                .get()
                .isEmpty(),
        )
    }

    @Test
    fun applicationContainerKeepsInjectedReminderRuntimeAsApplicationScopedInstances() {
        val database = Room.inMemoryDatabaseBuilder(application, TodoQuestDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repository = ReconciliationRepository()
        val publisher = ReminderPublisher { }
        try {
            val container = TodoQuestAppContainer(
                database = database,
                clock = FixedClock,
                reminderRepository = repository,
                reminderScheduler = AvailableScheduler,
                reminderPublisher = publisher,
            )

            assertSame(repository, container.reminderRepository)
            assertSame(AvailableScheduler, container.reminderScheduler)
            assertSame(publisher, container.reminderPublisher)
            assertSame(container.deliverReminderUseCase, container.deliverReminderUseCase)
            assertSame(
                container.reconcileAllRemindersUseCase,
                container.reconcileAllRemindersUseCase,
            )
        } finally {
            database.close()
        }
    }

    private fun installRuntime(repository: ReconciliationRepository) {
        val reconcileTask = ReconcileTaskReminderUseCase(
            repository = repository,
            scheduler = AvailableScheduler,
            clock = FixedClock,
            diagnosticSink = { _, _, _ -> },
        )
        application.reconcileAll = ReconcileAllRemindersUseCase(repository, reconcileTask)
        application.deliver = DeliverReminderUseCase(
            repository = repository,
            publisher = { },
            reconcileTaskReminder = reconcileTask,
            diagnosticSink = { _, _, _ -> },
        )
    }

    private fun buildWorker(runAttemptCount: Int = 0): ReminderReconciliationWorker =
        TestListenableWorkerBuilder<ReminderReconciliationWorker>(application)
            .setRunAttemptCount(runAttemptCount)
            .build()

    private class ReconciliationRepository(
        private val failure: Throwable? = null,
    ) : ReminderRepository {
        var reconciliationAttempts = 0

        override suspend fun getConfiguredTask(taskId: Long): TodoTask? = null

        override suspend fun getConfiguredTaskIds(): List<Long> {
            reconciliationAttempts += 1
            failure?.let { throw it }
            return emptyList()
        }

        override suspend fun getScheduleState(taskId: Long): ReminderScheduleState? = null

        override suspend fun updateScheduleState(
            state: ReminderScheduleState,
            expectedCurrentKey: ReminderOccurrenceKey?,
        ): Boolean = true

        override suspend fun findNextDeliverablePlan(
            taskId: Long,
            now: Instant,
            zoneId: ZoneId,
        ): ReminderPlan? = null

        override suspend fun getActiveTodoTaskForDelivery(key: ReminderOccurrenceKey): TodoTask? = null
    }

    private object AvailableScheduler : ReminderScheduler {
        override suspend fun capabilityStatus(
            capability: ReminderCapability,
        ): ReminderCapabilityStatus = ReminderCapabilityStatus.AVAILABLE

        override suspend fun scheduleExact(
            plan: ReminderPlan,
        ): ReminderScheduleStatus = ReminderScheduleStatus.SCHEDULED

        override suspend fun cancel(
            key: ReminderOccurrenceKey,
        ): ReminderScheduleStatus = ReminderScheduleStatus.DISABLED
    }

    private object FixedClock : AppClock {
        override val zoneId: ZoneId = ZoneId.of("Asia/Seoul")

        override fun now(): Instant = Instant.parse("2026-07-28T00:00:00Z")

        override fun today(): LocalDate = LocalDate.of(2026, 7, 28)
    }
}

class ReminderWorkerTestApplication : Application(), ReminderRuntimeProvider {
    var deliver: DeliverReminderUseCase? = null
    var reconcileAll: ReconcileAllRemindersUseCase? = null

    override val deliverReminderUseCase: DeliverReminderUseCase
        get() = requireNotNull(deliver)

    override val reconcileAllRemindersUseCase: ReconcileAllRemindersUseCase
        get() = requireNotNull(reconcileAll)
}
