package com.todoquest.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "character_status_effects",
    primaryKeys = ["characterId", "effectType"],
    foreignKeys = [
        ForeignKey(
            entity = CharacterProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["characterId", "active"])],
)
data class CharacterStatusEffectEntity(
    val characterId: Long,
    val effectType: String,
    val definitionVersion: Int,
    val appliedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val remainingRecoveryCompletions: Int,
    val active: Boolean,
    val revision: Long,
    val lastMutationId: String,
)

@Entity(
    tableName = "status_effect_recovery_occurrences",
    primaryKeys = [
        "characterId",
        "effectType",
        "revision",
        "taskId",
        "occurrenceDateEpochDay",
    ],
    foreignKeys = [
        ForeignKey(
            entity = CharacterStatusEffectEntity::class,
            parentColumns = ["characterId", "effectType"],
            childColumns = ["characterId", "effectType"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class StatusEffectRecoveryOccurrenceEntity(
    val characterId: Long,
    val effectType: String,
    val revision: Long,
    val taskId: Long,
    val occurrenceDateEpochDay: Long,
)
