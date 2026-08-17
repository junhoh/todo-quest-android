package com.todoquest.data.repository

import androidx.room.withTransaction
import com.todoquest.core.AppClock
import com.todoquest.data.local.CharacterStatusEffectEntity
import com.todoquest.data.local.CombatProgressEntity
import com.todoquest.data.local.FailureLogEntity
import com.todoquest.data.local.MonsterAttackEventEntity
import com.todoquest.data.local.MonsterInstanceEntity
import com.todoquest.data.local.PlayerAttackEventEntity
import com.todoquest.data.local.TodoQuestDatabase
import com.todoquest.data.mapper.CharacterMapper
import com.todoquest.data.mapper.CombatEntityMapper
import com.todoquest.data.mapper.TodoTaskMapper
import com.todoquest.domain.model.CharacterCurrentState
import com.todoquest.domain.model.CharacterStatBalanceConfig
import com.todoquest.domain.model.CharacterLoadoutCatalog
import com.todoquest.domain.model.CombatRewardBalanceCatalog
import com.todoquest.domain.model.CombatEventKey
import com.todoquest.domain.model.CombatEventKind
import com.todoquest.domain.model.CombatEventStatus
import com.todoquest.domain.model.CombatLifecycleEvent
import com.todoquest.domain.model.CombatReconciliationResult
import com.todoquest.domain.model.CombatSnapshot
import com.todoquest.domain.model.CombatTransition
import com.todoquest.domain.model.EquipmentStatModifier
import com.todoquest.domain.model.MonsterAttackResult
import com.todoquest.domain.model.MonsterAttackSkipReason
import com.todoquest.domain.model.MonsterAttackTrigger
import com.todoquest.domain.model.MonsterBalanceConfig
import com.todoquest.domain.model.MonsterCatalog
import com.todoquest.domain.model.MonsterDefinition
import com.todoquest.domain.model.MonsterInstance
import com.todoquest.domain.model.MonsterSpecies
import com.todoquest.domain.model.MonsterStats
import com.todoquest.domain.model.PlayerAttackResult
import com.todoquest.domain.model.PlayerCharacter
import com.todoquest.domain.model.StatusEffectType
import com.todoquest.domain.model.TemporaryStatEffect
import com.todoquest.domain.repository.CombatRepository
import com.todoquest.domain.usecase.CharacterProgressionPolicy
import com.todoquest.domain.usecase.CombatCalculator
import com.todoquest.domain.usecase.CombatReward
import com.todoquest.domain.usecase.CombatRewardPolicy
import com.todoquest.domain.usecase.MissedOccurrencePolicy
import com.todoquest.domain.usecase.MonsterCombatPolicy
import com.todoquest.domain.usecase.MonsterDiscoveryPolicy
import com.todoquest.domain.usecase.MonsterSpeciesPolicy
import com.todoquest.domain.usecase.MonsterStagePolicy
import com.todoquest.domain.usecase.MonsterStatsCalculator
import com.todoquest.domain.usecase.StatusEffectPolicy
import com.todoquest.domain.usecase.TaskDifficultyCombatPolicy
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun interface CombatSeedSource {
    fun nextSeed(): Long
}

object RandomCombatSeedSource : CombatSeedSource {
    override fun nextSeed(): Long = Random.Default.nextLong()
}

class RoomCombatRepository(
    private val database: TodoQuestDatabase,
    private val clock: AppClock,
    private val seedSource: CombatSeedSource = RandomCombatSeedSource,
    private val characterBalanceConfig: CharacterStatBalanceConfig = CharacterStatBalanceConfig(),
    private val monsterBalanceConfig: MonsterBalanceConfig = MonsterBalanceConfig(),
) : CombatRepository {
    private val characterDao = database.characterProfileDao()
    private val combatDao = database.combatDao()
    private val completionLogDao = database.completionLogDao()
    private val failureLogDao = database.failureLogDao()
    private val statusEffectDao = database.statusEffectDao()
    private val taskDao = database.todoTaskDao()
    private val commandMutex = Mutex()
    private val mutableEvents = MutableSharedFlow<CombatTransition>(replay = 0)
    private val monsterDefinitions = MonsterCatalog.definitions(monsterBalanceConfig)
        .associateBy(MonsterDefinition::id)

    override val events: Flow<CombatTransition> = mutableEvents.asSharedFlow()

    override fun observeCombat(): Flow<CombatSnapshot> = database.invalidationTracker
        .createFlow(*CombatObservationTables)
        .onStart {
            commandMutex.withLock {
                initializeCombatLocked()
            }
        }
        .map {
            commandMutex.withLock {
                database.withTransaction {
                    combatSnapshot(loadOrInitializeCombat())
                }
            }
        }

    override fun observeDiscoveredMonsterSpecies(): Flow<Set<MonsterSpecies>> =
        combatDao.observeMonsterInstances()
        .onStart {
            commandMutex.withLock {
                initializeCombatLocked()
            }
        }
        .map { entities ->
            MonsterDiscoveryPolicy.discoveredSpecies(
                instances = entities.map(CombatEntityMapper::toDomain),
                config = monsterBalanceConfig,
            )
        }

    override suspend fun processPlayerAttack(
        taskId: Long,
        occurrenceDate: LocalDate,
    ): PlayerAttackResult = commandMutex.withLock {
        processPlayerAttackLocked(taskId, occurrenceDate)
    }

    private suspend fun processPlayerAttackLocked(
        taskId: Long,
        occurrenceDate: LocalDate,
    ): PlayerAttackResult {
        val outcome = database.withTransaction {
            val stored = loadOrInitializeCombat()
            val event = combatDao.getPlayerAttackEvent(taskId, occurrenceDate.toEpochDay())
                ?: return@withTransaction PlayerAttackCommandOutcome(PlayerAttackResult.NotFound)

            when (CombatEntityMapper.toEventStatus(event.status)) {
                CombatEventStatus.APPLIED -> PlayerAttackCommandOutcome(
                    result = CombatEntityMapper.toAppliedPlayerAttack(
                        entity = event,
                        wasAlreadyApplied = true,
                    ),
                )
                CombatEventStatus.PENDING -> applyPendingPlayerAttack(event, stored)
                CombatEventStatus.SKIPPED -> error("Player attack event cannot be skipped")
            }
        }
        outcome.transition?.let { mutableEvents.emit(it) }
        return outcome.result
    }

    override suspend fun processPendingPlayerAttacks(): Int = commandMutex.withLock {
        processPendingPlayerAttacksLocked()
    }

    private suspend fun processPendingPlayerAttacksLocked(): Int {
        initializeCombatLocked()
        val pending = combatDao.findPendingPlayerAttackEvents()
        var appliedCount = 0
        pending.forEach { event ->
            val result = processPlayerAttackLocked(
                taskId = event.taskId,
                occurrenceDate = LocalDate.ofEpochDay(event.occurrenceDateEpochDay),
            )
            if (result is PlayerAttackResult.Applied && !result.wasAlreadyApplied) {
                appliedCount += 1
            }
        }
        return appliedCount
    }

    override suspend fun processFailedOccurrenceAttack(
        taskId: Long,
        occurrenceDate: LocalDate,
    ): MonsterAttackResult = commandMutex.withLock {
        processFailedOccurrenceAttackLocked(taskId, occurrenceDate)
    }

    private suspend fun processFailedOccurrenceAttackLocked(
        taskId: Long,
        occurrenceDate: LocalDate,
    ): MonsterAttackResult {
        val occurrenceEpochDay = occurrenceDate.toEpochDay()
        val outcome = database.withTransaction {
            val failure = failureLogDao.find(taskId, occurrenceEpochDay)
                ?: return@withTransaction MonsterAttackCommandOutcome(MonsterAttackResult.NotFound)
            val existing = combatDao.getMonsterAttackEvent(taskId, occurrenceEpochDay)
            if (existing != null) {
                return@withTransaction MonsterAttackCommandOutcome(
                    result = CombatEntityMapper.toAppliedMonsterAttack(
                        entity = existing,
                        wasAlreadyApplied = true,
                    ),
                )
            }
            applyFreshMonsterAttack(
                taskId = failure.taskId,
                occurrenceDateEpochDay = failure.occurrenceDateEpochDay,
                recurrenceSeriesId = failure.recurrenceSeriesId,
                trigger = MonsterAttackTrigger.MANUAL_FAILURE,
                processedAt = clock.now(),
                stored = loadOrInitializeCombat(),
            )
        }
        outcome.transition?.let { mutableEvents.emit(it) }
        return outcome.result
    }

    override suspend fun processPendingFailureAttacks(): Int = commandMutex.withLock {
        processPendingFailureAttacksLocked()
    }

    private suspend fun processPendingFailureAttacksLocked(): Int {
        initializeCombatLocked()
        var appliedCount = 0
        failureLogDao.findPendingMonsterAttacks().forEach { failure ->
            val result = processFailedOccurrenceAttackLocked(
                taskId = failure.taskId,
                occurrenceDate = LocalDate.ofEpochDay(failure.occurrenceDateEpochDay),
            )
            if (result is MonsterAttackResult.Applied && !result.wasAlreadyApplied) {
                appliedCount += 1
            }
        }
        return appliedCount
    }

    override suspend fun reconcileOverdue(now: Instant): CombatReconciliationResult =
        commandMutex.withLock {
            val playerAttacksProcessed = processPendingPlayerAttacksLocked()
            val manualAttacksApplied = processPendingFailureAttacksLocked()
            val missed = reconcileDeadlineAttacksLocked(now)
            CombatReconciliationResult(
                playerAttacksProcessed = playerAttacksProcessed,
                monsterAttacksApplied = manualAttacksApplied + missed.applied,
                monsterAttacksSkipped = missed.skipped,
            )
        }

    private suspend fun reconcileDeadlineAttacksLocked(now: Instant): MissedReconciliationCounts {
        val missed = database.withTransaction {
            val stored = loadOrInitializeCombat()
            val cursor = Instant.ofEpochMilli(stored.progress.lastReconciledAtEpochMillis)
            if (now.isBefore(cursor)) {
                return@withTransaction MissedReconciliationCounts()
            }
            val taskEntities = taskDao.findForCombatReconciliation(
                endEpochDay = now.atZone(clock.zoneId).toLocalDate().toEpochDay(),
                cursorEpochMillis = cursor.toEpochMilli(),
            )
            val candidates = MissedOccurrencePolicy.dueCandidates(
                sources = taskEntities.map { entity ->
                    MissedOccurrencePolicy.Source(
                        task = TodoTaskMapper.toDomain(entity),
                        recurrenceSeriesId = entity.recurrenceSeriesId,
                        deletedAt = entity.deletedAtEpochMillis?.let(Instant::ofEpochMilli),
                    )
                },
                cursor = cursor,
                now = now,
                zoneId = clock.zoneId,
            )
            val newDue = buildList {
                candidates.forEach { candidate ->
                    val occurrenceEpochDay = candidate.occurrenceDate.toEpochDay()
                    if (
                        completionLogDao.find(candidate.taskId, occurrenceEpochDay) == null &&
                        failureLogDao.find(candidate.taskId, occurrenceEpochDay) == null &&
                        combatDao.getMonsterAttackEvent(candidate.taskId, occurrenceEpochDay) == null
                    ) {
                        add(candidate)
                    }
                }
            }

            var workingStored = stored
            var appliedCount = 0
            var skippedCount = 0
            val transitions = mutableListOf<CombatTransition.MonsterAttack>()
            newDue.forEachIndexed { index, candidate ->
                val occurrenceEpochDay = candidate.occurrenceDate.toEpochDay()
                check(
                    failureLogDao.insert(
                        FailureLogEntity(
                            taskId = candidate.taskId,
                            occurrenceDateEpochDay = occurrenceEpochDay,
                            recurrenceSeriesId = candidate.recurrenceSeriesId,
                            failedAtEpochMillis = now.toEpochMilli(),
                        ),
                    ) != -1L,
                ) { "Missed occurrence failure log could not be inserted" }
                if (index >= MONSTER_ATTACK_DAMAGE_LIMIT) {
                    val playerStats = derivedStatsFor(
                        workingStored.character.character,
                        characterBalanceConfig,
                        workingStored.character.equipmentModifiers,
                        workingStored.character.statusModifiers,
                    )
                    val sourceStats = statsFor(workingStored.activeMonster)
                    val currentHp = workingStored.character.currentState.currentHp
                    check(
                        combatDao.insertMonsterAttackEvent(
                            MonsterAttackEventEntity(
                                taskId = candidate.taskId,
                                occurrenceDateEpochDay = occurrenceEpochDay,
                                recurrenceSeriesId = candidate.recurrenceSeriesId,
                                trigger = MonsterAttackTrigger.MISSED_DEADLINE.name,
                                status = CombatEventStatus.SKIPPED.name,
                                skipReason = MonsterAttackSkipReason.SKIPPED_RECONCILIATION_CAP.name,
                                sourceMonsterInstanceId = workingStored.activeMonster.id,
                                sourceMonsterLevel = workingStored.activeMonster.level,
                                sourceRawDamage = sourceStats.damage,
                                playerDefense = playerStats.defense,
                                playerMaxHp = playerStats.maxHp,
                                finalDamage = 0,
                                playerHpBefore = currentHp,
                                playerHpAfter = currentHp,
                                wasLethal = false,
                                revivedHp = null,
                                characterBalanceVersion = characterBalanceConfig.version,
                                monsterBalanceVersion = monsterBalanceConfig.version,
                                processedAtEpochMillis = now.toEpochMilli(),
                            ),
                        ) != -1L,
                    ) { "Skipped monster attack event could not be inserted" }
                    skippedCount += 1
                } else {
                    val outcome = applyFreshMonsterAttack(
                        taskId = candidate.taskId,
                        occurrenceDateEpochDay = occurrenceEpochDay,
                        recurrenceSeriesId = candidate.recurrenceSeriesId,
                        trigger = MonsterAttackTrigger.MISSED_DEADLINE,
                        processedAt = now,
                        stored = workingStored,
                    )
                    transitions += checkNotNull(outcome.transition)
                    workingStored = checkNotNull(outcome.storedAfter)
                    appliedCount += 1
                }
            }
            check(combatDao.updateLastReconciledAt(now.toEpochMilli()) == 1) {
                "Combat reconciliation cursor could not be updated"
            }
            MissedReconciliationCounts(
                applied = appliedCount,
                skipped = skippedCount,
                transitions = transitions,
            )
        }
        missed.transitions.forEach { mutableEvents.emit(it) }
        return missed
    }

    private suspend fun initializeCombatLocked() {
        database.withTransaction {
            loadOrInitializeCombat()
        }
    }

    private suspend fun applyFreshMonsterAttack(
        taskId: Long,
        occurrenceDateEpochDay: Long,
        recurrenceSeriesId: Long,
        trigger: MonsterAttackTrigger,
        processedAt: Instant,
        stored: StoredCombat,
    ): MonsterAttackCommandOutcome {
        val playerStats = derivedStatsFor(
            stored.character.character,
            characterBalanceConfig,
            stored.character.equipmentModifiers,
            stored.character.statusModifiers,
        )
        val sourceStats = statsFor(stored.activeMonster)
        val currentHp = stored.character.currentState.currentHp
        val before = combatSnapshot(stored)
        val finalDamage = CombatCalculator.damageAfterDefense(
            rawDamage = sourceStats.damage,
            defense = playerStats.defense,
            config = characterBalanceConfig,
        )
        val damageResult = MonsterCombatPolicy.playerHpAfterDamage(
            currentHp = currentHp,
            maxHp = playerStats.maxHp,
            finalDamage = finalDamage,
        )
        val defeat = if (damageResult.wasLethal) {
            prepareSevereInjuryDefeat(
                taskId = taskId,
                occurrenceDateEpochDay = occurrenceDateEpochDay,
                processedAt = processedAt,
                playerHpBefore = currentHp,
                playerMaxHpBeforeEffect = playerStats.maxHp,
                storedCharacter = stored.character,
            )
        } else {
            null
        }
        val currentHpAfterTransaction = defeat?.recoveredHp ?: damageResult.currentHp
        val event = MonsterAttackEventEntity(
            taskId = taskId,
            occurrenceDateEpochDay = occurrenceDateEpochDay,
            recurrenceSeriesId = recurrenceSeriesId,
            trigger = trigger.name,
            status = CombatEventStatus.APPLIED.name,
            skipReason = null,
            sourceMonsterInstanceId = stored.activeMonster.id,
            sourceMonsterLevel = stored.activeMonster.level,
            sourceRawDamage = sourceStats.damage,
            playerDefense = playerStats.defense,
            playerMaxHp = playerStats.maxHp,
            finalDamage = finalDamage,
            playerHpBefore = currentHp,
            playerHpAfter = damageResult.currentHp,
            wasLethal = damageResult.wasLethal,
            revivedHp = defeat?.recoveredHp,
            characterBalanceVersion = characterBalanceConfig.version,
            monsterBalanceVersion = monsterBalanceConfig.version,
            processedAtEpochMillis = processedAt.toEpochMilli(),
        )
        check(combatDao.insertMonsterAttackEvent(event) != -1L) {
            "Fresh monster attack event could not be inserted"
        }
        defeat?.let { statusEffectDao.upsertStatusEffect(it.effect) }
        val storedAfter = stored.copy(
            character = stored.character.copy(
                currentState = stored.character.currentState.copy(
                    currentHp = currentHpAfterTransaction,
                    balanceVersion = characterBalanceConfig.version,
                    updatedAtEpochMillis = processedAt.toEpochMilli(),
                ),
                statusModifiers = defeat?.statusModifiers ?: stored.character.statusModifiers,
            ),
        )
        characterDao.upsertCurrentState(
            CharacterMapper.fromDomain(
                storedAfter.character.currentState,
            ),
        )
        val result = CombatEntityMapper.toAppliedMonsterAttack(
            entity = event,
            wasAlreadyApplied = false,
        )
        return MonsterAttackCommandOutcome(
            result = result,
            transition = CombatTransition.MonsterAttack(
                attack = result.attack,
                before = before,
                after = combatSnapshot(storedAfter),
                lifecycleEvents = defeat?.lifecycleEvents.orEmpty(),
            ),
            storedAfter = storedAfter,
        )
    }

    private suspend fun prepareSevereInjuryDefeat(
        taskId: Long,
        occurrenceDateEpochDay: Long,
        processedAt: Instant,
        playerHpBefore: Int,
        playerMaxHpBeforeEffect: Int,
        storedCharacter: StoredCombatCharacter,
    ): SevereInjuryDefeatOutcome {
        val effectType = StatusEffectType.SEVERE_INJURY
        val definition = StatusEffectPolicy.currentDefinitionFor(effectType)
        val existing = statusEffectDao.getStatusEffect(
            characterId = storedCharacter.character.id,
            effectType = effectType.name,
        )
        val isRefresh = existing?.toDomain()?.isEffectiveAt(processedAt) == true
        val revision = Math.addExact(existing?.revision ?: 0L, 1L)
        val attackEventKey = CombatEventKey(
            kind = CombatEventKind.MONSTER_ATTACK,
            taskId = taskId,
            occurrenceDateEpochDay = occurrenceDateEpochDay,
        )
        val eventIdPrefix = severeInjuryEventIdPrefix(
            attackEventKey = attackEventKey,
            effectRevision = revision,
        )
        val effectLifecycleEventId = if (isRefresh) {
            "$eventIdPrefix:status-effect-refreshed"
        } else {
            "$eventIdPrefix:status-effect-applied"
        }
        val processedAtEpochMillis = processedAt.toEpochMilli()
        val effect = CharacterStatusEffectEntity(
            characterId = storedCharacter.character.id,
            effectType = effectType.name,
            definitionVersion = definition.version,
            appliedAtEpochMillis = processedAtEpochMillis,
            expiresAtEpochMillis = Math.addExact(
                processedAtEpochMillis,
                definition.durationMillis,
            ),
            remainingRecoveryCompletions = definition.recoveryCompletionCount,
            active = true,
            revision = revision,
            lastMutationId = effectLifecycleEventId,
        )
        val refreshedEffects = buildList {
            addAll(
                loadActiveStatusEffects(
                    database = database,
                    characterId = storedCharacter.character.id,
                    at = processedAt,
                ).filterNot { statusEffect -> statusEffect.type == effectType },
            )
            add(effect.toDomain())
        }
        val statusModifiers = StatusEffectPolicy.temporaryEffectsFor(
            statusEffects = refreshedEffects,
            at = processedAt,
        )
        val effectiveStats = derivedStatsFor(
            character = storedCharacter.character,
            config = characterBalanceConfig,
            equipmentModifiers = storedCharacter.equipmentModifiers,
            temporaryEffects = statusModifiers,
        )
        val recoveredHp = StatusEffectPolicy.emergencyRecoveryHp(
            type = effectType,
            definitionVersion = definition.version,
            effectiveMaxHp = effectiveStats.maxHp,
        )
        val effectEvent = if (isRefresh) {
            CombatLifecycleEvent.StatusEffectRefreshed(
                eventId = effectLifecycleEventId,
                attackEventKey = attackEventKey,
                effectType = effectType,
                effectRevision = revision,
                effectiveMaxHp = effectiveStats.maxHp,
            )
        } else {
            CombatLifecycleEvent.StatusEffectApplied(
                eventId = effectLifecycleEventId,
                attackEventKey = attackEventKey,
                effectType = effectType,
                effectRevision = revision,
                effectiveMaxHp = effectiveStats.maxHp,
            )
        }
        return SevereInjuryDefeatOutcome(
            effect = effect,
            statusModifiers = statusModifiers,
            recoveredHp = recoveredHp,
            lifecycleEvents = listOf(
                CombatLifecycleEvent.PlayerDefeated(
                    eventId = "$eventIdPrefix:player-defeated",
                    attackEventKey = attackEventKey,
                    effectRevision = revision,
                    playerHpBefore = playerHpBefore,
                    playerMaxHpBeforeEffect = playerMaxHpBeforeEffect,
                ),
                effectEvent,
                CombatLifecycleEvent.PlayerEmergencyRecovered(
                    eventId = "$eventIdPrefix:player-emergency-recovered",
                    attackEventKey = attackEventKey,
                    effectRevision = revision,
                    recoveredHp = recoveredHp,
                    effectiveMaxHp = effectiveStats.maxHp,
                ),
            ),
        )
    }

    private suspend fun loadOrInitializeCombat(): StoredCombat {
        val character = loadOrCreateCharacter()
        val progress = combatDao.getCombatProgress()
        if (progress != null) {
            val activeMonsterEntity = checkNotNull(
                combatDao.getMonsterInstance(progress.activeMonsterInstanceId),
            ) { "Combat progress points to a missing monster instance" }
            val activeMonster = CombatEntityMapper.toDomain(activeMonsterEntity)
            val playerStats = derivedStatsFor(
                character.character,
                characterBalanceConfig,
                character.equipmentModifiers,
                character.statusModifiers,
            )
            statsFor(activeMonster)
            validateStoredCombat(
                progress = progress,
                monster = activeMonster,
                currentState = character.currentState,
                playerMaxHp = playerStats.maxHp,
            )
            return StoredCombat(progress, activeMonster, character)
        }

        val initialMonster = combatDao.getMonsterInstanceAt(
            stageNumber = INITIAL_STAGE_NUMBER,
            encounterNumber = INITIAL_ENCOUNTER_NUMBER,
        )?.let(CombatEntityMapper::toDomain) ?: createMonster(
            stageNumber = INITIAL_STAGE_NUMBER,
            encounterNumber = INITIAL_ENCOUNTER_NUMBER,
            stageLevel = CharacterProgressionPolicy.levelFor(
                character.character.totalXp,
                characterBalanceConfig,
            ),
        )
        val stageLevel = CharacterProgressionPolicy.levelFor(
            character.character.totalXp,
            characterBalanceConfig,
        )
        validateMonsterSlot(initialMonster, INITIAL_STAGE_NUMBER, INITIAL_ENCOUNTER_NUMBER, stageLevel)
        val initialProgress = CombatProgressEntity(
            stageNumber = INITIAL_STAGE_NUMBER,
            stageLevel = stageLevel,
            activeMonsterInstanceId = initialMonster.id,
            lastReconciledAtEpochMillis = clock.now().toEpochMilli(),
            balanceVersion = monsterBalanceConfig.version,
        )
        check(combatDao.insertCombatProgress(initialProgress) != -1L) {
            "Combat progress could not be initialized"
        }
        return StoredCombat(initialProgress, initialMonster, character)
    }

    private suspend fun loadOrCreateCharacter(): StoredCombatCharacter {
        val profileEntity = characterDao.getProfile()
        val stateEntity = characterDao.getCurrentState()
        val appearanceEntity = characterDao.getAppearance()
        val equippedItemsEntity = characterDao.getEquippedItems()
        check(
            profileEntity != null ||
                listOf(stateEntity, appearanceEntity, equippedItemsEntity).all { it == null },
        ) {
            "Character source state cannot exist without its profile"
        }

        val character = profileEntity?.let(CharacterMapper::toDomain)
            ?: CharacterMapper.defaultCharacter(characterBalanceConfig)
        val now = clock.now()
        reconcileExpiredStatusEffects(database, character.id, now)
        val equipmentModifiers = loadEquippedEquipmentModifiers(database, character.id)
        val statusModifiers = loadActiveStatusModifiers(database, character.id, now)
        val derivedStats = derivedStatsFor(
            character,
            characterBalanceConfig,
            equipmentModifiers,
            statusModifiers,
        )
        val currentState = stateEntity?.let(CharacterMapper::toDomain)
            ?: defaultCurrentState(
                character = character,
                derivedStats = derivedStats,
                config = characterBalanceConfig,
            )

        if (profileEntity == null) {
            characterDao.insertCharacterIfAbsent(
                profile = CharacterMapper.fromDomain(character),
                currentState = CharacterMapper.fromDomain(currentState),
                appearance = CharacterMapper.fromDomain(
                    character.id,
                    CharacterLoadoutCatalog.defaultAppearance,
                ),
                equippedItems = CharacterMapper.fromDomain(
                    character.id,
                    CharacterLoadoutCatalog.defaultEquippedItems,
                ),
            )
        } else {
            if (stateEntity == null) {
                check(characterDao.insertCurrentState(CharacterMapper.fromDomain(currentState)) != -1L) {
                    "Character current state could not be initialized"
                }
            }
            if (appearanceEntity == null) {
                check(
                    characterDao.insertAppearance(
                        CharacterMapper.fromDomain(
                            character.id,
                            CharacterLoadoutCatalog.defaultAppearance,
                        ),
                    ) != -1L,
                ) {
                    "Character appearance could not be initialized"
                }
            }
            if (equippedItemsEntity == null) {
                check(
                    characterDao.insertEquippedItems(
                        CharacterMapper.fromDomain(
                            character.id,
                            CharacterLoadoutCatalog.defaultEquippedItems,
                        ),
                    ) != -1L,
                ) {
                    "Character equipped items could not be initialized"
                }
            }
        }
        check(currentState.balanceVersion == characterBalanceConfig.version) {
            "Unknown character balance version: ${currentState.balanceVersion}"
        }
        check(currentState.currentHp <= derivedStats.maxHp) {
            "Character current HP exceeds calculated max HP"
        }
        return StoredCombatCharacter(character, currentState, equipmentModifiers, statusModifiers)
    }

    private suspend fun applyPendingPlayerAttack(
        event: PlayerAttackEventEntity,
        stored: StoredCombat,
    ): PlayerAttackCommandOutcome {
        validatePendingEvent(event)
        check(event.characterBalanceVersion == characterBalanceConfig.version) {
            "Unknown character balance version: ${event.characterBalanceVersion}"
        }
        check(event.monsterBalanceVersion == monsterBalanceConfig.version) {
            "Unknown monster balance version: ${event.monsterBalanceVersion}"
        }
        check(
            event.combatRewardVersion == 0 ||
                CombatRewardBalanceCatalog.supports(event.combatRewardVersion),
        ) {
            "Unknown combat reward version: ${event.combatRewardVersion}"
        }

        val target = stored.activeMonster
        val targetStats = statsFor(target)
        check(!target.isDefeated) { "Active monster is already defeated" }
        val before = combatSnapshot(stored)
        val attackWithMomentum = Math.multiplyExact(
            event.sourceAttack.toLong(),
            characterBalanceConfig.basisPointScale.toLong() + event.sourceMomentumBp,
        ).div(characterBalanceConfig.basisPointScale)
            .toInt()
        val sourceTaskDifficulty = CombatEntityMapper.toTaskDifficulty(event.sourceTaskDifficulty)
        val difficultyScaledAttack = TaskDifficultyCombatPolicy.scaleDamage(
            value = attackWithMomentum,
            difficulty = sourceTaskDifficulty,
            version = event.taskDifficultyBalanceVersion,
        )
        val seed = seedSource.nextSeed()
        val roll = Math.floorMod(seed, characterBalanceConfig.basisPointScale.toLong()).toInt()
        val wasCritical = CombatCalculator.rollSucceeds(
            chanceBp = event.sourceCriticalChanceBp,
            roll = roll,
            config = characterBalanceConfig,
        )
        val rawDamage = if (wasCritical) {
            Math.multiplyExact(difficultyScaledAttack.toLong(), event.sourceCriticalDamageBp.toLong())
                .div(characterBalanceConfig.basisPointScale)
                .toInt()
        } else {
            difficultyScaledAttack
        }
        val finalDamage = CombatCalculator.damageAfterDefense(
            rawDamage = rawDamage,
            defense = targetStats.defense,
            config = characterBalanceConfig,
        )
        val targetHpAfter = MonsterCombatPolicy.monsterHpAfterDamage(
            currentHp = target.currentHp,
            maxHp = targetStats.maxHp,
            finalDamage = finalDamage,
        )
        val processedAtEpochMillis = clock.now().toEpochMilli()

        val oldPlayerStats = derivedStatsFor(
            stored.character.character,
            characterBalanceConfig,
            stored.character.equipmentModifiers,
            stored.character.statusModifiers,
        )
        val reward = if (event.combatRewardVersion == 0) {
            CombatReward(0L, 0L, 0L)
        } else {
            val baseReward = CombatRewardPolicy.rewardFor(
                monsterLevel = target.level,
                monsterGrade = target.grade,
                isKill = targetHpAfter == 0,
                goldGainBonusBp = oldPlayerStats.goldGainBonusBp,
                combatRewardVersion = event.combatRewardVersion,
                monsterConfig = monsterBalanceConfig,
                characterConfig = characterBalanceConfig,
            )
            CombatReward(
                hitXpAward = TaskDifficultyCombatPolicy.scaleXp(
                    value = baseReward.hitXpAward,
                    difficulty = sourceTaskDifficulty,
                    version = event.taskDifficultyBalanceVersion,
                ),
                killBonusXpAward = TaskDifficultyCombatPolicy.scaleXp(
                    value = baseReward.killBonusXpAward,
                    difficulty = sourceTaskDifficulty,
                    version = event.taskDifficultyBalanceVersion,
                ),
                killGoldAward = baseReward.killGoldAward,
            )
        }
        val rewardedCharacter = if (event.combatRewardVersion == 0) {
            stored.character.character
        } else {
            CharacterProgressionPolicy.awardXp(
                character = stored.character.character,
                xpAward = reward.totalXpAward,
                config = characterBalanceConfig,
            ).copy(
                currentGold = Math.addExact(
                    stored.character.character.currentGold,
                    reward.killGoldAward,
                ),
            )
        }
        val newPlayerStats = derivedStatsFor(
            rewardedCharacter,
            characterBalanceConfig,
            stored.character.equipmentModifiers,
            stored.character.statusModifiers,
        )
        val rewardedCurrentState = stored.character.currentState.copy(
            currentHp = if (oldPlayerStats.maxHp == newPlayerStats.maxHp) {
                stored.character.currentState.currentHp
            } else {
                CombatCalculator.preserveHpRatio(
                    oldHp = stored.character.currentState.currentHp,
                    oldMax = oldPlayerStats.maxHp,
                    newMax = newPlayerStats.maxHp,
                    config = characterBalanceConfig,
                )
            },
            balanceVersion = characterBalanceConfig.version,
            updatedAtEpochMillis = processedAtEpochMillis,
        )
        val rewardedStoredCharacter = stored.character.copy(
            character = rewardedCharacter,
            currentState = rewardedCurrentState,
        )

        val advanced = if (targetHpAfter == 0) {
            advanceAfterVictory(
                event = event,
                stored = stored.copy(character = rewardedStoredCharacter),
                processedAtEpochMillis = processedAtEpochMillis,
            )
        } else {
            null
        }

        check(combatDao.updateMonsterCurrentHp(target.id, targetHpAfter) == 1) {
            "Target monster HP could not be updated"
        }
        check(
            combatDao.markPlayerAttackApplied(
                taskId = event.taskId,
                occurrenceDateEpochDay = event.occurrenceDateEpochDay,
                pendingStatus = CombatEventStatus.PENDING.name,
                appliedStatus = CombatEventStatus.APPLIED.name,
                targetMonsterInstanceId = target.id,
                seed = seed,
                roll = roll,
                wasCritical = wasCritical,
                rawDamage = rawDamage,
                targetDefense = targetStats.defense,
                finalDamage = finalDamage,
                targetHpBefore = target.currentHp,
                targetHpAfter = targetHpAfter,
                processedAtEpochMillis = processedAtEpochMillis,
                hitXpAward = reward.hitXpAward,
                killBonusXpAward = reward.killBonusXpAward,
                killGoldAward = reward.killGoldAward,
                rewardGradeMultiplierBp = if (event.combatRewardVersion == 0) {
                    0
                } else {
                    monsterBalanceConfig.gradeRewardMultipliersBp.getValue(target.grade)
                },
                rewardGoldGainBonusBp = if (event.combatRewardVersion == 0) {
                    0
                } else {
                    oldPlayerStats.goldGainBonusBp
                },
            ) == 1,
        ) { "Pending player attack could not be marked applied" }

        if (event.combatRewardVersion != 0) {
            characterDao.upsert(CharacterMapper.fromDomain(rewardedCharacter))
        }
        if (advanced != null) {
            characterDao.upsertCurrentState(CharacterMapper.fromDomain(advanced.currentState))
            check(
                combatDao.updateCombatProgress(
                    id = stored.progress.id,
                    stageNumber = advanced.stageNumber,
                    stageLevel = advanced.stageLevel,
                    activeMonsterInstanceId = advanced.nextMonster.id,
                    lastReconciledAtEpochMillis = stored.progress.lastReconciledAtEpochMillis,
                    balanceVersion = monsterBalanceConfig.version,
                ) == 1,
            ) { "Combat progress could not advance after victory" }
        } else if (event.combatRewardVersion != 0) {
            characterDao.upsertCurrentState(CharacterMapper.fromDomain(rewardedCurrentState))
        }

        val applied = checkNotNull(
            combatDao.getPlayerAttackEvent(event.taskId, event.occurrenceDateEpochDay),
        ) { "Applied player attack disappeared" }
        val result = CombatEntityMapper.toAppliedPlayerAttack(
            entity = applied,
            wasAlreadyApplied = false,
        )
        val afterStored = if (advanced == null) {
            stored.copy(
                activeMonster = target.copy(currentHp = targetHpAfter),
                character = rewardedStoredCharacter,
            )
        } else {
            stored.copy(
                progress = stored.progress.copy(
                    stageNumber = advanced.stageNumber,
                    stageLevel = advanced.stageLevel,
                    activeMonsterInstanceId = advanced.nextMonster.id,
                    balanceVersion = monsterBalanceConfig.version,
                ),
                activeMonster = advanced.nextMonster,
                character = rewardedStoredCharacter.copy(currentState = advanced.currentState),
            )
        }
        return PlayerAttackCommandOutcome(
            result = result,
            transition = CombatTransition.PlayerAttack(
                attack = result.attack,
                before = before,
                after = combatSnapshot(afterStored),
            ),
        )
    }

    private suspend fun advanceAfterVictory(
        event: PlayerAttackEventEntity,
        stored: StoredCombat,
        processedAtEpochMillis: Long,
    ): AdvancedCombat {
        val currentStage = stored.activeMonster.stageNumber
        val currentEncounter = stored.activeMonster.encounterNumber
        val currentEncounterCount = MonsterStagePolicy.encounterCount(
            currentStage,
            monsterBalanceConfig,
        )
        val nextStageNumber: Int
        val nextEncounterNumber: Int
        val nextStageLevel: Int
        if (currentEncounter < currentEncounterCount) {
            nextStageNumber = currentStage
            nextEncounterNumber = currentEncounter + 1
            nextStageLevel = stored.progress.stageLevel
        } else {
            check(currentStage < monsterBalanceConfig.stageCount) {
                "Cannot advance beyond the configured final stage"
            }
            nextStageNumber = Math.addExact(currentStage, 1)
            nextEncounterNumber = INITIAL_ENCOUNTER_NUMBER
            nextStageLevel = event.sourcePlayerLevel
        }
        require(nextStageLevel in monsterBalanceConfig.stageLevelMin..monsterBalanceConfig.stageLevelMax) {
            "Source player level cannot be locked as the next stage level"
        }
        val nextMonster = createMonster(
            stageNumber = nextStageNumber,
            encounterNumber = nextEncounterNumber,
            stageLevel = nextStageLevel,
        )
        val playerStats = derivedStatsFor(
            stored.character.character,
            characterBalanceConfig,
            stored.character.equipmentModifiers,
            stored.character.statusModifiers,
        )
        val recoveredHp = MonsterCombatPolicy.playerHpAfterVictoryRecovery(
            currentHp = stored.character.currentState.currentHp,
            maxHp = playerStats.maxHp,
            hpRecovery = playerStats.hpRecovery,
        )
        return AdvancedCombat(
            stageNumber = nextStageNumber,
            stageLevel = nextStageLevel,
            nextMonster = nextMonster,
            currentState = stored.character.currentState.copy(
                currentHp = recoveredHp,
                balanceVersion = characterBalanceConfig.version,
                updatedAtEpochMillis = processedAtEpochMillis,
            ),
        )
    }

    private suspend fun createMonster(
        stageNumber: Int,
        encounterNumber: Int,
        stageLevel: Int,
    ): MonsterInstance {
        require(stageNumber > 0) { "stageNumber must be positive" }
        val grade = MonsterStagePolicy.gradeFor(stageNumber, monsterBalanceConfig)
        val type = MonsterStagePolicy.typeFor(
            stageNumber = stageNumber,
            encounterNumber = encounterNumber,
            config = monsterBalanceConfig,
        )
        val definition = MonsterCatalog.definitionFor(type, monsterBalanceConfig)
        val level = MonsterStagePolicy.monsterLevel(stageLevel, grade, monsterBalanceConfig)
        val stats = MonsterStatsCalculator.calculate(
            definition = definition,
            grade = grade,
            level = level,
            config = monsterBalanceConfig,
        )
        val entity = MonsterInstanceEntity(
            definitionId = definition.id,
            grade = grade.name,
            stageNumber = stageNumber,
            encounterNumber = encounterNumber,
            level = level,
            currentHp = stats.maxHp,
            balanceVersion = monsterBalanceConfig.version,
        )
        val id = combatDao.insertMonsterInstance(entity)
        check(id != -1L) {
            "Monster slot already exists for stage $stageNumber encounter $encounterNumber"
        }
        return CombatEntityMapper.toDomain(entity.copy(id = id))
    }

    private fun definitionAndStatsFor(
        monster: MonsterInstance,
    ): Pair<MonsterDefinition, MonsterStats> {
        check(monster.balanceVersion == monsterBalanceConfig.version) {
            "Unknown monster balance version: ${monster.balanceVersion}"
        }
        val definition = monsterDefinitions[monster.definitionId]
            ?: error("Unknown monster definition: ${monster.definitionId}")
        return definition to MonsterStatsCalculator.calculate(
            monster,
            definition,
            monsterBalanceConfig,
        )
    }

    private fun statsFor(monster: MonsterInstance): MonsterStats =
        definitionAndStatsFor(monster).second

    private fun combatSnapshot(stored: StoredCombat): CombatSnapshot {
        val playerStats = derivedStatsFor(
            stored.character.character,
            characterBalanceConfig,
            stored.character.equipmentModifiers,
            stored.character.statusModifiers,
        )
        val monsterStats = statsFor(stored.activeMonster)
        validateStoredCombat(
            progress = stored.progress,
            monster = stored.activeMonster,
            currentState = stored.character.currentState,
            playerMaxHp = playerStats.maxHp,
        )
        return CombatSnapshot(
            progress = CombatEntityMapper.toDomain(stored.progress),
            activeMonster = stored.activeMonster,
            activeMonsterStats = monsterStats,
            activeMonsterSpecies = MonsterSpeciesPolicy.speciesFor(
                stageNumber = stored.activeMonster.stageNumber,
                encounterNumber = stored.activeMonster.encounterNumber,
                grade = stored.activeMonster.grade,
                encounterCount = MonsterStagePolicy.encounterCount(
                    stored.activeMonster.stageNumber,
                    monsterBalanceConfig,
                ),
                balanceVersion = stored.activeMonster.balanceVersion,
            ),
            playerCurrentHp = stored.character.currentState.currentHp,
            playerMaxHp = playerStats.maxHp,
        )
    }

    private fun validateStoredCombat(
        progress: CombatProgressEntity,
        monster: MonsterInstance,
        currentState: CharacterCurrentState,
        playerMaxHp: Int,
    ) {
        check(progress.balanceVersion == monsterBalanceConfig.version) {
            "Unknown combat progress balance version: ${progress.balanceVersion}"
        }
        check(progress.activeMonsterInstanceId == monster.id) {
            "Combat progress and active monster do not match"
        }
        check(progress.stageNumber == monster.stageNumber) {
            "Combat progress and active monster stage do not match"
        }
        check(progress.stageLevel in monsterBalanceConfig.stageLevelMin..monsterBalanceConfig.stageLevelMax) {
            "Combat progress stage level is outside the configured range"
        }
        check(currentState.balanceVersion == characterBalanceConfig.version) {
            "Unknown character balance version: ${currentState.balanceVersion}"
        }
        check(currentState.currentHp <= playerMaxHp) {
            "Character current HP exceeds calculated max HP"
        }
        validateMonsterSlot(
            monster = monster,
            stageNumber = progress.stageNumber,
            encounterNumber = monster.encounterNumber,
            stageLevel = progress.stageLevel,
        )
    }

    private fun validateMonsterSlot(
        monster: MonsterInstance,
        stageNumber: Int,
        encounterNumber: Int,
        stageLevel: Int,
    ) {
        val expectedGrade = MonsterStagePolicy.gradeFor(stageNumber, monsterBalanceConfig)
        val expectedType = MonsterStagePolicy.typeFor(
            stageNumber,
            encounterNumber,
            monsterBalanceConfig,
        )
        val expectedDefinition = MonsterCatalog.definitionFor(expectedType, monsterBalanceConfig)
        val expectedLevel = MonsterStagePolicy.monsterLevel(
            stageLevel = stageLevel,
            grade = expectedGrade,
            config = monsterBalanceConfig,
        )
        check(monster.stageNumber == stageNumber && monster.encounterNumber == encounterNumber) {
            "Monster is stored in the wrong stage slot"
        }
        check(monster.grade == expectedGrade) { "Monster grade does not match its encounter slot" }
        check(monster.definitionId == expectedDefinition.id) {
            "Monster definition does not match its encounter slot"
        }
        check(monster.level == expectedLevel) { "Monster level does not match the locked stage level" }
        statsFor(monster)
    }

    private fun validatePendingEvent(event: PlayerAttackEventEntity) {
        require(event.sourcePlayerLevel in characterBalanceConfig.levelMin..characterBalanceConfig.levelMax) {
            "sourcePlayerLevel is outside the configured range"
        }
        require(event.sourceAttack in characterBalanceConfig.attackMin..characterBalanceConfig.attackMax) {
            "sourceAttack is outside the configured range"
        }
        require(
            event.sourceCriticalChanceBp in
                characterBalanceConfig.criticalChanceMinBp..characterBalanceConfig.criticalChanceMaxBp,
        ) { "sourceCriticalChanceBp is outside the configured range" }
        require(
            event.sourceCriticalDamageBp in
                characterBalanceConfig.criticalDamageMinBp..characterBalanceConfig.criticalDamageMaxBp,
        ) { "sourceCriticalDamageBp is outside the configured range" }
        require(event.sourceMomentumBp in 0..characterBalanceConfig.basisPointScale) {
            "sourceMomentumBp is outside the configured range"
        }
        check(
            listOf(
                event.targetMonsterInstanceId,
                event.seed,
                event.roll,
                event.wasCritical,
                event.rawDamage,
                event.targetDefense,
                event.finalDamage,
                event.targetHpBefore,
                event.targetHpAfter,
                event.processedAtEpochMillis,
            ).all { it == null },
        ) { "Pending player attack already contains applied result fields" }
        check(
            event.hitXpAward == 0L &&
                event.killBonusXpAward == 0L &&
                event.killGoldAward == 0L &&
                event.rewardGradeMultiplierBp == 0 &&
                event.rewardGoldGainBonusBp == 0,
        ) { "Pending player attack already contains reward result fields" }
    }

    private companion object {
        const val INITIAL_STAGE_NUMBER = 1
        const val INITIAL_ENCOUNTER_NUMBER = 1
        const val MONSTER_ATTACK_DAMAGE_LIMIT = 3

        fun severeInjuryEventIdPrefix(
            attackEventKey: CombatEventKey,
            effectRevision: Long,
        ): String =
            "monster-attack:${attackEventKey.taskId}:${attackEventKey.occurrenceDateEpochDay}:" +
                "severe-injury:$effectRevision"
    }
}

private data class MissedReconciliationCounts(
    val applied: Int = 0,
    val skipped: Int = 0,
    val transitions: List<CombatTransition.MonsterAttack> = emptyList(),
)

private data class PlayerAttackCommandOutcome(
    val result: PlayerAttackResult,
    val transition: CombatTransition.PlayerAttack? = null,
)

private data class MonsterAttackCommandOutcome(
    val result: MonsterAttackResult,
    val transition: CombatTransition.MonsterAttack? = null,
    val storedAfter: StoredCombat? = null,
)

private data class SevereInjuryDefeatOutcome(
    val effect: CharacterStatusEffectEntity,
    val statusModifiers: List<TemporaryStatEffect>,
    val recoveredHp: Int,
    val lifecycleEvents: List<CombatLifecycleEvent>,
)

private data class StoredCombatCharacter(
    val character: PlayerCharacter,
    val currentState: CharacterCurrentState,
    val equipmentModifiers: List<EquipmentStatModifier>,
    val statusModifiers: List<TemporaryStatEffect>,
)

private data class StoredCombat(
    val progress: CombatProgressEntity,
    val activeMonster: MonsterInstance,
    val character: StoredCombatCharacter,
)

private data class AdvancedCombat(
    val stageNumber: Int,
    val stageLevel: Int,
    val nextMonster: MonsterInstance,
    val currentState: CharacterCurrentState,
)

private val CombatObservationTables = arrayOf(
    "combat_progress",
    "monster_instances",
    "character_profile",
    "character_current_state",
    "equipment",
    "equipment_modifiers",
    "owned_equipment",
    "character_equipment",
    "character_status_effects",
)
