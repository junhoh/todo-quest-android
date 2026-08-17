package com.todoquest.data.repository

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import java.util.ArrayDeque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SharedPreferencesBattleSfxSettingsRepositoryTest {
    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
    }

    @Test
    fun emptyPreferencesDefaultToEnabledWithoutWritingTheKey() {
        val repository = SharedPreferencesBattleSfxSettingsRepository(
            preferences = preferences,
        )

        assertTrue(repository.isEnabled.value)
        assertFalse(preferences.contains(ENABLED_KEY))
    }

    @Test
    fun publicConstructorUsesTheDedicatedApplicationPreferenceFile() {
        val repository = SharedPreferencesBattleSfxSettingsRepository(context)

        assertTrue(repository.isEnabled.value)
        assertFalse(preferences.contains(ENABLED_KEY))
        assertTrue(repository.setEnabled(false))
        assertFalse(preferences.getBoolean(ENABLED_KEY, true))
    }

    @Test
    fun enabledSettingChangesPreferenceAndFlowBeforeReturning() {
        val repository = SharedPreferencesBattleSfxSettingsRepository(
            preferences = preferences,
        )

        assertTrue(repository.setEnabled(false))
        assertFalse(preferences.getBoolean(ENABLED_KEY, true))
        assertFalse(repository.isEnabled.value)

        assertTrue(repository.setEnabled(true))
        assertTrue(preferences.getBoolean(ENABLED_KEY, false))
        assertTrue(repository.isEnabled.value)
    }

    @Test
    fun settingTheConfirmedValueAgainIsAnIdempotentSuccessWithoutAnotherCommit() {
        val commitControlledPreferences = CommitControlledSharedPreferences(
            delegate = preferences,
        )
        val repository = SharedPreferencesBattleSfxSettingsRepository(
            preferences = commitControlledPreferences,
        )

        assertTrue(repository.setEnabled(true))
        assertEquals(0, commitControlledPreferences.commitCalls)

        assertTrue(repository.setEnabled(false))
        assertEquals(1, commitControlledPreferences.commitCalls)

        assertTrue(repository.setEnabled(false))
        assertEquals(1, commitControlledPreferences.commitCalls)
    }

    @Test
    fun repositoryRecreationImmediatelyReadsTheLastPersistedValue() {
        val repository = SharedPreferencesBattleSfxSettingsRepository(
            preferences = preferences,
        )
        assertTrue(repository.setEnabled(false))

        val recreated = SharedPreferencesBattleSfxSettingsRepository(
            preferences = preferences,
        )

        assertFalse(recreated.isEnabled.value)
    }

    @Test
    fun failedCommitKeepsPreferenceAndFlowAtTheLastConfirmedValue() {
        preferences.edit()
            .putBoolean(ENABLED_KEY, false)
            .commit()
        val commitControlledPreferences = CommitControlledSharedPreferences(
            delegate = preferences,
            commitResults = ArrayDeque(listOf(false, true)),
            persistBeforeReportingFailure = true,
        )
        val repository = SharedPreferencesBattleSfxSettingsRepository(
            preferences = commitControlledPreferences,
        )

        assertFalse(repository.setEnabled(true))

        assertFalse(preferences.getBoolean(ENABLED_KEY, true))
        assertFalse(repository.isEnabled.value)
    }

    private class CommitControlledSharedPreferences(
        private val delegate: SharedPreferences,
        private val commitResults: ArrayDeque<Boolean> = ArrayDeque(),
        private val persistBeforeReportingFailure: Boolean = false,
    ) : SharedPreferences by delegate {
        var commitCalls: Int = 0
            private set

        override fun edit(): SharedPreferences.Editor {
            val delegateEditor = delegate.edit()
            return object : SharedPreferences.Editor by delegateEditor {
                override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                    delegateEditor.putBoolean(key, value)
                    return this
                }

                override fun remove(key: String): SharedPreferences.Editor {
                    delegateEditor.remove(key)
                    return this
                }

                override fun commit(): Boolean {
                    commitCalls += 1
                    val shouldReportSuccess = if (commitResults.isEmpty()) {
                        true
                    } else {
                        commitResults.removeFirst()
                    }
                    if (!shouldReportSuccess && persistBeforeReportingFailure) {
                        delegateEditor.commit()
                        return false
                    }
                    return shouldReportSuccess && delegateEditor.commit()
                }
            }
        }
    }

    private companion object {
        private const val PREFERENCES_NAME = "todo_quest_audio_settings"
        private const val ENABLED_KEY = "battle_sfx_enabled_v1"
    }
}
