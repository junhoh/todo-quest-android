package com.todoquest.domain.usecase

import com.todoquest.domain.model.AllocateStatPointsResult
import com.todoquest.domain.model.StatAllocation
import com.todoquest.domain.model.StatResetResult
import com.todoquest.domain.repository.CharacterRepository

class AllocateStatPointsUseCase(
    private val repository: CharacterRepository,
) {
    suspend operator fun invoke(allocation: StatAllocation): AllocateStatPointsResult =
        repository.allocateStatPoints(allocation)
}

class ResetStatsUseCase(
    private val repository: CharacterRepository,
) {
    suspend operator fun invoke(): StatResetResult = repository.resetStats()
}
