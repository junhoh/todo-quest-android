package com.todoquest.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.todoquest.domain.model.CharacterStatGuideStatus
import com.todoquest.domain.repository.CharacterGuideRepository

class SharedPreferencesCharacterGuideRepository internal constructor(
    private val preferences: SharedPreferences,
    eligibleOnFirstInitialization: Boolean,
) : CharacterGuideRepository {
    constructor(
        context: Context,
        eligibleOnFirstInitialization: Boolean,
    ) : this(
        preferences = context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ),
        eligibleOnFirstInitialization = eligibleOnFirstInitialization,
    )

    private val eligibilityInitialized = synchronized(processLock) {
        if (preferences.contains(AUTOMATIC_ELIGIBILITY_KEY)) {
            true
        } else {
            commitBoolean(
                key = AUTOMATIC_ELIGIBILITY_KEY,
                value = eligibleOnFirstInitialization,
            )
        }
    }

    override fun statAllocationGuideStatus(): CharacterStatGuideStatus = synchronized(processLock) {
        CharacterStatGuideStatus(
            automaticDisplayEligible = eligibilityInitialized &&
                preferences.getBoolean(AUTOMATIC_ELIGIBILITY_KEY, false),
            acknowledged = preferences.getBoolean(ACKNOWLEDGED_KEY, false),
        )
    }

    override fun acknowledgeStatAllocationGuide(): Boolean = synchronized(processLock) {
        if (preferences.getBoolean(ACKNOWLEDGED_KEY, false)) {
            return@synchronized true
        }

        commitBoolean(
            key = ACKNOWLEDGED_KEY,
            value = true,
        )
    }

    private fun commitBoolean(
        key: String,
        value: Boolean,
    ): Boolean {
        val committed = preferences.edit()
            .putBoolean(key, value)
            .commit()
        if (!committed) {
            preferences.edit().remove(key).commit()
        }
        return committed
    }

    private companion object {
        private const val PREFERENCES_NAME = "todo_quest_character_guides"
        private const val AUTOMATIC_ELIGIBILITY_KEY = "stat_allocation_auto_eligible_v1"
        private const val ACKNOWLEDGED_KEY = "stat_allocation_acknowledged_v1"
        private val processLock = Any()
    }
}
