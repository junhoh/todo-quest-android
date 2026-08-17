package com.todoquest.feature.battle

import com.todoquest.R
import com.todoquest.audio.BattleSfx
import com.todoquest.audio.BattleSfxPlayer
import com.todoquest.audio.NoOpBattleSfxPlayer
import com.todoquest.domain.model.BattleEffectEvent
import com.todoquest.domain.model.BattleEntityRef
import com.todoquest.domain.model.CombatEventKey
import com.todoquest.domain.model.CombatLifecycleEvent
import com.todoquest.domain.model.CombatSnapshot
import com.todoquest.domain.model.CombatTransition
import com.todoquest.domain.model.StatusEffectType
import com.todoquest.ui.character.CharacterRenderState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class BattleAnimationPhase {
    IDLE,
    PLAYER_ATTACKING,
    MONSTER_HIT,
    MONSTER_DYING,
    MONSTER_SPAWN_ALERT,
    MONSTER_SPAWNING,
    MONSTER_ATTACKING,
    PLAYER_HIT,
    PLAYER_DYING,
    PLAYER_DEFEATED,
    STATUS_EFFECT_APPLYING,
    STATUS_EFFECT_REFRESHING,
    PLAYER_EMERGENCY_RECOVERING,
    STATUS_EFFECT_REMOVING,
}

data class BattleAnimationTimeline(
    val advanceMillis: Long = 140L,
    val hitMillis: Long = 180L,
    val deathMillis: Long = 320L,
    val defeatedMillis: Long = 220L,
    val statusEffectMillis: Long = 240L,
    val monsterSpawnAlertMillis: Long = 300L,
    val spawnOrRecoveryMillis: Long = 280L,
) {
    init {
        require(advanceMillis > 0L) { "advanceMillis must be positive" }
        require(hitMillis > 0L) { "hitMillis must be positive" }
        require(deathMillis > 0L) { "deathMillis must be positive" }
        require(defeatedMillis > 0L) { "defeatedMillis must be positive" }
        require(statusEffectMillis > 0L) { "statusEffectMillis must be positive" }
        require(monsterSpawnAlertMillis > 0L) {
            "monsterSpawnAlertMillis must be positive"
        }
        require(spawnOrRecoveryMillis > 0L) { "spawnOrRecoveryMillis must be positive" }
    }
}

data class BattlePresentationState(
    val phase: BattleAnimationPhase = BattleAnimationPhase.IDLE,
    val sequenceId: Long? = null,
    val eventId: String? = null,
    val eventKey: CombatEventKey? = null,
    val attacker: BattleUnitType? = null,
    val target: BattleUnitType? = null,
    val damage: Int? = null,
    val isCritical: Boolean = false,
    val isLethal: Boolean = false,
    val rewardFeedback: BattleRewardFeedback? = null,
    val sceneOverride: BattleMapUiState.Content? = null,
    val queuedTransitionCount: Int = 0,
    val latestLifecycleOutcome: BattleLifecycleOutcome? = null,
) {
    val isInputLocked: Boolean
        get() = phase != BattleAnimationPhase.IDLE || queuedTransitionCount > 0

    init {
        require(queuedTransitionCount >= 0) { "queuedTransitionCount must not be negative" }
        require(damage == null || damage >= 0) { "damage must not be negative" }
        if (phase == BattleAnimationPhase.IDLE) {
            require(sequenceId == null) { "idle presentation must not have a sequence id" }
            require(eventId == null) { "idle presentation must not have an event id" }
            require(eventKey == null) { "idle presentation must not have an event key" }
            require(attacker == null && target == null) {
                "idle presentation must not have combatants"
            }
            require(damage == null) { "idle presentation must not have damage" }
            require(rewardFeedback == null) { "idle presentation must not have reward feedback" }
            require(sceneOverride == null) { "idle presentation must not override the scene" }
        } else {
            require(sequenceId != null && sequenceId > 0L) {
                "active presentation must have a positive sequence id"
            }
            require(!eventId.isNullOrBlank()) { "active presentation must have an event id" }
            require(sceneOverride != null) { "active presentation must override the scene" }
            if (phase == BattleAnimationPhase.STATUS_EFFECT_REMOVING) {
                require(eventKey == null) { "status removal must not require an attack key" }
                require(attacker == null && target == null && damage == null) {
                    "status removal must not invent combat metadata"
                }
            } else {
                require(eventKey != null) { "combat presentation must have an event key" }
                require(attacker != null && target != null && attacker != target) {
                    "combat presentation must have different combatants"
                }
                require(damage != null) { "combat presentation must have damage" }
            }
        }
    }
}

enum class BattleStatusEffectChange {
    APPLIED,
    REFRESHED,
}

sealed interface BattleLifecycleOutcome {
    data class SevereInjury(
        val playerDefeatedEventId: String,
        val statusEffectEventId: String,
        val emergencyRecoveredEventId: String,
        val statusEffectChange: BattleStatusEffectChange,
        val playerHpBefore: Int,
        val playerMaxHpBeforeEffect: Int,
        val recoveredHp: Int,
        val effectiveMaxHp: Int,
    ) : BattleLifecycleOutcome

    data class StatusEffectRemoved(
        val eventId: String,
        val effectType: StatusEffectType,
        val effectRevision: Long,
        val currentHp: Int,
        val effectiveMaxHp: Int,
    ) : BattleLifecycleOutcome
}

data class BattleRewardFeedback(
    val xpAward: Long,
    val goldAward: Long,
    val isVictory: Boolean,
) {
    init {
        require(xpAward > 0L) { "xpAward must be positive" }
        require(goldAward >= 0L) { "goldAward must not be negative" }
        require(isVictory || goldAward == 0L) { "non-victory feedback must not award gold" }
    }
}

class BattleAnimationController(
    scope: CoroutineScope,
    private val timeline: BattleAnimationTimeline = BattleAnimationTimeline(),
    private val battleSfxPlayer: BattleSfxPlayer = NoOpBattleSfxPlayer,
) {
    private val transitions = Channel<QueuedBattlePresentation>(capacity = Channel.BUFFERED)
    private val enqueueMutex = Mutex()
    private val consumedCombatEventKeys = BoundedInsertionOrderSet<CombatEventKey>(
        maxSize = MAX_CONSUMED_EVENT_KEYS,
    )
    private val consumedStatusRemovalEventIds = BoundedInsertionOrderSet<String>(
        maxSize = MAX_CONSUMED_EVENT_KEYS,
    )
    private var nextSequenceId = 0L
    private val _presentation = MutableStateFlow(BattlePresentationState())

    val presentation: StateFlow<BattlePresentationState> = _presentation.asStateFlow()

    private val actorJob = scope.launch {
        for (transition in transitions) {
            play(transition)
        }
    }

    init {
        actorJob.invokeOnCompletion { transitions.close() }
    }

    suspend fun enqueue(
        transition: CombatTransition,
        characterRenderState: CharacterRenderState,
    ): Boolean = enqueueMutex.withLock {
        if (transition.eventKey in consumedCombatEventKeys) return@withLock false
        val mapped = BattlePresentationMapper.mapTransition(transition, characterRenderState)

        val queued = QueuedBattlePresentation.Combat(
            sequenceId = ++nextSequenceId,
            mapped = mapped,
        )
        _presentation.update { current ->
            current.copy(queuedTransitionCount = current.queuedTransitionCount + 1)
        }

        try {
            transitions.send(queued)
            consumedCombatEventKeys.add(transition.eventKey)
            true
        } catch (failure: Throwable) {
            _presentation.update { current ->
                current.copy(
                    queuedTransitionCount = (current.queuedTransitionCount - 1).coerceAtLeast(0),
                )
            }
            throw failure
        }
    }

    suspend fun enqueueStatusEffectRemoval(
        event: CombatLifecycleEvent.StatusEffectRemoved,
        scene: BattleMapUiState.Content,
    ): Boolean = enqueueMutex.withLock {
        if (event.eventId in consumedStatusRemovalEventIds) return@withLock false
        val queued = QueuedBattlePresentation.StatusEffectRemoval(
            sequenceId = ++nextSequenceId,
            event = event,
            scene = scene,
        )
        _presentation.update { current ->
            current.copy(queuedTransitionCount = current.queuedTransitionCount + 1)
        }
        try {
            transitions.send(queued)
            consumedStatusRemovalEventIds.add(event.eventId)
            true
        } catch (failure: Throwable) {
            _presentation.update { current ->
                current.copy(
                    queuedTransitionCount = (current.queuedTransitionCount - 1).coerceAtLeast(0),
                )
            }
            throw failure
        }
    }

    private suspend fun play(queued: QueuedBattlePresentation) {
        when (queued) {
            is QueuedBattlePresentation.Combat -> when (queued.mapped.kind) {
                BattleTransitionKind.PLAYER_ATTACK -> playPlayerAttack(queued)
                BattleTransitionKind.MONSTER_ATTACK -> playMonsterAttack(queued)
            }
            is QueuedBattlePresentation.StatusEffectRemoval -> playStatusEffectRemoval(queued)
        }
    }

    private suspend fun playPlayerAttack(queued: QueuedBattlePresentation.Combat) {
        val transition = queued.mapped
        val effects = transition.requirePlayerAttackEffects()
        val scenes = transition.scenes
        playSfx(BattleSfx.PLAYER_ATTACK, effects.started)
        activate(
            queued = queued,
            phase = BattleAnimationPhase.PLAYER_ATTACKING,
            scene = scenes.before,
            eventId = effects.started.eventId,
        )
        delay(timeline.advanceMillis)
        playSfx(BattleSfx.MONSTER_HIT, effects.hit)
        updatePhase(
            BattleAnimationPhase.MONSTER_HIT,
            scenes.hit,
            effects.hit.eventId,
        )
        delay(timeline.hitMillis)

        if (transition.isLethal) {
            val defeated = requireNotNull(effects.defeated)
            playSfx(BattleSfx.MONSTER_DEFEATED, defeated)
            updatePhase(
                BattleAnimationPhase.MONSTER_DYING,
                requireNotNull(scenes.death),
                defeated.eventId,
            )
            delay(timeline.deathMillis)
            updatePhase(
                BattleAnimationPhase.MONSTER_SPAWN_ALERT,
                requireNotNull(scenes.spawnAlert),
                defeated.eventId,
            )
            delay(timeline.monsterSpawnAlertMillis)
            updatePhase(
                BattleAnimationPhase.MONSTER_SPAWNING,
                requireNotNull(scenes.recovery),
                defeated.eventId,
            )
            delay(timeline.spawnOrRecoveryMillis)
        }

        finish()
    }

    private suspend fun playMonsterAttack(queued: QueuedBattlePresentation.Combat) {
        val transition = queued.mapped
        val effects = transition.requireMonsterAttackEffects()
        val scenes = transition.scenes
        playSfx(BattleSfx.MONSTER_ATTACK, effects.started)
        activate(
            queued = queued,
            phase = BattleAnimationPhase.MONSTER_ATTACKING,
            scene = scenes.before,
            eventId = effects.started.eventId,
        )
        delay(timeline.advanceMillis)
        playSfx(BattleSfx.PLAYER_HIT, effects.hit)
        updatePhase(
            BattleAnimationPhase.PLAYER_HIT,
            scenes.hit,
            effects.hit.eventId,
        )
        delay(timeline.hitMillis)

        if (transition.isLethal) {
            val lifecycle = requireNotNull(transition.severeInjuryLifecycle)
            val defeated = requireNotNull(effects.defeated)
            require(defeated.sourceLifecycleEventId == lifecycle.playerDefeated.eventId) {
                "player defeat sound must use the verified severe-injury lifecycle source"
            }
            playSfx(BattleSfx.PLAYER_DEFEATED, defeated)
            updatePhase(
                BattleAnimationPhase.PLAYER_DYING,
                requireNotNull(scenes.death),
                defeated.eventId,
            )
            delay(timeline.deathMillis)
            updatePhase(
                BattleAnimationPhase.PLAYER_DEFEATED,
                requireNotNull(scenes.defeated),
                lifecycle.playerDefeated.eventId,
            )
            delay(timeline.defeatedMillis)
            updatePhase(
                if (lifecycle.change == BattleStatusEffectChange.APPLIED) {
                    BattleAnimationPhase.STATUS_EFFECT_APPLYING
                } else {
                    BattleAnimationPhase.STATUS_EFFECT_REFRESHING
                },
                requireNotNull(scenes.statusEffect),
                lifecycle.statusEffectEvent.eventId,
            )
            delay(timeline.statusEffectMillis)
            updatePhase(
                BattleAnimationPhase.PLAYER_EMERGENCY_RECOVERING,
                requireNotNull(scenes.recovery),
                lifecycle.emergencyRecovered.eventId,
                latestLifecycleOutcome = lifecycle.toOutcome(),
            )
            delay(timeline.spawnOrRecoveryMillis)
        }

        finish()
    }

    private suspend fun playStatusEffectRemoval(
        queued: QueuedBattlePresentation.StatusEffectRemoval,
    ) {
        val outcome = BattleLifecycleOutcome.StatusEffectRemoved(
            eventId = queued.event.eventId,
            effectType = queued.event.effectType,
            effectRevision = queued.event.effectRevision,
            currentHp = queued.scene.player.currentHp,
            effectiveMaxHp = queued.scene.player.maxHp,
        )
        _presentation.update { current ->
            BattlePresentationState(
                phase = BattleAnimationPhase.STATUS_EFFECT_REMOVING,
                sequenceId = queued.sequenceId,
                eventId = queued.event.eventId,
                sceneOverride = queued.scene,
                queuedTransitionCount = (current.queuedTransitionCount - 1).coerceAtLeast(0),
                latestLifecycleOutcome = outcome,
            )
        }
        delay(timeline.statusEffectMillis)
        finish()
    }

    private fun activate(
        queued: QueuedBattlePresentation.Combat,
        phase: BattleAnimationPhase,
        scene: BattleMapUiState.Content,
        eventId: String,
    ) {
        val transition = queued.mapped
        _presentation.update { current ->
            BattlePresentationState(
                phase = phase,
                sequenceId = queued.sequenceId,
                eventId = eventId,
                eventKey = transition.eventKey,
                attacker = transition.attacker,
                target = transition.target,
                damage = transition.damage,
                isCritical = transition.isCritical,
                isLethal = transition.isLethal,
                rewardFeedback = transition.rewardFeedback,
                sceneOverride = scene,
                queuedTransitionCount = (current.queuedTransitionCount - 1).coerceAtLeast(0),
                latestLifecycleOutcome = current.latestLifecycleOutcome,
            )
        }
    }

    private fun updatePhase(
        phase: BattleAnimationPhase,
        scene: BattleMapUiState.Content,
        eventId: String,
        latestLifecycleOutcome: BattleLifecycleOutcome? = null,
    ) {
        _presentation.update { current ->
            current.copy(
                phase = phase,
                eventId = eventId,
                sceneOverride = scene,
                latestLifecycleOutcome = latestLifecycleOutcome
                    ?: current.latestLifecycleOutcome,
            )
        }
    }

    private fun finish() {
        _presentation.update { current ->
            BattlePresentationState(
                queuedTransitionCount = current.queuedTransitionCount,
                latestLifecycleOutcome = current.latestLifecycleOutcome,
            )
        }
    }

    private fun playSfx(effect: BattleSfx, event: BattleEffectEvent) {
        try {
            battleSfxPlayer.play(effect = effect, eventId = event.eventId)
        } catch (_: Throwable) {
            // Audio is transient presentation and must never cancel the visual timeline.
        }
    }

    private companion object {
        private const val MAX_CONSUMED_EVENT_KEYS = 256
    }
}

object BattlePresentationMapper {
    fun mapSnapshot(
        snapshot: CombatSnapshot,
        characterRenderState: CharacterRenderState,
    ): BattleMapUiState.Content = BattleMapUiState.Content(
        player = BattleUnitUiModel(
            id = PLAYER_ID,
            type = BattleUnitType.PLAYER,
            sprite = BattleSpriteUiModel.LayeredCharacter(
                renderState = characterRenderState,
                frame = BattleMapDefaults.PLAYER_FRAME,
            ),
            position = BattleMapDefaults.PLAYER_POSITION,
            scale = 1f,
            groundOffset = 0f,
            currentHp = snapshot.playerCurrentHp,
            maxHp = snapshot.playerMaxHp,
            nameResId = R.string.battle_player_name,
            deathAnnouncementResId = R.string.battle_player_death_announcement,
        ),
        monsters = listOf(snapshot.toMonsterUiModel()),
        stageNumber = snapshot.progress.stageNumber,
    )

    internal fun mapTransition(
        transition: CombatTransition,
        characterRenderState: CharacterRenderState,
    ): MappedBattleTransition = when (transition) {
        is CombatTransition.PlayerAttack -> mapPlayerAttack(transition, characterRenderState)
        is CombatTransition.MonsterAttack -> mapMonsterAttack(transition, characterRenderState)
    }

    private fun mapPlayerAttack(
        transition: CombatTransition.PlayerAttack,
        characterRenderState: CharacterRenderState,
    ): MappedBattleTransition {
        val attack = transition.attack
        require(transition.before.activeMonster.id == attack.targetMonsterInstanceId) {
            "player attack target must match the outgoing monster"
        }
        val before = mapSnapshot(transition.before, characterRenderState)
        val after = mapSnapshot(transition.after, characterRenderState)
        val hit = before.withMonsterHp(
            monsterId = attack.targetMonsterInstanceId,
            currentHp = attack.targetHpAfter,
        )
        val isLethal = attack.targetHpAfter == 0

        if (isLethal) {
            require(transition.after.activeMonster.id != attack.targetMonsterInstanceId) {
                "lethal player attack must provide a new monster"
            }
            require(after.monsters.single().currentHp == after.monsters.single().maxHp) {
                "new monster must start at full HP"
            }
        } else {
            require(transition.after.activeMonster.id == attack.targetMonsterInstanceId) {
                "nonlethal player attack must keep the target monster"
            }
        }

        return MappedBattleTransition(
            eventKey = transition.eventKey,
            effectEvents = transition.effectEvents,
            kind = BattleTransitionKind.PLAYER_ATTACK,
            attacker = BattleUnitType.PLAYER,
            target = BattleUnitType.MONSTER,
            damage = attack.finalDamage,
            isCritical = attack.wasCritical,
            isLethal = isLethal,
            rewardFeedback = if (
                attack.combatRewardVersion > 0 && attack.totalXpAward > 0L
            ) {
                BattleRewardFeedback(
                    xpAward = attack.totalXpAward,
                    goldAward = attack.killGoldAward,
                    isVictory = isLethal,
                )
            } else {
                null
            },
            scenes = BattleTransitionScenes(
                before = before,
                hit = if (isLethal) hit else after,
                death = hit.takeIf { isLethal },
                spawnAlert = after.takeIf { isLethal },
                recovery = after.takeIf { isLethal },
            ),
            severeInjuryLifecycle = null,
        )
    }

    private fun mapMonsterAttack(
        transition: CombatTransition.MonsterAttack,
        characterRenderState: CharacterRenderState,
    ): MappedBattleTransition {
        val attack = transition.attack
        require(transition.before.activeMonster.id == attack.sourceMonsterInstanceId) {
            "monster attack source must match the visible monster"
        }
        require(transition.after.activeMonster.id == attack.sourceMonsterInstanceId) {
            "monster attack must keep its source monster"
        }
        val before = mapSnapshot(transition.before, characterRenderState)
        val after = mapSnapshot(transition.after, characterRenderState)
        val hit = if (attack.wasLethal) before.withPlayerHp(0) else after
        val severeInjuryLifecycle = if (attack.wasLethal) {
            transition.toSevereInjuryLifecycle()
        } else {
            require(transition.lifecycleEvents.isEmpty()) {
                "nonlethal monster attack must not have defeat lifecycle events"
            }
            null
        }

        if (attack.wasLethal) {
            val lifecycle = requireNotNull(severeInjuryLifecycle)
            require(attack.playerHpAfter == 0) {
                "lethal monster attack must persist zero damage-result HP"
            }
            require(attack.revivedHp == transition.after.playerCurrentHp) {
                "lethal monster attack must preserve the persisted emergency recovery HP"
            }
            require(lifecycle.playerDefeated.playerMaxHpBeforeEffect == before.player.maxHp) {
                "defeat scene must use the previous effective max HP"
            }
            require(lifecycle.emergencyRecovered.recoveredHp == after.player.currentHp) {
                "recovery lifecycle HP must match the persisted combat snapshot"
            }
            require(lifecycle.emergencyRecovered.effectiveMaxHp == after.player.maxHp) {
                "recovery lifecycle max HP must match the injured combat snapshot"
            }
        }

        val statusEffectScene = severeInjuryLifecycle?.let { lifecycle ->
            hit.withPlayerHpAndMaxHp(
                currentHp = 0,
                maxHp = lifecycle.emergencyRecovered.effectiveMaxHp,
            )
        }
        val recoveryScene = severeInjuryLifecycle?.let { lifecycle ->
            requireNotNull(statusEffectScene).withPlayerHpAndMaxHp(
                currentHp = lifecycle.emergencyRecovered.recoveredHp,
                maxHp = lifecycle.emergencyRecovered.effectiveMaxHp,
            )
        }

        return MappedBattleTransition(
            eventKey = transition.eventKey,
            effectEvents = transition.effectEvents,
            kind = BattleTransitionKind.MONSTER_ATTACK,
            attacker = BattleUnitType.MONSTER,
            target = BattleUnitType.PLAYER,
            damage = attack.finalDamage,
            isCritical = false,
            isLethal = attack.wasLethal,
            rewardFeedback = null,
            scenes = BattleTransitionScenes(
                before = before,
                hit = hit,
                death = hit.takeIf { attack.wasLethal },
                defeated = hit.takeIf { attack.wasLethal },
                statusEffect = statusEffectScene,
                recovery = recoveryScene,
            ),
            severeInjuryLifecycle = severeInjuryLifecycle,
        )
    }

    private fun CombatTransition.MonsterAttack.toSevereInjuryLifecycle():
        MappedSevereInjuryLifecycle {
        require(lifecycleEvents.size == 3) {
            "lethal monster attack must contain exactly three severe-injury lifecycle events"
        }
        val defeated = lifecycleEvents[0] as? CombatLifecycleEvent.PlayerDefeated
            ?: error("first lethal lifecycle event must be PlayerDefeated")
        val statusEffectEvent = lifecycleEvents[1]
        val recovered = lifecycleEvents[2] as? CombatLifecycleEvent.PlayerEmergencyRecovered
            ?: error("third lethal lifecycle event must be PlayerEmergencyRecovered")
        val change = when (statusEffectEvent) {
            is CombatLifecycleEvent.StatusEffectApplied -> BattleStatusEffectChange.APPLIED
            is CombatLifecycleEvent.StatusEffectRefreshed -> BattleStatusEffectChange.REFRESHED
            else -> error("second lethal lifecycle event must apply or refresh a status effect")
        }
        val revision = defeated.effectRevision
        require(
            lifecycleEvents.all { lifecycle ->
                lifecycle.attackEventKey == eventKey && lifecycle.effectRevision == revision
            },
        ) { "lethal lifecycle events must share their attack key and effect revision" }
        require(lifecycleEvents.map(CombatLifecycleEvent::eventId).distinct().size == 3) {
            "lethal lifecycle event ids must be unique"
        }
        val effectType = when (statusEffectEvent) {
            is CombatLifecycleEvent.StatusEffectApplied -> statusEffectEvent.effectType
            is CombatLifecycleEvent.StatusEffectRefreshed -> statusEffectEvent.effectType
        }
        require(effectType == StatusEffectType.SEVERE_INJURY) {
            "lethal v1 lifecycle must use severe injury"
        }
        return MappedSevereInjuryLifecycle(
            playerDefeated = defeated,
            statusEffectEvent = statusEffectEvent,
            emergencyRecovered = recovered,
            change = change,
        )
    }

    private fun CombatSnapshot.toMonsterUiModel(): BattleUnitUiModel {
        val visual = BattleMonsterVisualCatalog.forSpecies(activeMonsterSpecies)
        return BattleUnitUiModel(
            id = monsterUiId(activeMonster.id),
            type = BattleUnitType.MONSTER,
            sprite = BattleSpriteUiModel.Resource(
                spriteResId = visual.spriteResId,
                frame = BattleMapDefaults.MONSTER_FRAME,
            ),
            position = BattleMonsterSlots.forCount(1).single(),
            scale = 1f,
            groundOffset = 0f,
            currentHp = activeMonster.currentHp,
            maxHp = activeMonsterStats.maxHp,
            nameResId = visual.nameResId,
            deathAnnouncementResId = visual.deathAnnouncementResId,
        )
    }

    private fun BattleMapUiState.Content.withMonsterHp(
        monsterId: Long,
        currentHp: Int,
    ): BattleMapUiState.Content {
        val uiId = monsterUiId(monsterId)
        require(monsters.any { it.id == uiId }) { "monster scene does not contain the event target" }
        return copy(
            monsters = monsters.map { monster ->
                if (monster.id == uiId) {
                    monster.copy(currentHp = currentHp.coerceIn(0, monster.maxHp))
                } else {
                    monster
                }
            },
        )
    }

    private fun BattleMapUiState.Content.withPlayerHp(currentHp: Int) = copy(
        player = player.copy(currentHp = currentHp.coerceIn(0, player.maxHp)),
    )

    private fun BattleMapUiState.Content.withPlayerHpAndMaxHp(
        currentHp: Int,
        maxHp: Int,
    ): BattleMapUiState.Content {
        require(maxHp > 0) { "player max HP must be positive" }
        return copy(
            player = player.copy(
                currentHp = currentHp.coerceIn(0, maxHp),
                maxHp = maxHp,
            ),
        )
    }

    private fun monsterUiId(monsterId: Long) = "monster-$monsterId"

    private const val PLAYER_ID = "player"
}

internal enum class BattleTransitionKind {
    PLAYER_ATTACK,
    MONSTER_ATTACK,
}

internal data class BattleTransitionScenes(
    val before: BattleMapUiState.Content,
    val hit: BattleMapUiState.Content,
    val death: BattleMapUiState.Content? = null,
    val defeated: BattleMapUiState.Content? = null,
    val statusEffect: BattleMapUiState.Content? = null,
    val spawnAlert: BattleMapUiState.Content? = null,
    val recovery: BattleMapUiState.Content? = null,
)

internal data class MappedBattleTransition(
    val eventKey: CombatEventKey,
    val effectEvents: List<BattleEffectEvent>,
    val kind: BattleTransitionKind,
    val attacker: BattleUnitType,
    val target: BattleUnitType,
    val damage: Int,
    val isCritical: Boolean,
    val isLethal: Boolean,
    val rewardFeedback: BattleRewardFeedback?,
    val scenes: BattleTransitionScenes,
    val severeInjuryLifecycle: MappedSevereInjuryLifecycle?,
) {
    init {
        require(effectEvents.isNotEmpty()) { "combat presentation must contain effect events" }
        require(effectEvents.all { it.attackEventKey == eventKey }) {
            "effect event attack keys must match the presentation event key"
        }
        require(effectEvents.map(BattleEffectEvent::eventId).distinct().size == 1) {
            "one combat transition must share one stable effect event id"
        }
    }
}

private data class PlayerAttackEffects(
    val started: BattleEffectEvent.PlayerAttackStarted,
    val hit: BattleEffectEvent.EntityHit,
    val defeated: BattleEffectEvent.MonsterDefeated?,
)

private data class MonsterAttackEffects(
    val started: BattleEffectEvent.MonsterAttackStarted,
    val hit: BattleEffectEvent.EntityHit,
    val defeated: BattleEffectEvent.PlayerDefeated?,
)

private fun MappedBattleTransition.requirePlayerAttackEffects(): PlayerAttackEffects {
    require(kind == BattleTransitionKind.PLAYER_ATTACK) {
        "player attack timeline requires a player attack transition"
    }
    require(effectEvents.size == if (isLethal) 3 else 2) {
        "player attack effect count must match its lethal result"
    }
    val started = effectEvents[0] as? BattleEffectEvent.PlayerAttackStarted
        ?: error("player attack timeline must start with PlayerAttackStarted")
    val hit = effectEvents[1] as? BattleEffectEvent.EntityHit
        ?: error("player attack timeline must continue with EntityHit")
    val defeated = effectEvents.getOrNull(2) as? BattleEffectEvent.MonsterDefeated
    require(started.attacker == BattleEntityRef.Player) {
        "player attack start must use the player attacker"
    }
    require(started.target is BattleEntityRef.Monster) {
        "player attack start must target a monster"
    }
    require(hit.attacker == started.attacker && hit.target == started.target) {
        "player attack hit must preserve its typed combatants"
    }
    require(defeated == null || defeated.attacker == started.attacker) {
        "monster defeat must preserve the player attacker"
    }
    require(defeated == null || defeated.target == started.target) {
        "monster defeat must preserve the outgoing monster target"
    }
    return PlayerAttackEffects(started = started, hit = hit, defeated = defeated)
}

private fun MappedBattleTransition.requireMonsterAttackEffects(): MonsterAttackEffects {
    require(kind == BattleTransitionKind.MONSTER_ATTACK) {
        "monster attack timeline requires a monster attack transition"
    }
    require(effectEvents.size == if (isLethal) 3 else 2) {
        "monster attack effect count must match its lethal result"
    }
    val started = effectEvents[0] as? BattleEffectEvent.MonsterAttackStarted
        ?: error("monster attack timeline must start with MonsterAttackStarted")
    val hit = effectEvents[1] as? BattleEffectEvent.EntityHit
        ?: error("monster attack timeline must continue with EntityHit")
    val defeated = effectEvents.getOrNull(2) as? BattleEffectEvent.PlayerDefeated
    require(started.attacker is BattleEntityRef.Monster) {
        "monster attack start must use a monster attacker"
    }
    require(started.target == BattleEntityRef.Player) {
        "monster attack start must target the player"
    }
    require(hit.attacker == started.attacker && hit.target == started.target) {
        "monster attack hit must preserve its typed combatants"
    }
    require(defeated == null || defeated.attacker == started.attacker) {
        "player defeat must preserve the monster attacker"
    }
    require(defeated == null || defeated.target == started.target) {
        "player defeat must preserve the player target"
    }
    return MonsterAttackEffects(started = started, hit = hit, defeated = defeated)
}

private class BoundedInsertionOrderSet<T>(
    private val maxSize: Int,
) {
    private val values = LinkedHashSet<T>()

    init {
        require(maxSize > 0) { "maxSize must be positive" }
    }

    operator fun contains(value: T): Boolean = value in values

    fun add(value: T) {
        if (!values.add(value)) return
        if (values.size <= maxSize) return
        val oldest = values.iterator()
        if (oldest.hasNext()) {
            oldest.next()
            oldest.remove()
        }
    }
}

internal data class MappedSevereInjuryLifecycle(
    val playerDefeated: CombatLifecycleEvent.PlayerDefeated,
    val statusEffectEvent: CombatLifecycleEvent,
    val emergencyRecovered: CombatLifecycleEvent.PlayerEmergencyRecovered,
    val change: BattleStatusEffectChange,
) {
    init {
        require(
            statusEffectEvent is CombatLifecycleEvent.StatusEffectApplied ||
                statusEffectEvent is CombatLifecycleEvent.StatusEffectRefreshed,
        ) { "status effect lifecycle must apply or refresh" }
    }

    fun toOutcome(): BattleLifecycleOutcome.SevereInjury = BattleLifecycleOutcome.SevereInjury(
        playerDefeatedEventId = playerDefeated.eventId,
        statusEffectEventId = statusEffectEvent.eventId,
        emergencyRecoveredEventId = emergencyRecovered.eventId,
        statusEffectChange = change,
        playerHpBefore = playerDefeated.playerHpBefore,
        playerMaxHpBeforeEffect = playerDefeated.playerMaxHpBeforeEffect,
        recoveredHp = emergencyRecovered.recoveredHp,
        effectiveMaxHp = emergencyRecovered.effectiveMaxHp,
    )
}

private sealed interface QueuedBattlePresentation {
    val sequenceId: Long

    data class Combat(
        override val sequenceId: Long,
        val mapped: MappedBattleTransition,
    ) : QueuedBattlePresentation

    data class StatusEffectRemoval(
        override val sequenceId: Long,
        val event: CombatLifecycleEvent.StatusEffectRemoved,
        val scene: BattleMapUiState.Content,
    ) : QueuedBattlePresentation
}
