package com.todoquest.domain.usecase

import com.todoquest.domain.repository.CharacterGuideRepository

class PrepareCharacterStatGuideUseCase(
    private val repository: CharacterGuideRepository,
) {
    operator fun invoke(): Boolean = repository.statAllocationGuideStatus().let { status ->
        status.automaticDisplayEligible && !status.acknowledged
    }
}

class AcknowledgeCharacterStatGuideUseCase(
    private val repository: CharacterGuideRepository,
) {
    operator fun invoke(): Boolean = repository.acknowledgeStatAllocationGuide()
}
