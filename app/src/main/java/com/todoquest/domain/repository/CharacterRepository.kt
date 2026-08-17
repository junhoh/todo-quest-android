package com.todoquest.domain.repository

import com.todoquest.domain.model.AllocateStatPointsResult
import com.todoquest.domain.model.CharacterAppearance
import com.todoquest.domain.model.CharacterLoadoutUpdateResult
import com.todoquest.domain.model.CharacterSnapshot
import com.todoquest.domain.model.EquippedItems
import com.todoquest.domain.model.StatAllocation
import com.todoquest.domain.model.StatResetResult
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface CharacterRepository {
    fun observeCharacter(referenceDate: LocalDate): Flow<CharacterSnapshot>

    suspend fun updateAppearance(appearance: CharacterAppearance): CharacterLoadoutUpdateResult

    suspend fun updateEquippedItems(items: EquippedItems): CharacterLoadoutUpdateResult

    suspend fun allocateStatPoints(allocation: StatAllocation): AllocateStatPointsResult

    suspend fun resetStats(): StatResetResult
}
