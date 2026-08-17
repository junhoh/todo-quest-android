package com.todoquest.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "character_equipped_items")
data class CharacterEquippedItemsEntity(
    @PrimaryKey
    val characterId: Long,
    val headId: String?,
    val topId: String,
    val bottomId: String,
    val shoesId: String,
    val accessoryId: String?,
    val weaponId: String?,
    val glovesId: String?,
)
