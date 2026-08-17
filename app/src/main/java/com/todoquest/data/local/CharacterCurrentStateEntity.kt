package com.todoquest.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "character_current_state")
data class CharacterCurrentStateEntity(
    @PrimaryKey
    val characterId: Long,
    val currentHp: Int,
    val balanceVersion: Int,
    val updatedAtEpochMillis: Long,
)
