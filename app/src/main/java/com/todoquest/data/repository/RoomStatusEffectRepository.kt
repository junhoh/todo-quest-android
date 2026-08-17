package com.todoquest.data.repository

import androidx.room.withTransaction
import com.todoquest.core.AppClock
import com.todoquest.data.local.CharacterStatusEffectEntity
import com.todoquest.data.local.StatusEffectRecoveryOccurrenceEntity
import com.todoquest.data.local.TodoQuestDatabase
import com.todoquest.domain.model.CharacterStatusEffect
import com.todoquest.domain.model.CombatLifecycleEvent
import com.todoquest.domain.model.StatusEffectType
import com.todoquest.domain.model.TemporaryStatEffect
import com.todoquest.domain.repository.StatusEffectRepository
import com.todoquest.domain.usecase.StatusEffectPolicy
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class RoomStatusEffectRepository(
    private val database: TodoQuestDatabase,
    private val clock: AppClock,
) : StatusEffectRepository {
    private val statusEffectDao = database.statusEffectDao()

    override fun observeActiveStatusEffects(
        characterId: Long,
    ): Flow<List<CharacterStatusEffect>> = statusEffectDao
        .observeActiveStatusEffects(characterId)
        .onStart { reconcileExpired(characterId) }
        .map { entities ->
            val now = clock.now()
            entities.map(CharacterStatusEffectEntity::toDomain)
                .filter { it.isEffectiveAt(now) }
        }

    override fun observeRemovalEvents(
        characterId: Long,
    ): Flow<CombatLifecycleEvent.StatusEffectRemoved> = flow {
        var previousByType: Map<String, CharacterStatusEffectEntity>? = null
        statusEffectDao.observeStatusEffects(characterId).collect { current ->
            val previous = previousByType
            if (previous != null) {
                current.forEach { entity ->
                    val prior = previous[entity.effectType]
                    if (
                        prior?.active == true &&
                        prior.revision == entity.revision &&
                        !entity.active
                    ) {
                        emit(
                            CombatLifecycleEvent.StatusEffectRemoved(
                                eventId = entity.lastMutationId,
                                effectType = StatusEffectType.entries.singleOrNull {
                                    it.name == entity.effectType
                                } ?: error("Unknown status effect type: ${entity.effectType}"),
                                effectRevision = entity.revision,
                                removedAtEpochMillis = clock.now().toEpochMilli(),
                            ),
                        )
                    }
                }
            }
            previousByType = current.associateBy(CharacterStatusEffectEntity::effectType)
        }
    }

    override suspend fun reconcileExpired(characterId: Long): Int = database.withTransaction {
        reconcileExpiredStatusEffects(database, characterId, clock.now())
    }

    override suspend fun removeStatusEffect(
        characterId: Long,
        type: StatusEffectType,
        revision: Long,
        mutationId: String,
    ): Boolean {
        require(characterId > 0L) { "characterId must be positive" }
        require(revision > 0L) { "revision must be positive" }
        require(mutationId.isNotBlank()) { "mutationId must not be blank" }
        return database.withTransaction {
            statusEffectDao.deactivateStatusEffect(
                characterId = characterId,
                effectType = type.name,
                revision = revision,
                lastMutationId = mutationId,
            ) == 1
        }
    }
}

internal suspend fun reconcileExpiredStatusEffects(
    database: TodoQuestDatabase,
    characterId: Long,
    now: Instant,
): Int {
    val dao = database.statusEffectDao()
    val nowEpochMillis = now.toEpochMilli()
    return dao.getActiveStatusEffects(characterId).count { effect ->
        effect.expiresAtEpochMillis <= nowEpochMillis &&
            dao.deactivateIfExpired(
                characterId = characterId,
                effectType = effect.effectType,
                revision = effect.revision,
                nowEpochMillis = nowEpochMillis,
                lastMutationId = expirationMutationId(effect, nowEpochMillis),
            ) == 1
    }
}

internal suspend fun loadActiveStatusEffects(
    database: TodoQuestDatabase,
    characterId: Long,
    at: Instant,
): List<CharacterStatusEffect> = database.statusEffectDao()
    .getActiveStatusEffects(characterId)
    .map(CharacterStatusEffectEntity::toDomain)
    .filter { effect -> effect.isEffectiveAt(at) }

internal suspend fun loadActiveStatusModifiers(
    database: TodoQuestDatabase,
    characterId: Long,
    at: Instant,
): List<TemporaryStatEffect> = StatusEffectPolicy.temporaryEffectsFor(
    statusEffects = loadActiveStatusEffects(database, characterId, at),
    at = at,
)

internal suspend fun creditCompletionRecovery(
    database: TodoQuestDatabase,
    characterId: Long,
    taskId: Long,
    occurrenceDateEpochDay: Long,
    completedAt: Instant,
) {
    val dao = database.statusEffectDao()
    loadActiveStatusEffects(database, characterId, completedAt).forEach { effect ->
        val creditMutationId = recoveryCreditMutationId(
            effect = effect,
            taskId = taskId,
            occurrenceDateEpochDay = occurrenceDateEpochDay,
        )
        val inserted = dao.insertRecoveryOccurrence(
            StatusEffectRecoveryOccurrenceEntity(
                characterId = characterId,
                effectType = effect.type.name,
                revision = effect.revision,
                taskId = taskId,
                occurrenceDateEpochDay = occurrenceDateEpochDay,
            ),
        )
        if (inserted == -1L) return@forEach

        check(
            dao.decrementRemainingRecoveryCompletions(
                characterId = characterId,
                effectType = effect.type.name,
                revision = effect.revision,
                lastMutationId = creditMutationId,
            ) == 1,
        ) { "status effect recovery credit could not decrement its current revision" }

        val updated = checkNotNull(
            dao.getStatusEffect(characterId, effect.type.name),
        ) { "status effect disappeared after recovery credit" }
        if (updated.remainingRecoveryCompletions == 0) {
            check(
                dao.deactivateIfRecovered(
                    characterId = characterId,
                    effectType = effect.type.name,
                    revision = effect.revision,
                    lastMutationId = recoveryRemovalMutationId(
                        effect = effect,
                        taskId = taskId,
                        occurrenceDateEpochDay = occurrenceDateEpochDay,
                    ),
                ) == 1,
            ) { "recovered status effect could not be removed" }
        }
    }
}

internal fun CharacterStatusEffectEntity.toDomain(): CharacterStatusEffect = CharacterStatusEffect(
    characterId = characterId,
    type = StatusEffectType.entries.singleOrNull { it.name == effectType }
        ?: throw IllegalArgumentException("Unknown status effect type: $effectType"),
    definitionVersion = definitionVersion,
    appliedAtEpochMillis = appliedAtEpochMillis,
    expiresAtEpochMillis = expiresAtEpochMillis,
    remainingRecoveryCompletions = remainingRecoveryCompletions,
    active = active,
    revision = revision,
    lastMutationId = lastMutationId,
)

private fun expirationMutationId(
    effect: CharacterStatusEffectEntity,
    nowEpochMillis: Long,
): String = "status-effect:expired:${effect.effectType}:${effect.revision}:$nowEpochMillis"

private fun recoveryCreditMutationId(
    effect: CharacterStatusEffect,
    taskId: Long,
    occurrenceDateEpochDay: Long,
): String =
    "status-effect:recovery:${effect.type.name}:${effect.revision}:$taskId:$occurrenceDateEpochDay"

private fun recoveryRemovalMutationId(
    effect: CharacterStatusEffect,
    taskId: Long,
    occurrenceDateEpochDay: Long,
): String =
    "status-effect:removed:recovery:${effect.type.name}:${effect.revision}:$taskId:$occurrenceDateEpochDay"
