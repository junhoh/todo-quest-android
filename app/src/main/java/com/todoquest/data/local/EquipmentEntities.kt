package com.todoquest.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "equipment",
    indices = [
        Index(value = ["isForSale"]),
        Index(value = ["slot"]),
    ],
)
data class EquipmentEntity(
    @PrimaryKey
    val id: Long,
    val nameKey: String,
    val descriptionKey: String,
    val type: String,
    val slot: String,
    val rarity: String,
    val price: Long,
    val requiredLevel: Int,
    val imageKey: String?,
    val layerKey: String?,
    val isForSale: Boolean,
    val weaponType: String? = null,
)

enum class EquipmentModifierTargetKind {
    BASE,
    DERIVED,
}

@Entity(
    tableName = "equipment_modifiers",
    primaryKeys = ["equipmentId", "sortOrder"],
    foreignKeys = [
        ForeignKey(
            entity = EquipmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["equipmentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["equipmentId"])],
)
data class EquipmentModifierEntity(
    val equipmentId: Long,
    val sortOrder: Int,
    val targetKind: String,
    val targetStat: String,
    val modifierType: String,
    val amount: Int,
)

@Entity(
    tableName = "owned_equipment",
    foreignKeys = [
        ForeignKey(
            entity = CharacterProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = EquipmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["equipmentId"],
        ),
    ],
    indices = [
        Index(value = ["characterId", "equipmentId"], unique = true),
        Index(value = ["equipmentId"]),
    ],
)
data class OwnedEquipmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val characterId: Long,
    val equipmentId: Long,
    val acquiredAtEpochMillis: Long,
)

@Entity(
    tableName = "character_equipment",
    primaryKeys = ["characterId", "slot"],
    foreignKeys = [
        ForeignKey(
            entity = CharacterProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = OwnedEquipmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["ownedEquipmentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["ownedEquipmentId"], unique = true)],
)
data class CharacterEquipmentEntity(
    val characterId: Long,
    val slot: String,
    val ownedEquipmentId: Long,
)
