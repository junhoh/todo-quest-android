package com.todoquest.data.repository

import androidx.room.withTransaction
import com.todoquest.core.AppClock
import com.todoquest.data.local.CharacterEquipmentEntity
import com.todoquest.data.local.EquipmentCatalogSeeder
import com.todoquest.data.local.EquipmentDao
import com.todoquest.data.local.EquipmentEntity
import com.todoquest.data.local.EquipmentModifierEntity
import com.todoquest.data.local.OwnedEquipmentEntity
import com.todoquest.data.local.TodoQuestDatabase
import com.todoquest.data.mapper.CharacterMapper
import com.todoquest.data.mapper.EquipmentMapper
import com.todoquest.domain.model.CharacterLoadoutCatalog
import com.todoquest.domain.model.CharacterStatBalanceConfig
import com.todoquest.domain.model.EquipOwnedEquipmentResult
import com.todoquest.domain.model.Equipment
import com.todoquest.domain.model.EquipmentInventorySnapshot
import com.todoquest.domain.model.EquipmentSlot
import com.todoquest.domain.model.EquipmentStatModifier
import com.todoquest.domain.model.EquipmentStoreSnapshot
import com.todoquest.domain.model.EquipmentUnequipAppearancePolicy
import com.todoquest.domain.model.EquippedItems
import com.todoquest.domain.model.EquippedEquipment
import com.todoquest.domain.model.OwnedEquipment
import com.todoquest.domain.model.PlayerCharacter
import com.todoquest.domain.model.PurchaseEligibility
import com.todoquest.domain.model.PurchaseEquipmentResult
import com.todoquest.domain.model.StatCalculationInput
import com.todoquest.domain.model.TemporaryStatEffect
import com.todoquest.domain.model.UnequipEquipmentResult
import com.todoquest.domain.repository.EquipmentRepository
import com.todoquest.domain.usecase.CharacterProgressionPolicy
import com.todoquest.domain.usecase.CombatCalculator
import com.todoquest.domain.usecase.DerivedStatsCalculator
import com.todoquest.domain.usecase.EquipmentPreviewProjectionCalculator
import com.todoquest.domain.usecase.EquipmentTypeSlotPolicy
import com.todoquest.domain.usecase.PurchaseEquipmentPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class EquipmentRepositoryDataException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

class RoomEquipmentRepository(
    private val database: TodoQuestDatabase,
    private val clock: AppClock,
    private val balanceConfig: CharacterStatBalanceConfig = CharacterStatBalanceConfig(),
) : EquipmentRepository {
    private val characterDao = database.characterProfileDao()
    private val equipmentDao = database.equipmentDao()

    override fun observeStore(characterId: Long): Flow<EquipmentStoreSnapshot> =
        database.invalidationTracker
            .createFlow(*StoreObservationTables)
            .onStart { prepareSources(characterId) }
            .map {
                database.withTransaction {
                    loadStoreSnapshot(characterId)
                }
            }

    override fun observeInventory(characterId: Long): Flow<EquipmentInventorySnapshot> = flow {
        prepareSources(characterId)
        emitAll(
            combine(
                equipmentDao.observeAllEquipment(),
                equipmentDao.observeAllEquipmentModifiers(),
                equipmentDao.observeOwnedEquipment(characterId),
                equipmentDao.observeCharacterEquipment(characterId),
            ) { definitionEntities, modifierEntities, ownedEntities, equippedEntities ->
                val mapped = mapEquipmentSources(
                    definitionEntities = definitionEntities,
                    modifierEntities = modifierEntities,
                    ownedEntities = ownedEntities,
                    equippedEntities = equippedEntities,
                )
                EquipmentInventorySnapshot(
                    characterId = characterId,
                    ownedEquipment = mapped.owned,
                    equippedBySlot = mapped.equipped.associateBy(EquippedEquipment::slot),
                )
            },
        )
    }

    override suspend fun purchaseEquipment(
        characterId: Long,
        equipmentId: Long,
    ): PurchaseEquipmentResult = database.withTransaction {
        EquipmentCatalogSeeder.seed(equipmentDao)
        val storedCharacter = loadOrCreateCharacter(characterId)
        val definition = loadDefinition(equipmentId)
        val isOwned = equipmentDao.findOwnedEquipment(characterId, equipmentId) != null
        when (
            val eligibility = PurchaseEquipmentPolicy.evaluate(
                equipment = definition,
                characterLevel = CharacterProgressionPolicy.levelFor(
                    storedCharacter.character.totalXp,
                    balanceConfig,
                ),
                availableGold = storedCharacter.character.currentGold,
                isOwned = isOwned,
            )
        ) {
            PurchaseEligibility.Eligible -> {
                val remainingGold = Math.subtractExact(
                    storedCharacter.character.currentGold,
                    definition.price,
                )
                check(remainingGold >= 0L) { "equipment purchase cannot create negative gold" }
                check(characterDao.updateCurrentGold(characterId, remainingGold) == 1) {
                    "character gold could not be updated"
                }
                val ownedEquipmentId = equipmentDao.insertOwnedEquipment(
                    OwnedEquipmentEntity(
                        characterId = characterId,
                        equipmentId = definition.id,
                        acquiredAtEpochMillis = clock.now().toEpochMilli(),
                    ),
                )
                check(ownedEquipmentId != -1L) {
                    "equipment ownership changed during the purchase transaction"
                }
                PurchaseEquipmentResult.Success(
                    ownedEquipmentId = ownedEquipmentId,
                    equipmentId = definition.id,
                    equipmentNameKey = definition.nameKey,
                    type = definition.type,
                    slot = definition.slot,
                    remainingGold = remainingGold,
                )
            }

            is PurchaseEligibility.Unavailable -> PurchaseEquipmentResult.Unavailable(eligibility)
        }
    }

    override suspend fun equipOwnedEquipment(
        characterId: Long,
        ownedEquipmentId: Long,
        targetSlot: EquipmentSlot,
    ): EquipOwnedEquipmentResult = database.withTransaction {
        EquipmentCatalogSeeder.seed(equipmentDao)
        val storedCharacter = loadOrCreateCharacter(characterId)
        val ownedEntity = equipmentDao.getOwnedEquipmentById(ownedEquipmentId)
            ?: return@withTransaction EquipOwnedEquipmentResult.OwnedEquipmentNotFound(
                characterId = characterId,
                ownedEquipmentId = ownedEquipmentId,
            )
        if (ownedEntity.characterId != characterId) {
            return@withTransaction EquipOwnedEquipmentResult.NotOwnedByCharacter(
                characterId = characterId,
                ownedEquipmentId = ownedEquipmentId,
                ownerCharacterId = ownedEntity.characterId,
            )
        }

        val definition = loadDefinition(ownedEntity.equipmentId)
        if (
            !EquipmentTypeSlotPolicy.isCompatible(definition.type, definition.slot) ||
            definition.slot != targetSlot
        ) {
            return@withTransaction EquipOwnedEquipmentResult.SlotMismatch(
                ownedEquipmentId = ownedEquipmentId,
                type = definition.type,
                equipmentSlot = definition.slot,
                targetSlot = targetSlot,
            )
        }

        val oldModifiers = loadEquippedModifiers(characterId)
        val oldStats = derivedStats(
            character = storedCharacter.character,
            equipmentModifiers = oldModifiers,
            statusModifiers = storedCharacter.statusModifiers,
        )
        equipmentDao.upsertCharacterEquipment(
            CharacterEquipmentEntity(
                characterId = characterId,
                slot = targetSlot.name,
                ownedEquipmentId = ownedEquipmentId,
            ),
        )
        val newModifiers = loadEquippedModifiers(characterId)
        val newStats = derivedStats(
            character = storedCharacter.character,
            equipmentModifiers = newModifiers,
            statusModifiers = storedCharacter.statusModifiers,
        )
        val newHp = if (oldStats.maxHp == newStats.maxHp) {
            storedCharacter.currentState.currentHp
        } else {
            CombatCalculator.preserveHpRatio(
                oldHp = storedCharacter.currentState.currentHp,
                oldMax = oldStats.maxHp,
                newMax = newStats.maxHp,
                config = balanceConfig,
            )
        }
        characterDao.upsertCurrentState(
            CharacterMapper.fromDomain(
                storedCharacter.currentState.copy(
                    currentHp = newHp,
                    balanceVersion = balanceConfig.version,
                    updatedAtEpochMillis = clock.now().toEpochMilli(),
                ),
            ),
        )
        EquipOwnedEquipmentResult.Success(
            ownedEquipmentId = ownedEquipmentId,
            equipmentId = definition.id,
            slot = targetSlot,
        )
    }

    override suspend fun unequipEquipment(
        characterId: Long,
        targetSlot: EquipmentSlot,
    ): UnequipEquipmentResult = database.withTransaction {
        EquipmentCatalogSeeder.seed(equipmentDao)
        val storedCharacter = loadOrCreateCharacter(characterId)
        val equippedEntity = equipmentDao.getCharacterEquipmentAtSlot(
            characterId = characterId,
            slot = targetSlot.name,
        ) ?: return@withTransaction UnequipEquipmentResult.AlreadyEmpty(targetSlot)
        val ownedEntity = equipmentDao.getOwnedEquipmentById(equippedEntity.ownedEquipmentId)
            ?: throw EquipmentRepositoryDataException(
                "Missing owned equipment ${equippedEntity.ownedEquipmentId}",
            )
        check(ownedEntity.characterId == characterId) {
            "equipped item belongs to another character"
        }
        val definition = loadDefinition(ownedEntity.equipmentId)
        check(definition.slot == targetSlot) {
            "equipped item does not match the stored slot"
        }
        val currentFallback = characterDao.getEquippedItems(characterId)
            ?.let(CharacterMapper::toDomain)
            ?: CharacterLoadoutCatalog.defaultEquippedItems
        val oldStats = derivedStats(
            character = storedCharacter.character,
            equipmentModifiers = loadEquippedModifiers(characterId),
            statusModifiers = storedCharacter.statusModifiers,
        )

        check(
            equipmentDao.deleteCharacterEquipmentAtSlot(
                characterId = characterId,
                slot = targetSlot.name,
            ) == 1,
        ) {
            "equipped item changed during the unequip transaction"
        }
        characterDao.upsertEquippedItems(
            CharacterMapper.fromDomain(
                characterId = characterId,
                items = EquipmentUnequipAppearancePolicy.clearSlot(
                    current = currentFallback,
                    slot = targetSlot,
                ),
            ),
        )
        val newStats = derivedStats(
            character = storedCharacter.character,
            equipmentModifiers = loadEquippedModifiers(characterId),
            statusModifiers = storedCharacter.statusModifiers,
        )
        if (oldStats.maxHp != newStats.maxHp) {
            val newHp = CombatCalculator.preserveHpRatio(
                oldHp = storedCharacter.currentState.currentHp,
                oldMax = oldStats.maxHp,
                newMax = newStats.maxHp,
                config = balanceConfig,
            )
            characterDao.upsertCurrentState(
                CharacterMapper.fromDomain(
                    storedCharacter.currentState.copy(
                        currentHp = newHp,
                        balanceVersion = balanceConfig.version,
                        updatedAtEpochMillis = clock.now().toEpochMilli(),
                    ),
                ),
            )
        }
        UnequipEquipmentResult.Success(
            ownedEquipmentId = ownedEntity.id,
            equipmentId = definition.id,
            slot = targetSlot,
        )
    }

    private suspend fun prepareSources(characterId: Long) {
        database.withTransaction {
            EquipmentCatalogSeeder.seed(equipmentDao)
            loadOrCreateCharacter(characterId)
        }
    }

    private suspend fun loadStoreSnapshot(characterId: Long): EquipmentStoreSnapshot {
        val profile = characterDao.getProfile(characterId)?.let(CharacterMapper::toDomain)
            ?: throw EquipmentRepositoryDataException("Missing character profile $characterId")
        val appearance = characterDao.getAppearance(characterId)?.let(CharacterMapper::toDomain)
            ?: CharacterLoadoutCatalog.defaultAppearance
        val fallbackItems = characterDao.getEquippedItems(characterId)?.let(CharacterMapper::toDomain)
            ?: CharacterLoadoutCatalog.defaultEquippedItems
        val mapped = mapEquipmentSources(
            definitionEntities = equipmentDao.getAllEquipment(),
            modifierEntities = equipmentDao.getAllEquipmentModifiers(),
            ownedEntities = equipmentDao.getOwnedEquipment(characterId),
            equippedEntities = equipmentDao.getCharacterEquipment(characterId),
        )
        val statusModifiers = loadActiveStatusModifiers(database, characterId, clock.now())
        val characterLevel = CharacterProgressionPolicy.levelFor(profile.totalXp, balanceConfig)
        val ownedEquipmentByEquipmentId = mapped.owned.associateBy(OwnedEquipment::equipmentId)
        if (ownedEquipmentByEquipmentId.size != mapped.owned.size) {
            throw EquipmentRepositoryDataException(
                "Duplicate owned equipment for character $characterId",
            )
        }
        val equippedBySlot = mapped.equipped.associateBy(EquippedEquipment::slot)
        if (equippedBySlot.size != mapped.equipped.size) {
            throw EquipmentRepositoryDataException(
                "Duplicate equipped slot for character $characterId",
            )
        }
        mapped.equipped.forEach { equipped ->
            if (
                ownedEquipmentByEquipmentId[equipped.ownedEquipment.equipmentId] !==
                equipped.ownedEquipment
            ) {
                throw EquipmentRepositoryDataException(
                    "Equipped item does not reference the projected owned equipment",
                )
            }
        }
        val renderedEquippedItems = projectEquippedCharacterLayers(fallbackItems, mapped.equipped)
        val statCalculationInput = StatCalculationInput(
            level = characterLevel,
            baseStats = profile.baseStats,
            equipmentModifiers = mapped.equipped.equipmentModifiers(),
            temporaryEffects = statusModifiers,
        )
        return EquipmentStoreSnapshot(
            characterId = characterId,
            currentGold = profile.currentGold,
            characterLevel = characterLevel,
            equipment = mapped.definitions,
            ownedEquipmentIds = ownedEquipmentByEquipmentId.keys,
            equippedBySlot = equippedBySlot,
            appearance = appearance,
            renderedEquippedItems = renderedEquippedItems,
            derivedStats = DerivedStatsCalculator.calculate(statCalculationInput, balanceConfig),
            previewByEquipmentId = mapped.definitions.associate { definition ->
                definition.id to EquipmentPreviewProjectionCalculator.calculate(
                    candidate = definition,
                    equippedBySlot = equippedBySlot,
                    renderedEquippedItems = renderedEquippedItems,
                    statCalculationInput = statCalculationInput,
                    config = balanceConfig,
                )
            },
            ownedEquipmentByEquipmentId = ownedEquipmentByEquipmentId,
        )
    }

    private suspend fun loadOrCreateCharacter(characterId: Long): StoredCharacter {
        val profileEntity = characterDao.getProfile(characterId)
        if (profileEntity == null && characterId != CharacterMapper.DEFAULT_CHARACTER_ID) {
            throw EquipmentRepositoryDataException("Missing character profile $characterId")
        }

        val character = profileEntity?.let(CharacterMapper::toDomain)
            ?: CharacterMapper.defaultCharacter(balanceConfig)
        val now = clock.now()
        reconcileExpiredStatusEffects(database, character.id, now)
        val statusModifiers = loadActiveStatusModifiers(database, character.id, now)
        val currentStateEntity = characterDao.getCurrentState(characterId)
        val currentState = currentStateEntity?.let(CharacterMapper::toDomain)
            ?: defaultCurrentState(
                character = character,
                derivedStats = derivedStats(character, emptyList(), statusModifiers),
                config = balanceConfig,
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
        } else if (currentStateEntity == null) {
            check(characterDao.insertCurrentState(CharacterMapper.fromDomain(currentState)) != -1L) {
                "character current state could not be initialized"
            }
        }
        return StoredCharacter(character, currentState, statusModifiers)
    }

    private suspend fun loadDefinition(equipmentId: Long): Equipment {
        val entity = equipmentDao.getEquipment(equipmentId)
            ?: throw EquipmentRepositoryDataException("Missing equipment definition $equipmentId")
        return EquipmentMapper.definition(
            entity = entity,
            modifierEntities = equipmentDao.getEquipmentModifiers(equipmentId),
        )
    }

    private suspend fun loadEquippedModifiers(characterId: Long): List<EquipmentStatModifier> =
        loadEquippedEquipment(database, characterId).equipmentModifiers()

    private fun derivedStats(
        character: PlayerCharacter,
        equipmentModifiers: List<EquipmentStatModifier>,
        statusModifiers: List<TemporaryStatEffect>,
    ) = derivedStatsFor(character, balanceConfig, equipmentModifiers, statusModifiers)

}

internal fun observeEquippedEquipment(
    database: TodoQuestDatabase,
    characterId: Long,
): Flow<List<EquippedEquipment>> {
    val equipmentDao = database.equipmentDao()
    return combine(
        equipmentDao.observeAllEquipment(),
        equipmentDao.observeAllEquipmentModifiers(),
        equipmentDao.observeOwnedEquipment(characterId),
        equipmentDao.observeCharacterEquipment(characterId),
    ) { definitions, modifiers, owned, equipped ->
        mapEquippedEquipmentSources(definitions, modifiers, owned, equipped)
    }
}

internal suspend fun loadEquippedEquipment(
    database: TodoQuestDatabase,
    characterId: Long,
): List<EquippedEquipment> {
    val equipmentDao = database.equipmentDao()
    return equipmentDao.getCharacterEquipment(characterId).map { equippedEntity ->
        val ownedEntity = equipmentDao.getOwnedEquipmentById(equippedEntity.ownedEquipmentId)
            ?: throw EquipmentRepositoryDataException(
                "Missing owned equipment ${equippedEntity.ownedEquipmentId}",
            )
        val definitionEntity = equipmentDao.getEquipment(ownedEntity.equipmentId)
            ?: throw EquipmentRepositoryDataException(
                "Missing equipment definition ${ownedEntity.equipmentId}",
            )
        val definition = EquipmentMapper.definition(
            definitionEntity,
            equipmentDao.getEquipmentModifiers(definitionEntity.id),
        )
        val owned = EquipmentMapper.owned(ownedEntity, mapOf(definition.id to definition))
        EquipmentMapper.equipped(equippedEntity, mapOf(owned.id to owned))
    }
}

internal suspend fun loadEquippedEquipmentModifiers(
    database: TodoQuestDatabase,
    characterId: Long,
): List<EquipmentStatModifier> = loadEquippedEquipment(database, characterId).equipmentModifiers()

internal fun List<EquippedEquipment>.equipmentModifiers(): List<EquipmentStatModifier> =
    flatMap { it.ownedEquipment.equipment.modifiers }

internal fun projectEquippedCharacterLayers(
    fallback: EquippedItems,
    equippedEquipment: List<EquippedEquipment>,
): EquippedItems = equippedEquipment
    .sortedBy { it.slot.ordinal }
    .fold(fallback) { current, equipped ->
        val layerId = equipped.ownedEquipment.equipment.layerKey ?: return@fold current
        val candidate = when (equipped.slot) {
            EquipmentSlot.HELMET -> current.copy(headId = layerId)
            EquipmentSlot.CHEST -> current.copy(topId = layerId)
            EquipmentSlot.LEGS -> current.copy(bottomId = layerId)
            EquipmentSlot.SHOES -> current.copy(shoesId = layerId)
            EquipmentSlot.ACCESSORY -> current.copy(accessoryId = layerId)
            EquipmentSlot.WEAPON -> current.copy(weaponId = layerId)
            EquipmentSlot.GLOVES -> current.copy(glovesId = layerId)
        }
        candidate.takeIf(CharacterLoadoutCatalog::contains) ?: current
    }

internal fun mapEquippedEquipmentSources(
    definitionEntities: List<EquipmentEntity>,
    modifierEntities: List<EquipmentModifierEntity>,
    ownedEntities: List<OwnedEquipmentEntity>,
    equippedEntities: List<CharacterEquipmentEntity>,
): List<EquippedEquipment> {
    if (equippedEntities.isEmpty()) return emptyList()
    val equippedOwnedIds = equippedEntities.mapTo(mutableSetOf()) { it.ownedEquipmentId }
    val equippedOwned = ownedEntities.filter { it.id in equippedOwnedIds }
    val equippedDefinitionIds = equippedOwned.mapTo(mutableSetOf()) { it.equipmentId }
    return mapEquipmentSources(
        definitionEntities = definitionEntities.filter { it.id in equippedDefinitionIds },
        modifierEntities = modifierEntities.filter { it.equipmentId in equippedDefinitionIds },
        ownedEntities = equippedOwned,
        equippedEntities = equippedEntities,
    ).equipped
}

internal fun mapEquipmentSources(
    definitionEntities: List<EquipmentEntity>,
    modifierEntities: List<EquipmentModifierEntity>,
    ownedEntities: List<OwnedEquipmentEntity>,
    equippedEntities: List<CharacterEquipmentEntity>,
): MappedEquipmentSources {
    val definitions = EquipmentMapper.definitions(definitionEntities, modifierEntities)
    val definitionsById = definitions.associateBy(Equipment::id)
    if (definitionsById.size != definitions.size) {
        throw EquipmentRepositoryDataException("Duplicate equipment definition")
    }
    val owned = ownedEntities.map { EquipmentMapper.owned(it, definitionsById) }
    val ownedById = owned.associateBy(OwnedEquipment::id)
    if (ownedById.size != owned.size) {
        throw EquipmentRepositoryDataException("Duplicate owned equipment row")
    }
    val equipped = equippedEntities.map { EquipmentMapper.equipped(it, ownedById) }
    return MappedEquipmentSources(definitions, owned, equipped)
}

internal data class MappedEquipmentSources(
    val definitions: List<Equipment>,
    val owned: List<OwnedEquipment>,
    val equipped: List<EquippedEquipment>,
)

private val StoreObservationTables = arrayOf(
    "character_profile",
    "character_appearance",
    "character_equipped_items",
    "equipment",
    "equipment_modifiers",
    "owned_equipment",
    "character_equipment",
    "character_status_effects",
)
