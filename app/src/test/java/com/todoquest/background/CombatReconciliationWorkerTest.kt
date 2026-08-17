package com.todoquest.background

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteDatabaseLockedException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.todoquest.app.TodoQuestAppContainer
import com.todoquest.app.TodoQuestContainerOwner
import com.todoquest.core.AppClock
import com.todoquest.data.local.CharacterCurrentStateEntity
import com.todoquest.data.local.TodoQuestDatabase
import com.todoquest.data.local.TodoTaskEntity
import com.todoquest.domain.model.CombatEventStatus
import com.todoquest.domain.model.CombatReconciliationResult
import com.todoquest.domain.model.CombatSnapshot
import com.todoquest.domain.model.CombatTransition
import com.todoquest.domain.model.CreateTaskInput
import com.todoquest.domain.model.MonsterAttackTrigger
import com.todoquest.domain.model.PlayerAttackResult
import com.todoquest.domain.model.RecurrenceRule
import com.todoquest.domain.model.StatusEffectType
import com.todoquest.domain.model.TaskCategory
import com.todoquest.domain.model.TaskDifficulty
import com.todoquest.domain.model.TaskOccurrenceStatus
import com.todoquest.domain.repository.CombatRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = CombatWorkerTestApplication::class)
class CombatReconciliationWorkerTest {
    private lateinit var application: CombatWorkerTestApplication
    private lateinit var database: TodoQuestDatabase

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(application, TodoQuestDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        CombatWorkerTestApplication.container = null
        database.close()
    }

    @Test
    fun schedulerKeepsOneStartupAndOneFifteenMinutePeriodicRequest() {
        val repository = RecordingCombatRepository(
            failure = SQLiteDatabaseLockedException("keep startup work pending"),
        )
        installContainer(repository = repository)
        val directExecutor = Executor(Runnable::run)
        WorkManagerTestInitHelper.initializeTestWorkManager(
            application,
            Configuration.Builder()
                .setExecutor(directExecutor)
                .setTaskExecutor(directExecutor)
                .build(),
        )
        val workManager = WorkManager.getInstance(application)

        CombatReconciliationWork.enqueue(workManager)
        CombatReconciliationWork.enqueue(workManager)

        val startup = workManager
            .getWorkInfosForUniqueWork(CombatReconciliationWork.STARTUP_WORK_NAME)
            .get()
        val periodic = workManager
            .getWorkInfosForUniqueWork(CombatReconciliationWork.PERIODIC_WORK_NAME)
            .get()
        assertEquals(1, startup.count { !it.state.isFinished })
        assertEquals(1, periodic.count { !it.state.isFinished })
        assertTrue(periodic.single().state == WorkInfo.State.ENQUEUED)
        assertEquals(Constraints.NONE, startup.single().constraints)
        assertEquals(Constraints.NONE, periodic.single().constraints)
        assertEquals(15L, CombatReconciliationWork.REPEAT_INTERVAL_MINUTES)
        assertEquals(
            TimeUnit.MINUTES.toMillis(15L),
            periodic.single().periodicityInfo!!.repeatIntervalMillis,
        )
    }

    @Test
    fun workerInvokesContainerUseCaseAndReturnsSuccess() = runTest {
        val now = Instant.parse("2026-07-21T02:00:00Z")
        val repository = RecordingCombatRepository()
        val container = installContainer(repository = repository, clock = MutableClock(now))
        val worker = buildWorker()

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(listOf(now), repository.reconciliationInputs)
        assertSame(container, application.todoQuestContainer)
        assertSame(database, application.todoQuestContainer.database)
    }

    @Test
    fun transientDatabaseLockRetriesOnlyUpToConfiguredLimit() = runTest {
        installContainer(
            repository = RecordingCombatRepository(
                failure = SQLiteDatabaseLockedException("database is locked"),
            ),
        )

        assertTrue(buildWorker(runAttemptCount = 0).doWork() is ListenableWorker.Result.Retry)
        assertTrue(
            buildWorker(runAttemptCount = CombatReconciliationWorker.MAX_RETRY_ATTEMPTS)
                .doWork() is ListenableWorker.Result.Failure,
        )
    }

    @Test
    fun deterministicSchemaAndConfigFailuresDoNotRetry() = runTest {
        installContainer(
            repository = RecordingCombatRepository(
                failure = SQLiteException("no such table: combat_progress"),
            ),
        )
        assertTrue(buildWorker().doWork() is ListenableWorker.Result.Failure)

        installContainer(
            repository = RecordingCombatRepository(
                failure = IllegalStateException("Unknown monster balance version: 999"),
            ),
        )

        assertTrue(buildWorker().doWork() is ListenableWorker.Result.Failure)
    }

    @Test
    fun concurrentWorkersApplyOneRoomEventAndOneHpChange() = runTest {
        val clock = MutableClock(Instant.parse("2026-07-20T00:00:00Z"))
        val container = installContainer(clock = clock)
        val initial = container.combatRepository.observeCombat().first()
        database.characterProfileDao().upsertCurrentState(
            CharacterCurrentStateEntity(
                characterId = 1L,
                currentHp = 1,
                balanceVersion = 1,
                updatedAtEpochMillis = clock.now().toEpochMilli(),
            ),
        )
        val dueDate = LocalDate.of(2026, 7, 20)
        database.todoTaskDao().insert(
            TodoTaskEntity(
                id = 42L,
                recurrenceSeriesId = 42L,
                title = "Quest 42",
                memo = "",
                startDateEpochDay = dueDate.toEpochDay(),
                endDateEpochDay = null,
                timeMinuteOfDay = null,
                difficulty = TaskDifficulty.MEDIUM.name,
                category = "General",
                recurrenceRule = RecurrenceRule.NONE.name,
                createdAtEpochMillis = clock.now().toEpochMilli(),
                updatedAtEpochMillis = clock.now().toEpochMilli(),
                deletedAtEpochMillis = null,
            ),
        )
        clock.instant = Instant.parse("2026-07-21T01:00:00Z")

        val results = listOf(
            async(Dispatchers.Default) { buildWorker().doWork() },
            async(Dispatchers.Default) { buildWorker().doWork() },
        ).awaitAll()

        assertTrue(results.all { it is ListenableWorker.Result.Success })
        assertNotNull(database.combatDao().getMonsterAttackEvent(42L, dueDate.toEpochDay()))
        val currentHp = database.characterProfileDao().getCurrentState()!!.currentHp
        assertEquals(44, currentHp)
        val injury = database.statusEffectDao().getStatusEffect(
            characterId = 1L,
            effectType = StatusEffectType.SEVERE_INJURY.name,
        )
        assertNotNull(injury)
        assertEquals(1L, injury?.revision)
        assertEquals(3, injury?.remainingRecoveryCompletions)
        assertEquals(
            initial.activeMonster.id,
            database.combatDao().getCombatProgress()?.activeMonsterInstanceId,
        )
    }

    @Test
    fun workerUsesApplicationRepositoryForPendingPlayerManualAndDeadlineAttacks() = runTest {
        val dueDate = LocalDate.of(2026, 7, 20)
        val clock = MutableClock(Instant.parse("2026-07-20T00:00:00Z"))
        val container = installContainer(clock = clock)
        container.combatRepository.observeCombat().first()
        val playerTaskId = container.taskRepository.createTask(taskInput("pending player", dueDate))
        val manualTaskId = container.taskRepository.createTask(taskInput("pending manual", dueDate))
        val deadlineTaskId = container.taskRepository.createTask(taskInput("deadline", dueDate))
        container.taskRepository.completeOccurrence(playerTaskId, dueDate)
        container.taskRepository.failOccurrence(manualTaskId, dueDate)
        clock.instant = Instant.parse("2026-07-21T01:00:00Z")

        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(
            CombatEventStatus.APPLIED.name,
            database.combatDao().getPlayerAttackEvent(playerTaskId, dueDate.toEpochDay())?.status,
        )
        assertEquals(
            MonsterAttackTrigger.MANUAL_FAILURE.name,
            database.combatDao().getMonsterAttackEvent(manualTaskId, dueDate.toEpochDay())?.trigger,
        )
        assertEquals(
            MonsterAttackTrigger.MISSED_DEADLINE.name,
            database.combatDao().getMonsterAttackEvent(deadlineTaskId, dueDate.toEpochDay())?.trigger,
        )
        assertEquals(
            TaskOccurrenceStatus.FAILED,
            container.taskRepository.observeOccurrences(dueDate, dueDate).first()
                .single { it.taskId == manualTaskId }.status,
        )

        container.undoFailOccurrenceUseCase(manualTaskId, dueDate)

        assertEquals(
            TaskOccurrenceStatus.TODO,
            container.taskRepository.observeOccurrences(dueDate, dueDate).first()
                .single { it.taskId == manualTaskId }.status,
        )
        assertEquals(
            MonsterAttackTrigger.MANUAL_FAILURE.name,
            database.combatDao().getMonsterAttackEvent(manualTaskId, dueDate.toEpochDay())?.trigger,
        )
    }

    private fun installContainer(
        repository: CombatRepository? = null,
        clock: MutableClock = MutableClock(),
    ): TodoQuestAppContainer {
        val container = TodoQuestAppContainer(
            database = database,
            clock = clock,
            combatRepository = repository,
        )
        CombatWorkerTestApplication.container = container
        return container
    }

    private fun buildWorker(runAttemptCount: Int = 0): CombatReconciliationWorker =
        TestListenableWorkerBuilder<CombatReconciliationWorker>(application)
            .setRunAttemptCount(runAttemptCount)
            .build()

    private fun taskInput(title: String, date: LocalDate) = CreateTaskInput(
        title = title,
        memo = "",
        startDate = date,
        time = null,
        difficulty = TaskDifficulty.MEDIUM,
        category = TaskCategory.DEFAULT,
        recurrenceRule = RecurrenceRule.NONE,
    )

    private class RecordingCombatRepository(
        private val failure: Throwable? = null,
    ) : CombatRepository {
        val reconciliationInputs = mutableListOf<Instant>()

        override val events = emptyFlow<CombatTransition>()

        override fun observeCombat(): Flow<CombatSnapshot> = emptyFlow()

        override suspend fun processPlayerAttack(
            taskId: Long,
            occurrenceDate: LocalDate,
        ): PlayerAttackResult = error("not used")

        override suspend fun processPendingPlayerAttacks(): Int = error("not used")

        override suspend fun reconcileOverdue(now: Instant): CombatReconciliationResult {
            reconciliationInputs += now
            failure?.let { throw it }
            return CombatReconciliationResult(playerAttacksProcessed = 0)
        }
    }

    private class MutableClock(
        var instant: Instant = Instant.parse("2026-07-21T00:00:00Z"),
    ) : AppClock {
        override val zoneId: ZoneId = ZoneId.of("UTC")

        override fun now(): Instant = instant

        override fun today(): LocalDate = LocalDate.ofInstant(instant, zoneId)
    }
}

class CombatWorkerTestApplication : Application(), TodoQuestContainerOwner {
    override val todoQuestContainer: TodoQuestAppContainer
        get() = requireNotNull(container)

    companion object {
        var container: TodoQuestAppContainer? = null
    }
}
