package com.todoquest.data.repository

import androidx.room.withTransaction
import com.todoquest.core.AppClock
import com.todoquest.data.mapper.CharacterMapper
import com.todoquest.data.local.TodoQuestDatabase
import com.todoquest.domain.model.AllocateStatPointsResult
import com.todoquest.domain.model.CharacterAppearance
import com.todoquest.domain.model.CharacterCurrentState
import com.todoquest.domain.model.CharacterLoadoutCatalog
import com.todoquest.domain.model.CharacterLoadoutUpdateResult
import com.todoquest.domain.model.CharacterSnapshot
import com.todoquest.domain.model.CharacterStatBalanceConfig
import com.todoquest.domain.model.DerivedStats
import com.todoquest.domain.model.EquippedItems
import com.todoquest.domain.model.EquipmentStatModifier
import com.todoquest.domain.model.PlayerCharacter
import com.todoquest.domain.model.StatCalculationInput
import com.todoquest.domain.model.StatAllocation
import com.todoquest.domain.model.StatResetResult
import com.todoquest.domain.model.TemporaryStatEffect
import com.todoquest.domain.repository.CharacterRepository
import com.todoquest.domain.usecase.CharacterProgressionPolicy
import com.todoquest.domain.usecase.CombatCalculator
import com.todoquest.domain.usecase.DerivedStatsCalculator
import com.todoquest.domain.usecase.StatAllocationPolicy
import com.todoquest.domain.usecase.StatResetPolicy
import com.todoquest.domain.usecase.StreakPolicy
import com.todoquest.domain.usecase.StatusEffectPolicy
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart

class RoomCharacterRepository(
    private val database: TodoQuestDatabase,
    private val clock: AppClock,
    private val balanceConfig: CharacterStatBalanceConfig = CharacterStatBalanceConfig(),
) : CharacterRepository {
    private val characterDao = database.characterProfileDao()
    private val rewardLedgerDao = database.rewardLedgerDao()

    override fun observeCharacter(referenceDate: LocalDate): Flow<CharacterSnapshot> = combine(
        combine(
            characterDao.observeProfile(),
            characterDao.observeCurrentState(),
            rewardLedgerDao.observeOnTimeOccurrenceDatesThrough(referenceDate.toEpochDay()),
            characterDao.observeAppearance(),
            characterDao.observeEquippedItems(),
        ) { profile, currentState, onTimeEpochDays, appearance, equippedItems ->
            ObservedCharacterSources(
                profile = profile,
                currentState = currentState,
                onTimeEpochDays = onTimeEpochDays,
                appearance = appearance,
                equippedItems = equippedItems,
            )
        },
        observeEquippedEquipment(database, CharacterMapper.DEFAULT_CHARACTER_ID),
        database.statusEffectDao().observeActiveStatusEffects(CharacterMapper.DEFAULT_CHARACTER_ID),
    ) { sources, equippedEquipment, statusEffectEntities ->
        val now = clock.now()
        val character = sources.profile?.let(CharacterMapper::toDomain)
            ?: CharacterMapper.defaultCharacter(balanceConfig)
        val appearance = sources.appearance?.let(CharacterMapper::toDomain)
            ?: CharacterLoadoutCatalog.defaultAppearance
        val fallbackItems = sources.equippedItems?.let(CharacterMapper::toDomain)
            ?: CharacterLoadoutCatalog.defaultEquippedItems
        val equippedItems = projectEquippedCharacterLayers(fallbackItems, equippedEquipment)
        val derivedStats = derivedStatsFor(
            character = character,
            config = balanceConfig,
            equipmentModifiers = equippedEquipment.equipmentModifiers(),
            temporaryEffects = StatusEffectPolicy.temporaryEffectsFor(
                statusEffects = statusEffectEntities.map { it.toDomain() },
                at = now,
            ),
        )
        val currentState = sources.currentState?.let(CharacterMapper::toDomain)
            ?: defaultCurrentState(character, derivedStats, balanceConfig)
        val streak = StreakPolicy.calculate(
            onTimeOccurrenceDates = sources.onTimeEpochDays.map(LocalDate::ofEpochDay),
            referenceDate = referenceDate,
            config = balanceConfig,
        )
        characterSnapshot(
            character,
            appearance,
            equippedItems,
            currentState,
            derivedStats,
            streak,
            balanceConfig,
        )
    }.onStart {
        RoomStatusEffectRepository(database, clock)
            .reconcileExpired(CharacterMapper.DEFAULT_CHARACTER_ID)
    }

    override suspend fun updateAppearance(
        appearance: CharacterAppearance,
    ): CharacterLoadoutUpdateResult {
        if (!CharacterLoadoutCatalog.contains(appearance)) {
            return CharacterLoadoutUpdateResult.InvalidItem
        }
        return database.withTransaction {
            val stored = loadOrCreateCharacter()
            characterDao.upsertAppearance(CharacterMapper.fromDomain(stored.character.id, appearance))
            CharacterLoadoutUpdateResult.Success
        }
    }

    override suspend fun updateEquippedItems(
        items: EquippedItems,
    ): CharacterLoadoutUpdateResult {
        if (!CharacterLoadoutCatalog.contains(items)) {
            return CharacterLoadoutUpdateResult.InvalidItem
        }
        return database.withTransaction {
            val stored = loadOrCreateCharacter()
            characterDao.upsertEquippedItems(CharacterMapper.fromDomain(stored.character.id, items))
            CharacterLoadoutUpdateResult.Success
        }
    }

    override suspend fun allocateStatPoints(
        allocation: StatAllocation,
    ): AllocateStatPointsResult =
        database.withTransaction {
            val stored = loadOrCreateCharacter()
            val result = StatAllocationPolicy.evaluate(
                character = stored.character,
                allocation = allocation,
                config = balanceConfig,
            )
            if (result !is AllocateStatPointsResult.Success) {
                return@withTransaction result
            }

            val equipmentModifiers = loadEquippedEquipmentModifiers(
                database,
                stored.character.id,
            )
            val oldStats = derivedStatsFor(
                stored.character,
                balanceConfig,
                equipmentModifiers,
                stored.statusModifiers,
            )
            val updatedCharacter = CharacterProgressionPolicy.allocate(
                character = stored.character,
                allocation = allocation,
                config = balanceConfig,
            )
            val newStats = derivedStatsFor(
                updatedCharacter,
                balanceConfig,
                equipmentModifiers,
                stored.statusModifiers,
            )
            persistCharacterAndCurrentState(
                character = updatedCharacter,
                currentState = stored.currentState.withPreservedHp(oldStats, newStats),
            )
            result
        }

    override suspend fun resetStats(): StatResetResult = database.withTransaction {
        val stored = loadOrCreateCharacter()
        val investedPoints = with(stored.character.baseStats) {
            strength + vitality + focus + willpower - 4 * balanceConfig.initialBaseStat
        }
        if (investedPoints == 0) {
            return@withTransaction StatResetResult.NothingToReset
        }

        val level = CharacterProgressionPolicy.levelFor(stored.character.totalXp, balanceConfig)
        val requiredGold = if (stored.character.hasUsedFreeStatReset) {
            StatResetPolicy.resetCost(level, balanceConfig)
        } else {
            0L
        }
        if (stored.character.currentGold < requiredGold) {
            return@withTransaction StatResetResult.InsufficientGold(
                requiredGold = requiredGold,
                availableGold = stored.character.currentGold,
            )
        }

        val equipmentModifiers = loadEquippedEquipmentModifiers(database, stored.character.id)
        val oldStats = derivedStatsFor(
            stored.character,
            balanceConfig,
            equipmentModifiers,
            stored.statusModifiers,
        )
        val reset = StatResetPolicy.reset(stored.character, balanceConfig)
        val newStats = derivedStatsFor(
            reset.character,
            balanceConfig,
            equipmentModifiers,
            stored.statusModifiers,
        )
        persistCharacterAndCurrentState(
            character = reset.character,
            currentState = stored.currentState.withPreservedHp(oldStats, newStats),
        )
        StatResetResult.Success(goldSpent = reset.goldSpent)
    }

    private suspend fun loadOrCreateCharacter(): StoredCharacter {
        val profileEntity = characterDao.getProfile()
        val stateEntity = characterDao.getCurrentState()
        val appearanceEntity = characterDao.getAppearance()
        val equippedItemsEntity = characterDao.getEquippedItems()
        check(
            profileEntity != null ||
                listOf(stateEntity, appearanceEntity, equippedItemsEntity).all { it == null },
        ) {
            "character source state cannot exist without its profile"
        }

        val character = profileEntity?.let(CharacterMapper::toDomain)
            ?: CharacterMapper.defaultCharacter(balanceConfig)
        val now = clock.now()
        reconcileExpiredStatusEffects(database, character.id, now)
        val equipmentModifiers = loadEquippedEquipmentModifiers(database, character.id)
        val statusModifiers = loadActiveStatusModifiers(database, character.id, now)
        val derivedStats = derivedStatsFor(
            character,
            balanceConfig,
            equipmentModifiers,
            statusModifiers,
        )
        val currentState = stateEntity?.let(CharacterMapper::toDomain)
            ?: defaultCurrentState(character, derivedStats, balanceConfig)
        val appearance = appearanceEntity?.let(CharacterMapper::toDomain)
            ?: CharacterLoadoutCatalog.defaultAppearance
        val equippedItems = equippedItemsEntity?.let(CharacterMapper::toDomain)
            ?: CharacterLoadoutCatalog.defaultEquippedItems

        if (profileEntity == null) {
            characterDao.insertCharacterIfAbsent(
                profile = CharacterMapper.fromDomain(character),
                currentState = CharacterMapper.fromDomain(currentState),
                appearance = CharacterMapper.fromDomain(character.id, appearance),
                equippedItems = CharacterMapper.fromDomain(character.id, equippedItems),
            )
        } else {
            if (stateEntity == null) {
                check(characterDao.insertCurrentState(CharacterMapper.fromDomain(currentState)) != -1L) {
                    "character current state could not be initialized"
                }
            }
            if (appearanceEntity == null) {
                check(
                    characterDao.insertAppearance(
                        CharacterMapper.fromDomain(character.id, appearance),
                    ) != -1L,
                ) {
                    "character appearance could not be initialized"
                }
            }
            if (equippedItemsEntity == null) {
                check(
                    characterDao.insertEquippedItems(
                        CharacterMapper.fromDomain(character.id, equippedItems),
                    ) != -1L,
                ) {
                    "character equipped items could not be initialized"
                }
            }
        }
        return StoredCharacter(character, currentState, statusModifiers)
    }

    private suspend fun persistCharacterAndCurrentState(
        character: PlayerCharacter,
        currentState: CharacterCurrentState,
    ) {
        characterDao.upsert(CharacterMapper.fromDomain(character))
        characterDao.upsertCurrentState(CharacterMapper.fromDomain(currentState))
    }

    private fun CharacterCurrentState.withPreservedHp(
        oldStats: DerivedStats,
        newStats: DerivedStats,
    ): CharacterCurrentState = copy(
        currentHp = if (oldStats.maxHp == newStats.maxHp) {
            currentHp
        } else {
            CombatCalculator.preserveHpRatio(
                oldHp = currentHp,
                oldMax = oldStats.maxHp,
                newMax = newStats.maxHp,
                config = balanceConfig,
            )
        },
        balanceVersion = balanceConfig.version,
        updatedAtEpochMillis = clock.now().toEpochMilli(),
    )
}

internal data class StoredCharacter(
    val character: PlayerCharacter,
    val currentState: CharacterCurrentState,
    val statusModifiers: List<TemporaryStatEffect>,
)

internal fun derivedStatsFor(
    character: PlayerCharacter,
    config: CharacterStatBalanceConfig,
    equipmentModifiers: List<EquipmentStatModifier>,
    temporaryEffects: List<TemporaryStatEffect> = emptyList(),
): DerivedStats = DerivedStatsCalculator.calculate(
    input = StatCalculationInput(
        level = CharacterProgressionPolicy.levelFor(character.totalXp, config),
        baseStats = character.baseStats,
        equipmentModifiers = equipmentModifiers,
        temporaryEffects = temporaryEffects,
    ),
    config = config,
)

private data class ObservedCharacterSources(
    val profile: com.todoquest.data.local.CharacterProfileEntity?,
    val currentState: com.todoquest.data.local.CharacterCurrentStateEntity?,
    val onTimeEpochDays: List<Long>,
    val appearance: com.todoquest.data.local.CharacterAppearanceEntity?,
    val equippedItems: com.todoquest.data.local.CharacterEquippedItemsEntity?,
)

internal fun defaultCurrentState(
    character: PlayerCharacter,
    derivedStats: DerivedStats,
    config: CharacterStatBalanceConfig,
    updatedAtEpochMillis: Long = 0L,
): CharacterCurrentState = CharacterCurrentState(
    characterId = character.id,
    currentHp = derivedStats.maxHp,
    balanceVersion = config.version,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

private fun characterSnapshot(
    character: PlayerCharacter,
    appearance: CharacterAppearance,
    equippedItems: EquippedItems,
    currentState: CharacterCurrentState,
    derivedStats: DerivedStats,
    streak: StreakPolicy.Result,
    config: CharacterStatBalanceConfig,
): CharacterSnapshot {
    val level = CharacterProgressionPolicy.levelFor(character.totalXp, config)
    val isMaxLevel = level == config.levelMax
    return CharacterSnapshot(
        character = character,
        appearance = appearance,
        equippedItems = equippedItems,
        level = level,
        xpIntoCurrentLevel = if (isMaxLevel) {
            config.xpPerLevel
        } else {
            character.totalXp % config.xpPerLevel
        },
        xpRequiredForNextLevel = config.xpPerLevel,
        isMaxLevel = isMaxLevel,
        currentState = currentState,
        derivedStats = derivedStats,
        currentStreak = streak.streakDays,
        momentumBonusBp = streak.momentumBonusBp,
    )
}
