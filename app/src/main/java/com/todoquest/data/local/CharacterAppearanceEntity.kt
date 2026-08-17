package com.todoquest.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "character_appearance")
data class CharacterAppearanceEntity(
    @PrimaryKey
    val characterId: Long,
    val hairId: String,
)
