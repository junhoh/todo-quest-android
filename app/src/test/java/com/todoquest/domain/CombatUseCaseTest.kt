package com.todoquest.domain

import com.todoquest.core.AppClock
import com.todoquest.domain.model.CombatEventKey
import com.todoquest.domain.model.CombatEventKind
import com.todoquest.domain.model.CombatLifecycleEvent
import com.todoquest.domain.model.CombatReconciliationResult
import com.todoquest.domain.model.CombatSnapshot
import com.todoquest.domain.model.CombatTransition
import com.todoquest.domain.model.CompletionResult
import com.todoquest.domain.model.CreateTaskInput
import com.todoquest.domain.model.FailureResult
import com.todoquest.domain.model.MonsterAttackSnapshot
import com.todoquest.domain.model.MonsterAttackResult
import com.todoquest.domain.model.MonsterAttackTrigger
import com.todoquest.domain.model.StatusEffectType
import com.todoquest.domain.model.MonsterGrade
import com.todoquest.domain.model.MonsterInstance
import com.todoquest.domain.model.MonsterSpecies
import com.todoquest.domain.model.MonsterStats
import com.todoquest.domain.model.OccurrenceStateConflictException
import com.todoquest.domain.model.PlayerAttackResult
import com.todoquest.domain.model.PlayerAttackSnapshot
import com.todoquest.domain.model.StageProgress
import com.todoquest.domain.model.TaskOccurrence
import com.todoquest.domain.model.TaskOccurrenceStatus
import com.todoquest.domain.model.TodoTask
import com.todoquest.domain.repository.CombatRepository
import com.todoquest.domain.repository.TaskRepository
import com.todoquest.domain.usecase.CombatProcessingDiagnosticSink
import com.todoquest.domain.usecase.CompleteOccurrenceUseCase
import com.todoquest.domain.usecase.FailOccurrenceUseCase
import com.todoquest.domain.usecase.ReconcileCombatUseCase
import com.todoquest.domain.usecase.UndoFailOccurrenceUseCase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class CombatUseCaseTest {
    private val occurrenceDate = LocalDate.of(2026, 7, 14)

    @Test
    fun completionSuccessProcessesPendingPlayerAttackAndReturnsEconomicResultUnchanged() = runTest {
        val completionResult = CompletionResult(
            awardedXp = 19,
            awardedGold = 7,
            alreadyRewarded = false,
            isOnTime = true,
            rewardEfficiencyBp = 5_000,
        )
        val taskRepository = FakeTaskRepository(completionResult)
        val combatRepository = FakeCombatRepository()
        val diagnostics = RecordingCombatDiagnostics()
        val useCase = CompleteOccurrenceUseCase(taskRepository, combatRepository, diagnostics)

        val actual = useCase(taskId = 7L, occurrenceDate = occurrenceDate)

        assertSame(completionResult, actual)
        assertEquals(listOf(7L to occurrenceDate), combatRepository.playerAttackInputs)
        assertEquals(emptyList<RecordedCombatFailure>(), diagnostics.failures)
    }

    @Test
    fun completionFailurePropagatesWithoutCallingCombat() = runTest {
        val failure = IllegalStateException("completion failed")
        val taskRepository = FakeTaskRepository(failure)
        val combatRepository = FakeCombatRepository()
        val diagnostics = RecordingCombatDiagnostics()
        val useCase = CompleteOccurrenceUseCase(taskRepository, combatRepository, diagnostics)

        try {
            useCase(taskId = 8L, occurrenceDate = occurrenceDate)
            fail("Expected completion failure")
        } catch (actual: IllegalStateException) {
            assertSame(failure, actual)
        }

        assertEquals(emptyList<Pair<Long, LocalDate>>(), combatRepository.playerAttackInputs)
        assertEquals(emptyList<RecordedCombatFailure>(), diagnostics.failures)
    }

    @Test
    fun combatFailureIsReportedWithoutChangingSuccessfulEconomicResult() = runTest {
        val completionResult = CompletionResult(
            awardedXp = 20,
            awardedGold = 10,
            alreadyRewarded = false,
        )
        val failure = IllegalStateException("combat unavailable")
        val taskRepository = FakeTaskRepository(completionResult)
        val combatRepository = FakeCombatRepository(playerAttackFailure = failure)
        val diagnostics = RecordingCombatDiagnostics()
        val useCase = CompleteOccurrenceUseCase(taskRepository, combatRepository, diagnostics)

        val actual = useCase(taskId = 9L, occurrenceDate = occurrenceDate)

        assertSame(completionResult, actual)
        assertEquals(listOf(9L to occurrenceDate), combatRepository.playerAttackInputs)
        assertEquals(
            listOf(RecordedCombatFailure(9L, occurrenceDate, failure)),
            diagnostics.failures,
        )
    }

    @Test
    fun combatCancellationIsRethrownAndNotReportedAsRetryableFailure() = runTest {
        val cancellation = CancellationException("cancel combat")
        val taskRepository = FakeTaskRepository(
            CompletionResult(awardedXp = 20, awardedGold = 10, alreadyRewarded = false),
        )
        val combatRepository = FakeCombatRepository(playerAttackFailure = cancellation)
        val diagnostics = RecordingCombatDiagnostics()
        val useCase = CompleteOccurrenceUseCase(taskRepository, combatRepository, diagnostics)

        try {
            useCase(taskId = 10L, occurrenceDate = occurrenceDate)
            fail("Expected cancellation")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }

        assertEquals(emptyList<RecordedCombatFailure>(), diagnostics.failures)
    }

    @Test
    fun alreadyRewardedOccurrenceDoesNotProcessAnotherPlayerAttack() = runTest {
        val completionResult = CompletionResult(
            awardedXp = 0,
            awardedGold = 0,
            alreadyRewarded = true,
        )
        val combatRepository = FakeCombatRepository()
        val useCase = CompleteOccurrenceUseCase(
            FakeTaskRepository(completionResult),
            combatRepository,
            RecordingCombatDiagnostics(),
        )

        assertSame(completionResult, useCase(taskId = 11L, occurrenceDate = occurrenceDate))
        assertEquals(emptyList<Pair<Long, LocalDate>>(), combatRepository.playerAttackInputs)
    }

    @Test
    fun newFailureProcessesImmediateMonsterAttackAndReturnsStoredResult() = runTest {
        val failureResult = FailureResult(wasAlreadyFailed = false)
        val taskRepository = FakeTaskRepository(failureOutcomes = listOf(failureResult))
        val combatRepository = FakeCombatRepository()
        val useCase = FailOccurrenceUseCase(
            taskRepository,
            combatRepository,
            RecordingCombatDiagnostics(),
        )

        val actual = useCase(taskId = 12L, occurrenceDate = occurrenceDate)

        assertSame(failureResult, actual)
        assertEquals(listOf(12L to occurrenceDate), taskRepository.failureInputs)
        assertEquals(listOf(12L to occurrenceDate), combatRepository.monsterAttackInputs)
    }

    @Test
    fun repeatedFailureDoesNotProcessAnotherMonsterAttack() = runTest {
        val repeated = FailureResult(wasAlreadyFailed = true)
        val combatRepository = FakeCombatRepository()
        val useCase = FailOccurrenceUseCase(
            FakeTaskRepository(failureOutcomes = listOf(repeated)),
            combatRepository,
            RecordingCombatDiagnostics(),
        )

        assertSame(repeated, useCase(taskId = 13L, occurrenceDate = occurrenceDate))
        assertEquals(emptyList<Pair<Long, LocalDate>>(), combatRepository.monsterAttackInputs)
    }

    @Test
    fun failureCombatFailureIsDiagnosedWithoutRollingBackStoredFailure() = runTest {
        val failureResult = FailureResult(wasAlreadyFailed = false)
        val combatFailure = IllegalStateException("combat unavailable")
        val taskRepository = FakeTaskRepository(failureOutcomes = listOf(failureResult))
        val combatRepository = FakeCombatRepository(monsterAttackFailure = combatFailure)
        val diagnostics = RecordingCombatDiagnostics()
        val useCase = FailOccurrenceUseCase(taskRepository, combatRepository, diagnostics)

        val actual = useCase(taskId = 14L, occurrenceDate = occurrenceDate)

        assertSame(failureResult, actual)
        assertEquals(listOf(14L to occurrenceDate), taskRepository.failureInputs)
        assertEquals(
            listOf(RecordedCombatFailure(14L, occurrenceDate, combatFailure)),
            diagnostics.monsterFailures,
        )
    }

    @Test
    fun failureCombatCancellationIsRethrownWithoutDiagnostic() = runTest {
        val cancellation = CancellationException("cancel monster combat")
        val diagnostics = RecordingCombatDiagnostics()
        val useCase = FailOccurrenceUseCase(
            FakeTaskRepository(
                failureOutcomes = listOf(FailureResult(wasAlreadyFailed = false)),
            ),
            FakeCombatRepository(monsterAttackFailure = cancellation),
            diagnostics,
        )

        try {
            useCase(taskId = 15L, occurrenceDate = occurrenceDate)
            fail("Expected cancellation")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }

        assertEquals(emptyList<RecordedCombatFailure>(), diagnostics.failures)
    }

    @Test
    fun undoFailureOnlyChangesDisplayStateAndAllowsFailureCommandAgain() = runTest {
        val taskRepository = FakeTaskRepository(
            failureOutcomes = listOf(
                FailureResult(wasAlreadyFailed = false),
                FailureResult(wasAlreadyFailed = false),
            ),
        )
        val combatRepository = FakeCombatRepository()
        val failUseCase = FailOccurrenceUseCase(
            taskRepository,
            combatRepository,
            RecordingCombatDiagnostics(),
        )
        val undoUseCase = UndoFailOccurrenceUseCase(taskRepository)

        failUseCase(taskId = 16L, occurrenceDate = occurrenceDate)
        undoUseCase(taskId = 16L, occurrenceDate = occurrenceDate)
        failUseCase(taskId = 16L, occurrenceDate = occurrenceDate)

        assertEquals(listOf(16L to occurrenceDate), taskRepository.undoFailureInputs)
        assertEquals(
            listOf(16L to occurrenceDate, 16L to occurrenceDate),
            combatRepository.monsterAttackInputs,
        )
    }

    @Test
    fun completedOccurrenceFailureConflictPropagatesWithoutCallingCombat() = runTest {
        val conflict = OccurrenceStateConflictException(
            taskId = 17L,
            occurrenceDate = occurrenceDate,
            currentStatus = TaskOccurrenceStatus.COMPLETED,
            requestedStatus = TaskOccurrenceStatus.FAILED,
        )
        val combatRepository = FakeCombatRepository()
        val useCase = FailOccurrenceUseCase(
            FakeTaskRepository(failureOutcomes = listOf(conflict)),
            combatRepository,
            RecordingCombatDiagnostics(),
        )

        try {
            useCase(taskId = 17L, occurrenceDate = occurrenceDate)
            fail("Expected occurrence state conflict")
        } catch (actual: OccurrenceStateConflictException) {
            assertSame(conflict, actual)
        }

        assertEquals(emptyList<Pair<Long, LocalDate>>(), combatRepository.monsterAttackInputs)
    }

    @Test
    fun failedOccurrenceCompletionConflictPropagatesWithoutCallingCombat() = runTest {
        val conflict = OccurrenceStateConflictException(
            taskId = 18L,
            occurrenceDate = occurrenceDate,
            currentStatus = TaskOccurrenceStatus.FAILED,
            requestedStatus = TaskOccurrenceStatus.COMPLETED,
        )
        val combatRepository = FakeCombatRepository()
        val useCase = CompleteOccurrenceUseCase(
            FakeTaskRepository(completionOutcome = conflict),
            combatRepository,
            RecordingCombatDiagnostics(),
        )

        try {
            useCase(taskId = 18L, occurrenceDate = occurrenceDate)
            fail("Expected occurrence state conflict")
        } catch (actual: OccurrenceStateConflictException) {
            assertSame(conflict, actual)
        }

        assertEquals(emptyList<Pair<Long, LocalDate>>(), combatRepository.playerAttackInputs)
    }

    @Test
    fun playerAndMonsterTransitionsUseStableDistinctEventKeys() {
        val processedAt = Instant.parse("2026-07-14T09:00:00Z")
        val before = combatSnapshot(monsterHp = 75, playerHp = 110)
        val playerAfter = combatSnapshot(monsterHp = 60, playerHp = 110)
        val monsterAfter = combatSnapshot(monsterHp = 75, playerHp = 99)
        val playerAttack = PlayerAttackSnapshot(
            taskId = 19L,
            occurrenceDateEpochDay = occurrenceDate.toEpochDay(),
            targetMonsterInstanceId = 100L,
            seed = 1L,
            roll = 1,
            wasCritical = false,
            rawDamage = 16,
            targetDefense = 7,
            finalDamage = 15,
            targetHpBefore = 75,
            targetHpAfter = 60,
            processedAt = processedAt,
        )
        val monsterAttack = MonsterAttackSnapshot(
            taskId = 19L,
            occurrenceDateEpochDay = occurrenceDate.toEpochDay(),
            trigger = MonsterAttackTrigger.MANUAL_FAILURE,
            sourceMonsterInstanceId = 100L,
            sourceMonsterLevel = 1,
            sourceRawDamage = 12,
            playerDefense = 8,
            playerMaxHp = 110,
            finalDamage = 11,
            playerHpBefore = 110,
            playerHpAfter = 99,
            wasLethal = false,
            revivedHp = null,
            processedAt = processedAt,
        )

        val playerTransition = CombatTransition.PlayerAttack(playerAttack, before, playerAfter)
        val monsterTransition = CombatTransition.MonsterAttack(monsterAttack, before, monsterAfter)

        assertEquals(
            CombatEventKey(
                kind = CombatEventKind.PLAYER_ATTACK,
                taskId = 19L,
                occurrenceDateEpochDay = occurrenceDate.toEpochDay(),
            ),
            playerTransition.eventKey,
        )
        assertEquals(
            CombatEventKey(
                kind = CombatEventKind.MONSTER_ATTACK,
                taskId = 19L,
                occurrenceDateEpochDay = occurrenceDate.toEpochDay(),
            ),
            monsterTransition.eventKey,
        )
        assertEquals(false, playerTransition.eventKey == monsterTransition.eventKey)
        assertSame(before, playerTransition.before)
        assertSame(playerAfter, playerTransition.after)
        assertSame(monsterAttack, monsterTransition.attack)
    }

    @Test
    fun monsterAttackTransitionPreservesOrderedStatusLifecycleWithDeterministicIds() {
        val occurrenceDate = LocalDate.of(2026, 7, 14)
        val occurrenceEpochDay = occurrenceDate.toEpochDay()
        val before = combatSnapshot(monsterHp = 75, playerHp = 1)
        val after = combatSnapshot(monsterHp = 75, playerHp = 44)
        val attackEventKey = CombatEventKey(
            kind = CombatEventKind.MONSTER_ATTACK,
            taskId = 19L,
            occurrenceDateEpochDay = occurrenceEpochDay,
        )
        val attack = MonsterAttackSnapshot(
            taskId = 19L,
            occurrenceDateEpochDay = occurrenceEpochDay,
            trigger = MonsterAttackTrigger.MISSED_DEADLINE,
            sourceMonsterInstanceId = 100L,
            sourceMonsterLevel = 1,
            sourceRawDamage = 12,
            playerDefense = 8,
            playerMaxHp = 110,
            finalDamage = 11,
            playerHpBefore = 1,
            playerHpAfter = 0,
            wasLethal = true,
            revivedHp = 44,
            processedAt = Instant.parse("2026-07-14T09:00:00Z"),
        )
        val mutationId = "monster-attack:19:$occurrenceEpochDay"
        val lifecycle = listOf(
            CombatLifecycleEvent.PlayerDefeated(
                eventId = "$mutationId:severe-injury:1:player-defeated",
                attackEventKey = attackEventKey,
                effectRevision = 1L,
                playerHpBefore = 1,
                playerMaxHpBeforeEffect = 110,
            ),
            CombatLifecycleEvent.StatusEffectApplied(
                eventId = "$mutationId:severe-injury:1:status-effect-applied",
                attackEventKey = attackEventKey,
                effectType = StatusEffectType.SEVERE_INJURY,
                effectRevision = 1L,
                effectiveMaxHp = 88,
            ),
            CombatLifecycleEvent.PlayerEmergencyRecovered(
                eventId = "$mutationId:severe-injury:1:player-emergency-recovered",
                attackEventKey = attackEventKey,
                effectRevision = 1L,
                recoveredHp = 44,
                effectiveMaxHp = 88,
            ),
        )

        val transition = CombatTransition.MonsterAttack(
            attack = attack,
            before = before,
            after = after,
            lifecycleEvents = lifecycle,
        )

        assertEquals(lifecycle, transition.lifecycleEvents)
        assertEquals(lifecycle.map { it.eventId }, transition.lifecycleEvents.map { it.eventId })
    }

    @Test
    fun reconcileUsesInjectedClockCurrentInstant() = runTest {
        val now = Instant.parse("2026-07-14T09:00:00Z")
        val expected = CombatReconciliationResult(
            playerAttacksProcessed = 2,
            monsterAttacksApplied = 1,
            monsterAttacksSkipped = 3,
        )
        val combatRepository = FakeCombatRepository(reconciliationResult = expected)
        val useCase = ReconcileCombatUseCase(combatRepository, FixedClock(now))

        assertSame(expected, useCase())
        assertEquals(listOf(now), combatRepository.reconciliationInputs)
    }

    private class FakeTaskRepository(
        private val completionOutcome: Any = CompletionResult(
            awardedXp = 0,
            awardedGold = 0,
            alreadyRewarded = true,
        ),
        private val failureOutcomes: List<Any> = listOf(
            FailureResult(wasAlreadyFailed = false),
        ),
    ) : TaskRepository {
        val failureInputs = mutableListOf<Pair<Long, LocalDate>>()
        val undoFailureInputs = mutableListOf<Pair<Long, LocalDate>>()
        private var failureOutcomeIndex = 0

        override fun observeOccurrences(
            rangeStart: LocalDate,
            rangeEnd: LocalDate,
        ): Flow<List<TaskOccurrence>> = emptyFlow()

        override suspend fun createTask(input: CreateTaskInput): Long = error("not used")

        override suspend fun updateTask(task: TodoTask) = error("not used")

        override suspend fun deleteTask(taskId: Long) = error("not used")

        override suspend fun completeOccurrence(
            taskId: Long,
            occurrenceDate: LocalDate,
        ): CompletionResult = when (completionOutcome) {
            is CompletionResult -> completionOutcome
            is Throwable -> throw completionOutcome
            else -> error("Unsupported completion outcome")
        }

        override suspend fun undoCompleteOccurrence(taskId: Long, occurrenceDate: LocalDate) =
            error("not used")

        override suspend fun failOccurrence(
            taskId: Long,
            occurrenceDate: LocalDate,
        ): FailureResult {
            failureInputs += taskId to occurrenceDate
            val outcome = failureOutcomes.getOrElse(failureOutcomeIndex) { failureOutcomes.last() }
            failureOutcomeIndex += 1
            return when (outcome) {
                is FailureResult -> outcome
                is Throwable -> throw outcome
                else -> error("Unsupported failure outcome")
            }
        }

        override suspend fun undoFailOccurrence(taskId: Long, occurrenceDate: LocalDate) {
            undoFailureInputs += taskId to occurrenceDate
        }
    }

    private class FakeCombatRepository(
        private val playerAttackFailure: Throwable? = null,
        private val monsterAttackFailure: Throwable? = null,
        private val reconciliationResult: CombatReconciliationResult =
            CombatReconciliationResult(playerAttacksProcessed = 0),
    ) : CombatRepository {
        val playerAttackInputs = mutableListOf<Pair<Long, LocalDate>>()
        val monsterAttackInputs = mutableListOf<Pair<Long, LocalDate>>()
        val reconciliationInputs = mutableListOf<Instant>()

        override val events = emptyFlow<CombatTransition>()

        override fun observeCombat(): Flow<CombatSnapshot> = emptyFlow()

        override suspend fun processPlayerAttack(
            taskId: Long,
            occurrenceDate: LocalDate,
        ): PlayerAttackResult {
            playerAttackInputs += taskId to occurrenceDate
            playerAttackFailure?.let { throw it }
            return PlayerAttackResult.NotFound
        }

        override suspend fun processPendingPlayerAttacks(): Int = error("not used")

        override suspend fun processFailedOccurrenceAttack(
            taskId: Long,
            occurrenceDate: LocalDate,
        ): MonsterAttackResult {
            monsterAttackInputs += taskId to occurrenceDate
            monsterAttackFailure?.let { throw it }
            return MonsterAttackResult.NotFound
        }

        override suspend fun processPendingFailureAttacks(): Int = error("not used")

        override suspend fun reconcileOverdue(now: Instant): CombatReconciliationResult {
            reconciliationInputs += now
            return reconciliationResult
        }
    }

    private class RecordingCombatDiagnostics : CombatProcessingDiagnosticSink {
        val failures = mutableListOf<RecordedCombatFailure>()
        val monsterFailures = mutableListOf<RecordedCombatFailure>()

        override fun reportPlayerAttackFailure(
            taskId: Long,
            occurrenceDate: LocalDate,
            failure: Throwable,
        ) {
            failures += RecordedCombatFailure(taskId, occurrenceDate, failure)
        }

        override fun reportMonsterAttackFailure(
            taskId: Long,
            occurrenceDate: LocalDate,
            failure: Throwable,
        ) {
            val recorded = RecordedCombatFailure(taskId, occurrenceDate, failure)
            failures += recorded
            monsterFailures += recorded
        }
    }

    private data class RecordedCombatFailure(
        val taskId: Long,
        val occurrenceDate: LocalDate,
        val failure: Throwable,
    )

    private fun combatSnapshot(monsterHp: Int, playerHp: Int) = CombatSnapshot(
        progress = StageProgress(
            stageNumber = 1,
            stageLevel = 1,
            activeMonsterInstanceId = 100L,
            lastReconciledAt = Instant.EPOCH,
            balanceVersion = 1,
        ),
        activeMonster = MonsterInstance(
            id = 100L,
            definitionId = "balanced",
            grade = MonsterGrade.NORMAL,
            stageNumber = 1,
            encounterNumber = 1,
            level = 1,
            currentHp = monsterHp,
            balanceVersion = 1,
        ),
        activeMonsterStats = MonsterStats(maxHp = 75, damage = 12, defense = 7),
        activeMonsterSpecies = MonsterSpecies.GOBLIN_SCOUT,
        playerCurrentHp = playerHp,
        playerMaxHp = 110,
    )

    private class FixedClock(
        private val instant: Instant,
    ) : AppClock {
        override val zoneId: ZoneId = ZoneId.of("UTC")

        override fun now(): Instant = instant

        override fun today(): LocalDate = LocalDate.ofInstant(instant, zoneId)
    }
}
