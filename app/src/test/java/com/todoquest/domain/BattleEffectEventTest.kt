package com.todoquest.domain

import com.todoquest.domain.model.BattleEffectEvent
import com.todoquest.domain.model.BattleEntityRef
import com.todoquest.domain.model.CombatEventKey
import com.todoquest.domain.model.CombatEventKind
import com.todoquest.domain.model.CombatLifecycleEvent
import com.todoquest.domain.model.CombatSnapshot
import com.todoquest.domain.model.CombatTransition
import com.todoquest.domain.model.MonsterAttackSnapshot
import com.todoquest.domain.model.MonsterAttackTrigger
import com.todoquest.domain.model.MonsterGrade
import com.todoquest.domain.model.MonsterInstance
import com.todoquest.domain.model.MonsterSpecies
import com.todoquest.domain.model.MonsterStats
import com.todoquest.domain.model.PlayerAttackSnapshot
import com.todoquest.domain.model.StageProgress
import com.todoquest.domain.model.StatusEffectType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleEffectEventTest {
    @Test
    fun playerNonlethalAttackCreatesStartedThenHitWithSharedMetadata() {
        val transition = playerTransition(
            targetHpBefore = 20,
            finalDamage = 7,
            targetHpAfter = 13,
        )

        assertEquals(
            listOf(
                BattleEffectEvent.PlayerAttackStarted::class,
                BattleEffectEvent.EntityHit::class,
            ),
            transition.effectEvents.map { it::class },
        )
        assertCommonPlayerMetadata(transition.effectEvents, damage = 7, monsterId = OUTGOING_MONSTER_ID)
        assertTrue(transition.effectEvents.none(BattleEffectEvent::isTerminal))
    }

    @Test
    fun playerLethalAttackAddsMonsterDefeatedForTheOutgoingMonster() {
        val transition = playerTransition(
            targetHpBefore = 7,
            finalDamage = 7,
            targetHpAfter = 0,
            incomingMonsterId = INCOMING_MONSTER_ID,
        )

        assertEquals(
            listOf(
                BattleEffectEvent.PlayerAttackStarted::class,
                BattleEffectEvent.EntityHit::class,
                BattleEffectEvent.MonsterDefeated::class,
            ),
            transition.effectEvents.map { it::class },
        )
        assertCommonPlayerMetadata(transition.effectEvents, damage = 7, monsterId = OUTGOING_MONSTER_ID)
        assertFalse(transition.effectEvents[1].isTerminal)
        assertTrue(transition.effectEvents[2].isTerminal)
        assertEquals(OUTGOING_MONSTER_ID, transition.effectEvents[2].monsterId)
    }

    @Test
    fun playerOverkillClampsAtZeroAndCreatesOnlyOneTerminalEvent() {
        val transition = playerTransition(
            targetHpBefore = 3,
            finalDamage = 99,
            targetHpAfter = 0,
            incomingMonsterId = INCOMING_MONSTER_ID,
        )

        assertEquals(1, transition.effectEvents.count(BattleEffectEvent::isTerminal))
        assertEquals(99, transition.effectEvents.single(BattleEffectEvent::isTerminal).damage)
        assertEquals(
            BattleEntityRef.Monster(OUTGOING_MONSTER_ID),
            transition.effectEvents.single(BattleEffectEvent::isTerminal).target,
        )
    }

    @Test
    fun duplicatePlayerTransitionProducesTheSameDeterministicEffectEvents() {
        val first = playerTransition(targetHpBefore = 20, finalDamage = 7, targetHpAfter = 13)
        val duplicate = playerTransition(targetHpBefore = 20, finalDamage = 7, targetHpAfter = 13)

        assertEquals(first.effectEvents, duplicate.effectEvents)
        assertEquals(
            setOf("combat:PLAYER_ATTACK:$TASK_ID:$OCCURRENCE_EPOCH_DAY"),
            first.effectEvents.map(BattleEffectEvent::eventId).toSet(),
        )
    }

    @Test
    fun monsterNonlethalAttackCreatesStartedThenPlayerHitWithSharedMetadata() {
        val transition = monsterTransition(
            playerHpBefore = 50,
            finalDamage = 11,
            playerHpAfter = 39,
        )

        assertEquals(
            listOf(
                BattleEffectEvent.MonsterAttackStarted::class,
                BattleEffectEvent.EntityHit::class,
            ),
            transition.effectEvents.map { it::class },
        )
        assertCommonMonsterMetadata(transition.effectEvents, damage = 11)
        assertTrue(transition.effectEvents.none(BattleEffectEvent::isTerminal))
    }

    @Test
    fun monsterLethalAttackAddsPlayerDefeatedOnlyAfterValidatedLifecycle() {
        val lifecycle = severeInjuryLifecycle(playerHpBefore = 7)
        val transition = monsterTransition(
            playerHpBefore = 7,
            finalDamage = 7,
            playerHpAfter = 0,
            revivedHp = 40,
            lifecycleEvents = lifecycle,
        )

        assertEquals(
            listOf(
                BattleEffectEvent.MonsterAttackStarted::class,
                BattleEffectEvent.EntityHit::class,
                BattleEffectEvent.PlayerDefeated::class,
            ),
            transition.effectEvents.map { it::class },
        )
        assertCommonMonsterMetadata(transition.effectEvents, damage = 7)
        val defeated = transition.effectEvents.last() as BattleEffectEvent.PlayerDefeated
        assertTrue(defeated.isTerminal)
        assertEquals(lifecycle.first().eventId, defeated.sourceLifecycleEventId)
    }

    @Test
    fun monsterOverkillClampsAtZeroAndCreatesOnlyOneTerminalEvent() {
        val transition = monsterTransition(
            playerHpBefore = 2,
            finalDamage = 99,
            playerHpAfter = 0,
            revivedHp = 40,
            lifecycleEvents = severeInjuryLifecycle(playerHpBefore = 2),
        )

        assertEquals(1, transition.effectEvents.count(BattleEffectEvent::isTerminal))
        assertEquals(99, transition.effectEvents.single(BattleEffectEvent::isTerminal).damage)
        assertEquals(BattleEntityRef.Player, transition.effectEvents.single(BattleEffectEvent::isTerminal).target)
    }

    @Test
    fun duplicateMonsterTransitionProducesTheSameDeterministicEffectEvents() {
        val first = monsterTransition(playerHpBefore = 50, finalDamage = 11, playerHpAfter = 39)
        val duplicate = monsterTransition(playerHpBefore = 50, finalDamage = 11, playerHpAfter = 39)

        assertEquals(first.effectEvents, duplicate.effectEvents)
        assertEquals(
            setOf("combat:MONSTER_ATTACK:$TASK_ID:$OCCURRENCE_EPOCH_DAY"),
            first.effectEvents.map(BattleEffectEvent::eventId).toSet(),
        )
    }

    @Test
    fun transitionConstructionRejectsAttackAndSnapshotMismatch() {
        assertThrows(IllegalArgumentException::class.java) {
            playerTransition(
                targetHpBefore = 20,
                finalDamage = 7,
                targetHpAfter = 13,
                attackTargetMonsterId = OUTGOING_MONSTER_ID + 99,
            )
        }
    }

    @Test
    fun lethalMonsterTransitionRejectsMissingOrMisorderedSevereInjuryLifecycle() {
        assertThrows(IllegalArgumentException::class.java) {
            monsterTransition(
                playerHpBefore = 7,
                finalDamage = 7,
                playerHpAfter = 0,
                revivedHp = 40,
            )
        }
        val lifecycle = severeInjuryLifecycle(playerHpBefore = 7)
        assertThrows(IllegalArgumentException::class.java) {
            monsterTransition(
                playerHpBefore = 7,
                finalDamage = 7,
                playerHpAfter = 0,
                revivedHp = 40,
                lifecycleEvents = listOf(lifecycle[1], lifecycle[0], lifecycle[2]),
            )
        }
    }

    private fun assertCommonPlayerMetadata(
        events: List<BattleEffectEvent>,
        damage: Int,
        monsterId: Long,
    ) {
        assertEquals(1, events.map(BattleEffectEvent::eventId).distinct().size)
        assertTrue(events.all { it.attackEventKey == playerEventKey() })
        assertTrue(events.all { it.attacker == BattleEntityRef.Player })
        assertTrue(events.all { it.target == BattleEntityRef.Monster(monsterId) })
        assertTrue(events.all { it.damage == damage })
        assertTrue(events.all { it.monsterId == monsterId })
    }

    private fun assertCommonMonsterMetadata(events: List<BattleEffectEvent>, damage: Int) {
        assertEquals(1, events.map(BattleEffectEvent::eventId).distinct().size)
        assertTrue(events.all { it.attackEventKey == monsterEventKey() })
        assertTrue(events.all { it.attacker == BattleEntityRef.Monster(OUTGOING_MONSTER_ID) })
        assertTrue(events.all { it.target == BattleEntityRef.Player })
        assertTrue(events.all { it.damage == damage })
        assertTrue(events.all { it.monsterId == OUTGOING_MONSTER_ID })
    }

    private fun playerTransition(
        targetHpBefore: Int,
        finalDamage: Int,
        targetHpAfter: Int,
        attackTargetMonsterId: Long = OUTGOING_MONSTER_ID,
        incomingMonsterId: Long = OUTGOING_MONSTER_ID,
    ): CombatTransition.PlayerAttack {
        val before = combatSnapshot(
            monsterId = OUTGOING_MONSTER_ID,
            monsterHp = targetHpBefore,
            playerHp = 80,
            playerMaxHp = 100,
        )
        val after = combatSnapshot(
            monsterId = incomingMonsterId,
            monsterHp = if (incomingMonsterId == OUTGOING_MONSTER_ID) targetHpAfter else 75,
            playerHp = if (targetHpAfter == 0) 87 else 80,
            playerMaxHp = 100,
        )
        return CombatTransition.PlayerAttack(
            attack = PlayerAttackSnapshot(
                taskId = TASK_ID,
                occurrenceDateEpochDay = OCCURRENCE_EPOCH_DAY,
                targetMonsterInstanceId = attackTargetMonsterId,
                seed = 123L,
                roll = 5_000,
                wasCritical = false,
                rawDamage = finalDamage,
                targetDefense = 0,
                finalDamage = finalDamage,
                targetHpBefore = targetHpBefore,
                targetHpAfter = targetHpAfter,
                processedAt = NOW,
            ),
            before = before,
            after = after,
        )
    }

    private fun monsterTransition(
        playerHpBefore: Int,
        finalDamage: Int,
        playerHpAfter: Int,
        revivedHp: Int? = null,
        lifecycleEvents: List<CombatLifecycleEvent> = emptyList(),
    ): CombatTransition.MonsterAttack = CombatTransition.MonsterAttack(
        attack = MonsterAttackSnapshot(
            taskId = TASK_ID,
            occurrenceDateEpochDay = OCCURRENCE_EPOCH_DAY,
            trigger = MonsterAttackTrigger.MANUAL_FAILURE,
            sourceMonsterInstanceId = OUTGOING_MONSTER_ID,
            sourceMonsterLevel = 1,
            sourceRawDamage = 12,
            playerDefense = 8,
            playerMaxHp = 100,
            finalDamage = finalDamage,
            playerHpBefore = playerHpBefore,
            playerHpAfter = playerHpAfter,
            wasLethal = playerHpAfter == 0,
            revivedHp = revivedHp,
            processedAt = NOW,
        ),
        before = combatSnapshot(
            monsterId = OUTGOING_MONSTER_ID,
            monsterHp = 50,
            playerHp = playerHpBefore,
            playerMaxHp = 100,
        ),
        after = combatSnapshot(
            monsterId = OUTGOING_MONSTER_ID,
            monsterHp = 50,
            playerHp = revivedHp ?: playerHpAfter,
            playerMaxHp = if (revivedHp == null) 100 else 80,
        ),
        lifecycleEvents = lifecycleEvents,
    )

    private fun severeInjuryLifecycle(playerHpBefore: Int): List<CombatLifecycleEvent> {
        val key = monsterEventKey()
        return listOf(
            CombatLifecycleEvent.PlayerDefeated(
                eventId = "lifecycle:player-defeated",
                attackEventKey = key,
                effectRevision = 1L,
                playerHpBefore = playerHpBefore,
                playerMaxHpBeforeEffect = 100,
            ),
            CombatLifecycleEvent.StatusEffectApplied(
                eventId = "lifecycle:status-effect-applied",
                attackEventKey = key,
                effectType = StatusEffectType.SEVERE_INJURY,
                effectRevision = 1L,
                effectiveMaxHp = 80,
            ),
            CombatLifecycleEvent.PlayerEmergencyRecovered(
                eventId = "lifecycle:player-emergency-recovered",
                attackEventKey = key,
                effectRevision = 1L,
                recoveredHp = 40,
                effectiveMaxHp = 80,
            ),
        )
    }

    private fun combatSnapshot(
        monsterId: Long,
        monsterHp: Int,
        playerHp: Int,
        playerMaxHp: Int,
    ): CombatSnapshot = CombatSnapshot(
        progress = StageProgress(
            stageNumber = 1,
            stageLevel = 1,
            activeMonsterInstanceId = monsterId,
            lastReconciledAt = NOW,
            balanceVersion = 1,
        ),
        activeMonster = MonsterInstance(
            id = monsterId,
            definitionId = "monster_balanced_v1",
            grade = MonsterGrade.NORMAL,
            stageNumber = 1,
            encounterNumber = if (monsterId == OUTGOING_MONSTER_ID) 1 else 2,
            level = 1,
            currentHp = monsterHp,
            balanceVersion = 1,
        ),
        activeMonsterStats = MonsterStats(maxHp = 75, damage = 12, defense = 0),
        activeMonsterSpecies = MonsterSpecies.GOBLIN_SCOUT,
        playerCurrentHp = playerHp,
        playerMaxHp = playerMaxHp,
    )

    private fun playerEventKey() = CombatEventKey(
        kind = CombatEventKind.PLAYER_ATTACK,
        taskId = TASK_ID,
        occurrenceDateEpochDay = OCCURRENCE_EPOCH_DAY,
    )

    private fun monsterEventKey() = CombatEventKey(
        kind = CombatEventKind.MONSTER_ATTACK,
        taskId = TASK_ID,
        occurrenceDateEpochDay = OCCURRENCE_EPOCH_DAY,
    )

    private companion object {
        const val TASK_ID = 17L
        const val OCCURRENCE_EPOCH_DAY = 20_000L
        const val OUTGOING_MONSTER_ID = 101L
        const val INCOMING_MONSTER_ID = 102L
        val NOW: Instant = Instant.parse("2026-08-12T03:00:00Z")
    }
}
