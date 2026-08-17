package com.todoquest.domain.repository

import kotlinx.coroutines.flow.StateFlow

interface BattleSfxSettingsRepository {
    val isEnabled: StateFlow<Boolean>

    fun setEnabled(enabled: Boolean): Boolean
}
