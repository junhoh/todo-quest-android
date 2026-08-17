package com.todoquest.domain.usecase

import com.todoquest.domain.model.CharacterStatusEffect
import com.todoquest.domain.model.DerivedStatType
import com.todoquest.domain.model.ModifierType
import com.todoquest.domain.model.StatTarget
import com.todoquest.domain.model.StatusEffectDefinition
import com.todoquest.domain.model.StatusEffectStatModifierDefinition
import com.todoquest.domain.model.StatusEffectType
import com.todoquest.domain.model.TemporaryStatEffect
import java.time.Instant

object StatusEffectPolicy {
    private const val BASIS_POINT_SCALE = 10_000
    private const val SEVERE_INJURY_VERSION = 1
    private const val SEVERE_INJURY_DURATION_MILLIS = 24L * 60L * 60L * 1_000L

    private val definitions = listOf(
        StatusEffectDefinition(
            type = StatusEffectType.SEVERE_INJURY,
            version = SEVERE_INJURY_VERSION,
            basisPointScale = BASIS_POINT_SCALE,
            durationMillis = SEVERE_INJURY_DURATION_MILLIS,
            recoveryCompletionCount = 3,
            emergencyRecoveryBp = 5_000,
            temporaryModifiers = listOf(
                StatusEffectStatModifierDefinition(
                    modifierId = 51_001,
                    target = StatTarget.Derived(DerivedStatType.MAX_HP),
                    type = ModifierType.PERCENT_ADD,
                    amount = -2_000,
                    stackingKey = "status_effect:severe_injury:max_hp",
                ),
                StatusEffectStatModifierDefinition(
                    modifierId = 51_002,
                    target = StatTarget.Derived(DerivedStatType.ATTACK),
                    type = ModifierType.PERCENT_ADD,
                    amount = -2_000,
                    stackingKey = "status_effect:severe_injury:attack",
                ),
            ),
        ),
    ).associateBy { definition -> definition.type to definition.version }

    private val currentVersions = mapOf(
        StatusEffectType.SEVERE_INJURY to SEVERE_INJURY_VERSION,
    )

    fun currentDefinitionFor(type: StatusEffectType): StatusEffectDefinition =
        definitionFor(type, checkNotNull(currentVersions[type]))

    fun definitionFor(type: StatusEffectType, version: Int): StatusEffectDefinition =
        requireNotNull(definitions[type to version]) {
            "Unknown status effect definition: $type version $version"
        }

    fun temporaryEffectsFor(
        statusEffects: Collection<CharacterStatusEffect>,
        at: Instant,
    ): List<TemporaryStatEffect> {
        require(statusEffects.map { it.characterId }.distinct().size <= 1) {
            "status effects must belong to one character"
        }
        return statusEffects
            .filter { statusEffect -> statusEffect.isEffectiveAt(at) }
            .groupBy { statusEffect -> statusEffect.type }
            .values
            .map { sameType ->
                sameType.maxWith(
                    compareBy<CharacterStatusEffect> { it.revision }
                        .thenBy { it.appliedAtEpochMillis }
                        .thenBy { it.lastMutationId },
                )
            }
            .flatMap { statusEffect ->
                definitionFor(statusEffect.type, statusEffect.definitionVersion)
                    .temporaryModifiers
                    .map { modifier -> modifier.toTemporaryEffect(statusEffect) }
            }
    }

    fun emergencyRecoveryHp(
        type: StatusEffectType,
        definitionVersion: Int,
        effectiveMaxHp: Int,
    ): Int {
        require(effectiveMaxHp >= 1) { "effectiveMaxHp must be at least 1" }
        val definition = definitionFor(type, definitionVersion)
        return Math.multiplyExact(
            effectiveMaxHp.toLong(),
            definition.emergencyRecoveryBp.toLong(),
        ).div(definition.basisPointScale.toLong())
            .coerceAtLeast(1L)
            .toInt()
    }

    private fun StatusEffectStatModifierDefinition.toTemporaryEffect(
        statusEffect: CharacterStatusEffect,
    ): TemporaryStatEffect = TemporaryStatEffect(
        effectId = modifierId,
        target = target,
        type = type,
        amount = amount,
        stackingKey = stackingKey,
        startedAtEpochMillis = statusEffect.appliedAtEpochMillis,
        endsAtEpochMillis = statusEffect.expiresAtEpochMillis,
        remainingTriggers = statusEffect.remainingRecoveryCompletions,
    )
}
