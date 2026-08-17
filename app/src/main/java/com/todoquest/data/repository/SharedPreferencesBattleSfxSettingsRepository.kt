package com.todoquest.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.todoquest.domain.repository.BattleSfxSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SharedPreferencesBattleSfxSettingsRepository internal constructor(
    private val preferences: SharedPreferences,
) : BattleSfxSettingsRepository {
    constructor(context: Context) : this(
        preferences = context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ),
    )

    private val mutableIsEnabled = MutableStateFlow(
        synchronized(processLock) {
            preferences.getBoolean(ENABLED_KEY, true)
        },
    )

    override val isEnabled: StateFlow<Boolean> = mutableIsEnabled.asStateFlow()

    override fun setEnabled(enabled: Boolean): Boolean = synchronized(processLock) {
        val confirmedValue = mutableIsEnabled.value
        if (confirmedValue == enabled) {
            return@synchronized true
        }

        val hadPersistedValue = preferences.contains(ENABLED_KEY)
        val committed = preferences.edit()
            .putBoolean(ENABLED_KEY, enabled)
            .commit()
        if (!committed) {
            val rollbackEditor = preferences.edit()
            if (hadPersistedValue) {
                rollbackEditor.putBoolean(ENABLED_KEY, confirmedValue)
            } else {
                rollbackEditor.remove(ENABLED_KEY)
            }
            rollbackEditor.commit()
            return@synchronized false
        }

        mutableIsEnabled.value = enabled
        true
    }

    private companion object {
        private const val PREFERENCES_NAME = "todo_quest_audio_settings"
        private const val ENABLED_KEY = "battle_sfx_enabled_v1"
        private val processLock = Any()
    }
}
