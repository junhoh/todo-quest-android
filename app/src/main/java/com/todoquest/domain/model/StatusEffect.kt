package com.todoquest.domain.model

import java.time.Instant

enum class StatusEffectType {
    SEVERE_INJURY,
}

data class CharacterStatusEffect(
    val characterId: Long,
    val type: StatusEffectType,
    val definitionVersion: Int,
    val appliedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val remainingRecoveryCompletions: Int,
    val active: Boolean,
    val revision: Long,
    val lastMutationId: String,
) {
    init {
        require(characterId > 0) { "characterId must be positive" }
        require(definitionVersion > 0) { "definitionVersion must be positive" }
        require(expiresAtEpochMillis > appliedAtEpochMillis) {
            "expiresAtEpochMillis must be after appliedAtEpochMillis"
        }
        require(remainingRecoveryCompletions >= 0) {
            "remainingRecoveryCompletions must not be negative"
        }
        require(revision > 0) { "revision must be positive" }
        require(lastMutationId.isNotBlank()) { "lastMutationId must not be blank" }
    }

    fun isEffectiveAt(at: Instant): Boolean {
        val atEpochMillis = at.toEpochMilli()
        return active &&
            remainingRecoveryCompletions > 0 &&
            atEpochMillis >= appliedAtEpochMillis &&
            atEpochMillis < expiresAtEpochMillis
    }
}

data class StatusEffectStatModifierDefinition(
    val modifierId: Long,
    val target: StatTarget,
    val type: ModifierType,
    val amount: Int,
    val stackingKey: String,
) {
    init {
        require(modifierId > 0) { "modifierId must be positive" }
        require(stackingKey.isNotBlank()) { "stackingKey must not be blank" }
    }
}

data class StatusEffectDefinition(
    val type: StatusEffectType,
    val version: Int,
    val basisPointScale: Int,
    val durationMillis: Long,
    val recoveryCompletionCount: Int,
    val emergencyRecoveryBp: Int,
    val temporaryModifiers: List<StatusEffectStatModifierDefinition>,
) {
    init {
        require(version > 0) { "version must be positive" }
        require(basisPointScale > 0) { "basisPointScale must be positive" }
        require(durationMillis > 0) { "durationMillis must be positive" }
        require(recoveryCompletionCount > 0) { "recoveryCompletionCount must be positive" }
        require(emergencyRecoveryBp in 1..basisPointScale) {
            "emergencyRecoveryBp must be within the basis-point scale"
        }
        require(temporaryModifiers.isNotEmpty()) { "temporaryModifiers must not be empty" }
        require(temporaryModifiers.map { it.modifierId }.distinct().size == temporaryModifiers.size) {
            "temporary modifier ids must be unique within a definition"
        }
        require(temporaryModifiers.map { it.stackingKey }.distinct().size == temporaryModifiers.size) {
            "temporary modifier stacking keys must be unique within a definition"
        }
    }
}
