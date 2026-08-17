package com.todoquest.domain

import com.todoquest.domain.model.CharacterStatusEffect
import com.todoquest.domain.model.DerivedStatType
import com.todoquest.domain.model.ModifierType
import com.todoquest.domain.model.StatTarget
import com.todoquest.domain.model.StatusEffectType
import com.todoquest.domain.model.TemporaryStatEffect
import com.todoquest.domain.usecase.StatusEffectPolicy
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusEffectPolicyTest {
    private val appliedAt = Instant.parse("2026-08-05T01:00:00Z")
    private val expiresAt = Instant.parse("2026-08-06T01:00:00Z")

    @Test
    fun severeInjuryDefinitionKeepsEveryVersionedRuleInOneCatalog() {
        val definition = StatusEffectPolicy.currentDefinitionFor(StatusEffectType.SEVERE_INJURY)

        assertEquals(StatusEffectType.SEVERE_INJURY, definition.type)
        assertEquals(1, definition.version)
        assertEquals(24L * 60L * 60L * 1_000L, definition.durationMillis)
        assertEquals(3, definition.recoveryCompletionCount)
        assertEquals(5_000, definition.emergencyRecoveryBp)
        assertEquals(
            setOf(
                StatTarget.Derived(DerivedStatType.MAX_HP) to -2_000,
                StatTarget.Derived(DerivedStatType.ATTACK) to -2_000,
            ),
            definition.temporaryModifiers.map { it.target to it.amount }.toSet(),
        )
        assertEquals(2, definition.temporaryModifiers.map { it.stackingKey }.toSet().size)
        assertTrue(definition.temporaryModifiers.all { it.type == ModifierType.PERCENT_ADD })
        assertThrows(IllegalArgumentException::class.java) {
            StatusEffectPolicy.definitionFor(StatusEffectType.SEVERE_INJURY, version = 999)
        }
    }

    @Test
    fun characterStatusEffectUsesEpochMillisAndTracksRefreshMutationState() {
        val effect = severeInjury()

        assertEquals(appliedAt.toEpochMilli(), effect.appliedAtEpochMillis)
        assertEquals(expiresAt.toEpochMilli(), effect.expiresAtEpochMillis)
        assertEquals(3, effect.remainingRecoveryCompletions)
        assertTrue(effect.active)
        assertEquals(2L, effect.revision)
        assertEquals("monster:42:2026-08-05", effect.lastMutationId)

        assertThrows(IllegalArgumentException::class.java) {
            effect.copy(expiresAtEpochMillis = effect.appliedAtEpochMillis)
        }
        assertThrows(IllegalArgumentException::class.java) {
            effect.copy(remainingRecoveryCompletions = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            effect.copy(lastMutationId = " ")
        }
    }

    @Test
    fun temporaryEffectsAllowTimeTriggerOrBothButRejectNeither() {
        temporaryEffect(endsAtEpochMillis = expiresAt.toEpochMilli(), remainingTriggers = null)
        temporaryEffect(endsAtEpochMillis = null, remainingTriggers = 3)
        temporaryEffect(endsAtEpochMillis = expiresAt.toEpochMilli(), remainingTriggers = 3)

        assertThrows(IllegalArgumentException::class.java) {
            temporaryEffect(endsAtEpochMillis = null, remainingTriggers = null)
        }
    }

    @Test
    fun severeInjuryMapsToTwoActiveNonStackingTemporaryModifiers() {
        val original = severeInjury(revision = 1)
        val refreshed = severeInjury(revision = 2)

        val modifiers = StatusEffectPolicy.temporaryEffectsFor(
            statusEffects = listOf(original, refreshed),
            at = appliedAt.plusSeconds(1),
        )

        assertEquals(2, modifiers.size)
        assertEquals(2, modifiers.map { it.stackingKey }.toSet().size)
        assertEquals(setOf(-2_000), modifiers.map { it.amount }.toSet())
        assertEquals(
            setOf(
                StatTarget.Derived(DerivedStatType.MAX_HP),
                StatTarget.Derived(DerivedStatType.ATTACK),
            ),
            modifiers.map { it.target }.toSet(),
        )
        assertTrue(modifiers.all { it.startedAtEpochMillis == appliedAt.toEpochMilli() })
        assertTrue(modifiers.all { it.endsAtEpochMillis == expiresAt.toEpochMilli() })
        assertTrue(modifiers.all { it.remainingTriggers == 3 })
        assertNotEquals(modifiers[0].effectId, modifiers[1].effectId)

        assertTrue(
            StatusEffectPolicy.temporaryEffectsFor(
                listOf(refreshed.copy(active = false)),
                appliedAt.plusSeconds(1),
            ).isEmpty(),
        )
        assertTrue(
            StatusEffectPolicy.temporaryEffectsFor(
                listOf(refreshed),
                expiresAt,
            ).isEmpty(),
        )
        assertFalse(refreshed.copy(remainingRecoveryCompletions = 0).isEffectiveAt(appliedAt.plusSeconds(1)))
    }

    @Test
    fun emergencyRecoveryUsesLongFloorMathAndKeepsAtLeastOneHp() {
        assertEquals(
            55,
            StatusEffectPolicy.emergencyRecoveryHp(
                StatusEffectType.SEVERE_INJURY,
                definitionVersion = 1,
                effectiveMaxHp = 111,
            ),
        )
        assertEquals(
            1,
            StatusEffectPolicy.emergencyRecoveryHp(
                StatusEffectType.SEVERE_INJURY,
                definitionVersion = 1,
                effectiveMaxHp = 1,
            ),
        )
        assertEquals(
            1_073_741_823,
            StatusEffectPolicy.emergencyRecoveryHp(
                StatusEffectType.SEVERE_INJURY,
                definitionVersion = 1,
                effectiveMaxHp = Int.MAX_VALUE,
            ),
        )
    }

    private fun severeInjury(revision: Long = 2): CharacterStatusEffect = CharacterStatusEffect(
        characterId = 1,
        type = StatusEffectType.SEVERE_INJURY,
        definitionVersion = 1,
        appliedAtEpochMillis = appliedAt.toEpochMilli(),
        expiresAtEpochMillis = expiresAt.toEpochMilli(),
        remainingRecoveryCompletions = 3,
        active = true,
        revision = revision,
        lastMutationId = "monster:42:2026-08-05",
    )

    private fun temporaryEffect(
        endsAtEpochMillis: Long?,
        remainingTriggers: Int?,
    ): TemporaryStatEffect = TemporaryStatEffect(
        effectId = 1,
        target = StatTarget.Derived(DerivedStatType.MAX_HP),
        type = ModifierType.PERCENT_ADD,
        amount = -2_000,
        stackingKey = "test",
        startedAtEpochMillis = appliedAt.toEpochMilli(),
        endsAtEpochMillis = endsAtEpochMillis,
        remainingTriggers = remainingTriggers,
    )
}
