package com.todoquest.data.mapper

import com.todoquest.data.local.CharacterEquipmentEntity
import com.todoquest.data.local.EquipmentEntity
import com.todoquest.data.local.EquipmentModifierEntity
import com.todoquest.data.local.EquipmentModifierTargetKind
import com.todoquest.data.local.OwnedEquipmentEntity
import com.todoquest.data.repository.EquipmentRepositoryDataException
import com.todoquest.domain.model.DerivedStatType
import com.todoquest.domain.model.Equipment
import com.todoquest.domain.model.EquipmentRarity
import com.todoquest.domain.model.EquipmentSlot
import com.todoquest.domain.model.EquipmentSlotMappingResult
import com.todoquest.domain.model.EquipmentStatModifier
import com.todoquest.domain.model.EquipmentType
import com.todoquest.domain.model.EquippedEquipment
import com.todoquest.domain.model.ModifierType
import com.todoquest.domain.model.OwnedEquipment
import com.todoquest.domain.model.StatTarget
import com.todoquest.domain.model.StatType
import com.todoquest.domain.model.WeaponType

internal object EquipmentMapper {
    fun definitions(
        entities: List<EquipmentEntity>,
        modifierEntities: List<EquipmentModifierEntity>,
    ): List<Equipment> {
        val modifiersByEquipment = modifierEntities.groupBy(EquipmentModifierEntity::equipmentId)
        return entities.map { entity ->
            definition(entity, modifiersByEquipment[entity.id].orEmpty())
        }
    }

    fun definition(
        entity: EquipmentEntity,
        modifierEntities: List<EquipmentModifierEntity>,
    ): Equipment = mapStorage("equipment ${entity.id}") {
        Equipment(
            id = entity.id,
            nameKey = entity.nameKey,
            descriptionKey = entity.descriptionKey,
            type = equipmentType(entity.type),
            slot = equipmentSlot(entity.slot),
            rarity = enumValue<EquipmentRarity>("rarity", entity.rarity),
            price = entity.price,
            requiredLevel = entity.requiredLevel,
            modifiers = modifierEntities.sortedBy(EquipmentModifierEntity::sortOrder).map(::modifier),
            imageKey = entity.imageKey,
            layerKey = entity.layerKey,
            isForSale = entity.isForSale,
            weaponType = entity.weaponType?.let {
                enumValue<WeaponType>("weapon type", it)
            },
        )
    }

    fun owned(
        entity: OwnedEquipmentEntity,
        definitionsById: Map<Long, Equipment>,
    ): OwnedEquipment = mapStorage("owned equipment ${entity.id}") {
        OwnedEquipment(
            id = entity.id,
            characterId = entity.characterId,
            equipment = definitionsById[entity.equipmentId]
                ?: storageError("missing equipment definition ${entity.equipmentId}"),
            acquiredAtEpochMillis = entity.acquiredAtEpochMillis,
        )
    }

    fun equipped(
        entity: CharacterEquipmentEntity,
        ownedById: Map<Long, OwnedEquipment>,
    ): EquippedEquipment = mapStorage("character equipment ${entity.characterId}/${entity.slot}") {
        val slot = equipmentSlot(entity.slot)
        val owned = ownedById[entity.ownedEquipmentId]
            ?: storageError("missing owned equipment ${entity.ownedEquipmentId}")
        if (owned.characterId != entity.characterId) {
            storageError("equipped item belongs to another character")
        }
        if (owned.equipment.slot != slot) {
            storageError("equipped item does not match its stored slot")
        }
        EquippedEquipment(
            characterId = entity.characterId,
            slot = slot,
            ownedEquipment = owned,
        )
    }

    private fun modifier(entity: EquipmentModifierEntity): EquipmentStatModifier =
        mapStorage("equipment modifier ${entity.equipmentId}/${entity.sortOrder}") {
            val target = when (entity.targetKind) {
                EquipmentModifierTargetKind.BASE.name ->
                    StatTarget.Base(enumValue<StatType>("base stat", entity.targetStat))

                EquipmentModifierTargetKind.DERIVED.name ->
                    StatTarget.Derived(enumValue<DerivedStatType>("derived stat", entity.targetStat))

                else -> storageError("unknown modifier target kind ${entity.targetKind}")
            }
            EquipmentStatModifier(
                itemId = entity.equipmentId,
                target = target,
                type = enumValue<ModifierType>("modifier type", entity.modifierType),
                amount = entity.amount,
            )
        }

    private fun equipmentType(value: String): EquipmentType = when (value) {
        "WEAPON" -> EquipmentType.WEAPON
        "HEAD", "HELMET" -> EquipmentType.HELMET
        "ARMOR", "TOP", "CHEST" -> EquipmentType.CHEST
        "BOTTOM", "LEGS" -> EquipmentType.LEGS
        "GLOVES" -> EquipmentType.GLOVES
        "SHOES" -> EquipmentType.SHOES
        "ACCESSORY" -> EquipmentType.ACCESSORY
        else -> storageError("unknown equipment type $value")
    }

    private fun equipmentSlot(value: String): EquipmentSlot =
        when (val mapped = EquipmentSlot.fromStorageValue(value)) {
            is EquipmentSlotMappingResult.Supported -> mapped.slot
            is EquipmentSlotMappingResult.Unsupported ->
                storageError("unknown equipment slot ${mapped.storageValue}")
        }

    private inline fun <reified T : Enum<T>> enumValue(label: String, value: String): T =
        enumValues<T>().firstOrNull { it.name == value }
            ?: storageError("unknown $label $value")

    private inline fun <T> mapStorage(context: String, block: () -> T): T = try {
        block()
    } catch (error: EquipmentRepositoryDataException) {
        throw error
    } catch (error: RuntimeException) {
        throw EquipmentRepositoryDataException("Invalid $context", error)
    }

    private fun storageError(message: String): Nothing =
        throw EquipmentRepositoryDataException(message)
}
