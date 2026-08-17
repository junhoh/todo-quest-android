package com.todoquest.domain.model

enum class EquipmentType {
    WEAPON,
    HELMET,
    CHEST,
    LEGS,
    GLOVES,
    SHOES,
    ACCESSORY,
}

enum class WeaponType {
    LONGSWORD,
    DAGGER,
    SPEAR,
    BLUNT,
}

enum class EquipmentSlot {
    WEAPON,
    HELMET,
    CHEST,
    LEGS,
    GLOVES,
    SHOES,
    ACCESSORY;

    companion object {
        fun fromStorageValue(value: String): EquipmentSlotMappingResult =
            EquipmentSlotCompatibilityMapper.map(value)
    }
}

fun EquipmentType.toEquipmentSlot(): EquipmentSlot = when (this) {
    EquipmentType.WEAPON -> EquipmentSlot.WEAPON
    EquipmentType.HELMET -> EquipmentSlot.HELMET
    EquipmentType.CHEST -> EquipmentSlot.CHEST
    EquipmentType.LEGS -> EquipmentSlot.LEGS
    EquipmentType.GLOVES -> EquipmentSlot.GLOVES
    EquipmentType.SHOES -> EquipmentSlot.SHOES
    EquipmentType.ACCESSORY -> EquipmentSlot.ACCESSORY
}

fun EquipmentSlot.toEquipmentType(): EquipmentType = when (this) {
    EquipmentSlot.WEAPON -> EquipmentType.WEAPON
    EquipmentSlot.HELMET -> EquipmentType.HELMET
    EquipmentSlot.CHEST -> EquipmentType.CHEST
    EquipmentSlot.LEGS -> EquipmentType.LEGS
    EquipmentSlot.GLOVES -> EquipmentType.GLOVES
    EquipmentSlot.SHOES -> EquipmentType.SHOES
    EquipmentSlot.ACCESSORY -> EquipmentType.ACCESSORY
}

sealed interface EquipmentSlotMappingResult {
    data class Supported(val slot: EquipmentSlot) : EquipmentSlotMappingResult

    data class Unsupported(val storageValue: String) : EquipmentSlotMappingResult
}

object EquipmentSlotCompatibilityMapper {
    fun map(storageValue: String): EquipmentSlotMappingResult = when (storageValue) {
        "WEAPON" -> EquipmentSlotMappingResult.Supported(EquipmentSlot.WEAPON)
        "HEAD", "HELMET" -> EquipmentSlotMappingResult.Supported(EquipmentSlot.HELMET)
        "ARMOR", "TOP", "CHEST" -> EquipmentSlotMappingResult.Supported(EquipmentSlot.CHEST)
        "BOTTOM", "LEGS" -> EquipmentSlotMappingResult.Supported(EquipmentSlot.LEGS)
        "GLOVES" -> EquipmentSlotMappingResult.Supported(EquipmentSlot.GLOVES)
        "SHOES" -> EquipmentSlotMappingResult.Supported(EquipmentSlot.SHOES)
        "ACCESSORY" -> EquipmentSlotMappingResult.Supported(EquipmentSlot.ACCESSORY)
        else -> EquipmentSlotMappingResult.Unsupported(storageValue)
    }
}

enum class EquipmentRarity {
    COMMON,
    UNCOMMON,
    RARE,
    EPIC,
    LEGENDARY,
}

data class EquipmentStatModifier(
    val itemId: Long,
    val target: StatTarget,
    val type: ModifierType,
    val amount: Int,
)

data class Equipment(
    val id: Long,
    val nameKey: String,
    val descriptionKey: String,
    val type: EquipmentType,
    val slot: EquipmentSlot,
    val rarity: EquipmentRarity,
    val price: Long,
    val requiredLevel: Int,
    val modifiers: List<EquipmentStatModifier>,
    val imageKey: String? = null,
    val layerKey: String? = null,
    val isForSale: Boolean,
    val weaponType: WeaponType? = null,
) {
    init {
        require(id > 0) { "id must be positive" }
        require(nameKey.isNotBlank()) { "nameKey must not be blank" }
        require(descriptionKey.isNotBlank()) { "descriptionKey must not be blank" }
        require(price >= 0) { "price must not be negative" }
        require(requiredLevel > 0) { "requiredLevel must be positive" }
        require(imageKey == null || imageKey.isNotBlank()) { "imageKey must be null or non-blank" }
        require(layerKey == null || layerKey.isNotBlank()) { "layerKey must be null or non-blank" }
        require(modifiers.all { it.itemId == id }) { "all modifiers must reference this equipment" }
        require((type == EquipmentType.WEAPON) == (weaponType != null)) {
            "weaponType must be non-null only for weapon equipment"
        }
    }
}

data class OwnedEquipment(
    val id: Long,
    val characterId: Long,
    val equipment: Equipment,
    val acquiredAtEpochMillis: Long,
) {
    init {
        require(id > 0) { "id must be positive" }
        require(characterId > 0) { "characterId must be positive" }
        require(acquiredAtEpochMillis >= 0) { "acquiredAtEpochMillis must not be negative" }
    }

    val equipmentId: Long
        get() = equipment.id
}

data class EquippedEquipment(
    val characterId: Long,
    val slot: EquipmentSlot,
    val ownedEquipment: OwnedEquipment,
) {
    init {
        require(characterId > 0) { "characterId must be positive" }
        require(ownedEquipment.characterId == characterId) { "owned equipment belongs to another character" }
        require(ownedEquipment.equipment.slot == slot) { "owned equipment does not match the equipped slot" }
    }
}

data class EquipmentPreviewProjection(
    val renderedEquippedItems: EquippedItems,
    val derivedStats: DerivedStats,
)

data class EquipmentStoreSnapshot(
    val characterId: Long,
    val currentGold: Long,
    val characterLevel: Int,
    val equipment: List<Equipment>,
    val ownedEquipmentIds: Set<Long>,
    val equippedBySlot: Map<EquipmentSlot, EquippedEquipment>,
    val appearance: CharacterAppearance,
    val renderedEquippedItems: EquippedItems,
    val derivedStats: DerivedStats,
    val previewByEquipmentId: Map<Long, EquipmentPreviewProjection> = emptyMap(),
    val ownedEquipmentByEquipmentId: Map<Long, OwnedEquipment> = emptyMap(),
) {
    init {
        require(characterId > 0) { "characterId must be positive" }
        require(currentGold >= 0) { "currentGold must not be negative" }
        require(characterLevel > 0) { "characterLevel must be positive" }
        val equipmentById = equipment.associateBy(Equipment::id)
        require(
            ownedEquipmentByEquipmentId.all { (equipmentId, ownedEquipment) ->
                equipmentId == ownedEquipment.equipmentId &&
                    ownedEquipment.characterId == characterId &&
                    equipmentById[equipmentId] == ownedEquipment.equipment
            },
        ) {
            "owned equipment lookup must use catalog equipment ids for this character"
        }
    }
}

data class EquipmentInventorySnapshot(
    val characterId: Long,
    val ownedEquipment: List<OwnedEquipment>,
    val equippedBySlot: Map<EquipmentSlot, EquippedEquipment>,
) {
    init {
        require(characterId > 0) { "characterId must be positive" }
    }
}

data class EquipmentStatComparison(
    val target: StatTarget,
    val modifierType: ModifierType,
    val currentAmount: Int,
    val candidateAmount: Int,
    val difference: Int,
)

sealed interface PurchaseEligibility {
    data object Eligible : PurchaseEligibility

    sealed interface Unavailable : PurchaseEligibility

    data class UnsupportedSlot(
        val equipmentId: Long,
        val type: EquipmentType,
        val slot: EquipmentSlot,
    ) : Unavailable

    data class NotForSale(val equipmentId: Long) : Unavailable

    data class AlreadyOwned(val equipmentId: Long) : Unavailable

    data class LevelTooLow(
        val equipmentId: Long,
        val requiredLevel: Int,
        val characterLevel: Int,
    ) : Unavailable

    data class InsufficientGold(
        val equipmentId: Long,
        val price: Long,
        val availableGold: Long,
    ) : Unavailable
}

sealed interface PurchaseEquipmentResult {
    data class Success(
        val ownedEquipmentId: Long,
        val equipmentId: Long,
        val equipmentNameKey: String,
        val type: EquipmentType,
        val slot: EquipmentSlot,
        val remainingGold: Long,
    ) : PurchaseEquipmentResult

    data class Unavailable(val reason: PurchaseEligibility.Unavailable) : PurchaseEquipmentResult
}

sealed interface EquipOwnedEquipmentResult {
    data class Success(
        val ownedEquipmentId: Long,
        val equipmentId: Long,
        val slot: EquipmentSlot,
    ) : EquipOwnedEquipmentResult

    data class OwnedEquipmentNotFound(
        val characterId: Long,
        val ownedEquipmentId: Long,
    ) : EquipOwnedEquipmentResult

    data class NotOwnedByCharacter(
        val characterId: Long,
        val ownedEquipmentId: Long,
        val ownerCharacterId: Long,
    ) : EquipOwnedEquipmentResult

    data class SlotMismatch(
        val ownedEquipmentId: Long,
        val type: EquipmentType,
        val equipmentSlot: EquipmentSlot,
        val targetSlot: EquipmentSlot,
    ) : EquipOwnedEquipmentResult
}

sealed interface UnequipEquipmentResult {
    data class Success(
        val ownedEquipmentId: Long,
        val equipmentId: Long,
        val slot: EquipmentSlot,
    ) : UnequipEquipmentResult {
        init {
            require(ownedEquipmentId > 0) { "ownedEquipmentId must be positive" }
            require(equipmentId > 0) { "equipmentId must be positive" }
        }
    }

    data class AlreadyEmpty(val slot: EquipmentSlot) : UnequipEquipmentResult
}
