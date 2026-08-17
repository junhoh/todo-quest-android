package com.todoquest.notification

import android.content.Context
import android.content.SharedPreferences
import com.todoquest.domain.repository.FirstLaunchNotificationPromptStore

class SharedPreferencesFirstLaunchNotificationPromptStore internal constructor(
    private val preferences: SharedPreferences,
) : FirstLaunchNotificationPromptStore {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ),
    )

    override fun consumeFirstLaunchCheck(): Boolean = synchronized(processLock) {
        if (preferences.getBoolean(FIRST_LAUNCH_CHECK_CONSUMED_KEY, false)) {
            return@synchronized false
        }

        val committed = preferences.edit()
            .putBoolean(FIRST_LAUNCH_CHECK_CONSUMED_KEY, true)
            .commit()
        if (!committed) {
            preferences.edit().remove(FIRST_LAUNCH_CHECK_CONSUMED_KEY).commit()
        }
        committed
    }

    private companion object {
        private const val PREFERENCES_NAME = "todo_quest_notification_permission_prompt"
        private const val FIRST_LAUNCH_CHECK_CONSUMED_KEY = "first_launch_check_consumed"
        private val processLock = Any()
    }
}
