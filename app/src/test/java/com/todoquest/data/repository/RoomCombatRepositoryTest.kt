package com.todoquest.data.repository

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.todoquest.core.AppClock
import com.todoquest.data.local.CharacterCurrentStateEntity
import com.todoquest.data.local.CharacterProfileEntity
import com.todoquest.data.local.CombatProgressEntity
import com.todoquest.data.local.CompletionLogEntity
import com.todoquest.data.local.EquipmentCatalogSeeder
import com.todoquest.data.local.FailureLogEntity
import com.todoquest.data.local.MonsterAttackEventEntity
import com.todoquest.data.local.MonsterInstanceEntity
import com.todoquest.data.local.OwnedEquipmentEntity
import com.todoquest.data.local.PlayerAttackEventEntity
import com.todoquest.data.local.StatusEffectRecoveryOccurrenceEntity
import com.todoquest.data.local.TodoTaskEntity
import com.todoquest.data.local.TodoQuestDatabase
import com.todoquest.domain.model.BattleEffectEvent
import com.todoquest.domain.model.BattleEntityRef
import com.todoquest.domain.model.CharacterStatBalanceConfig
import com.todoquest.domain.model.CombatLifecycleEvent
import com.todoquest.domain.model.CombatRewardBalanceCatalog
import com.todoquest.domain.model.CombatEventStatus
import com.todoquest.domain.model.CombatEventKind
import com.todoquest.domain.model.CombatTransition
import com.todoquest.domain.model.EquipmentSlot
import com.todoquest.domain.model.MonsterAttackSkipReason
import com.todoquest.domain.model.MonsterAttackResult
import com.todoquest.domain.model.MonsterAttackTrigger
import com.todoquest.domain.model.MonsterBalanceConfig
import com.todoquest.domain.model.MonsterCatalog
import com.todoquest.domain.model.MonsterGrade
import com.todoquest.domain.model.MonsterSpecies
import com.todoquest.domain.model.MonsterType
import com.todoquest.domain.model.PlayerAttackResult
import com.todoquest.domain.model.RecurrenceRule
import com.todoquest.domain.model.StatusEffectType
import com.todoquest.domain.model.TaskDifficulty
import com.todoquest.domain.model.TaskDifficultyCombatBalanceCatalog
import com.todoquest.domain.model.TaskOccurrenceStatus
import com.todoquest.domain.usecase.MonsterStagePolicy
import com.todoquest.domain.usecase.MonsterStatsCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RoomCombatRepositoryTest {
    private lateinit var database: TodoQuestDatabase
    private lateinit var clock: MutableClock
    private lateinit var seedSource: SequenceCombatSeedSource
    private lateinit var repository: RoomCombatRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            TodoQuestDatabase::class.java,
        ).allowMainThreadQueries().build()
        clock = MutableClock()
        seedSource = SequenceCombatSeedSource(9_999L)
        repository = RoomCombatRepository(database, clock, seedSource)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun firstObservationInitializesDefaultsAndRestartRestoresStoredCombat() = runTest {
        val initial = repository.observeCombat().first()

        assertEquals(1, initial.progress.stageNumber)
        assertEquals(1, initial.progress.stageLevel)
        assertEquals(clock.now(), initial.progress.lastReconciledAt)
        assertEquals(1, initial.activeMonster.encounterNumber)
        assertEquals(MonsterGrade.NORMAL, initial.activeMonster.grade)
        assertEquals(75, initial.activeMonster.currentHp)
        assertEquals(75, initial.activeMonsterStats.maxHp)
        assertEquals(110, initial.playerCurrentHp)
        assertEquals(110, initial.playerMaxHp)
        assertNotNull(database.characterProfileDao().getProfile())
        assertNotNull(database.characterProfileDao().getCurrentState())

        database.combatDao().updateMonsterCurrentHp(initial.activeMonster.id, 54)
        val restarted = RoomCombatRepository(
            database = database,
            clock = clock,
            seedSource = SequenceCombatSeedSource(123L),
        )

        assertEquals(54, restarted.observeCombat().first().activeMonster.currentHp)
        assertEquals(1, database.combatDao().getCombatProgress()?.stageNumber)
    }

    @Test
    fun firstDiscoveryObservationInitializesAndIncludesTheActiveMonster() = runTest {
        val discovered = repository.observeDiscoveredMonsterSpecies().first()

        assertEquals(setOf(MonsterSpecies.SKELETON_SOLDIER), discovered)
        assertNotNull(database.combatDao().getCombatProgress())
        assertNotNull(database.combatDao().getMonsterInstanceAt(1, 1))
    }

    @Test
    fun discoveryIncludesDefeatedHistoryAndCurrentMonsterWhileCollapsingDuplicateSpecies() = runTest {
        val initial = repository.observeCombat().first()
        database.combatDao().updateMonsterCurrentHp(initial.activeMonster.id, currentHp = 0)
        val currentMonsterId = insertMonster(
            stageNumber = 1,
            encounterNumber = 2,
            stageLevel = 1,
            type = MonsterType.ATTACK,
            grade = MonsterGrade.NORMAL,
        )
        insertMonster(
            stageNumber = 1,
            encounterNumber = 4,
            stageLevel = 1,
            type = MonsterType.BALANCED,
            grade = MonsterGrade.NORMAL,
        )
        val progress = database.combatDao().getCombatProgress()!!
        database.combatDao().updateCombatProgress(
            id = progress.id,
            stageNumber = 1,
            stageLevel = 1,
            activeMonsterInstanceId = currentMonsterId,
            lastReconciledAtEpochMillis = progress.lastReconciledAtEpochMillis,
            balanceVersion = progress.balanceVersion,
        )

        assertEquals(
            setOf(MonsterSpecies.SKELETON_SOLDIER, MonsterSpecies.HARPY),
            repository.observeDiscoveredMonsterSpecies().first(),
        )
    }

    @Test
    fun recreatedRepositoryRestoresDiscoveredSpeciesFromExistingMonsterRows() = runTest {
        repository.observeCombat().first()
        insertMonster(
            stageNumber = 2,
            encounterNumber = 3,
            stageLevel = 1,
            type = MonsterType.DEFENSE,
            grade = MonsterGrade.NORMAL,
        )
        val restarted = RoomCombatRepository(
            database = database,
            clock = clock,
            seedSource = SequenceCombatSeedSource(123L),
        )

        assertEquals(
            setOf(MonsterSpecies.SKELETON_SOLDIER, MonsterSpecies.SLIME),
            restarted.observeDiscoveredMonsterSpecies().first(),
        )
    }

    @Test
    fun discoveryFlowUpdatesWhenVictoryCreatesTheNextMonster() = runTest {
        assertEquals(
            setOf(MonsterSpecies.SKELETON_SOLDIER),
            repository.observeDiscoveredMonsterSpecies().first(),
        )
        val discoveredAfterVictory = backgroundScope.async(
            UnconfinedTestDispatcher(testScheduler),
        ) {
            repository.observeDiscoveredMonsterSpecies().first { discovered ->
                MonsterSpecies.HARPY in discovered
            }
        }
        val date = LocalDate.of(2026, 7, 21)
        enqueueAttack(taskId = 701L, date = date, sourceAttack = 200, criticalChanceBp = 0)

        repository.processPlayerAttack(701L, date)

        assertEquals(
            setOf(MonsterSpecies.SKELETON_SOLDIER, MonsterSpecies.HARPY),
            withTimeout(1_000L) { discoveredAfterVictory.await() },
        )
    }

    @Test
    fun storedNormalEncountersFollowTheStageOneAndTwoGoldenSpeciesSchedules() = runTest {
        repository.observeCombat().first()
        val expectedByStage = mapOf(
            1 to listOf(
                MonsterSpecies.SKELETON_SOLDIER,
                MonsterSpecies.HARPY,
                MonsterSpecies.GOBLIN_SCOUT,
                MonsterSpecies.SKELETON_SOLDIER,
                MonsterSpecies.SLIME,
                MonsterSpecies.HARPY,
                MonsterSpecies.CORRUPTED_TREE_SPIRIT,
                MonsterSpecies.SLIME,
            ),
            2 to listOf(
                MonsterSpecies.CORRUPTED_TREE_SPIRIT,
                MonsterSpecies.GOBLIN_SCOUT,
                MonsterSpecies.SLIME,
                MonsterSpecies.SKELETON_SOLDIER,
                MonsterSpecies.GOBLIN_SCOUT,
                MonsterSpecies.SKELETON_SOLDIER,
                MonsterSpecies.HARPY,
                MonsterSpecies.HARPY,
            ),
        )

        expectedByStage.forEach { (stageNumber, expectedSpecies) ->
            expectedSpecies.forEachIndexed { index, species ->
                val encounterNumber = index + 1
                val monsterId = activateScheduledMonster(stageNumber, encounterNumber)
                val storedBeforeObservation = database.combatDao().getMonsterInstance(monsterId)!!

                val snapshot = repository.observeCombat().first()

                assertEquals(species, snapshot.activeMonsterSpecies)
                assertEquals(storedBeforeObservation.id, snapshot.activeMonster.id)
                assertEquals(stageNumber, snapshot.activeMonster.stageNumber)
                assertEquals(encounterNumber, snapshot.activeMonster.encounterNumber)
                assertEquals(
                    storedBeforeObservation,
                    database.combatDao().getMonsterInstance(monsterId),
                )
            }
        }
    }

    @Test
    fun storedEliteAndBossUseTheirDeterministicScheduledSpecies() = runTest {
        repository.observeCombat().first()
        val expectedByStage = mapOf(
            5 to MonsterSpecies.CORRUPTED_TREE_SPIRIT,
            10 to MonsterSpecies.HARPY,
        )

        expectedByStage.forEach { (stageNumber, expectedSpecies) ->
            val monsterId = activateScheduledMonster(stageNumber, encounterNumber = 1)
            val storedBeforeObservation = database.combatDao().getMonsterInstance(monsterId)!!

            val snapshot = repository.observeCombat().first()

            assertEquals(expectedSpecies, snapshot.activeMonsterSpecies)
            assertEquals(storedBeforeObservation.id, snapshot.activeMonster.id)
            assertEquals(
                storedBeforeObservation,
                database.combatDao().getMonsterInstance(monsterId),
            )
        }
    }

    @Test
    fun storedScheduledSpeciesIsStableAcrossRepositoryRestartWithoutChangingCombatSource() = runTest {
        repository.observeCombat().first()
        val monsterId = activateScheduledMonster(stageNumber = 2, encounterNumber = 3)
        val storedBeforeRestart = database.combatDao().getMonsterInstance(monsterId)!!
        val beforeRestart = repository.observeCombat().first()
        val restarted = RoomCombatRepository(
            database = database,
            clock = clock,
            seedSource = SequenceCombatSeedSource(123L),
        )

        val afterRestart = restarted.observeCombat().first()

        assertEquals(MonsterSpecies.SLIME, beforeRestart.activeMonsterSpecies)
        assertEquals(MonsterSpecies.SLIME, afterRestart.activeMonsterSpecies)
        assertEquals(beforeRestart.activeMonster, afterRestart.activeMonster)
        assertEquals(beforeRestart.activeMonsterStats, afterRestart.activeMonsterStats)
        assertEquals(storedBeforeRestart, database.combatDao().getMonsterInstance(monsterId))
    }

    @Test
    fun observeAndMonsterAttackUseCurrentEquippedMaxHpAndDefense() = runTest {
        initializeEquipmentCharacter()
        grantAndEquip(EquipmentCatalogSeeder.LEATHER_ARMOR_ID, EquipmentSlot.CHEST)

        val observed = repository.observeCombat().first()
        assertEquals(130, observed.playerMaxHp)
        assertEquals(130, observed.playerCurrentHp)

        val date = LocalDate.of(2026, 7, 21)
        insertFailure(taskId = 200L, date = date, failedAtEpochMillis = 2_000L)
        repository.processFailedOccurrenceAttack(200L, date)

        val event = monsterAttack(200L, date)!!
        assertEquals(13, event.playerDefense)
        assertEquals(130, event.playerMaxHp)
        assertEquals(10, event.finalDamage)
        assertEquals(120, event.playerHpAfter)
    }

    @Test
    fun activeObservationSurvivesAtomicEquipmentAndHpUpdates() = runTest {
        initializeEquipmentCharacter()
        repository.observeCombat().first()
        val updatedSnapshot = async(Dispatchers.Default) {
            withTimeout(5_000L) {
                repository.observeCombat().first { it.playerMaxHp == 122 }
            }
        }

        grantAndEquip(EquipmentCatalogSeeder.CLOTH_TOP_ID, EquipmentSlot.CHEST)
        grantAndEquip(EquipmentCatalogSeeder.CLOTH_PANTS_ID, EquipmentSlot.LEGS)

        assertEquals(122, updatedSnapshot.await().playerCurrentHp)
    }

    @Test
    fun victoryRecoveryUsesCurrentlyEquippedRecoveryModifier() = runTest {
        initializeEquipmentCharacter()
        grantAndEquip(EquipmentCatalogSeeder.CLOTH_PANTS_ID, EquipmentSlot.LEGS)
        val initial = repository.observeCombat().first()
        database.characterProfileDao().upsertCurrentState(
            database.characterProfileDao().getCurrentState()!!.copy(currentHp = 50),
        )
        val date = LocalDate.of(2026, 7, 21)
        enqueueAttack(taskId = 201L, date = date, sourceAttack = 200, criticalChanceBp = 0)

        repository.processPlayerAttack(201L, date)

        assertEquals(0, database.combatDao().getMonsterInstance(initial.activeMonster.id)?.currentHp)
        assertEquals(58, database.characterProfileDao().getCurrentState()?.currentHp)
    }

    @Test
    fun equipmentChangeDoesNotRewriteAlreadyAppliedAttackSourceOrResult() = runTest {
        val initial = repository.observeCombat().first()
        val date = LocalDate.of(2026, 7, 21)
        enqueueAttack(taskId = 202L, date = date, sourceAttack = 20, criticalChanceBp = 0)
        repository.processPlayerAttack(202L, date)
        val appliedBeforeEquipment = attack(202L, date)

        database.characterProfileDao().upsert(
            database.characterProfileDao().getProfile()!!.copy(currentGold = 100L),
        )
        grantAndEquip(EquipmentCatalogSeeder.WORN_SWORD_ID, EquipmentSlot.WEAPON)
        val duplicate = repository.processPlayerAttack(202L, date) as PlayerAttackResult.Applied

        assertTrue(duplicate.wasAlreadyApplied)
        assertEquals(appliedBeforeEquipment, attack(202L, date))
        assertEquals(
            initial.activeMonster.currentHp - appliedBeforeEquipment.finalDamage!!,
            duplicate.attack.targetHpAfter,
        )
    }

    @Test
    fun normalAttackAppliesMomentumThenDefenseAndPersistsEveryOperand() = runTest {
        repository.observeCombat().first()
        val date = LocalDate.of(2026, 7, 21)
        enqueueAttack(
            taskId = 10L,
            date = date,
            sourceAttack = 20,
            criticalChanceBp = 750,
            momentumBp = 500,
        )

        val result = repository.processPlayerAttack(10L, date)

        assertTrue(result is PlayerAttackResult.Applied)
        result as PlayerAttackResult.Applied
        assertFalse(result.wasAlreadyApplied)
        assertEquals(9_999L, result.attack.seed)
        assertEquals(9_999, result.attack.roll)
        assertFalse(result.attack.wasCritical)
        assertEquals(21, result.attack.rawDamage)
        assertEquals(7, result.attack.targetDefense)
        assertEquals(19, result.attack.finalDamage)
        assertEquals(75, result.attack.targetHpBefore)
        assertEquals(56, result.attack.targetHpAfter)
        assertEquals(56, database.combatDao().getMonsterInstance(result.attack.targetMonsterInstanceId)?.currentHp)
        assertEquals(0L, result.attack.totalXpAward)
        assertEquals(0L, database.characterProfileDao().getProfile()?.totalXp)
        assertEquals(0L, database.characterProfileDao().getProfile()?.currentGold)
    }

    @Test
    fun currentDifficultyScalesDamageAfterMomentumBeforeCriticalAndDefense() = runTest {
        seedSource = SequenceCombatSeedSource(9_999L, 9_999L, 9_999L, 0L)
        repository = RoomCombatRepository(
            database = database,
            clock = clock,
            seedSource = seedSource,
            monsterBalanceConfig = MonsterBalanceConfig(
                baseMaxHp = 999,
                baseDefense = 0,
                defenseGrowthPerLevel = 0,
            ),
        )
        repository.observeCombat().first()
        val date = LocalDate.of(2026, 7, 21)
        val expectedDamageByDifficulty = mapOf(
            TaskDifficulty.EASY to 100,
            TaskDifficulty.MEDIUM to 150,
            TaskDifficulty.HARD to 200,
        )

        expectedDamageByDifficulty.entries.forEachIndexed { index, (difficulty, expectedDamage) ->
            enqueueAttack(
                taskId = 110L + index,
                date = date.plusDays(index.toLong()),
                sourceAttack = 100,
                sourceTaskDifficulty = difficulty.name,
                taskDifficultyBalanceVersion = TaskDifficultyCombatBalanceCatalog.CURRENT_VERSION,
            )

            val applied = repository.processPlayerAttack(
                taskId = 110L + index,
                occurrenceDate = date.plusDays(index.toLong()),
            ) as PlayerAttackResult.Applied

            assertEquals(difficulty, applied.attack.sourceTaskDifficulty)
            assertEquals(
                TaskDifficultyCombatBalanceCatalog.CURRENT_VERSION,
                applied.attack.taskDifficultyBalanceVersion,
            )
            assertEquals(expectedDamage, applied.attack.rawDamage)
            assertEquals(expectedDamage, applied.attack.finalDamage)
        }

        enqueueAttack(
            taskId = 113L,
            date = date.plusDays(3),
            sourceAttack = 100,
            criticalChanceBp = 5_000,
            momentumBp = 500,
            sourceTaskDifficulty = TaskDifficulty.MEDIUM.name,
            taskDifficultyBalanceVersion = TaskDifficultyCombatBalanceCatalog.CURRENT_VERSION,
        )

        val critical = repository.processPlayerAttack(113L, date.plusDays(3)) as PlayerAttackResult.Applied

        assertTrue(critical.attack.wasCritical)
        assertEquals(239, critical.attack.rawDamage)
        assertEquals(239, critical.attack.finalDamage)
    }

    @Test
    fun currentDifficultyScalesVersionTwoNonlethalHitXp() = runTest {
        seedSource = SequenceCombatSeedSource(9_999L, 9_999L, 9_999L)
        repository = RoomCombatRepository(database, clock, seedSource)
        repository.observeCombat().first()
        val date = LocalDate.of(2026, 7, 21)
        val expectedHitXpByDifficulty = mapOf(
            TaskDifficulty.EASY to 3L,
            TaskDifficulty.MEDIUM to 4L,
            TaskDifficulty.HARD to 6L,
        )

        expectedHitXpByDifficulty.entries.forEachIndexed { index, (difficulty, expectedHitXp) ->
            enqueueAttack(
                taskId = 120L + index,
                date = date.plusDays(index.toLong()),
                sourceAttack = 1,
                combatRewardVersion = CombatRewardBalanceCatalog.CURRENT_VERSION,
                sourceTaskDifficulty = difficulty.name,
                taskDifficultyBalanceVersion = TaskDifficultyCombatBalanceCatalog.CURRENT_VERSION,
            )

            val applied = repository.processPlayerAttack(
                taskId = 120L + index,
                occurrenceDate = date.plusDays(index.toLong()),
            ) as PlayerAttackResult.Applied

            assertEquals(expectedHitXp, applied.attack.hitXpAward)
            assertEquals(0L, applied.attack.killBonusXpAward)
            assertEquals(0L, applied.attack.killGoldAward)
        }

        assertEquals(13L, database.characterProfileDao().getProfile()?.totalXp)
        assertEquals(0L, database.characterProfileDao().getProfile()?.currentGold)
    }

    @Test
    fun currentDifficultyScalesVersionTwoKillXpButNeverKillGold() = runTest {
        seedSource = SequenceCombatSeedSource(9_999L, 9_999L, 9_999L)
        repository = RoomCombatRepository(database, clock, seedSource)
        repository.observeCombat().first()
        val date = LocalDate.of(2026, 7, 21)
        val expectedByDifficulty = mapOf(
            TaskDifficulty.EASY to Triple(3L, 20L, 23L),
            TaskDifficulty.MEDIUM to Triple(4L, 30L, 34L),
            TaskDifficulty.HARD to Triple(6L, 40L, 46L),
        )

        expectedByDifficulty.entries.forEachIndexed { index, (difficulty, expected) ->
            enqueueAttack(
                taskId = 130L + index,
                date = date.plusDays(index.toLong()),
                sourceAttack = 200,
                combatRewardVersion = CombatRewardBalanceCatalog.CURRENT_VERSION,
                sourceTaskDifficulty = difficulty.name,
                taskDifficultyBalanceVersion = TaskDifficultyCombatBalanceCatalog.CURRENT_VERSION,
            )

            val applied = repository.processPlayerAttack(
                taskId = 130L + index,
                occurrenceDate = date.plusDays(index.toLong()),
            ) as PlayerAttackResult.Applied

            assertEquals(expected.first, applied.attack.hitXpAward)
            assertEquals(expected.second, applied.attack.killBonusXpAward)
            assertEquals(expected.third, applied.attack.totalXpAward)
            assertEquals(15L, applied.attack.killGoldAward)
        }

        assertEquals(103L, database.characterProfileDao().getProfile()?.totalXp)
        assertEquals(45L, database.characterProfileDao().getProfile()?.currentGold)
    }

    @Test
    fun legacyDifficultyVersionKeepsDamageAndRewardNeutralForStoredDifficulty() = runTest {
        repository.observeCombat().first()
        val date = LocalDate.of(2026, 7, 21)
        enqueueAttack(
            taskId = 140L,
            date = date,
            sourceAttack = 20,
            combatRewardVersion = CombatRewardBalanceCatalog.CURRENT_VERSION,
            sourceTaskDifficulty = TaskDifficulty.HARD.name,
            taskDifficultyBalanceVersion = TaskDifficultyCombatBalanceCatalog.LEGACY_VERSION,
        )

        val applied = repository.processPlayerAttack(140L, date) as PlayerAttackResult.Applied

        assertEquals(TaskDifficulty.HARD, applied.attack.sourceTaskDifficulty)
        assertEquals(TaskDifficultyCombatBalanceCatalog.LEGACY_VERSION, applied.attack.taskDifficultyBalanceVersion)
        assertEquals(20, applied.attack.rawDamage)
        assertEquals(18, applied.attack.finalDamage)
        assertEquals(3L, applied.attack.totalXpAward)
        assertEquals(3L, database.characterProfileDao().getProfile()?.totalXp)
    }

    @Test
    fun invalidCurrentDifficultyAndUnknownVersionRollBackEveryPlayerAttackSource() = runTest {
        val initial = repository.observeCombat().first()
        val initialProfile = database.characterProfileDao().getProfile()
        val initialCurrentState = database.characterProfileDao().getCurrentState()
        val initialProgress = database.combatDao().getCombatProgress()
        val initialMonster = database.combatDao().getMonsterInstance(initial.activeMonster.id)
        val date = LocalDate.of(2026, 7, 21)
        val invalidSources = listOf(
            150L to Pair(null, TaskDifficultyCombatBalanceCatalog.CURRENT_VERSION),
            151L to Pair("LEGENDARY", TaskDifficultyCombatBalanceCatalog.CURRENT_VERSION),
            152L to Pair(TaskDifficulty.EASY.name, 999),
        )

        invalidSources.forEachIndexed { index, (taskId, source) ->
            val occurrenceDate = date.plusDays(index.toLong())
            enqueueAttack(
                taskId = taskId,
                date = occurrenceDate,
                sourceAttack = 200,
                combatRewardVersion = CombatRewardBalanceCatalog.CURRENT_VERSION,
                sourceTaskDifficulty = source.first,
                taskDifficultyBalanceVersion = source.second,
            )
            val pending = attack(taskId, occurrenceDate)

            val failure = runCatching {
                repository.processPlayerAttack(taskId, occurrenceDate)
            }.exceptionOrNull()

            assertTrue(failure is IllegalArgumentException)
            assertEquals(pending, attack(taskId, occurrenceDate))
            assertEquals(initialProfile, database.characterProfileDao().getProfile())
            assertEquals(initialCurrentState, database.characterProfileDao().getCurrentState())
            assertEquals(initialProgress, database.combatDao().getCombatProgress())
            assertEquals(initialMonster, database.combatDao().getMonsterInstance(initial.activeMonster.id))
        }
        assertEquals(0, seedSource.calls.get())
    }

    @Test
    fun versionTwoGrantsNewHitXpOnceForNonLethalAttack() = runTest {
        val initial = repository.observeCombat().first()
        val date = LocalDate.of(2026, 7, 21)
        enqueueAttack(
            taskId = 101L,
            date = date,
            sourceAttack = 20,
            combatRewardVersion = CombatRewardBalanceCatalog.CURRENT_VERSION,
        )
        val transitions = mutableListOf<CombatTransition>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.events.collect { transitions += it }
        }

        val first = repository.processPlayerAttack(101L, date) as PlayerAttackResult.Applied
        val repeated = repository.processPlayerAttack(101L, date) as PlayerAttackResult.Applied
        val profile = database.characterProfileDao().getProfile()!!

        assertFalse(first.wasAlreadyApplied)
        assertTrue(repeated.wasAlreadyApplied)
        assertEquals(3L, first.attack.hitXpAward)
        assertEquals(0L, first.attack.killBonusXpAward)
        assertEquals(0L, first.attack.killGoldAward)
        assertEquals(3L, first.attack.totalXpAward)
        assertEquals(3L, profile.totalXp)
        assertEquals(0L, profile.currentGold)
        assertEquals(1, transitions.size)
        val transition = transitions.single() as CombatTransition.PlayerAttack
        assertEquals(
            listOf(
                BattleEffectEvent.PlayerAttackStarted::class,
                BattleEffectEvent.EntityHit::class,
            ),
            transition.effectEvents.map { it::class },
        )
        assertTrue(transition.effectEvents.all { it.monsterId == initial.activeMonster.id })
        assertTrue(transition.effectEvents.all { it.attacker == BattleEntityRef.Player })
        assertTrue(
            transition.effectEvents.all {
                it.target == BattleEntityRef.Monster(initial.activeMonster.id)
            },
        )
    }

    @Test
    fun versionTwoAddsNewKillXpAndGoldOnceForLethalAttack() = runTest {
        repository.observeCombat().first()
        val date = LocalDate.of(2026, 7, 21)
        enqueueAttack(
            taskId = 102L,
            date = date,
            sourceAttack = 200,
            combatRewardVersion = CombatRewardBalanceCatalog.CURRENT_VERSION,
        )

        val first = repository.processPlayerAttack(102L, date) as PlayerAttackResult.Applied
        val repeated = repository.processPlayerAttack(102L, date) as PlayerAttackResult.Applied
        val profile = database.characterProfileDao().getProfile()!!

        assertFalse(first.wasAlreadyApplied)
        assertTrue(repeated.wasAlreadyApplied)
        assertEquals(3L, first.attack.hitXpAward)
        assertEquals(20L, first.attack.killBonusXpAward)
        assertEquals(15L, first.attack.killGoldAward)
        assertEquals(23L, first.attack.totalXpAward)
        assertEquals(23L, profile.totalXp)
        assertEquals(15L, profile.currentGold)
    }

    @Test
    fun pendingVersionOneKeepsOriginalKillRewardAfterCurrentVersionChanges() = runTest {
        repository.observeCombat().first()
        val date = LocalDate.of(2026, 7, 21)
        enqueueAttack(
            taskId = 103L,
            date = date,
            sourceAttack = 200,
            combatRewardVersion = 1,
        )

        val applied = repository.processPlayerAttack(103L, date) as PlayerAttackResult.Applied
        val profile = database.characterProfileDao().getProfile()!!

        assertEquals(1, attack(103L, date).combatRewardVersion)
        assertEquals(1L, applied.attack.hitXpAward)
        assertEquals(10L, applied.attack.killBonusXpAward)
        assertEquals(5L, applied.attack.killGoldAward)
        assertEquals(11L, applied.attack.totalXpAward)
        assertEquals(11L, profile.totalXp)
        assertEquals(5L, profile.currentGold)
    }

    @Test
    fun appliedVersionTwoReturnsStoredSnapshotAfterProcessRecreationWithoutReplay() = runTest {
        repository.observeCombat().first()
        val date = LocalDate.of(2026, 7, 21)
        enqueueAttack(
            taskId = 104L,
            date = date,
            sourceAttack = 200,
            combatRewardVersion = CombatRewardBalanceCatalog.CURRENT_VERSION,
            sourceTaskDifficulty = TaskDifficulty.HARD.name,
            taskDifficultyBalanceVersion = TaskDifficultyCombatBalanceCatalog.CURRENT_VERSION,
        )
        val firstTransitions = mutableListOf<CombatTransition>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.events.collect { firstTransitions += it }
        }

        val first = repository.processPlayerAttack(104L, date) as PlayerAttackResult.Applied
        val storedEvent = attack(104L, date)
        val storedProfile = database.characterProfileDao().getProfile()
        val storedCurrentState = database.characterProfileDao().getCurrentState()
        val storedProgress = database.combatDao().getCombatProgress()
        val storedTarget = database.combatDao().getMonsterInstance(first.attack.targetMonsterInstanceId)
        assertEquals(1, firstTransitions.size)

        val restarted = RoomCombatRepository(database, clock, SequenceCombatSeedSource(123L))
        val lateTransitions = mutableListOf<CombatTransition>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            restarted.events.collect { lateTransitions += it }
        }
        val repeated = restarted.processPlayerAttack(104L, date) as PlayerAttackResult.Applied

        assertTrue(repeated.wasAlreadyApplied)
        assertEquals(first.attack, repeated.attack)
        assertEquals(TaskDifficulty.HARD, repeated.attack.sourceTaskDifficulty)
        assertEquals(
            TaskDifficultyCombatBalanceCatalog.CURRENT_VERSION,
            repeated.attack.taskDifficultyBalanceVersion,
        )
        assertEquals(6L, repeated.attack.hitXpAward)
        assertEquals(40L, repeated.attack.killBonusXpAward)
        assertEquals(15L, repeated.attack.killGoldAward)
        assertEquals(storedEvent, attack(104L, date))
        assertEquals(storedProfile, database.characterProfileDao().getProfile())
        assertEquals(storedCurrentState, database.characterProfileDao().getCurrentState())
        assertEquals(storedProgress, database.combatDao().getCombatProgress())
        assertEquals(storedTarget, database.combatDao().getMonsterInstance(first.attack.targetMonsterInstanceId))
        assertEquals(emptyList<CombatTransition>(), lateTransitions)
        assertEquals(
            listOf(
                BattleEffectEvent.PlayerAttackStarted::class,
                BattleEffectEvent.EntityHit::class,
                BattleEffectEvent.MonsterDefeated::class,
            ),
            firstTransitions.single().effectEvents.map { it::class },
        )
    }

    @Test
    fun versionTwoRewardTransactionRollsBackEverySourceWhenStageWriteFails() = runTest {
        val initial = repository.observeCombat().first()
        val initialProfile = database.characterProfileDao().getProfile()
        val initialCurrentState = database.characterProfileDao().getCurrentState()
        val initialProgress = database.combatDao().getCombatProgress()
        val initialMonster = database.combatDao().getMonsterInstance(initial.activeMonster.id)
        val date = LocalDate.of(2026, 7, 21)
        enqueueAttack(
            taskId = 105L,
            date = date,
            sourceAttack = 200,
            combatRewardVersion = CombatRewardBalanceCatalog.CURRENT_VERSION,
        )
        val pending = attack(105L, date)
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_combat_progress_update
            BEFORE UPDATE ON combat_progress
            BEGIN
                SELECT RAISE(ABORT, 'forced combat progress failure');
            END
            """.trimIndent(),
        )

        val failure = runCatching { repository.processPlayerAttack(105L, date) }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals(pending, attack(105L, date))
        assertEquals(initialProfile, database.characterProfileDao().getProfile())
        assertEquals(initialCurrentState, database.characterProfileDao().getCurrentState())
        assertEquals(initialProgress, database.combatDao().getCombatProgress())
        assertEquals(initialMonster, database.combatDao().getMonsterInstance(initial.activeMonster.id))
    }

    @Test
    fun criticalAttackIsAppliedOnceAcrossConcurrentAndRepeatedRequests() = runTest {
        seedSource = SequenceCombatSeedSource(0L, 1L)
        repository = RoomCombatRepository(database, clock, seedSource)
        repository.observeCombat().first()
        val date = LocalDate.of(2026, 7, 21)
        enqueueAttack(
            taskId = 11L,
            date = date,
            sourceAttack = 20,
            criticalChanceBp = 750,
            combatRewardVersion = CombatRewardBalanceCatalog.CURRENT_VERSION,
        )

        val concurrent = listOf(
            async { repository.processPlayerAttack(11L, date) },
            async { repository.processPlayerAttack(11L, date) },
        ).awaitAll().map { it as PlayerAttackResult.Applied }
        val repeated = repository.processPlayerAttack(11L, date) as PlayerAttackResult.Applied

        assertEquals(1, concurrent.count { !it.wasAlreadyApplied })
        assertEquals(1, concurrent.count { it.wasAlreadyApplied })
        assertTrue(repeated.wasAlreadyApplied)
        assertEquals(1, seedSource.calls.get())
        assertEquals(0L, repeated.attack.seed)
        assertEquals(0, repeated.attack.roll)
        assertTrue(repeated.attack.wasCritical)
        assertEquals(30, repeated.attack.rawDamage)
        assertEquals(28, repeated.attack.finalDamage)
        assertEquals(47, repeated.attack.targetHpAfter)
        assertEquals(47, database.combatDao().getMonsterInstance(repeated.attack.targetMonsterInstanceId)?.currentHp)
        assertEquals(3L, repeated.attack.totalXpAward)
        assertEquals(3L, database.characterProfileDao().getProfile()?.totalXp)
    }

    @Test
    fun concurrentReconciliationAppliesVersionTwoRewardExactlyOnce() = runTest {
        seedSource = SequenceCombatSeedSource(41L, 42L)
        repository = RoomCombatRepository(database, clock, seedSource)
        repository.observeCombat().first()
        val date = LocalDate.of(2026, 7, 21)
        enqueueAttack(
            taskId = 106L,
            date = date,
            sourceAttack = 20,
            combatRewardVersion = CombatRewardBalanceCatalog.CURRENT_VERSION,
        )

        val results = listOf(
            async { repository.reconcileOverdue(clock.now()) },
            async { repository.reconcileOverdue(clock.now()) },
        ).awaitAll()

        assertEquals(1, results.sumOf { it.playerAttacksProcessed })
        assertEquals(CombatEventStatus.APPLIED.name, attack(106L, date).status)
        assertEquals(3L, attack(106L, date).hitXpAward)
        assertEquals(3L, database.characterProfileDao().getProfile()?.totalXp)
        assertEquals(1, seedSource.calls.get())
    }

    @Test
    fun manualFailureAppliesOnceAndEmitsOneNonReplayableTransition() = runTest {
        val initial = repository.observeCombat().first()
        val date = LocalDate.of(2026, 7, 21)
        insertFailure(taskId = 21L, date = date, failedAtEpochMillis = 2_000L)
        val transitions = mutableListOf<CombatTransition>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.events.collect { transitions += it }
        }

        val first = repository.processFailedOccurrenceAttack(21L, date) as MonsterAttackResult.Applied
        val duplicate = repository.processFailedOccurrenceAttack(21L, date) as MonsterAttackResult.Applied
        val stored = monsterAttack(21L, date)!!

        assertFalse(first.wasAlreadyApplied)
        assertTrue(duplicate.wasAlreadyApplied)
        assertEquals(MonsterAttackTrigger.MANUAL_FAILURE, first.attack.trigger)
        assertEquals(MonsterAttackTrigger.MANUAL_FAILURE.name, stored.trigger)
        assertEquals(initial.playerCurrentHp, first.attack.playerHpBefore)
        assertEquals(initial.playerCurrentHp - 11, first.attack.playerHpAfter)
        assertEquals(initial.playerCurrentHp - 11, database.characterProfileDao().getCurrentState()?.currentHp)
        assertEquals(1, transitions.size)
        val transition = transitions.single() as CombatTransition.MonsterAttack
        assertEquals(initial.playerCurrentHp, transition.before.playerCurrentHp)
        assertEquals(initial.playerCurrentHp - 11, transition.after.playerCurrentHp)
        assertEquals(first.attack, transition.attack)
        assertEquals(
            listOf(
                BattleEffectEvent.MonsterAttackStarted::class,
                BattleEffectEvent.EntityHit::class,
            ),
            transition.effectEvents.map { it::class },
        )
        assertTrue(
            transition.effectEvents.all {
                it.attacker == BattleEntityRef.Monster(initial.activeMonster.id)
            },
        )
        assertTrue(transition.effectEvents.all { it.target == BattleEntityRef.Player })
    }

    @Test
    fun completedManualFailureDoesNotReplayToLateCollectorOrOnDuplicate() = runTest {
        repository.observeCombat().first()
        val date = LocalDate.of(2026, 7, 21)
        insertFailure(taskId = 22L, date = date, failedAtEpochMillis = 2_000L)
        repository.processFailedOccurrenceAttack(22L, date)
        val lateTransitions = mutableListOf<CombatTransition>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.events.collect { lateTransitions += it }
        }

        val duplicate = repository.processFailedOccurrenceAttack(22L, date) as MonsterAttackResult.Applied

        assertTrue(duplicate.wasAlreadyApplied)
        assertEquals(emptyList<CombatTransition>(), lateTransitions)
    }

    @Test
    fun pendingFailureRepairUsesDeterministicOrderAndDoesNotApplyCapOrReplay() = runTest {
        repository.observeCombat().first()
        val date = LocalDate.of(2026, 7, 21)
        insertFailure(taskId = 3L, date = date, failedAtEpochMillis = 2_000L)
        insertFailure(taskId = 2L, date = date, failedAtEpochMillis = 1_000L)
        insertFailure(taskId = 1L, date = date.minusDays(1), failedAtEpochMillis = 1_000L)
        insertFailure(taskId = 4L, date = date, failedAtEpochMillis = 3_000L)
        val transitions = mutableListOf<CombatTransition>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.events.collect { transitions += it }
        }

        assertEquals(4, repository.processPendingFailureAttacks())
        assertEquals(0, repository.processPendingFailureAttacks())

        assertEquals(
            listOf(1L, 2L, 3L, 4L),
            transitions.map { it.eventKey.taskId },
        )
        assertEquals(4, transitions.size)
        assertTrue(transitions.all { it is CombatTransition.MonsterAttack })
        assertTrue(
            listOf(1L, 2L, 3L, 4L).all { taskId ->
                monsterAttack(taskId, if (taskId == 1L) date.minusDays(1) else date)?.status ==
                    CombatEventStatus.APPLIED.name
            },
        )
    }

    @Test
    fun reconcileEmitsPlayerManualAndDeadlineTransitionsInThatOrderWithoutCollision() = runTest {
        clock.instant = Instant.parse("2026-07-20T00:00:00Z")
        repository.observeCombat().first()
        val date = LocalDate.of(2026, 7, 20)
        enqueueAttack(taskId = 90L, date = date, sourceAttack = 1)
        insertFailure(taskId = 20L, date = date, failedAtEpochMillis = 1_000L)
        insertTask(taskId = 30L, date = date)
        val transitions = mutableListOf<CombatTransition>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.events.collect { transitions += it }
        }
        clock.instant = Instant.parse("2026-07-21T01:00:00Z")

        val result = repository.reconcileOverdue(clock.now())

        assertEquals(1, result.playerAttacksProcessed)
        assertEquals(2, result.monsterAttacksApplied)
        assertEquals(0, result.monsterAttacksSkipped)
        assertEquals(
            listOf(
                CombatEventKind.PLAYER_ATTACK to 90L,
                CombatEventKind.MONSTER_ATTACK to 20L,
                CombatEventKind.MONSTER_ATTACK to 30L,
            ),
            transitions.map { it.eventKey.kind to it.eventKey.taskId },
        )
        assertEquals(MonsterAttackTrigger.MANUAL_FAILURE.name, monsterAttack(20L, date)?.trigger)
        assertEquals(MonsterAttackTrigger.MISSED_DEADLINE.name, monsterAttack(30L, date)?.trigger)
        assertEquals(1_000L, failure(20L, date)?.failedAtEpochMillis)
        assertEquals(clock.now().toEpochMilli(), failure(30L, date)?.failedAtEpochMillis)
    }

    @Test
    fun defeatingMonsterPreservesBeforeSpeciesAndUsesNextEncounterScheduledSpecies() = runTest {
        val initial = repository.observeCombat().first()
        database.characterProfileDao().upsertCurrentState(
            CharacterCurrentStateEntity(
                characterId = 1L,
                currentHp = 50,
                balanceVersion = 1,
                updatedAtEpochMillis = clock.now().toEpochMilli(),
            ),
        )
        val date = LocalDate.of(2026, 7, 21)
        enqueueAttack(taskId = 12L, date = date, sourceAttack = 200, criticalChanceBp = 0)
        val transitions = mutableListOf<CombatTransition>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.events.collect { transitions += it }
        }

        val applied = repository.processPlayerAttack(12L, date) as PlayerAttackResult.Applied
        val repeated = repository.processPlayerAttack(12L, date) as PlayerAttackResult.Applied
        val progress = database.combatDao().getCombatProgress()!!
        val next = database.combatDao().getMonsterInstance(progress.activeMonsterInstanceId)!!

        assertEquals(0, database.combatDao().getMonsterInstance(initial.activeMonster.id)?.currentHp)
        assertEquals(57, database.characterProfileDao().getCurrentState()?.currentHp)
        assertEquals(1, progress.stageNumber)
        assertEquals(1, progress.stageLevel)
        assertEquals(2, next.encounterNumber)
        assertEquals(MonsterGrade.NORMAL.name, next.grade)
        assertEquals("monster_attack_v1", next.definitionId)
        assertEquals(67, next.currentHp)
        assertFalse(applied.wasAlreadyApplied)
        assertTrue(repeated.wasAlreadyApplied)
        assertEquals(57, database.characterProfileDao().getCurrentState()?.currentHp)
        assertEquals(1, transitions.size)
        val transition = transitions.single() as CombatTransition.PlayerAttack
        assertEquals(MonsterSpecies.SKELETON_SOLDIER, transition.before.activeMonsterSpecies)
        assertEquals(initial.activeMonster.id, transition.before.activeMonster.id)
        assertEquals(initial.activeMonster.id, transition.attack.targetMonsterInstanceId)
        assertEquals(0, transition.attack.targetHpAfter)
        assertEquals(MonsterSpecies.HARPY, transition.after.activeMonsterSpecies)
        assertEquals(next.id, transition.after.activeMonster.id)
        assertEquals(next.currentHp, transition.after.activeMonster.currentHp)
        assertEquals(57, transition.after.playerCurrentHp)
        assertEquals(
            listOf(
                BattleEffectEvent.PlayerAttackStarted::class,
                BattleEffectEvent.EntityHit::class,
                BattleEffectEvent.MonsterDefeated::class,
            ),
            transition.effectEvents.map { it::class },
        )
        assertTrue(transition.effectEvents.all { it.monsterId == initial.activeMonster.id })
        assertTrue(transition.effectEvents.last().isTerminal)
    }

    @Test
    fun defeatingLastNormalEncounterStartsNextStageAndLocksAttackSourcePlayerLevel() = runTest {
        repository.observeCombat().first()
        val lastMonsterId = activateScheduledMonster(
            stageNumber = 1,
            encounterNumber = 8,
        )
        val date = LocalDate.of(2026, 7, 21)
        enqueueAttack(
            taskId = 13L,
            date = date,
            sourcePlayerLevel = 10,
            sourceAttack = 2_000,
            criticalChanceBp = 0,
        )

        repository.processPlayerAttack(13L, date)
        val advanced = database.combatDao().getCombatProgress()!!
        val next = database.combatDao().getMonsterInstance(advanced.activeMonsterInstanceId)!!

        assertEquals(0, database.combatDao().getMonsterInstance(lastMonsterId)?.currentHp)
        assertEquals(2, advanced.stageNumber)
        assertEquals(10, advanced.stageLevel)
        assertEquals(1, next.encounterNumber)
        assertEquals(10, next.level)
        assertEquals("monster_attack_v1", next.definitionId)
        assertEquals(108, next.currentHp)
    }

    @Test
    fun pendingDrainUsesCreatedDateAndTaskOrderAndDoesNotReapply() = runTest {
        seedSource = SequenceCombatSeedSource(101L, 102L, 103L)
        repository = RoomCombatRepository(database, clock, seedSource)
        repository.observeCombat().first()
        val firstDate = LocalDate.of(2026, 7, 20)
        val secondDate = LocalDate.of(2026, 7, 21)
        enqueueAttack(2L, firstDate, createdAtEpochMillis = 2_000L, sourceAttack = 1)
        enqueueAttack(3L, secondDate, createdAtEpochMillis = 1_000L, sourceAttack = 1)
        enqueueAttack(1L, firstDate, createdAtEpochMillis = 1_000L, sourceAttack = 1)

        assertEquals(3, repository.processPendingPlayerAttacks())
        assertEquals(0, repository.processPendingPlayerAttacks())

        assertEquals(101L, attack(1L, firstDate).seed)
        assertEquals(102L, attack(3L, secondDate).seed)
        assertEquals(103L, attack(2L, firstDate).seed)
        assertEquals(3, seedSource.calls.get())
    }

    @Test
    fun unknownDefinitionFailsClearlyAndKeepsPendingAndHpUntouched() = runTest {
        val initial = repository.observeCombat().first()
        val broken = MonsterInstanceEntity(
            definitionId = "missing_definition_v1",
            grade = MonsterGrade.NORMAL.name,
            stageNumber = 2,
            encounterNumber = 1,
            level = 1,
            currentHp = 75,
            balanceVersion = 1,
        )
        val brokenId = database.combatDao().insertMonsterInstance(broken)
        val progress = database.combatDao().getCombatProgress()!!
        database.combatDao().updateCombatProgress(
            id = progress.id,
            stageNumber = 2,
            stageLevel = 1,
            activeMonsterInstanceId = brokenId,
            lastReconciledAtEpochMillis = progress.lastReconciledAtEpochMillis,
            balanceVersion = 1,
        )
        val date = LocalDate.of(2026, 7, 21)
        enqueueAttack(14L, date)

        val failure = runCatching { repository.processPlayerAttack(14L, date) }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(CombatEventStatus.PENDING.name, attack(14L, date).status)
        assertEquals(75, database.combatDao().getMonsterInstance(brokenId)?.currentHp)
        assertEquals(initial.playerCurrentHp, database.characterProfileDao().getCurrentState()?.currentHp)
    }

    @Test
    fun unknownEventVersionFailsWithoutConsumingPendingAttack() = runTest {
        val initial = repository.observeCombat().first()
        val date = LocalDate.of(2026, 7, 21)
        enqueueAttack(taskId = 15L, date = date)
        val pending = attack(15L, date)
        database.openHelper.writableDatabase.execSQL(
            "UPDATE player_attack_events SET monsterBalanceVersion = 999 " +
                "WHERE taskId = 15 AND occurrenceDateEpochDay = ${date.toEpochDay()}",
        )

        val failure = runCatching { repository.processPlayerAttack(15L, date) }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(CombatEventStatus.PENDING.name, attack(15L, date).status)
        assertEquals(pending.sourceAttack, attack(15L, date).sourceAttack)
        assertEquals(initial.activeMonster.currentHp, repository.observeCombat().first().activeMonster.currentHp)
    }

    @Test
    fun unknownCombatRewardVersionKeepsPendingEventAndEverySourceUntouched() = runTest {
        val initial = repository.observeCombat().first()
        val initialProfile = database.characterProfileDao().getProfile()
        val initialCurrentState = database.characterProfileDao().getCurrentState()
        val initialProgress = database.combatDao().getCombatProgress()
        val initialMonster = database.combatDao().getMonsterInstance(initial.activeMonster.id)
        val date = LocalDate.of(2026, 7, 21)
        enqueueAttack(
            taskId = 16L,
            date = date,
            sourceAttack = 200,
            combatRewardVersion = 999,
        )
        val pending = attack(16L, date)

        val failure = runCatching { repository.processPlayerAttack(16L, date) }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(pending, attack(16L, date))
        assertEquals(initialProfile, database.characterProfileDao().getProfile())
        assertEquals(initialCurrentState, database.characterProfileDao().getCurrentState())
        assertEquals(initialProgress, database.combatDao().getCombatProgress())
        assertEquals(initialMonster, database.combatDao().getMonsterInstance(initial.activeMonster.id))
        assertEquals(0, seedSource.calls.get())
    }

    @Test
    fun unknownStoredMonsterBalanceVersionStillFailsWithoutChangingStoredSource() = runTest {
        val initial = repository.observeCombat().first()
        database.openHelper.writableDatabase.execSQL(
            "UPDATE monster_instances SET balanceVersion = 999 WHERE id = ${initial.activeMonster.id}",
        )
        val stored = database.combatDao().getMonsterInstance(initial.activeMonster.id)!!
        val restarted = RoomCombatRepository(database, clock, SequenceCombatSeedSource(123L))

        val failure = runCatching { restarted.observeCombat().first() }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(999, stored.balanceVersion)
        assertEquals(stored, database.combatDao().getMonsterInstance(initial.activeMonster.id))
    }

    @Test
    fun storedMonsterDefinitionMustStillMatchItsEncounterSlot() = runTest {
        repository.observeCombat().first()
        val invalidMonsterId = insertMonster(
            stageNumber = 1,
            encounterNumber = 2,
            stageLevel = 1,
            type = MonsterType.BALANCED,
            grade = MonsterGrade.NORMAL,
        )
        val progress = database.combatDao().getCombatProgress()!!
        database.combatDao().updateCombatProgress(
            id = progress.id,
            stageNumber = progress.stageNumber,
            stageLevel = progress.stageLevel,
            activeMonsterInstanceId = invalidMonsterId,
            lastReconciledAtEpochMillis = progress.lastReconciledAtEpochMillis,
            balanceVersion = progress.balanceVersion,
        )
        val stored = database.combatDao().getMonsterInstance(invalidMonsterId)!!
        val restarted = RoomCombatRepository(database, clock, SequenceCombatSeedSource(123L))

        val failure = runCatching { restarted.observeCombat().first() }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals("monster_balanced_v1", stored.definitionId)
        assertEquals(stored, database.combatDao().getMonsterInstance(invalidMonsterId))
    }

    @Test
    fun firstReconciliationInitializesCursorWithoutAttackingEarlierOccurrences() = runTest {
        insertTask(taskId = 1L, date = LocalDate.of(2026, 7, 19))
        clock.instant = Instant.parse("2026-07-21T01:00:00Z")

        val result = repository.reconcileOverdue(clock.now())

        assertEquals(0, result.monsterAttacksApplied)
        assertEquals(0, result.monsterAttacksSkipped)
        assertNull(monsterAttack(1L, LocalDate.of(2026, 7, 19)))
        assertEquals(clock.now().toEpochMilli(), database.combatDao().getCombatProgress()?.lastReconciledAtEpochMillis)
    }

    @Test
    fun untimedOccurrenceBecomesFailedOnlyAfterNextDayStarts() = runTest {
        val occurrenceDate = LocalDate.of(2026, 7, 20)
        clock.instant = occurrenceDate.atStartOfDay(ZoneId.of("UTC")).toInstant()
        repository.observeCombat().first()
        insertTask(taskId = 1L, date = occurrenceDate)
        val taskRepository = RoomTaskRepository(database, clock)

        clock.instant = occurrenceDate.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant()
        repository.reconcileOverdue(clock.now())
        assertEquals(
            TaskOccurrenceStatus.TODO,
            taskRepository.observeOccurrences(occurrenceDate, occurrenceDate).first().single().status,
        )

        clock.instant = clock.now().plusMillis(1)
        repository.reconcileOverdue(clock.now())

        assertEquals(
            TaskOccurrenceStatus.FAILED,
            taskRepository.observeOccurrences(occurrenceDate, occurrenceDate).first().single().status,
        )
        assertEquals(
            clock.now().toEpochMilli(),
            failure(1L, occurrenceDate)?.failedAtEpochMillis,
        )
    }

    @Test
    fun recurringReconciliationFailsOnlyTheDueOccurrenceDate() = runTest {
        val dueDate = LocalDate.of(2026, 7, 20)
        clock.instant = dueDate.atStartOfDay(ZoneId.of("UTC")).toInstant()
        repository.observeCombat().first()
        insertTask(
            taskId = 1L,
            date = dueDate,
            recurrenceRule = RecurrenceRule.DAILY,
            recurrenceSeriesId = 100L,
        )
        val taskRepository = RoomTaskRepository(database, clock)
        clock.instant = dueDate.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant().plusMillis(1)

        repository.reconcileOverdue(clock.now())

        val occurrences = taskRepository
            .observeOccurrences(dueDate, dueDate.plusDays(1))
            .first()
        assertEquals(
            listOf(TaskOccurrenceStatus.FAILED, TaskOccurrenceStatus.TODO),
            occurrences.map { it.status },
        )
        assertEquals(100L, failure(1L, dueDate)?.recurrenceSeriesId)
        assertNull(failure(1L, dueDate.plusDays(1)))
    }

    @Test
    fun reconciliationSortsNewDueEventsCapsDamageAtThreeAndIsIdempotent() = runTest {
        clock.instant = Instant.parse("2026-07-20T00:00:00Z")
        val initial = repository.observeCombat().first()
        val earlierDate = LocalDate.of(2026, 7, 19)
        val date = LocalDate.of(2026, 7, 20)
        listOf(1L, 2L, 3L, 4L, 5L, 7L).forEach { insertTask(it, date) }
        insertTask(6L, earlierDate)
        database.completionLogDao().insert(
            CompletionLogEntity(
                taskId = 3L,
                occurrenceDateEpochDay = date.toEpochDay(),
                completedAtEpochMillis = Instant.parse("2026-07-20T10:00:00Z").toEpochMilli(),
            ),
        )
        insertSkippedMonsterAttack(2L, date, initial.activeMonster.id)
        clock.instant = Instant.parse("2026-07-21T01:00:00Z")

        val first = repository.reconcileOverdue(clock.now())
        val hpAfterFirst = database.characterProfileDao().getCurrentState()!!.currentHp
        val second = repository.reconcileOverdue(clock.now().plusSeconds(60))

        assertEquals(3, first.monsterAttacksApplied)
        assertEquals(2, first.monsterAttacksSkipped)
        assertEquals(77, hpAfterFirst)
        assertEquals(0, second.monsterAttacksApplied)
        assertEquals(0, second.monsterAttacksSkipped)
        assertEquals(hpAfterFirst, database.characterProfileDao().getCurrentState()?.currentHp)
        assertEquals(CombatEventStatus.APPLIED.name, monsterAttack(6L, earlierDate)?.status)
        assertEquals(CombatEventStatus.APPLIED.name, monsterAttack(1L, date)?.status)
        assertEquals(CombatEventStatus.APPLIED.name, monsterAttack(4L, date)?.status)
        assertEquals(CombatEventStatus.SKIPPED.name, monsterAttack(5L, date)?.status)
        assertEquals(CombatEventStatus.SKIPPED.name, monsterAttack(7L, date)?.status)
        assertEquals(
            MonsterAttackSkipReason.SKIPPED_RECONCILIATION_CAP.name,
            monsterAttack(5L, date)?.skipReason,
        )
        listOf(
            6L to earlierDate,
            1L to date,
            4L to date,
            5L to date,
            7L to date,
        ).forEach { (taskId, occurrenceDate) ->
            val failure = failure(taskId, occurrenceDate)
            assertNotNull(failure)
            assertEquals(taskId, failure?.recurrenceSeriesId)
            assertEquals(
                Instant.parse("2026-07-21T01:00:00Z").toEpochMilli(),
                failure?.failedAtEpochMillis,
            )
        }
        assertNull(monsterAttack(3L, date))
        assertNull(failure(2L, date))
        assertNull(failure(3L, date))
        assertEquals(clock.now().plusSeconds(60).toEpochMilli(), database.combatDao().getCombatProgress()?.lastReconciledAtEpochMillis)
    }

    @Test
    fun reconciliationDrainsPendingPlayerAttacksBeforeChoosingMonsterAttackSource() = runTest {
        clock.instant = Instant.parse("2026-07-20T00:00:00Z")
        val initial = repository.observeCombat().first()
        val date = LocalDate.of(2026, 7, 20)
        enqueueAttack(
            taskId = 100L,
            date = date,
            sourceAttack = 200,
            criticalChanceBp = 0,
        )
        insertTask(1L, date)
        clock.instant = Instant.parse("2026-07-21T01:00:00Z")

        val result = repository.reconcileOverdue(clock.now())

        val progress = database.combatDao().getCombatProgress()!!
        val event = monsterAttack(1L, date)!!
        assertEquals(1, result.playerAttacksProcessed)
        assertEquals(1, result.monsterAttacksApplied)
        assertTrue(progress.activeMonsterInstanceId != initial.activeMonster.id)
        assertEquals(progress.activeMonsterInstanceId, event.sourceMonsterInstanceId)
        assertEquals(15, event.sourceRawDamage)
        assertEquals(8, event.playerDefense)
        assertEquals(13, event.finalDamage)
    }

    @Test
    fun hundredsOfOverdueOccurrencesAreFinalizedInOneFiniteRun() = runTest {
        val firstDate = LocalDate.of(2026, 1, 1)
        clock.instant = firstDate.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant()
        repository.observeCombat().first()
        insertTask(
            taskId = 1L,
            date = firstDate,
            recurrenceRule = RecurrenceRule.DAILY,
        )
        val lastDate = firstDate.plusDays(204)
        clock.instant = lastDate.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant().plusMillis(1)

        val first = repository.reconcileOverdue(clock.now())
        val second = repository.reconcileOverdue(clock.now().plusSeconds(1))

        assertEquals(3, first.monsterAttacksApplied)
        assertEquals(202, first.monsterAttacksSkipped)
        assertEquals(0, second.monsterAttacksApplied)
        assertEquals(0, second.monsterAttacksSkipped)
        assertEquals(CombatEventStatus.APPLIED.name, monsterAttack(1L, firstDate)?.status)
        assertEquals(CombatEventStatus.SKIPPED.name, monsterAttack(1L, firstDate.plusDays(3))?.status)
        assertEquals(CombatEventStatus.SKIPPED.name, monsterAttack(1L, lastDate)?.status)
    }

    @Test
    fun undoAutomaticFailureAllowsLateCompletionRewardAndPlayerAttack() = runTest {
        clock.instant = Instant.parse("2026-07-21T08:00:00Z")
        repository.observeCombat().first()
        val date = LocalDate.of(2026, 7, 21)
        insertTask(taskId = 1L, date = date, time = LocalTime.of(9, 0))
        clock.instant = Instant.parse("2026-07-21T09:16:00Z")

        repository.reconcileOverdue(clock.now())
        val taskRepository = RoomTaskRepository(database, clock)
        taskRepository.undoFailOccurrence(1L, date)
        val completion = taskRepository.completeOccurrence(1L, date)

        assertFalse(completion.alreadyRewarded)
        assertNull(failure(1L, date))
        assertNotNull(monsterAttack(1L, date))
        assertNotNull(database.completionLogDao().find(1L, date.toEpochDay()))
        assertNotNull(database.rewardLedgerDao().find(1L, date.toEpochDay()))
        assertEquals(CombatEventStatus.PENDING.name, attack(1L, date).status)
    }

    @Test
    fun reconciliationRecordsNonlethalAndLethalDamageWithDefeatedHpAtZero() = runTest {
        clock.instant = Instant.parse("2026-07-20T00:00:00Z")
        repository.observeCombat().first()
        database.characterProfileDao().upsertCurrentState(
            CharacterCurrentStateEntity(
                characterId = 1L,
                currentHp = 12,
                balanceVersion = 1,
                updatedAtEpochMillis = clock.now().toEpochMilli(),
            ),
        )
        val date = LocalDate.of(2026, 7, 20)
        insertTask(1L, date)
        insertTask(2L, date)
        val transitions = mutableListOf<CombatTransition>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.events.collect { transitions += it }
        }
        clock.instant = Instant.parse("2026-07-21T01:00:00Z")

        repository.reconcileOverdue(clock.now())

        val nonlethal = monsterAttack(1L, date)!!
        val lethal = monsterAttack(2L, date)!!
        assertEquals(12, nonlethal.playerHpBefore)
        assertEquals(1, nonlethal.playerHpAfter)
        assertFalse(nonlethal.wasLethal)
        assertNull(nonlethal.revivedHp)
        assertEquals(1, lethal.playerHpBefore)
        assertEquals(0, lethal.playerHpAfter)
        assertTrue(lethal.wasLethal)
        assertEquals(44, lethal.revivedHp)
        assertEquals(44, database.characterProfileDao().getCurrentState()?.currentHp)
        assertEquals(2, transitions.size)
        assertEquals(12, transitions[0].before.playerCurrentHp)
        assertEquals(1, transitions[0].after.playerCurrentHp)
        assertEquals(1, transitions[1].before.playerCurrentHp)
        assertEquals(44, transitions[1].after.playerCurrentHp)
        assertEquals(
            listOf(
                CombatLifecycleEvent.PlayerDefeated::class,
                CombatLifecycleEvent.StatusEffectApplied::class,
                CombatLifecycleEvent.PlayerEmergencyRecovered::class,
            ),
            (transitions[1] as CombatTransition.MonsterAttack)
                .lifecycleEvents
                .map { it::class },
        )
    }

    @Test
    fun oneHpMaximumStillClampsLethalDamageToZero() = runTest {
        val oneHpConfig = CharacterStatBalanceConfig(
            maxHpBase = 1,
            maxHpPerLevel = 0,
            maxHpPerVitality = 0,
        )
        repository = RoomCombatRepository(
            database = database,
            clock = clock,
            seedSource = seedSource,
            characterBalanceConfig = oneHpConfig,
        )
        clock.instant = Instant.parse("2026-07-20T00:00:00Z")
        repository.observeCombat().first()
        val date = LocalDate.of(2026, 7, 20)
        insertTask(1L, date)
        clock.instant = Instant.parse("2026-07-21T01:00:00Z")

        repository.reconcileOverdue(clock.now())

        val event = monsterAttack(1L, date)!!
        assertTrue(event.wasLethal)
        assertEquals(1, event.playerMaxHp)
        assertEquals(1, event.revivedHp)
        assertEquals(0, event.playerHpAfter)
        assertEquals(1, database.characterProfileDao().getCurrentState()?.currentHp)
    }

    @Test
    fun zeroHpTriggersTheStatusLifecycleAndEmergencyRecovery() = runTest {
        clock.instant = Instant.parse("2026-07-20T00:00:00Z")
        repository.observeCombat().first()
        database.characterProfileDao().upsertCurrentState(
            CharacterCurrentStateEntity(
                characterId = 1L,
                currentHp = 0,
                balanceVersion = 1,
                updatedAtEpochMillis = clock.now().toEpochMilli(),
            ),
        )
        val date = LocalDate.of(2026, 7, 20)
        insertTask(1L, date)
        clock.instant = Instant.parse("2026-07-21T01:00:00Z")

        repository.reconcileOverdue(clock.now())

        val event = monsterAttack(1L, date)!!
        assertNull(event.skipReason)
        assertEquals(0, event.playerHpBefore)
        assertEquals(0, event.playerHpAfter)
        assertTrue(event.wasLethal)
        assertEquals(44, event.revivedHp)
        assertEquals(44, database.characterProfileDao().getCurrentState()?.currentHp)
    }

    @Test
    fun lethalAttackAppliesSevereInjuryOnceAndDuplicateReturnsStoredResult() = runTest {
        clock.instant = Instant.parse("2026-07-20T00:00:00Z")
        val initial = repository.observeCombat().first()
        val date = LocalDate.of(2026, 7, 20)
        insertFailure(taskId = 1L, date = date, failedAtEpochMillis = clock.now().toEpochMilli())
        database.characterProfileDao().upsertCurrentState(
            database.characterProfileDao().getCurrentState()!!.copy(currentHp = 1),
        )
        val transitions = mutableListOf<CombatTransition>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.events.collect { transitions += it }
        }
        clock.instant = Instant.parse("2026-07-20T01:00:00Z")

        val first = repository.processFailedOccurrenceAttack(1L, date) as MonsterAttackResult.Applied
        val storedAfterFirst = monsterAttack(1L, date)!!
        val effectAfterFirst = database.statusEffectDao().getStatusEffect(
            characterId = 1L,
            effectType = StatusEffectType.SEVERE_INJURY.name,
        )!!
        val hpAfterFirst = database.characterProfileDao().getCurrentState()!!.currentHp
        val duplicate = repository.processFailedOccurrenceAttack(1L, date) as MonsterAttackResult.Applied

        assertFalse(first.wasAlreadyApplied)
        assertTrue(duplicate.wasAlreadyApplied)
        assertEquals(first.attack, duplicate.attack)
        assertEquals(storedAfterFirst, monsterAttack(1L, date))
        assertEquals(
            effectAfterFirst,
            database.statusEffectDao().getStatusEffect(1L, StatusEffectType.SEVERE_INJURY.name),
        )
        assertEquals(hpAfterFirst, database.characterProfileDao().getCurrentState()!!.currentHp)
        assertEquals(0, first.attack.playerHpAfter)
        assertEquals(44, first.attack.revivedHp)
        assertEquals(44, hpAfterFirst)
        assertEquals(1L, effectAfterFirst.revision)
        assertEquals(3, effectAfterFirst.remainingRecoveryCompletions)
        assertEquals(clock.now().toEpochMilli(), effectAfterFirst.appliedAtEpochMillis)
        assertEquals(
            clock.now().plusSeconds(24 * 60 * 60).toEpochMilli(),
            effectAfterFirst.expiresAtEpochMillis,
        )
        assertEquals(1, transitions.size)
        val transition = transitions.single() as CombatTransition.MonsterAttack
        val lifecycle = transition.lifecycleEvents
        assertEquals(
            listOf(
                CombatLifecycleEvent.PlayerDefeated::class,
                CombatLifecycleEvent.StatusEffectApplied::class,
                CombatLifecycleEvent.PlayerEmergencyRecovered::class,
            ),
            lifecycle.map { it::class },
        )
        assertEquals(3, lifecycle.map { it.eventId }.distinct().size)
        assertTrue(lifecycle.all { it.eventId.contains("monster-attack:1:${date.toEpochDay()}") })
        assertTrue(lifecycle.all { it.effectRevision == 1L })
        assertEquals(
            listOf(
                BattleEffectEvent.MonsterAttackStarted::class,
                BattleEffectEvent.EntityHit::class,
                BattleEffectEvent.PlayerDefeated::class,
            ),
            transition.effectEvents.map { it::class },
        )
        assertEquals(
            (lifecycle.first() as CombatLifecycleEvent.PlayerDefeated).eventId,
            (transition.effectEvents.last() as BattleEffectEvent.PlayerDefeated)
                .sourceLifecycleEventId,
        )
        assertEquals(initial.activeMonster, repository.observeCombat().first().activeMonster)
    }

    @Test
    fun defeatWhileInjuredRefreshesOneEffectAndIgnoresPreviousRevisionCredits() = runTest {
        clock.instant = Instant.parse("2026-07-20T00:00:00Z")
        repository.observeCombat().first()
        val firstDate = LocalDate.of(2026, 7, 20)
        insertFailure(1L, firstDate, clock.now().toEpochMilli())
        database.characterProfileDao().upsertCurrentState(
            database.characterProfileDao().getCurrentState()!!.copy(currentHp = 1),
        )
        clock.instant = Instant.parse("2026-07-20T03:00:00Z")
        val first = repository.processFailedOccurrenceAttack(1L, firstDate) as MonsterAttackResult.Applied
        check(first.attack.wasLethal)
        check(
            database.statusEffectDao().insertRecoveryOccurrence(
                StatusEffectRecoveryOccurrenceEntity(
                    characterId = 1L,
                    effectType = StatusEffectType.SEVERE_INJURY.name,
                    revision = 1L,
                    taskId = 99L,
                    occurrenceDateEpochDay = firstDate.toEpochDay(),
                ),
            ) != -1L,
        )
        check(
            database.statusEffectDao().decrementRemainingRecoveryCompletions(
                characterId = 1L,
                effectType = StatusEffectType.SEVERE_INJURY.name,
                revision = 1L,
                lastMutationId = "test:revision-1-credit",
            ) == 1,
        )
        database.characterProfileDao().upsertCurrentState(
            database.characterProfileDao().getCurrentState()!!.copy(currentHp = 1),
        )
        val secondDate = firstDate.plusDays(1)
        insertFailure(2L, secondDate, clock.now().toEpochMilli())
        val transitions = mutableListOf<CombatTransition>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.events.collect { transitions += it }
        }
        clock.instant = Instant.parse("2026-07-21T02:00:00Z")

        val second = repository.processFailedOccurrenceAttack(2L, secondDate) as MonsterAttackResult.Applied

        val effect = database.statusEffectDao().getStatusEffect(1L, StatusEffectType.SEVERE_INJURY.name)!!
        assertEquals(2L, effect.revision)
        assertEquals(3, effect.remainingRecoveryCompletions)
        assertEquals(clock.now().toEpochMilli(), effect.appliedAtEpochMillis)
        assertEquals(clock.now().plusSeconds(24 * 60 * 60).toEpochMilli(), effect.expiresAtEpochMillis)
        assertEquals(44, second.attack.revivedHp)
        assertEquals(44, database.characterProfileDao().getCurrentState()!!.currentHp)
        assertEquals(88, repository.observeCombat().first().playerMaxHp)
        assertEquals(2, loadActiveStatusModifiers(database, 1L, clock.now()).size)
        assertEquals(
            listOf(1L),
            database.statusEffectDao()
                .getRecoveryOccurrences(1L, StatusEffectType.SEVERE_INJURY.name)
                .map { it.revision },
        )
        assertEquals(
            listOf(
                CombatLifecycleEvent.PlayerDefeated::class,
                CombatLifecycleEvent.StatusEffectRefreshed::class,
                CombatLifecycleEvent.PlayerEmergencyRecovered::class,
            ),
            (transitions.single() as CombatTransition.MonsterAttack)
                .lifecycleEvents
                .map { it::class },
        )
    }

    @Test
    fun rapidLethalOverdueAttacksPreserveAttackAndBatchOrderWhileRefreshingEachDefeat() = runTest {
        val oneHpConfig = CharacterStatBalanceConfig(
            maxHpBase = 1,
            maxHpPerLevel = 0,
            maxHpPerVitality = 0,
        )
        repository = RoomCombatRepository(
            database = database,
            clock = clock,
            seedSource = seedSource,
            characterBalanceConfig = oneHpConfig,
        )
        clock.instant = Instant.parse("2026-07-20T00:00:00Z")
        repository.observeCombat().first()
        val date = LocalDate.of(2026, 7, 20)
        listOf(30L, 10L, 20L).forEach { insertTask(it, date) }
        val transitions = mutableListOf<CombatTransition>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.events.collect { transitions += it }
        }
        clock.instant = Instant.parse("2026-07-21T01:00:00Z")

        repository.reconcileOverdue(clock.now())

        val attacks = transitions.map { it as CombatTransition.MonsterAttack }
        assertEquals(listOf(10L, 20L, 30L), attacks.map { it.attack.taskId })
        assertEquals(
            listOf(1L, 2L, 3L),
            attacks.map {
                it.lifecycleEvents.single { event ->
                    event is CombatLifecycleEvent.PlayerDefeated
                }.effectRevision
            },
        )
        attacks.forEachIndexed { index, transition ->
            assertEquals(
                listOf(
                    CombatLifecycleEvent.PlayerDefeated::class,
                    if (index == 0) {
                        CombatLifecycleEvent.StatusEffectApplied::class
                    } else {
                        CombatLifecycleEvent.StatusEffectRefreshed::class
                    },
                    CombatLifecycleEvent.PlayerEmergencyRecovered::class,
                ),
                transition.lifecycleEvents.map { it::class },
            )
        }
        assertEquals(
            3L,
            database.statusEffectDao()
                .getStatusEffect(1L, StatusEffectType.SEVERE_INJURY.name)
                ?.revision,
        )
        assertEquals(1, database.characterProfileDao().getCurrentState()?.currentHp)
    }

    @Test
    fun restartRestoresActiveInjuryAndExactExpiryReconciliationOnlyRemovesModifier() = runTest {
        clock.instant = Instant.parse("2026-07-20T00:00:00Z")
        repository.observeCombat().first()
        val date = LocalDate.of(2026, 7, 20)
        insertFailure(1L, date, clock.now().toEpochMilli())
        database.characterProfileDao().upsertCurrentState(
            database.characterProfileDao().getCurrentState()!!.copy(currentHp = 1),
        )
        clock.instant = Instant.parse("2026-07-20T01:00:00Z")
        repository.processFailedOccurrenceAttack(1L, date)
        val active = database.statusEffectDao().getStatusEffect(1L, StatusEffectType.SEVERE_INJURY.name)!!
        val restarted = RoomCombatRepository(database, clock, SequenceCombatSeedSource(1L))

        assertEquals(88, restarted.observeCombat().first().playerMaxHp)
        assertEquals(44, restarted.observeCombat().first().playerCurrentHp)

        clock.instant = Instant.ofEpochMilli(active.expiresAtEpochMillis)
        restarted.reconcileOverdue(clock.now())

        val expired = database.statusEffectDao().getStatusEffect(1L, StatusEffectType.SEVERE_INJURY.name)!!
        assertFalse(expired.active)
        assertEquals(110, restarted.observeCombat().first().playerMaxHp)
        assertEquals(44, database.characterProfileDao().getCurrentState()?.currentHp)
    }

    @Test
    fun lethalMonsterAttackPreservesMonsterProgressAndPlayerRewardSnapshot() = runTest {
        clock.instant = Instant.parse("2026-07-20T00:00:00Z")
        val initial = repository.observeCombat().first()
        val playerDate = LocalDate.of(2026, 7, 19)
        enqueueAttack(
            taskId = 50L,
            date = playerDate,
            combatRewardVersion = CombatRewardBalanceCatalog.CURRENT_VERSION,
        )
        repository.processPlayerAttack(50L, playerDate)
        val playerAttackBefore = attack(50L, playerDate)
        val progressBefore = database.combatDao().getCombatProgress()
        val monsterBefore = database.combatDao().getMonsterInstance(initial.activeMonster.id)
        database.characterProfileDao().upsertCurrentState(
            database.characterProfileDao().getCurrentState()!!.copy(currentHp = 1),
        )
        val failureDate = LocalDate.of(2026, 7, 20)
        insertFailure(60L, failureDate, clock.now().toEpochMilli())

        repository.processFailedOccurrenceAttack(60L, failureDate)

        assertEquals(progressBefore, database.combatDao().getCombatProgress())
        assertEquals(monsterBefore, database.combatDao().getMonsterInstance(initial.activeMonster.id))
        assertEquals(playerAttackBefore, attack(50L, playerDate))
        assertTrue(playerAttackBefore.hitXpAward > 0L)
    }

    @Test
    fun cursorFailureRollsBackFailureEventCharacterHpAndCursorTogether() = runTest {
        clock.instant = Instant.parse("2026-07-20T00:00:00Z")
        val initial = repository.observeCombat().first()
        val date = LocalDate.of(2026, 7, 20)
        insertTask(1L, date)
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_combat_cursor
            BEFORE UPDATE OF lastReconciledAtEpochMillis ON combat_progress
            BEGIN
                SELECT RAISE(ABORT, 'forced cursor failure');
            END
            """.trimIndent(),
        )
        val transitions = mutableListOf<CombatTransition>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.events.collect { transitions += it }
        }
        clock.instant = Instant.parse("2026-07-21T01:00:00Z")

        val failure = runCatching { repository.reconcileOverdue(clock.now()) }.exceptionOrNull()

        assertNotNull(failure)
        assertNull(failure(1L, date))
        assertNull(monsterAttack(1L, date))
        assertEquals(initial.playerCurrentHp, database.characterProfileDao().getCurrentState()?.currentHp)
        assertEquals(
            initial.progress.lastReconciledAt.toEpochMilli(),
            database.combatDao().getCombatProgress()?.lastReconciledAtEpochMillis,
        )
        assertEquals(emptyList<CombatTransition>(), transitions)
    }

    @Test
    fun undoAutomaticFailureKeepsDamageAndEventWithoutRecreatingFailureOrTransition() = runTest {
        clock.instant = Instant.parse("2026-07-20T00:00:00Z")
        val initial = repository.observeCombat().first()
        val occurrenceDate = LocalDate.of(2026, 7, 20)
        insertTask(taskId = 1L, date = occurrenceDate)
        val taskRepository = RoomTaskRepository(database, clock)
        val transitions = mutableListOf<CombatTransition>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.events.collect { transitions += it }
        }
        clock.instant = Instant.parse("2026-07-21T01:00:00Z")

        repository.reconcileOverdue(clock.now())
        val storedEvent = monsterAttack(1L, occurrenceDate)
        val hpAfterDamage = database.characterProfileDao().getCurrentState()?.currentHp
        taskRepository.undoFailOccurrence(1L, occurrenceDate)
        val repeated = repository.reconcileOverdue(clock.now().plusSeconds(60))

        assertEquals(TaskOccurrenceStatus.TODO, taskRepository
            .observeOccurrences(occurrenceDate, occurrenceDate)
            .first()
            .single()
            .status)
        assertNull(failure(1L, occurrenceDate))
        assertNotNull(storedEvent)
        assertEquals(storedEvent, monsterAttack(1L, occurrenceDate))
        assertEquals(initial.playerCurrentHp - 11, hpAfterDamage)
        assertEquals(hpAfterDamage, database.characterProfileDao().getCurrentState()?.currentHp)
        assertEquals(0, repeated.monsterAttacksApplied)
        assertEquals(0, repeated.monsterAttacksSkipped)
        assertEquals(1, transitions.size)
    }

    @Test
    fun deletedAndSplitRecurrencesKeepPastDueBoundariesAndCompletionHistory() = runTest {
        clock.instant = Instant.parse("2026-07-18T00:00:00Z")
        val initial = repository.observeCombat().first()
        insertTask(
            taskId = 10L,
            date = LocalDate.of(2026, 7, 17),
            recurrenceRule = RecurrenceRule.DAILY,
            endDate = LocalDate.of(2026, 7, 19),
            recurrenceSeriesId = 100L,
        )
        insertTask(
            taskId = 20L,
            date = LocalDate.of(2026, 7, 20),
            recurrenceRule = RecurrenceRule.DAILY,
            recurrenceSeriesId = 100L,
        )
        insertTask(
            taskId = 30L,
            date = LocalDate.of(2026, 7, 18),
            recurrenceRule = RecurrenceRule.DAILY,
            deletedAt = Instant.parse("2026-07-20T12:00:00Z"),
        )
        listOf(
            10L to LocalDate.of(2026, 7, 17),
            10L to LocalDate.of(2026, 7, 18),
            20L to LocalDate.of(2026, 7, 21),
            30L to LocalDate.of(2026, 7, 18),
        ).forEach { (taskId, date) -> insertCompletion(taskId, date) }
        clock.instant = Instant.parse("2026-07-22T01:00:00Z")

        val result = repository.reconcileOverdue(clock.now())

        assertEquals(3, result.monsterAttacksApplied)
        assertEquals(0, result.monsterAttacksSkipped)
        assertNotNull(monsterAttack(10L, LocalDate.of(2026, 7, 19)))
        assertNotNull(monsterAttack(20L, LocalDate.of(2026, 7, 20)))
        assertNotNull(monsterAttack(30L, LocalDate.of(2026, 7, 19)))
        assertNull(monsterAttack(10L, LocalDate.of(2026, 7, 20)))
        assertNull(monsterAttack(30L, LocalDate.of(2026, 7, 20)))
        assertEquals(initial.activeMonster.id, monsterAttack(20L, LocalDate.of(2026, 7, 20))?.sourceMonsterInstanceId)
    }

    @Test
    fun reconciliationUsesClockZoneAndDoesNotDependOnNotificationPermission() = runTest {
        val application = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        clock = MutableClock(ZoneId.of("America/New_York"))
        repository = RoomCombatRepository(database, clock, seedSource)
        clock.instant = Instant.parse("2026-03-08T05:00:00Z")
        repository.observeCombat().first()
        val date = LocalDate.of(2026, 3, 8)
        insertTask(1L, date)
        clock.instant = Instant.parse("2026-03-09T04:00:00.001Z")

        val result = repository.reconcileOverdue(clock.now())

        assertEquals(1, result.monsterAttacksApplied)
        assertNotNull(monsterAttack(1L, date))
    }

    @Test
    fun combatTablesPersistOnlySourceStateNotDerivedStats() = runTest {
        repository.observeCombat().first()

        val monsterColumns = tableColumns("monster_instances")
        val progressColumns = tableColumns("combat_progress")

        assertFalse("maxHp" in monsterColumns)
        assertFalse("damage" in monsterColumns)
        assertFalse("defense" in monsterColumns)
        assertFalse("isDefeated" in monsterColumns)
        assertFalse("species" in monsterColumns)
        assertFalse("schedule" in monsterColumns)
        assertFalse("randomSeed" in monsterColumns)
        assertFalse("maxHp" in progressColumns)
        assertFalse("attack" in progressColumns)
    }

    private suspend fun insertTask(
        taskId: Long,
        date: LocalDate,
        time: LocalTime? = null,
        recurrenceRule: RecurrenceRule = RecurrenceRule.NONE,
        endDate: LocalDate? = null,
        recurrenceSeriesId: Long = taskId,
        deletedAt: Instant? = null,
    ) {
        val inserted = database.todoTaskDao().insert(
            TodoTaskEntity(
                id = taskId,
                recurrenceSeriesId = recurrenceSeriesId,
                title = "Quest $taskId",
                memo = "",
                startDateEpochDay = date.toEpochDay(),
                endDateEpochDay = endDate?.toEpochDay(),
                timeMinuteOfDay = time?.let { it.hour * 60 + it.minute },
                difficulty = TaskDifficulty.MEDIUM.name,
                category = "General",
                recurrenceRule = recurrenceRule.name,
                createdAtEpochMillis = clock.now().toEpochMilli(),
                updatedAtEpochMillis = clock.now().toEpochMilli(),
                deletedAtEpochMillis = deletedAt?.toEpochMilli(),
            ),
        )
        check(inserted == taskId)
    }

    private suspend fun initializeEquipmentCharacter() {
        RoomCharacterRepository(database, clock).resetStats()
        database.characterProfileDao().upsert(
            database.characterProfileDao().getProfile()!!.copy(currentGold = 10_000L),
        )
    }

    private suspend fun grantAndEquip(equipmentId: Long, slot: EquipmentSlot) {
        EquipmentCatalogSeeder.seed(database.equipmentDao())
        val ownedEquipmentId = database.equipmentDao().insertOwnedEquipment(
            OwnedEquipmentEntity(
                characterId = 1L,
                equipmentId = equipmentId,
                acquiredAtEpochMillis = clock.now().toEpochMilli(),
            ),
        ).also { check(it != -1L) }
        val equipmentRepository = RoomEquipmentRepository(database, clock)
        equipmentRepository.equipOwnedEquipment(1L, ownedEquipmentId, slot)
    }

    private suspend fun insertCompletion(taskId: Long, date: LocalDate) {
        check(
            database.completionLogDao().insert(
                CompletionLogEntity(
                    taskId = taskId,
                    occurrenceDateEpochDay = date.toEpochDay(),
                    completedAtEpochMillis = clock.now().toEpochMilli(),
                ),
            ) != -1L,
        )
    }

    private suspend fun insertFailure(
        taskId: Long,
        date: LocalDate,
        failedAtEpochMillis: Long,
    ) {
        check(
            database.failureLogDao().insert(
                FailureLogEntity(
                    taskId = taskId,
                    occurrenceDateEpochDay = date.toEpochDay(),
                    recurrenceSeriesId = taskId,
                    failedAtEpochMillis = failedAtEpochMillis,
                ),
            ) != -1L,
        )
    }

    private suspend fun insertSkippedMonsterAttack(
        taskId: Long,
        date: LocalDate,
        sourceMonsterInstanceId: Long,
    ) {
        check(
            database.combatDao().insertMonsterAttackEvent(
                MonsterAttackEventEntity(
                    taskId = taskId,
                    occurrenceDateEpochDay = date.toEpochDay(),
                    recurrenceSeriesId = taskId,
                    status = CombatEventStatus.SKIPPED.name,
                    skipReason = MonsterAttackSkipReason.SKIPPED_RECONCILIATION_CAP.name,
                    sourceMonsterInstanceId = sourceMonsterInstanceId,
                    sourceMonsterLevel = 1,
                    sourceRawDamage = 12,
                    playerDefense = 8,
                    playerMaxHp = 110,
                    finalDamage = 0,
                    playerHpBefore = 110,
                    playerHpAfter = 110,
                    wasLethal = false,
                    revivedHp = null,
                    characterBalanceVersion = 1,
                    monsterBalanceVersion = 1,
                    processedAtEpochMillis = clock.now().toEpochMilli(),
                ),
            ) != -1L,
        )
    }

    private suspend fun enqueueAttack(
        taskId: Long,
        date: LocalDate,
        sourcePlayerLevel: Int = 1,
        sourceAttack: Int = 20,
        criticalChanceBp: Int = 0,
        criticalDamageBp: Int = 15_250,
        momentumBp: Int = 0,
        combatRewardVersion: Int = 0,
        sourceTaskDifficulty: String? = null,
        taskDifficultyBalanceVersion: Int = TaskDifficultyCombatBalanceCatalog.LEGACY_VERSION,
        createdAtEpochMillis: Long = 1_000L,
    ) {
        val inserted = database.combatDao().insertPlayerAttackEvent(
            PlayerAttackEventEntity(
                taskId = taskId,
                occurrenceDateEpochDay = date.toEpochDay(),
                recurrenceSeriesId = taskId,
                status = CombatEventStatus.PENDING.name,
                sourcePlayerLevel = sourcePlayerLevel,
                sourceAttack = sourceAttack,
                sourceCriticalChanceBp = criticalChanceBp,
                sourceCriticalDamageBp = criticalDamageBp,
                sourceMomentumBp = momentumBp,
                characterBalanceVersion = 1,
                monsterBalanceVersion = 1,
                combatRewardVersion = combatRewardVersion,
                sourceTaskDifficulty = sourceTaskDifficulty,
                taskDifficultyBalanceVersion = taskDifficultyBalanceVersion,
                createdAtEpochMillis = createdAtEpochMillis,
                targetMonsterInstanceId = null,
                seed = null,
                roll = null,
                wasCritical = null,
                rawDamage = null,
                targetDefense = null,
                finalDamage = null,
                targetHpBefore = null,
                targetHpAfter = null,
                processedAtEpochMillis = null,
            ),
        )
        check(inserted != -1L)
    }

    private suspend fun insertMonster(
        stageNumber: Int,
        encounterNumber: Int,
        stageLevel: Int,
        type: MonsterType,
        grade: MonsterGrade,
    ): Long {
        val config = MonsterBalanceConfig()
        val definition = MonsterCatalog.definitionFor(type, config)
        val level = stageLevel + when (grade) {
            MonsterGrade.NORMAL -> 0
            MonsterGrade.ELITE -> 1
            MonsterGrade.BOSS -> 2
        }
        val stats = MonsterStatsCalculator.calculate(definition, grade, level, config)
        return database.combatDao().insertMonsterInstance(
            MonsterInstanceEntity(
                definitionId = definition.id,
                grade = grade.name,
                stageNumber = stageNumber,
                encounterNumber = encounterNumber,
                level = level,
                currentHp = stats.maxHp,
                balanceVersion = config.version,
            ),
        ).also { check(it != -1L) }
    }

    private suspend fun activateScheduledMonster(
        stageNumber: Int,
        encounterNumber: Int,
        stageLevel: Int = 1,
    ): Long {
        val config = MonsterBalanceConfig()
        val grade = MonsterStagePolicy.gradeFor(stageNumber, config)
        val type = MonsterStagePolicy.typeFor(stageNumber, encounterNumber, config)
        val monsterId = database.combatDao()
            .getMonsterInstanceAt(stageNumber, encounterNumber)
            ?.id
            ?: insertMonster(
                stageNumber = stageNumber,
                encounterNumber = encounterNumber,
                stageLevel = stageLevel,
                type = type,
                grade = grade,
            )
        val progress = database.combatDao().getCombatProgress()!!
        check(
            database.combatDao().updateCombatProgress(
                id = progress.id,
                stageNumber = stageNumber,
                stageLevel = stageLevel,
                activeMonsterInstanceId = monsterId,
                lastReconciledAtEpochMillis = progress.lastReconciledAtEpochMillis,
                balanceVersion = progress.balanceVersion,
            ) == 1,
        )
        return monsterId
    }

    private suspend fun attack(taskId: Long, date: LocalDate) =
        database.combatDao().getPlayerAttackEvent(taskId, date.toEpochDay())!!

    private suspend fun monsterAttack(taskId: Long, date: LocalDate) =
        database.combatDao().getMonsterAttackEvent(taskId, date.toEpochDay())

    private suspend fun failure(taskId: Long, date: LocalDate) =
        database.failureLogDao().find(taskId, date.toEpochDay())

    private fun tableColumns(tableName: String): Set<String> {
        val cursor = database.openHelper.readableDatabase.query("PRAGMA table_info($tableName)")
        return cursor.use {
            buildSet {
                val nameIndex = it.getColumnIndexOrThrow("name")
                while (it.moveToNext()) add(it.getString(nameIndex))
            }
        }
    }

    private class MutableClock(
        override val zoneId: ZoneId = ZoneId.of("UTC"),
    ) : AppClock {
        var instant: Instant = Instant.parse("2026-07-21T01:00:00Z")

        override fun now(): Instant = instant

        override fun today(): LocalDate = instant.atZone(zoneId).toLocalDate()
    }

    private class SequenceCombatSeedSource(vararg seeds: Long) : CombatSeedSource {
        private val values = seeds.toList()
        val calls = AtomicInteger()

        override fun nextSeed(): Long = values.getOrElse(calls.getAndIncrement()) {
            error("No fixed combat seed remains")
        }
    }
}
