package com.todoquest.feature.battle

import com.todoquest.R
import com.todoquest.audio.BattleSfx
import com.todoquest.audio.BattleSfxPlayer
import com.todoquest.domain.model.CharacterLoadoutCatalog
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
import com.todoquest.ui.character.CharacterRenderState
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BattleAnimationControllerTest {
    private val dispatcher = StandardTestDispatcher()
    private val renderState = CharacterRenderState(
        appearance = CharacterLoadoutCatalog.defaultAppearance,
        equippedItems = CharacterLoadoutCatalog.defaultEquippedItems,
    )

    @Test
    fun lethalPlayerSfxUsesOrderedEffectsAtAttackHitAndDeathPhaseBoundaries() =
        runTest(dispatcher) {
            val player = FakeBattleSfxPlayer()
            val controller = BattleAnimationController(
                scope = backgroundScope,
                battleSfxPlayer = player,
            )
            val transition = playerTransition(lethal = true)
            val eventId = transition.eventKey.battleEffectEventId()

            assertTrue(controller.enqueue(transition, renderState))
            runCurrent()

            assertEquals(BattleAnimationPhase.PLAYER_ATTACKING, controller.presentation.value.phase)
            assertEquals(
                listOf(SfxRequest(BattleSfx.PLAYER_ATTACK, eventId)),
                player.requests,
            )

            advanceExactly(140)
            assertEquals(BattleAnimationPhase.MONSTER_HIT, controller.presentation.value.phase)
            assertEquals(
                listOf(
                    SfxRequest(BattleSfx.PLAYER_ATTACK, eventId),
                    SfxRequest(BattleSfx.MONSTER_HIT, eventId),
                ),
                player.requests,
            )

            advanceExactly(180)
            assertEquals(BattleAnimationPhase.MONSTER_DYING, controller.presentation.value.phase)
            assertEquals(
                listOf(
                    SfxRequest(BattleSfx.PLAYER_ATTACK, eventId),
                    SfxRequest(BattleSfx.MONSTER_HIT, eventId),
                    SfxRequest(BattleSfx.MONSTER_DEFEATED, eventId),
                ),
                player.requests,
            )

            advanceExactly(320)
            advanceExactly(300)
            advanceExactly(280)
            assertEquals(BattleAnimationPhase.IDLE, controller.presentation.value.phase)
            assertEquals(3, player.requests.size)
        }

    @Test
    fun lethalMonsterSfxUsesOrderedEffectsBeforeSevereInjuryLifecyclePhases() =
        runTest(dispatcher) {
            val player = FakeBattleSfxPlayer()
            val controller = BattleAnimationController(
                scope = backgroundScope,
                battleSfxPlayer = player,
            )
            val transition = monsterTransition(lethal = true, refreshed = true)
            val eventId = transition.eventKey.battleEffectEventId()

            assertTrue(controller.enqueue(transition, renderState))
            runCurrent()
            assertEquals(BattleAnimationPhase.MONSTER_ATTACKING, controller.presentation.value.phase)
            assertEquals(
                listOf(SfxRequest(BattleSfx.MONSTER_ATTACK, eventId)),
                player.requests,
            )

            advanceExactly(140)
            assertEquals(BattleAnimationPhase.PLAYER_HIT, controller.presentation.value.phase)
            assertEquals(BattleSfx.PLAYER_HIT, player.requests.last().effect)

            advanceExactly(180)
            assertEquals(BattleAnimationPhase.PLAYER_DYING, controller.presentation.value.phase)
            assertEquals(
                listOf(
                    SfxRequest(BattleSfx.MONSTER_ATTACK, eventId),
                    SfxRequest(BattleSfx.PLAYER_HIT, eventId),
                    SfxRequest(BattleSfx.PLAYER_DEFEATED, eventId),
                ),
                player.requests,
            )

            advanceExactly(320)
            advanceExactly(220)
            assertEquals(
                BattleAnimationPhase.STATUS_EFFECT_REFRESHING,
                controller.presentation.value.phase,
            )
            advanceExactly(240)
            assertEquals(
                BattleAnimationPhase.PLAYER_EMERGENCY_RECOVERING,
                controller.presentation.value.phase,
            )
            advanceExactly(280)
            assertEquals(3, player.requests.size)
        }

    @Test
    fun nonlethalAttacksRequestOnlyAttackAndHitSfx() = runTest(dispatcher) {
        val player = FakeBattleSfxPlayer()
        val controller = BattleAnimationController(
            scope = backgroundScope,
            battleSfxPlayer = player,
        )
        val playerAttack = playerTransition(lethal = false)
        val monsterAttack = monsterTransition(lethal = false)

        assertTrue(controller.enqueue(playerAttack, renderState))
        assertTrue(controller.enqueue(monsterAttack, renderState))
        runCurrent()
        advanceExactly(140)
        advanceExactly(180)
        advanceExactly(140)
        advanceExactly(180)

        assertEquals(
            listOf(
                SfxRequest(BattleSfx.PLAYER_ATTACK, playerAttack.eventKey.battleEffectEventId()),
                SfxRequest(BattleSfx.MONSTER_HIT, playerAttack.eventKey.battleEffectEventId()),
                SfxRequest(BattleSfx.MONSTER_ATTACK, monsterAttack.eventKey.battleEffectEventId()),
                SfxRequest(BattleSfx.PLAYER_HIT, monsterAttack.eventKey.battleEffectEventId()),
            ),
            player.requests,
        )
        assertFalse(player.requests.any { it.effect == BattleSfx.MONSTER_DEFEATED })
        assertFalse(player.requests.any { it.effect == BattleSfx.PLAYER_DEFEATED })
    }

    @Test
    fun duplicateCombatKeyIsRejectedButDistinctRapidEventsRemainQueued() = runTest(dispatcher) {
        val player = FakeBattleSfxPlayer()
        val controller = BattleAnimationController(
            scope = backgroundScope,
            battleSfxPlayer = player,
        )
        val first = playerTransition(lethal = false, taskId = 301L)
        val duplicate = playerTransition(lethal = false, taskId = 301L)
        val second = playerTransition(lethal = false, taskId = 302L)

        assertTrue(controller.enqueue(first, renderState))
        assertFalse(controller.enqueue(duplicate, renderState))
        assertTrue(controller.enqueue(second, renderState))
        runCurrent()
        advanceExactly(140)
        advanceExactly(180)
        advanceExactly(140)
        advanceExactly(180)

        assertEquals(
            listOf(
                first.eventKey.battleEffectEventId(),
                first.eventKey.battleEffectEventId(),
                second.eventKey.battleEffectEventId(),
                second.eventKey.battleEffectEventId(),
            ),
            player.requests.map(SfxRequest::eventId),
        )
    }

    @Test
    fun combatAndStatusRemovalDedupCachesUseIndependentIdentities() = runTest(dispatcher) {
        val controller = BattleAnimationController(backgroundScope)
        val transition = playerTransition(lethal = false)
        val scene = BattlePresentationMapper.mapSnapshot(transition.before, renderState)
        val removal = CombatLifecycleEvent.StatusEffectRemoved(
            eventId = transition.eventKey.battleEffectEventId(),
            effectType = StatusEffectType.SEVERE_INJURY,
            effectRevision = 1L,
            removedAtEpochMillis = NOW.toEpochMilli(),
        )

        assertTrue(controller.enqueue(transition, renderState))
        assertTrue(controller.enqueueStatusEffectRemoval(removal, scene))
        assertFalse(controller.enqueue(transition, renderState))
        assertFalse(controller.enqueueStatusEffectRemoval(removal, scene))
    }

    @Test
    fun combatDedupCacheEvictsOnlyTheOldestKeyAfterTwoHundredFiftySixEntries() =
        runTest(dispatcher) {
            val controller = BattleAnimationController(
                scope = backgroundScope,
                timeline = BattleAnimationTimeline(
                    advanceMillis = 1L,
                    hitMillis = 1L,
                    deathMillis = 1L,
                    defeatedMillis = 1L,
                    statusEffectMillis = 1L,
                    monsterSpawnAlertMillis = 1L,
                    spawnOrRecoveryMillis = 1L,
                ),
            )
            val transitions = List(257) { index ->
                playerTransition(lethal = false, taskId = 10_000L + index)
            }

            transitions.forEach { transition ->
                assertTrue(controller.enqueue(transition, renderState))
                runCurrent()
                advanceExactly(1)
                advanceExactly(1)
            }

            assertTrue(controller.enqueue(transitions.first(), renderState))
            assertFalse(controller.enqueue(transitions.last(), renderState))
        }

    @Test
    fun throwingSfxPlayerCannotCancelTheVisualTimeline() = runTest(dispatcher) {
        val player = FakeBattleSfxPlayer(throwOnPlay = true)
        val controller = BattleAnimationController(
            scope = backgroundScope,
            battleSfxPlayer = player,
        )
        val transition = playerTransition(lethal = false)

        assertTrue(controller.enqueue(transition, renderState))
        runCurrent()
        assertEquals(BattleAnimationPhase.PLAYER_ATTACKING, controller.presentation.value.phase)
        advanceExactly(140)
        assertEquals(BattleAnimationPhase.MONSTER_HIT, controller.presentation.value.phase)
        advanceExactly(180)

        assertEquals(BattleAnimationPhase.IDLE, controller.presentation.value.phase)
        assertEquals(2, player.requests.size)
    }

    @Test
    fun nonlethalPlayerAttackUsesExactSerialTimelineAndMetadata() = runTest(dispatcher) {
        val controller = BattleAnimationController(backgroundScope)
        val transition = playerTransition(lethal = false)

        assertTrue(controller.enqueue(transition, renderState))
        assertEquals(BattleAnimationPhase.IDLE, controller.presentation.value.phase)
        assertEquals(1, controller.presentation.value.queuedTransitionCount)
        assertTrue(controller.presentation.value.isInputLocked)

        runCurrent()

        assertPresentation(
            controller = controller,
            phase = BattleAnimationPhase.PLAYER_ATTACKING,
            sequenceId = 1L,
            eventKey = transition.eventKey,
            attacker = BattleUnitType.PLAYER,
            target = BattleUnitType.MONSTER,
            damage = 17,
            isCritical = true,
            isLethal = false,
            monsterHp = 37,
        )
        assertMonsterVisual(
            controller.presentation.value.sceneOverride,
            spriteResId = R.drawable.todo_quest_skeleton_soldier_front_idle,
            nameResId = R.string.battle_monster_skeleton_soldier_name,
            deathAnnouncementResId =
                R.string.battle_monster_skeleton_soldier_death_announcement,
        )
        assertPhaseFor(controller, BattleAnimationPhase.PLAYER_ATTACKING, 140)
        assertPresentation(
            controller = controller,
            phase = BattleAnimationPhase.MONSTER_HIT,
            sequenceId = 1L,
            eventKey = transition.eventKey,
            attacker = BattleUnitType.PLAYER,
            target = BattleUnitType.MONSTER,
            damage = 17,
            isCritical = true,
            isLethal = false,
            monsterHp = 20,
        )
        assertMonsterVisual(
            controller.presentation.value.sceneOverride,
            spriteResId = R.drawable.todo_quest_skeleton_soldier_front_idle,
            nameResId = R.string.battle_monster_skeleton_soldier_name,
            deathAnnouncementResId =
                R.string.battle_monster_skeleton_soldier_death_announcement,
        )
        assertPhaseFor(controller, BattleAnimationPhase.MONSTER_HIT, 180)

        val idle = controller.presentation.value
        assertEquals(BattleAnimationPhase.IDLE, idle.phase)
        assertEquals(0, idle.queuedTransitionCount)
        assertFalse(idle.isInputLocked)
        assertNull(idle.sequenceId)
        assertNull(idle.eventKey)
        assertNull(idle.sceneOverride)
    }

    @Test
    fun currentPlayerAttackRewardIsPresentedOnlyDuringItsSequence() = runTest(dispatcher) {
        val controller = BattleAnimationController(backgroundScope)
        val transition = playerTransition(lethal = false, withReward = true)

        assertTrue(controller.enqueue(transition, renderState))
        runCurrent()

        assertEquals(
            BattleRewardFeedback(xpAward = 1L, goldAward = 0L, isVictory = false),
            controller.presentation.value.rewardFeedback,
        )

        advanceExactly(140)
        assertEquals(1L, controller.presentation.value.rewardFeedback?.xpAward)
        advanceExactly(180)
        assertNull(controller.presentation.value.rewardFeedback)
    }

    @Test
    fun lethalPlayerAttackShowsOutgoingDeathAndMapsIncomingAlertAndSpawn() =
        runTest(dispatcher) {
            val controller = BattleAnimationController(backgroundScope)
            val transition = playerTransition(
                lethal = true,
                beforeSpecies = MonsterSpecies.GOBLIN_SCOUT,
                afterSpecies = MonsterSpecies.SKELETON_SOLDIER,
            )

            assertTrue(controller.enqueue(transition, renderState))
            runCurrent()
            assertEquals(BattleAnimationPhase.PLAYER_ATTACKING, controller.presentation.value.phase)

            advanceExactly(140)
            assertScene(
                controller,
                phase = BattleAnimationPhase.MONSTER_HIT,
                monsterIds = listOf("monster-42"),
                monsterHp = 0,
            )
            assertMonsterVisual(
                controller.presentation.value.sceneOverride,
                spriteResId = R.drawable.todo_quest_goblin_scout_front_idle,
                nameResId = R.string.battle_monster_goblin_scout_name,
                deathAnnouncementResId = R.string.battle_monster_death_announcement,
            )
            advanceExactly(180)
            assertScene(
                controller,
                phase = BattleAnimationPhase.MONSTER_DYING,
                monsterIds = listOf("monster-42"),
                monsterHp = 0,
            )
            assertMonsterVisual(
                controller.presentation.value.sceneOverride,
                spriteResId = R.drawable.todo_quest_goblin_scout_front_idle,
                nameResId = R.string.battle_monster_goblin_scout_name,
                deathAnnouncementResId = R.string.battle_monster_death_announcement,
            )
            advanceExactly(320)
            assertScene(
                controller,
                phase = BattleAnimationPhase.MONSTER_SPAWN_ALERT,
                monsterIds = listOf("monster-43"),
                monsterHp = 55,
            )
            assertMonsterVisual(
                controller.presentation.value.sceneOverride,
                spriteResId = R.drawable.todo_quest_skeleton_soldier_front_idle,
                nameResId = R.string.battle_monster_skeleton_soldier_name,
                deathAnnouncementResId =
                    R.string.battle_monster_skeleton_soldier_death_announcement,
            )
            assertTrue(controller.presentation.value.isInputLocked)
            advanceExactly(300)
            assertScene(
                controller,
                phase = BattleAnimationPhase.MONSTER_SPAWNING,
                monsterIds = listOf("monster-43"),
                monsterHp = 55,
            )
            assertMonsterVisual(
                controller.presentation.value.sceneOverride,
                spriteResId = R.drawable.todo_quest_skeleton_soldier_front_idle,
                nameResId = R.string.battle_monster_skeleton_soldier_name,
                deathAnnouncementResId =
                    R.string.battle_monster_skeleton_soldier_death_announcement,
            )
            assertEquals(8, controller.presentation.value.sceneOverride?.stageNumber)
            advanceExactly(280)

            assertEquals(BattleAnimationPhase.IDLE, controller.presentation.value.phase)
            assertFalse(controller.presentation.value.isInputLocked)
        }

    @Test
    fun lethalPlayerAttackMapsSkeletonDeathAndGoblinSpawnIndependently() {
        val mapped = BattlePresentationMapper.mapTransition(
            transition = playerTransition(
                lethal = true,
                beforeSpecies = MonsterSpecies.SKELETON_SOLDIER,
                afterSpecies = MonsterSpecies.GOBLIN_SCOUT,
            ),
            characterRenderState = renderState,
        )

        assertMonsterVisual(
            mapped.scenes.death,
            spriteResId = R.drawable.todo_quest_skeleton_soldier_front_idle,
            nameResId = R.string.battle_monster_skeleton_soldier_name,
            deathAnnouncementResId =
                R.string.battle_monster_skeleton_soldier_death_announcement,
        )
        assertMonsterVisual(
            mapped.scenes.spawnAlert,
            spriteResId = R.drawable.todo_quest_goblin_scout_front_idle,
            nameResId = R.string.battle_monster_goblin_scout_name,
            deathAnnouncementResId = R.string.battle_monster_death_announcement,
        )
        assertMonsterVisual(
            mapped.scenes.recovery,
            spriteResId = R.drawable.todo_quest_goblin_scout_front_idle,
            nameResId = R.string.battle_monster_goblin_scout_name,
            deathAnnouncementResId = R.string.battle_monster_death_announcement,
        )
    }

    @Test
    fun lethalPlayerAttackKeepsOutgoingAndIncomingVisualsAcrossSpeciesTransitions() {
        MonsterSpecies.entries.forEach { beforeSpecies ->
            MonsterSpecies.entries.forEach { afterSpecies ->
                val mapped = BattlePresentationMapper.mapTransition(
                    transition = playerTransition(
                        lethal = true,
                        beforeSpecies = beforeSpecies,
                        afterSpecies = afterSpecies,
                    ),
                    characterRenderState = renderState,
                )

                assertMonsterVisual(mapped.scenes.death, VISUALS_BY_SPECIES.getValue(beforeSpecies))
                assertMonsterVisual(
                    mapped.scenes.spawnAlert,
                    VISUALS_BY_SPECIES.getValue(afterSpecies),
                )
                assertMonsterVisual(
                    mapped.scenes.recovery,
                    VISUALS_BY_SPECIES.getValue(afterSpecies),
                )
            }
        }
    }

    @Test
    fun nonlethalPlayerAndMonsterAttacksKeepEachSpeciesVisualAcrossTheirScenes() {
        MonsterSpecies.entries.forEach { species ->
            val expectedVisual = VISUALS_BY_SPECIES.getValue(species)
            val playerAttack = BattlePresentationMapper.mapTransition(
                transition = playerTransition(lethal = false, beforeSpecies = species),
                characterRenderState = renderState,
            )
            val monsterAttack = BattlePresentationMapper.mapTransition(
                transition = monsterTransition(lethal = true, species = species),
                characterRenderState = renderState,
            )

            assertMonsterVisual(playerAttack.scenes.before, expectedVisual)
            assertMonsterVisual(playerAttack.scenes.hit, expectedVisual)
            assertMonsterVisual(monsterAttack.scenes.before, expectedVisual)
            assertMonsterVisual(monsterAttack.scenes.hit, expectedVisual)
            assertMonsterVisual(monsterAttack.scenes.death, expectedVisual)
            assertMonsterVisual(monsterAttack.scenes.recovery, expectedVisual)
        }
    }

    @Test
    fun nonlethalPlayerAttackKeepsTreeSpiritVisualInBeforeAndHitScenes() {
        val mapped = BattlePresentationMapper.mapTransition(
            transition = playerTransition(
                lethal = false,
                beforeSpecies = MonsterSpecies.CORRUPTED_TREE_SPIRIT,
            ),
            characterRenderState = renderState,
        )

        assertMonsterVisual(mapped.scenes.before, TREE_SPIRIT_VISUAL)
        assertMonsterVisual(mapped.scenes.hit, TREE_SPIRIT_VISUAL)
        assertNull(mapped.scenes.death)
        assertNull(mapped.scenes.spawnAlert)
        assertNull(mapped.scenes.recovery)
    }

    @Test
    fun monsterAttackKeepsTreeSpiritVisualAcrossItsScenes() {
        val mapped = BattlePresentationMapper.mapTransition(
            transition = monsterTransition(
                lethal = true,
                species = MonsterSpecies.CORRUPTED_TREE_SPIRIT,
            ),
            characterRenderState = renderState,
        )

        assertMonsterVisual(mapped.scenes.before, TREE_SPIRIT_VISUAL)
        assertMonsterVisual(mapped.scenes.hit, TREE_SPIRIT_VISUAL)
        assertMonsterVisual(mapped.scenes.death, TREE_SPIRIT_VISUAL)
        assertMonsterVisual(mapped.scenes.recovery, TREE_SPIRIT_VISUAL)
    }

    @Test
    fun nonlethalPlayerAttackKeepsHarpyVisualInBeforeAndHitScenes() {
        val mapped = BattlePresentationMapper.mapTransition(
            transition = playerTransition(
                lethal = false,
                beforeSpecies = MonsterSpecies.HARPY,
            ),
            characterRenderState = renderState,
        )

        assertMonsterVisual(mapped.scenes.before, HARPY_VISUAL)
        assertMonsterVisual(mapped.scenes.hit, HARPY_VISUAL)
        assertNull(mapped.scenes.death)
        assertNull(mapped.scenes.spawnAlert)
        assertNull(mapped.scenes.recovery)
    }

    @Test
    fun monsterAttackKeepsHarpyVisualAcrossItsScenes() {
        val mapped = BattlePresentationMapper.mapTransition(
            transition = monsterTransition(
                lethal = true,
                species = MonsterSpecies.HARPY,
            ),
            characterRenderState = renderState,
        )

        assertMonsterVisual(mapped.scenes.before, HARPY_VISUAL)
        assertMonsterVisual(mapped.scenes.hit, HARPY_VISUAL)
        assertMonsterVisual(mapped.scenes.death, HARPY_VISUAL)
        assertMonsterVisual(mapped.scenes.recovery, HARPY_VISUAL)
    }

    @Test
    fun lethalMonsterAttackConsumesTheEntireSevereInjuryLifecycleInOrder() = runTest(dispatcher) {
        val controller = BattleAnimationController(backgroundScope)
        val transition = monsterTransition(lethal = true)

        assertTrue(controller.enqueue(transition, renderState))
        runCurrent()
        assertPresentation(
            controller = controller,
            phase = BattleAnimationPhase.MONSTER_ATTACKING,
            sequenceId = 1L,
            eventKey = transition.eventKey,
            attacker = BattleUnitType.MONSTER,
            target = BattleUnitType.PLAYER,
            damage = 14,
            isCritical = false,
            isLethal = true,
            playerHp = 10,
        )
        assertMonsterVisual(
            controller.presentation.value.sceneOverride,
            spriteResId = R.drawable.todo_quest_skeleton_soldier_front_idle,
            nameResId = R.string.battle_monster_skeleton_soldier_name,
            deathAnnouncementResId =
                R.string.battle_monster_skeleton_soldier_death_announcement,
        )

        advanceExactly(140)
        assertScene(
            controller,
            phase = BattleAnimationPhase.PLAYER_HIT,
            monsterIds = listOf("monster-42"),
            playerHp = 0,
            playerMaxHp = 80,
        )
        advanceExactly(180)
        assertScene(
            controller,
            phase = BattleAnimationPhase.PLAYER_DYING,
            monsterIds = listOf("monster-42"),
            playerHp = 0,
            playerMaxHp = 80,
        )
        advanceExactly(320)
        assertScene(
            controller,
            phase = BattleAnimationPhase.PLAYER_DEFEATED,
            monsterIds = listOf("monster-42"),
            playerHp = 0,
            playerMaxHp = 80,
        )
        advanceExactly(220)
        assertScene(
            controller,
            phase = BattleAnimationPhase.STATUS_EFFECT_APPLYING,
            monsterIds = listOf("monster-42"),
            playerHp = 0,
            playerMaxHp = 64,
        )
        advanceExactly(240)
        assertScene(
            controller,
            phase = BattleAnimationPhase.PLAYER_EMERGENCY_RECOVERING,
            monsterIds = listOf("monster-42"),
            playerHp = 32,
            playerMaxHp = 64,
        )
        assertMonsterVisual(
            controller.presentation.value.sceneOverride,
            spriteResId = R.drawable.todo_quest_skeleton_soldier_front_idle,
            nameResId = R.string.battle_monster_skeleton_soldier_name,
            deathAnnouncementResId =
                R.string.battle_monster_skeleton_soldier_death_announcement,
        )
        advanceExactly(280)

        val idle = controller.presentation.value
        assertEquals(BattleAnimationPhase.IDLE, idle.phase)
        assertFalse(idle.isInputLocked)
        assertEquals(
            BattleLifecycleOutcome.SevereInjury(
                playerDefeatedEventId = "severe-1:player-defeated",
                statusEffectEventId = "severe-1:status-effect-applied",
                emergencyRecoveredEventId = "severe-1:player-emergency-recovered",
                statusEffectChange = BattleStatusEffectChange.APPLIED,
                playerHpBefore = 10,
                playerMaxHpBeforeEffect = 80,
                recoveredHp = 32,
                effectiveMaxHp = 64,
            ),
            idle.latestLifecycleOutcome,
        )
    }

    @Test
    fun refreshedSevereInjuryUsesRefreshingPhaseAndDoesNotDropLifecycleEvents() =
        runTest(dispatcher) {
            val controller = BattleAnimationController(backgroundScope)
            val transition = monsterTransition(lethal = true, refreshed = true)

            assertTrue(controller.enqueue(transition, renderState))
            runCurrent()
            advanceExactly(140)
            advanceExactly(180)
            advanceExactly(320)
            advanceExactly(220)

            assertScene(
                controller = controller,
                phase = BattleAnimationPhase.STATUS_EFFECT_REFRESHING,
                monsterIds = listOf("monster-42"),
                playerHp = 0,
                playerMaxHp = 64,
            )

            advanceExactly(240)
            assertEquals(
                BattleAnimationPhase.PLAYER_EMERGENCY_RECOVERING,
                controller.presentation.value.phase,
            )
            assertFalse(controller.enqueue(transition, renderState))
        }

    @Test
    fun statusEffectRemovalIsConsumedOnceAndKeepsCurrentHpUnchanged() = runTest(dispatcher) {
        val player = FakeBattleSfxPlayer()
        val controller = BattleAnimationController(
            scope = backgroundScope,
            battleSfxPlayer = player,
        )
        val scene = BattlePresentationMapper.mapSnapshot(
            snapshot = snapshot(
                monsterId = 42L,
                monsterHp = 37,
                playerHp = 32,
                playerMaxHp = 80,
            ),
            characterRenderState = renderState,
        )
        val event = CombatLifecycleEvent.StatusEffectRemoved(
            eventId = "status-effect:removed:SEVERE_INJURY:1",
            effectType = StatusEffectType.SEVERE_INJURY,
            effectRevision = 1L,
            removedAtEpochMillis = NOW.toEpochMilli(),
        )

        assertTrue(controller.enqueueStatusEffectRemoval(event, scene))
        assertFalse(controller.enqueueStatusEffectRemoval(event, scene))
        runCurrent()

        assertScene(
            controller = controller,
            phase = BattleAnimationPhase.STATUS_EFFECT_REMOVING,
            monsterIds = listOf("monster-42"),
            playerHp = 32,
            playerMaxHp = 80,
        )
        advanceExactly(240)

        assertEquals(BattleAnimationPhase.IDLE, controller.presentation.value.phase)
        assertEquals(
            BattleLifecycleOutcome.StatusEffectRemoved(
                eventId = event.eventId,
                effectType = StatusEffectType.SEVERE_INJURY,
                effectRevision = 1L,
                currentHp = 32,
                effectiveMaxHp = 80,
            ),
            controller.presentation.value.latestLifecycleOutcome,
        )
        assertTrue(player.requests.isEmpty())
    }

    @Test
    fun queuedTransitionsRunOneAtATimeAndDuplicateKeysStayConsumedForControllerLifetime() =
        runTest(dispatcher) {
            val controller = BattleAnimationController(backgroundScope)
            val first = playerTransition(lethal = false)
            val duplicate = playerTransition(lethal = false)
            val second = monsterTransition(lethal = false)

            assertTrue(controller.enqueue(first, renderState))
            assertFalse(controller.enqueue(duplicate, renderState))
            assertTrue(controller.enqueue(second, renderState))
            assertEquals(2, controller.presentation.value.queuedTransitionCount)
            assertTrue(controller.presentation.value.isInputLocked)

            runCurrent()
            assertEquals(1L, controller.presentation.value.sequenceId)
            assertEquals(BattleAnimationPhase.PLAYER_ATTACKING, controller.presentation.value.phase)
            assertEquals(1, controller.presentation.value.queuedTransitionCount)

            advanceExactly(140)
            advanceExactly(180)
            assertEquals(2L, controller.presentation.value.sequenceId)
            assertEquals(BattleAnimationPhase.MONSTER_ATTACKING, controller.presentation.value.phase)
            assertEquals(0, controller.presentation.value.queuedTransitionCount)

            advanceExactly(140)
            advanceExactly(180)
            assertEquals(BattleAnimationPhase.IDLE, controller.presentation.value.phase)
            assertFalse(controller.presentation.value.isInputLocked)

            assertFalse(controller.enqueue(first, renderState))
            assertEquals(0, controller.presentation.value.queuedTransitionCount)
        }

    @Test
    fun presentationCollectorResubscriptionOnlyRendersCurrentSequence() = runTest(dispatcher) {
        val player = FakeBattleSfxPlayer()
        val controller = BattleAnimationController(
            scope = backgroundScope,
            battleSfxPlayer = player,
        )
        val transition = playerTransition(lethal = false)

        assertTrue(controller.enqueue(transition, renderState))
        runCurrent()

        val firstCollectorState = controller.presentation.first()
        assertEquals(BattleAnimationPhase.PLAYER_ATTACKING, firstCollectorState.phase)
        assertEquals(1L, firstCollectorState.sequenceId)

        val resubscribedState = controller.presentation.first()
        assertEquals(firstCollectorState, resubscribedState)
        assertFalse(controller.enqueue(transition, renderState))
        assertEquals(1, player.requests.size)

        advanceExactly(140)
        assertEquals(2, player.requests.size)
        advanceExactly(180)
        assertEquals(BattleAnimationPhase.IDLE, controller.presentation.value.phase)
        assertFalse(controller.presentation.value.isInputLocked)
        assertEquals(2, player.requests.size)
    }

    private suspend fun kotlinx.coroutines.test.TestScope.assertPhaseFor(
        controller: BattleAnimationController,
        phase: BattleAnimationPhase,
        durationMillis: Long,
    ) {
        advanceTimeBy(durationMillis - 1)
        runCurrent()
        assertEquals(phase, controller.presentation.value.phase)
        advanceExactly(1)
    }

    private suspend fun kotlinx.coroutines.test.TestScope.advanceExactly(durationMillis: Long) {
        advanceTimeBy(durationMillis)
        runCurrent()
    }

    @Suppress("LongParameterList")
    private fun assertPresentation(
        controller: BattleAnimationController,
        phase: BattleAnimationPhase,
        sequenceId: Long,
        eventKey: CombatEventKey,
        attacker: BattleUnitType,
        target: BattleUnitType,
        damage: Int,
        isCritical: Boolean,
        isLethal: Boolean,
        playerHp: Int? = null,
        monsterHp: Int? = null,
    ) {
        val state = controller.presentation.value
        assertEquals(phase, state.phase)
        assertEquals(sequenceId, state.sequenceId)
        assertEquals(eventKey, state.eventKey)
        assertEquals(attacker, state.attacker)
        assertEquals(target, state.target)
        assertEquals(damage, state.damage)
        assertEquals(isCritical, state.isCritical)
        assertEquals(isLethal, state.isLethal)
        playerHp?.let { assertEquals(it, state.sceneOverride?.player?.currentHp) }
        monsterHp?.let { assertEquals(it, state.sceneOverride?.monsters?.single()?.currentHp) }
        assertTrue(state.isInputLocked)
    }

    private fun assertScene(
        controller: BattleAnimationController,
        phase: BattleAnimationPhase,
        monsterIds: List<String>,
        playerHp: Int? = null,
        playerMaxHp: Int? = null,
        monsterHp: Int? = null,
    ) {
        val state = controller.presentation.value
        assertEquals(phase, state.phase)
        assertEquals(monsterIds, state.sceneOverride?.monsters?.map { it.id })
        playerHp?.let { assertEquals(it, state.sceneOverride?.player?.currentHp) }
        playerMaxHp?.let { assertEquals(it, state.sceneOverride?.player?.maxHp) }
        monsterHp?.let { assertEquals(it, state.sceneOverride?.monsters?.single()?.currentHp) }
    }

    private fun assertMonsterVisual(
        scene: BattleMapUiState.Content?,
        spriteResId: Int,
        nameResId: Int,
        deathAnnouncementResId: Int,
    ) {
        val monster = requireNotNull(scene).monsters.single()
        assertEquals(
            spriteResId,
            (monster.sprite as BattleSpriteUiModel.Resource).spriteResId,
        )
        assertEquals(BattleMapDefaults.MONSTER_FRAME, monster.sprite.frame)
        assertEquals(nameResId, monster.nameResId)
        assertEquals(deathAnnouncementResId, monster.deathAnnouncementResId)
    }

    private fun assertMonsterVisual(
        scene: BattleMapUiState.Content?,
        visual: ExpectedMonsterVisual,
    ) {
        assertMonsterVisual(
            scene = scene,
            spriteResId = visual.spriteResId,
            nameResId = visual.nameResId,
            deathAnnouncementResId = visual.deathAnnouncementResId,
        )
    }

    private fun playerTransition(
        lethal: Boolean,
        withReward: Boolean = false,
        beforeSpecies: MonsterSpecies = MonsterSpecies.SKELETON_SOLDIER,
        afterSpecies: MonsterSpecies = beforeSpecies,
        taskId: Long = 100L,
        occurrenceDateEpochDay: Long = 20_000L,
    ): CombatTransition.PlayerAttack {
        val before = snapshot(
            monsterId = 42L,
            monsterHp = if (lethal) 10 else 37,
            species = beforeSpecies,
        )
        val after = if (lethal) {
            snapshot(
                monsterId = 43L,
                monsterHp = 55,
                stageNumber = 8,
                species = afterSpecies,
            )
        } else {
            snapshot(monsterId = 42L, monsterHp = 20, species = afterSpecies)
        }
        return CombatTransition.PlayerAttack(
            attack = PlayerAttackSnapshot(
                taskId = taskId,
                occurrenceDateEpochDay = occurrenceDateEpochDay,
                targetMonsterInstanceId = 42L,
                seed = 1L,
                roll = 9_500,
                wasCritical = true,
                rawDamage = 25,
                targetDefense = 8,
                finalDamage = 17,
                targetHpBefore = before.activeMonster.currentHp,
                targetHpAfter = if (lethal) 0 else 20,
                processedAt = NOW,
                combatRewardVersion = if (withReward) 1 else 0,
                hitXpAward = if (withReward) 1L else 0L,
                killBonusXpAward = if (withReward && lethal) 10L else 0L,
                killGoldAward = if (withReward && lethal) 5L else 0L,
            ),
            before = before,
            after = after,
        )
    }

    private fun monsterTransition(
        lethal: Boolean,
        species: MonsterSpecies = MonsterSpecies.SKELETON_SOLDIER,
        refreshed: Boolean = false,
    ): CombatTransition.MonsterAttack {
        val beforeHp = if (lethal) 10 else 48
        val afterHp = if (lethal) 32 else 34
        val eventKey = CombatEventKey(
            kind = CombatEventKind.MONSTER_ATTACK,
            taskId = 200L,
            occurrenceDateEpochDay = 20_001L,
        )
        val lifecycleEvents = if (lethal) {
            listOf(
                CombatLifecycleEvent.PlayerDefeated(
                    eventId = "severe-1:player-defeated",
                    attackEventKey = eventKey,
                    effectRevision = 1L,
                    playerHpBefore = beforeHp,
                    playerMaxHpBeforeEffect = 80,
                ),
                if (refreshed) {
                    CombatLifecycleEvent.StatusEffectRefreshed(
                        eventId = "severe-1:status-effect-refreshed",
                        attackEventKey = eventKey,
                        effectType = StatusEffectType.SEVERE_INJURY,
                        effectRevision = 1L,
                        effectiveMaxHp = 64,
                    )
                } else {
                    CombatLifecycleEvent.StatusEffectApplied(
                        eventId = "severe-1:status-effect-applied",
                        attackEventKey = eventKey,
                        effectType = StatusEffectType.SEVERE_INJURY,
                        effectRevision = 1L,
                        effectiveMaxHp = 64,
                    )
                },
                CombatLifecycleEvent.PlayerEmergencyRecovered(
                    eventId = "severe-1:player-emergency-recovered",
                    attackEventKey = eventKey,
                    effectRevision = 1L,
                    recoveredHp = afterHp,
                    effectiveMaxHp = 64,
                ),
            )
        } else {
            emptyList()
        }
        return CombatTransition.MonsterAttack(
            attack = MonsterAttackSnapshot(
                taskId = 200L,
                occurrenceDateEpochDay = 20_001L,
                trigger = MonsterAttackTrigger.MANUAL_FAILURE,
                sourceMonsterInstanceId = 42L,
                sourceMonsterLevel = 3,
                sourceRawDamage = 20,
                playerDefense = 6,
                playerMaxHp = 80,
                finalDamage = 14,
                playerHpBefore = beforeHp,
                playerHpAfter = if (lethal) 0 else afterHp,
                wasLethal = lethal,
                revivedHp = if (lethal) afterHp else null,
                processedAt = NOW,
            ),
            before = snapshot(
                monsterId = 42L,
                monsterHp = 37,
                playerHp = beforeHp,
                species = species,
            ),
            after = snapshot(
                monsterId = 42L,
                monsterHp = 37,
                playerHp = afterHp,
                playerMaxHp = if (lethal) 64 else 80,
                species = species,
            ),
            lifecycleEvents = lifecycleEvents,
        )
    }

    private fun snapshot(
        monsterId: Long,
        monsterHp: Int,
        playerHp: Int = 48,
        playerMaxHp: Int = 80,
        stageNumber: Int = 7,
        species: MonsterSpecies = MonsterSpecies.SKELETON_SOLDIER,
    ) = CombatSnapshot(
        progress = StageProgress(
            stageNumber = stageNumber,
            stageLevel = 3,
            activeMonsterInstanceId = monsterId,
            lastReconciledAt = NOW,
            balanceVersion = 1,
        ),
        activeMonster = MonsterInstance(
            id = monsterId,
            definitionId = "monster_attack_v1",
            grade = MonsterGrade.NORMAL,
            stageNumber = stageNumber,
            encounterNumber = 2,
            level = 3,
            currentHp = monsterHp,
            balanceVersion = 1,
        ),
        activeMonsterStats = MonsterStats(maxHp = 55, damage = 15, defense = 8),
        activeMonsterSpecies = species,
        playerCurrentHp = playerHp,
        playerMaxHp = playerMaxHp,
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-07-14T08:55:00Z")

        val GOBLIN_VISUAL = ExpectedMonsterVisual(
            spriteResId = R.drawable.todo_quest_goblin_scout_front_idle,
            nameResId = R.string.battle_monster_goblin_scout_name,
            deathAnnouncementResId = R.string.battle_monster_death_announcement,
        )
        val SKELETON_VISUAL = ExpectedMonsterVisual(
            spriteResId = R.drawable.todo_quest_skeleton_soldier_front_idle,
            nameResId = R.string.battle_monster_skeleton_soldier_name,
            deathAnnouncementResId =
                R.string.battle_monster_skeleton_soldier_death_announcement,
        )
        val TREE_SPIRIT_VISUAL = ExpectedMonsterVisual(
            spriteResId = R.drawable.todo_quest_corrupted_tree_spirit_front_idle,
            nameResId = R.string.battle_monster_corrupted_tree_spirit_name,
            deathAnnouncementResId =
                R.string.battle_monster_corrupted_tree_spirit_death_announcement,
        )
        val HARPY_VISUAL = ExpectedMonsterVisual(
            spriteResId = R.drawable.todo_quest_harpy_front_idle,
            nameResId = R.string.battle_monster_harpy_name,
            deathAnnouncementResId = R.string.battle_monster_harpy_death_announcement,
        )
        val SLIME_VISUAL = ExpectedMonsterVisual(
            spriteResId = R.drawable.todo_quest_slime_front_idle,
            nameResId = R.string.battle_monster_slime_name,
            deathAnnouncementResId = R.string.battle_monster_slime_death_announcement,
        )
        val VISUALS_BY_SPECIES = mapOf(
            MonsterSpecies.GOBLIN_SCOUT to GOBLIN_VISUAL,
            MonsterSpecies.SKELETON_SOLDIER to SKELETON_VISUAL,
            MonsterSpecies.CORRUPTED_TREE_SPIRIT to TREE_SPIRIT_VISUAL,
            MonsterSpecies.HARPY to HARPY_VISUAL,
            MonsterSpecies.SLIME to SLIME_VISUAL,
        )
    }

    private data class SfxRequest(
        val effect: BattleSfx,
        val eventId: String,
    )

    private class FakeBattleSfxPlayer(
        private val throwOnPlay: Boolean = false,
    ) : BattleSfxPlayer {
        val requests = mutableListOf<SfxRequest>()

        override fun play(effect: BattleSfx, eventId: String) {
            requests += SfxRequest(effect = effect, eventId = eventId)
            if (throwOnPlay) error("broken audio delegate")
        }

        override fun release() = Unit
    }
}

private data class ExpectedMonsterVisual(
    val spriteResId: Int,
    val nameResId: Int,
    val deathAnnouncementResId: Int,
)
