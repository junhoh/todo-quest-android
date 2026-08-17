package com.todoquest.data.local

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.todoquest.data.mapper.CombatEntityMapper
import com.todoquest.domain.model.CombatEventStatus
import com.todoquest.domain.model.MonsterAttackSkipReason
import com.todoquest.domain.model.MonsterAttackTrigger
import com.todoquest.domain.model.MonsterGrade
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CombatDaoTest {
    private lateinit var database: TodoQuestDatabase
    private lateinit var dao: CombatDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            TodoQuestDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.combatDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun monsterInstanceStageSlotIsUniqueAndUnknownGradeIsRejectedAtMapperBoundary() = runTest {
        val first = monsterInstance(stageNumber = 1, encounterNumber = 1)
        val insertedId = dao.insertMonsterInstance(first)

        assertEquals(1L, insertedId)
        assertEquals(
            -1L,
            dao.insertMonsterInstance(
                first.copy(id = 0L, definitionId = "monster_attack_v1"),
            ),
        )

        val stored = dao.getMonsterInstanceAt(stageNumber = 1, encounterNumber = 1)
        assertNotNull(stored)
        assertEquals(MonsterGrade.NORMAL, CombatEntityMapper.toDomain(stored!!).grade)
        assertThrows(IllegalArgumentException::class.java) {
            CombatEntityMapper.toDomain(stored.copy(grade = "UNKNOWN_GRADE"))
        }
    }

    @Test
    fun observeMonsterInstancesIncludesDefeatedHistoryAndOrdersByStageThenEncounter() = runTest {
        val stageTwo = monsterInstance(stageNumber = 2, encounterNumber = 1)
        val defeated = monsterInstance(stageNumber = 1, encounterNumber = 1).copy(currentHp = 0)
        val current = monsterInstance(stageNumber = 1, encounterNumber = 2)

        dao.insertMonsterInstance(stageTwo)
        dao.insertMonsterInstance(defeated)
        dao.insertMonsterInstance(current)

        assertEquals(
            listOf(
                defeated.copy(id = 2L),
                current.copy(id = 3L),
                stageTwo.copy(id = 1L),
            ),
            dao.observeMonsterInstances().first(),
        )
    }

    @Test
    fun playerAttackEventUsesOccurrenceKeyAndTransitionsPendingResultOnce() = runTest {
        val pending = playerAttackEvent(taskId = 10L, occurrenceDateEpochDay = 20_000L)

        assertNotNull(dao.insertPlayerAttackEvent(pending).takeUnless { it == -1L })
        assertEquals(-1L, dao.insertPlayerAttackEvent(pending.copy(sourceAttack = 999)))
        assertEquals(listOf(pending), dao.findPendingPlayerAttackEvents())

        assertEquals(
            1,
            dao.markPlayerAttackApplied(
                taskId = pending.taskId,
                occurrenceDateEpochDay = pending.occurrenceDateEpochDay,
                pendingStatus = CombatEventStatus.PENDING.name,
                appliedStatus = CombatEventStatus.APPLIED.name,
                targetMonsterInstanceId = 1L,
                seed = 1234L,
                roll = 234,
                wasCritical = true,
                rawDamage = 35,
                targetDefense = 7,
                finalDamage = 32,
                targetHpBefore = 75,
                targetHpAfter = 43,
                hitXpAward = 1L,
                killBonusXpAward = 0L,
                killGoldAward = 0L,
                rewardGradeMultiplierBp = 10_000,
                rewardGoldGainBonusBp = 10_000,
                processedAtEpochMillis = 2_000L,
            ),
        )
        assertEquals(
            0,
            dao.markPlayerAttackApplied(
                taskId = pending.taskId,
                occurrenceDateEpochDay = pending.occurrenceDateEpochDay,
                pendingStatus = CombatEventStatus.PENDING.name,
                appliedStatus = CombatEventStatus.APPLIED.name,
                targetMonsterInstanceId = 2L,
                seed = 9999L,
                roll = 9999,
                wasCritical = false,
                rawDamage = 1,
                targetDefense = 1,
                finalDamage = 1,
                targetHpBefore = 1,
                targetHpAfter = 0,
                hitXpAward = 1L,
                killBonusXpAward = 10L,
                killGoldAward = 5L,
                rewardGradeMultiplierBp = 10_000,
                rewardGoldGainBonusBp = 10_000,
                processedAtEpochMillis = 3_000L,
            ),
        )

        assertEquals(emptyList<PlayerAttackEventEntity>(), dao.findPendingPlayerAttackEvents())
        val applied = dao.getPlayerAttackEvent(pending.taskId, pending.occurrenceDateEpochDay)
        assertNotNull(applied)
        assertEquals(CombatEventStatus.APPLIED.name, applied!!.status)
        assertEquals(1L, applied.targetMonsterInstanceId)
        assertEquals(1234L, applied.seed)
        assertEquals(234, applied.roll)
        assertEquals(true, applied.wasCritical)
        assertEquals(32, applied.finalDamage)
        assertEquals(43, applied.targetHpAfter)
        assertEquals(1L, applied.hitXpAward)
        assertEquals(0L, applied.killBonusXpAward)
        assertEquals(0L, applied.killGoldAward)
        assertEquals(10_000, applied.rewardGradeMultiplierBp)
        assertEquals(10_000, applied.rewardGoldGainBonusBp)
        assertEquals(2_000L, applied.processedAtEpochMillis)
        assertEquals(
            CombatEventStatus.APPLIED,
            CombatEntityMapper.toEventStatus(applied.status),
        )
        assertThrows(IllegalArgumentException::class.java) {
            CombatEntityMapper.toEventStatus("UNKNOWN_STATUS")
        }
    }

    @Test
    fun playerAttackDifficultySnapshotRoundTripsWithoutChangingOtherSources() = runTest {
        val event = playerAttackEvent(
            taskId = 11L,
            occurrenceDateEpochDay = 20_001L,
        ).copy(
            sourceTaskDifficulty = "HARD",
            taskDifficultyBalanceVersion = 1,
            combatRewardVersion = 2,
        )

        assertNotNull(dao.insertPlayerAttackEvent(event).takeUnless { it == -1L })

        assertEquals(event, dao.getPlayerAttackEvent(event.taskId, event.occurrenceDateEpochDay))
        assertEquals(listOf(event), dao.findPendingPlayerAttackEvents())
    }

    @Test
    fun attackDirectionsHaveIndependentKeysAndReassignTaskWithoutChangingLineage() = runTest {
        val playerEvent = playerAttackEvent(taskId = 10L, occurrenceDateEpochDay = 20_000L)
        val monsterEvent = monsterAttackEvent(
            taskId = 10L,
            occurrenceDateEpochDay = 20_000L,
            trigger = MonsterAttackTrigger.MANUAL_FAILURE,
        )

        assertNotNull(dao.insertPlayerAttackEvent(playerEvent).takeUnless { it == -1L })
        assertNotNull(dao.insertMonsterAttackEvent(monsterEvent).takeUnless { it == -1L })
        assertEquals(-1L, dao.insertMonsterAttackEvent(monsterEvent.copy(finalDamage = 999)))

        dao.reassignPlayerAttackEventsFrom(
            taskId = 10L,
            fromOccurrenceDateEpochDay = 20_000L,
            newTaskId = 20L,
        )
        dao.reassignMonsterAttackEventsFrom(
            taskId = 10L,
            fromOccurrenceDateEpochDay = 20_000L,
            newTaskId = 20L,
        )

        assertNull(dao.getPlayerAttackEvent(10L, 20_000L))
        assertNull(dao.getMonsterAttackEvent(10L, 20_000L))
        assertEquals(100L, dao.getPlayerAttackEvent(20L, 20_000L)!!.recurrenceSeriesId)
        assertEquals(100L, dao.getMonsterAttackEvent(20L, 20_000L)!!.recurrenceSeriesId)
        assertEquals(
            MonsterAttackTrigger.MANUAL_FAILURE.name,
            dao.getMonsterAttackEvent(20L, 20_000L)!!.trigger,
        )
        assertEquals(
            MonsterAttackSkipReason.SKIPPED_RECONCILIATION_CAP,
            CombatEntityMapper.toMonsterAttackSkipReason(
                dao.getMonsterAttackEvent(20L, 20_000L)!!.skipReason,
            ),
        )
    }

    @Test
    fun manualAndDeadlineMonsterAttackTriggersShareOneOccurrenceKey() = runTest {
        val manual = monsterAttackEvent(
            taskId = 30L,
            occurrenceDateEpochDay = 21_000L,
            trigger = MonsterAttackTrigger.MANUAL_FAILURE,
        )
        val deadline = manual.copy(trigger = MonsterAttackTrigger.MISSED_DEADLINE.name)

        assertNotNull(dao.insertMonsterAttackEvent(manual).takeUnless { it == -1L })
        assertEquals(-1L, dao.insertMonsterAttackEvent(deadline))
        assertEquals(
            MonsterAttackTrigger.MANUAL_FAILURE.name,
            dao.getMonsterAttackEvent(30L, 21_000L)!!.trigger,
        )
    }

    @Test
    fun combatProgressAndMonsterStateCanBeObservedAndUpdatedExplicitly() = runTest {
        assertNull(dao.getCombatProgress())
        val monsterId = dao.insertMonsterInstance(monsterInstance())
        val progress = CombatProgressEntity(
            id = 1L,
            stageNumber = 1,
            stageLevel = 3,
            activeMonsterInstanceId = monsterId,
            lastReconciledAtEpochMillis = 1_000L,
            balanceVersion = 1,
        )
        assertNotNull(dao.insertCombatProgress(progress).takeUnless { it == -1L })

        assertEquals(progress, dao.observeCombatProgress().first())
        assertEquals(1, dao.updateMonsterCurrentHp(monsterId, currentHp = 40))
        assertEquals(40, dao.getMonsterInstance(monsterId)!!.currentHp)
        assertEquals(
            1,
            dao.updateCombatProgress(
                id = 1L,
                stageNumber = 2,
                stageLevel = 4,
                activeMonsterInstanceId = monsterId,
                lastReconciledAtEpochMillis = 2_000L,
                balanceVersion = 1,
            ),
        )
        assertEquals(2, dao.getCombatProgress()!!.stageNumber)
        assertEquals(2_000L, dao.getCombatProgress()!!.lastReconciledAtEpochMillis)
    }

    private fun monsterInstance(
        stageNumber: Int = 1,
        encounterNumber: Int = 1,
    ) = MonsterInstanceEntity(
        definitionId = "monster_balanced_v1",
        grade = MonsterGrade.NORMAL.name,
        stageNumber = stageNumber,
        encounterNumber = encounterNumber,
        level = 3,
        currentHp = 85,
        balanceVersion = 1,
    )

    private fun playerAttackEvent(
        taskId: Long,
        occurrenceDateEpochDay: Long,
    ) = PlayerAttackEventEntity(
        taskId = taskId,
        occurrenceDateEpochDay = occurrenceDateEpochDay,
        recurrenceSeriesId = 100L,
        status = CombatEventStatus.PENDING.name,
        sourcePlayerLevel = 3,
        sourceAttack = 25,
        sourceCriticalChanceBp = 800,
        sourceCriticalDamageBp = 15_250,
        sourceMomentumBp = 300,
        characterBalanceVersion = 1,
        monsterBalanceVersion = 1,
        createdAtEpochMillis = 1_000L,
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
    )

    private fun monsterAttackEvent(
        taskId: Long,
        occurrenceDateEpochDay: Long,
        trigger: MonsterAttackTrigger = MonsterAttackTrigger.MISSED_DEADLINE,
    ) = MonsterAttackEventEntity(
        taskId = taskId,
        occurrenceDateEpochDay = occurrenceDateEpochDay,
        recurrenceSeriesId = 100L,
        trigger = trigger.name,
        status = CombatEventStatus.SKIPPED.name,
        skipReason = MonsterAttackSkipReason.SKIPPED_RECONCILIATION_CAP.name,
        sourceMonsterInstanceId = 1L,
        sourceMonsterLevel = 3,
        sourceRawDamage = 16,
        playerDefense = 10,
        playerMaxHp = 120,
        finalDamage = 0,
        playerHpBefore = 120,
        playerHpAfter = 120,
        wasLethal = false,
        revivedHp = null,
        characterBalanceVersion = 1,
        monsterBalanceVersion = 1,
        processedAtEpochMillis = 2_000L,
    )
}
