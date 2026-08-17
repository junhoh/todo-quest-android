package com.todoquest.domain.model

import java.time.Instant

data class StageProgress(
    val stageNumber: Int,
    val stageLevel: Int,
    val activeMonsterInstanceId: Long,
    val lastReconciledAt: Instant,
    val balanceVersion: Int,
)

data class CombatSnapshot(
    val progress: StageProgress,
    val activeMonster: MonsterInstance,
    val activeMonsterStats: MonsterStats,
    val activeMonsterSpecies: MonsterSpecies,
    val playerCurrentHp: Int,
    val playerMaxHp: Int,
)

@Suppress("LongParameterList")
data class PlayerAttackSnapshot(
    val taskId: Long,
    val occurrenceDateEpochDay: Long,
    val targetMonsterInstanceId: Long,
    val seed: Long,
    val roll: Int,
    val wasCritical: Boolean,
    val rawDamage: Int,
    val targetDefense: Int,
    val finalDamage: Int,
    val targetHpBefore: Int,
    val targetHpAfter: Int,
    val processedAt: Instant,
    val combatRewardVersion: Int = 0,
    val hitXpAward: Long = 0L,
    val killBonusXpAward: Long = 0L,
    val killGoldAward: Long = 0L,
    val rewardGradeMultiplierBp: Int = 0,
    val rewardGoldGainBonusBp: Int = 0,
    val sourceTaskDifficulty: TaskDifficulty? = null,
    val taskDifficultyBalanceVersion: Int = 0,
) {
    val totalXpAward: Long
        get() = Math.addExact(hitXpAward, killBonusXpAward)
}

enum class MonsterAttackTrigger {
    MANUAL_FAILURE,
    MISSED_DEADLINE,
}

@Suppress("LongParameterList")
data class MonsterAttackSnapshot(
    val taskId: Long,
    val occurrenceDateEpochDay: Long,
    val trigger: MonsterAttackTrigger,
    val sourceMonsterInstanceId: Long,
    val sourceMonsterLevel: Int,
    val sourceRawDamage: Int,
    val playerDefense: Int,
    val playerMaxHp: Int,
    val finalDamage: Int,
    val playerHpBefore: Int,
    val playerHpAfter: Int,
    val wasLethal: Boolean,
    val revivedHp: Int?,
    val processedAt: Instant,
)

sealed interface PlayerAttackResult {
    data class Applied(
        val attack: PlayerAttackSnapshot,
        val wasAlreadyApplied: Boolean,
    ) : PlayerAttackResult

    data object NotFound : PlayerAttackResult
}

sealed interface MonsterAttackResult {
    data class Applied(
        val attack: MonsterAttackSnapshot,
        val wasAlreadyApplied: Boolean,
    ) : MonsterAttackResult

    data object NotFound : MonsterAttackResult
}

enum class CombatEventKind {
    PLAYER_ATTACK,
    MONSTER_ATTACK,
}

data class CombatEventKey(
    val kind: CombatEventKind,
    val taskId: Long,
    val occurrenceDateEpochDay: Long,
) {
    fun battleEffectEventId(): String =
        "combat:${kind.stableId}:$taskId:$occurrenceDateEpochDay"
}

private val CombatEventKind.stableId: String
    get() = when (this) {
        CombatEventKind.PLAYER_ATTACK -> "PLAYER_ATTACK"
        CombatEventKind.MONSTER_ATTACK -> "MONSTER_ATTACK"
    }

enum class BattleEntityKind {
    PLAYER,
    MONSTER,
}

sealed interface BattleEntityRef {
    val kind: BattleEntityKind

    data object Player : BattleEntityRef {
        override val kind: BattleEntityKind = BattleEntityKind.PLAYER
    }

    data class Monster(val monsterId: Long) : BattleEntityRef {
        override val kind: BattleEntityKind = BattleEntityKind.MONSTER

        init {
            require(monsterId > 0L) { "monsterId must be positive" }
        }
    }
}

sealed interface BattleEffectEvent {
    val eventId: String
    val attackEventKey: CombatEventKey
    val attacker: BattleEntityRef
    val target: BattleEntityRef
    val damage: Int
    val monsterId: Long
    val isTerminal: Boolean

    data class PlayerAttackStarted(
        override val eventId: String,
        override val attackEventKey: CombatEventKey,
        override val attacker: BattleEntityRef,
        override val target: BattleEntityRef,
        override val damage: Int,
        override val monsterId: Long,
    ) : BattleEffectEvent {
        override val isTerminal: Boolean = false
    }

    data class MonsterAttackStarted(
        override val eventId: String,
        override val attackEventKey: CombatEventKey,
        override val attacker: BattleEntityRef,
        override val target: BattleEntityRef,
        override val damage: Int,
        override val monsterId: Long,
    ) : BattleEffectEvent {
        override val isTerminal: Boolean = false
    }

    data class EntityHit(
        override val eventId: String,
        override val attackEventKey: CombatEventKey,
        override val attacker: BattleEntityRef,
        override val target: BattleEntityRef,
        override val damage: Int,
        override val monsterId: Long,
    ) : BattleEffectEvent {
        override val isTerminal: Boolean = false
    }

    data class MonsterDefeated(
        override val eventId: String,
        override val attackEventKey: CombatEventKey,
        override val attacker: BattleEntityRef,
        override val target: BattleEntityRef,
        override val damage: Int,
        override val monsterId: Long,
    ) : BattleEffectEvent {
        override val isTerminal: Boolean = true
    }

    data class PlayerDefeated(
        override val eventId: String,
        override val attackEventKey: CombatEventKey,
        override val attacker: BattleEntityRef,
        override val target: BattleEntityRef,
        override val damage: Int,
        override val monsterId: Long,
        val sourceLifecycleEventId: String,
    ) : BattleEffectEvent {
        override val isTerminal: Boolean = true
    }
}

sealed interface CombatLifecycleEvent {
    val eventId: String
    val attackEventKey: CombatEventKey?
    val effectRevision: Long

    data class PlayerDefeated(
        override val eventId: String,
        override val attackEventKey: CombatEventKey,
        override val effectRevision: Long,
        val playerHpBefore: Int,
        val playerMaxHpBeforeEffect: Int,
    ) : CombatLifecycleEvent

    data class StatusEffectApplied(
        override val eventId: String,
        override val attackEventKey: CombatEventKey,
        val effectType: StatusEffectType,
        override val effectRevision: Long,
        val effectiveMaxHp: Int,
    ) : CombatLifecycleEvent

    data class PlayerEmergencyRecovered(
        override val eventId: String,
        override val attackEventKey: CombatEventKey,
        override val effectRevision: Long,
        val recoveredHp: Int,
        val effectiveMaxHp: Int,
    ) : CombatLifecycleEvent

    data class StatusEffectRefreshed(
        override val eventId: String,
        override val attackEventKey: CombatEventKey,
        val effectType: StatusEffectType,
        override val effectRevision: Long,
        val effectiveMaxHp: Int,
    ) : CombatLifecycleEvent

    data class StatusEffectRemoved(
        override val eventId: String,
        override val attackEventKey: CombatEventKey? = null,
        val effectType: StatusEffectType,
        override val effectRevision: Long,
        val removedAtEpochMillis: Long,
    ) : CombatLifecycleEvent
}

sealed interface CombatTransition {
    val eventKey: CombatEventKey
    val before: CombatSnapshot
    val after: CombatSnapshot
    val effectEvents: List<BattleEffectEvent>

    data class PlayerAttack(
        val attack: PlayerAttackSnapshot,
        override val before: CombatSnapshot,
        override val after: CombatSnapshot,
    ) : CombatTransition {
        override val eventKey: CombatEventKey = CombatEventKey(
            kind = CombatEventKind.PLAYER_ATTACK,
            taskId = attack.taskId,
            occurrenceDateEpochDay = attack.occurrenceDateEpochDay,
        )
        override val effectEvents: List<BattleEffectEvent> =
            BattleEffectEventFactory.forPlayerAttack(
                eventKey = eventKey,
                attack = attack,
                before = before,
                after = after,
            )
    }

    data class MonsterAttack(
        val attack: MonsterAttackSnapshot,
        override val before: CombatSnapshot,
        override val after: CombatSnapshot,
        val lifecycleEvents: List<CombatLifecycleEvent> = emptyList(),
    ) : CombatTransition {
        override val eventKey: CombatEventKey = CombatEventKey(
            kind = CombatEventKind.MONSTER_ATTACK,
            taskId = attack.taskId,
            occurrenceDateEpochDay = attack.occurrenceDateEpochDay,
        )
        override val effectEvents: List<BattleEffectEvent> =
            BattleEffectEventFactory.forMonsterAttack(
                eventKey = eventKey,
                attack = attack,
                before = before,
                after = after,
                lifecycleEvents = lifecycleEvents,
            )
    }
}

private object BattleEffectEventFactory {
    fun forPlayerAttack(
        eventKey: CombatEventKey,
        attack: PlayerAttackSnapshot,
        before: CombatSnapshot,
        after: CombatSnapshot,
    ): List<BattleEffectEvent> {
        require(eventKey.kind == CombatEventKind.PLAYER_ATTACK) {
            "Player attack effect requires a player attack event key"
        }
        require(before.activeMonster.id == attack.targetMonsterInstanceId) {
            "Player attack target must match the outgoing active monster"
        }
        require(before.activeMonster.currentHp == attack.targetHpBefore) {
            "Player attack target HP before must match the before snapshot"
        }
        require(before.activeMonsterStats.defense == attack.targetDefense) {
            "Player attack target defense must match the before snapshot"
        }
        require(attack.finalDamage >= 0) { "Player attack damage must not be negative" }
        require(
            attack.targetHpAfter == clampedHpAfterDamage(
                hpBefore = attack.targetHpBefore,
                damage = attack.finalDamage,
            ),
        ) { "Player attack target HP after does not match its damage snapshot" }

        val monsterId = attack.targetMonsterInstanceId
        val attacker = BattleEntityRef.Player
        val target = BattleEntityRef.Monster(monsterId)
        val eventId = eventKey.battleEffectEventId()
        val common = listOf(
            BattleEffectEvent.PlayerAttackStarted(
                eventId = eventId,
                attackEventKey = eventKey,
                attacker = attacker,
                target = target,
                damage = attack.finalDamage,
                monsterId = monsterId,
            ),
            BattleEffectEvent.EntityHit(
                eventId = eventId,
                attackEventKey = eventKey,
                attacker = attacker,
                target = target,
                damage = attack.finalDamage,
                monsterId = monsterId,
            ),
        )
        if (attack.targetHpAfter > 0) {
            require(after.activeMonster.id == monsterId) {
                "Surviving player attack target must remain the active monster"
            }
            require(after.activeMonster.currentHp == attack.targetHpAfter) {
                "Player attack target HP after must match the after snapshot"
            }
            return common
        }

        require(after.activeMonster.id != monsterId) {
            "Defeated outgoing monster must not be replaced in effect metadata by itself"
        }
        return common + BattleEffectEvent.MonsterDefeated(
            eventId = eventId,
            attackEventKey = eventKey,
            attacker = attacker,
            target = target,
            damage = attack.finalDamage,
            monsterId = monsterId,
        )
    }

    fun forMonsterAttack(
        eventKey: CombatEventKey,
        attack: MonsterAttackSnapshot,
        before: CombatSnapshot,
        after: CombatSnapshot,
        lifecycleEvents: List<CombatLifecycleEvent>,
    ): List<BattleEffectEvent> {
        require(eventKey.kind == CombatEventKind.MONSTER_ATTACK) {
            "Monster attack effect requires a monster attack event key"
        }
        require(before.activeMonster.id == attack.sourceMonsterInstanceId) {
            "Monster attack source must match the active monster"
        }
        require(after.activeMonster.id == attack.sourceMonsterInstanceId) {
            "Monster attack source must remain the active monster"
        }
        require(before.activeMonster.level == attack.sourceMonsterLevel) {
            "Monster attack source level must match the before snapshot"
        }
        require(before.playerCurrentHp == attack.playerHpBefore) {
            "Monster attack player HP before must match the before snapshot"
        }
        require(before.playerMaxHp == attack.playerMaxHp) {
            "Monster attack player max HP must match the before snapshot"
        }
        require(attack.finalDamage >= 0) { "Monster attack damage must not be negative" }
        require(
            attack.playerHpAfter == clampedHpAfterDamage(
                hpBefore = attack.playerHpBefore,
                damage = attack.finalDamage,
            ),
        ) { "Monster attack player HP after does not match its damage snapshot" }
        require(attack.wasLethal == (attack.playerHpAfter == 0)) {
            "Monster attack lethal flag must match the zero-HP snapshot"
        }

        val monsterId = attack.sourceMonsterInstanceId
        val attacker = BattleEntityRef.Monster(monsterId)
        val target = BattleEntityRef.Player
        val eventId = eventKey.battleEffectEventId()
        val common = listOf(
            BattleEffectEvent.MonsterAttackStarted(
                eventId = eventId,
                attackEventKey = eventKey,
                attacker = attacker,
                target = target,
                damage = attack.finalDamage,
                monsterId = monsterId,
            ),
            BattleEffectEvent.EntityHit(
                eventId = eventId,
                attackEventKey = eventKey,
                attacker = attacker,
                target = target,
                damage = attack.finalDamage,
                monsterId = monsterId,
            ),
        )
        if (!attack.wasLethal) {
            require(attack.revivedHp == null) {
                "Nonlethal monster attack must not contain emergency recovery HP"
            }
            require(lifecycleEvents.isEmpty()) {
                "Nonlethal monster attack must not contain defeat lifecycle events"
            }
            require(after.playerCurrentHp == attack.playerHpAfter) {
                "Monster attack player HP after must match the after snapshot"
            }
            return common
        }

        val playerDefeated = validateSevereInjuryLifecycle(
            eventKey = eventKey,
            attack = attack,
            before = before,
            after = after,
            lifecycleEvents = lifecycleEvents,
        )
        return common + BattleEffectEvent.PlayerDefeated(
            eventId = eventId,
            attackEventKey = eventKey,
            attacker = attacker,
            target = target,
            damage = attack.finalDamage,
            monsterId = monsterId,
            sourceLifecycleEventId = playerDefeated.eventId,
        )
    }

    private fun validateSevereInjuryLifecycle(
        eventKey: CombatEventKey,
        attack: MonsterAttackSnapshot,
        before: CombatSnapshot,
        after: CombatSnapshot,
        lifecycleEvents: List<CombatLifecycleEvent>,
    ): CombatLifecycleEvent.PlayerDefeated {
        require(lifecycleEvents.size == 3) {
            "Lethal monster attack requires exactly three severe-injury lifecycle events"
        }
        val playerDefeated = lifecycleEvents[0] as? CombatLifecycleEvent.PlayerDefeated
            ?: throw IllegalArgumentException(
                "Lethal monster attack lifecycle must start with PlayerDefeated",
            )
        val statusEffect = lifecycleEvents[1]
        require(
            statusEffect is CombatLifecycleEvent.StatusEffectApplied ||
                statusEffect is CombatLifecycleEvent.StatusEffectRefreshed,
        ) { "PlayerDefeated must be followed by severe-injury application or refresh" }
        val recovered = lifecycleEvents[2] as? CombatLifecycleEvent.PlayerEmergencyRecovered
            ?: throw IllegalArgumentException(
                "Severe-injury lifecycle must end with PlayerEmergencyRecovered",
            )
        val statusEffectType = when (statusEffect) {
            is CombatLifecycleEvent.StatusEffectApplied -> statusEffect.effectType
            is CombatLifecycleEvent.StatusEffectRefreshed -> statusEffect.effectType
        }
        val effectiveMaxHp = when (statusEffect) {
            is CombatLifecycleEvent.StatusEffectApplied -> statusEffect.effectiveMaxHp
            is CombatLifecycleEvent.StatusEffectRefreshed -> statusEffect.effectiveMaxHp
        }
        require(statusEffectType == StatusEffectType.SEVERE_INJURY) {
            "Lethal monster attack lifecycle must apply or refresh severe injury"
        }
        require(lifecycleEvents.all { it.attackEventKey == eventKey }) {
            "Severe-injury lifecycle attack keys must match the monster attack"
        }
        require(lifecycleEvents.all { it.effectRevision == playerDefeated.effectRevision }) {
            "Severe-injury lifecycle revisions must match"
        }
        require(playerDefeated.playerHpBefore == attack.playerHpBefore) {
            "PlayerDefeated HP must match the monster attack snapshot"
        }
        require(playerDefeated.playerMaxHpBeforeEffect == before.playerMaxHp) {
            "PlayerDefeated max HP must match the before snapshot"
        }
        require(recovered.effectiveMaxHp == effectiveMaxHp) {
            "Emergency recovery max HP must match the severe-injury effect"
        }
        require(attack.revivedHp == recovered.recoveredHp) {
            "Monster attack recovery HP must match the lifecycle"
        }
        require(after.playerCurrentHp == recovered.recoveredHp) {
            "Emergency recovery HP must match the after snapshot"
        }
        return playerDefeated
    }

    private fun clampedHpAfterDamage(hpBefore: Int, damage: Int): Int =
        (hpBefore.toLong() - damage.toLong()).coerceAtLeast(0L).toInt()
}

data class CombatReconciliationResult(
    val playerAttacksProcessed: Int,
    val monsterAttacksApplied: Int = 0,
    val monsterAttacksSkipped: Int = 0,
)
