package com.todoquest.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "character_profile")
data class CharacterProfileEntity(
    @PrimaryKey
    val id: Long = 1L,
    val totalXp: Long,
    val currentGold: Long,
    val strength: Int,
    val vitality: Int,
    val focus: Int,
    val willpower: Int,
    val unspentStatPoints: Int,
    val hasUsedFreeStatReset: Boolean,
)
