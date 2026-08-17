package com.todoquest.domain.repository

import com.todoquest.domain.model.CharacterStatGuideStatus

interface CharacterGuideRepository {
    fun statAllocationGuideStatus(): CharacterStatGuideStatus

    fun acknowledgeStatAllocationGuide(): Boolean
}
