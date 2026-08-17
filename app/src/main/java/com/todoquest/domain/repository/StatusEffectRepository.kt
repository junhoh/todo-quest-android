package com.todoquest.domain.repository

import com.todoquest.domain.model.CharacterStatusEffect
import com.todoquest.domain.model.CombatLifecycleEvent
import com.todoquest.domain.model.StatusEffectType
import kotlinx.coroutines.flow.Flow

interface StatusEffectRepository {
    fun observeActiveStatusEffects(characterId: Long): Flow<List<CharacterStatusEffect>>

    fun observeRemovalEvents(
        characterId: Long,
    ): Flow<CombatLifecycleEvent.StatusEffectRemoved>

    suspend fun reconcileExpired(characterId: Long): Int

    suspend fun removeStatusEffect(
        characterId: Long,
        type: StatusEffectType,
        revision: Long,
        mutationId: String,
    ): Boolean
}
