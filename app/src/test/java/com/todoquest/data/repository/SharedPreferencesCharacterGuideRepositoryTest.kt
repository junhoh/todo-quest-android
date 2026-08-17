package com.todoquest.data.repository

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import java.util.ArrayDeque
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SharedPreferencesCharacterGuideRepositoryTest {
    private lateinit var context: Context
    private lateinit var preferences: SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
    }

    @Test
    fun newInstallationPersistsAutomaticEligibility() {
        val repository = SharedPreferencesCharacterGuideRepository(
            preferences = preferences,
            eligibleOnFirstInitialization = true,
        )

        val status = repository.statAllocationGuideStatus()

        assertTrue(status.automaticDisplayEligible)
        assertFalse(status.acknowledged)
        assertTrue(preferences.contains(AUTOMATIC_ELIGIBILITY_KEY))
        assertTrue(preferences.getBoolean(AUTOMATIC_ELIGIBILITY_KEY, false))
    }

    @Test
    fun existingInstallationPersistsIneligibleClassification() {
        val repository = SharedPreferencesCharacterGuideRepository(
            preferences = preferences,
            eligibleOnFirstInitialization = false,
        )

        val status = repository.statAllocationGuideStatus()

        assertFalse(status.automaticDisplayEligible)
        assertFalse(status.acknowledged)
        assertTrue(preferences.contains(AUTOMATIC_ELIGIBILITY_KEY))
        assertFalse(preferences.getBoolean(AUTOMATIC_ELIGIBILITY_KEY, true))
    }

    @Test
    fun repositoryRecreationNeverReclassifiesPersistedEligibility() {
        SharedPreferencesCharacterGuideRepository(
            preferences = preferences,
            eligibleOnFirstInitialization = false,
        )

        val recreated = SharedPreferencesCharacterGuideRepository(
            preferences = preferences,
            eligibleOnFirstInitialization = true,
        )

        assertFalse(recreated.statAllocationGuideStatus().automaticDisplayEligible)
        assertFalse(preferences.getBoolean(AUTOMATIC_ELIGIBILITY_KEY, true))
    }

    @Test
    fun firstAcknowledgementPersistsIndependentlyFromEligibility() {
        val repository = SharedPreferencesCharacterGuideRepository(
            preferences = preferences,
            eligibleOnFirstInitialization = true,
        )

        assertTrue(repository.acknowledgeStatAllocationGuide())

        val status = repository.statAllocationGuideStatus()
        assertTrue(status.automaticDisplayEligible)
        assertTrue(status.acknowledged)
    }

    @Test
    fun duplicateAcknowledgementIsAnIdempotentSuccess() {
        val repository = SharedPreferencesCharacterGuideRepository(
            preferences = preferences,
            eligibleOnFirstInitialization = true,
        )

        assertTrue(repository.acknowledgeStatAllocationGuide())
        assertTrue(repository.acknowledgeStatAllocationGuide())
        assertTrue(repository.statAllocationGuideStatus().acknowledged)
    }

    @Test
    fun emptyApplicationPreferencesInitializeFromThePublicConstructor() {
        val repository = SharedPreferencesCharacterGuideRepository(
            context = context,
            eligibleOnFirstInitialization = true,
        )

        assertTrue(repository.statAllocationGuideStatus().automaticDisplayEligible)
        assertTrue(preferences.contains(AUTOMATIC_ELIGIBILITY_KEY))
        assertFalse(preferences.contains(ACKNOWLEDGED_KEY))
    }

    @Test
    fun failedEligibilityInitializationIsFailClosed() {
        val commitControlledPreferences = CommitControlledSharedPreferences(
            delegate = preferences,
            commitResults = ArrayDeque(listOf(false)),
        )

        val repository = SharedPreferencesCharacterGuideRepository(
            preferences = commitControlledPreferences,
            eligibleOnFirstInitialization = true,
        )

        assertFalse(repository.statAllocationGuideStatus().automaticDisplayEligible)
        assertFalse(repository.statAllocationGuideStatus().acknowledged)
    }

    @Test
    fun failedAcknowledgementReturnsFalseWithoutChangingEligibility() {
        preferences.edit()
            .putBoolean(AUTOMATIC_ELIGIBILITY_KEY, true)
            .commit()
        val commitControlledPreferences = CommitControlledSharedPreferences(
            delegate = preferences,
            commitResults = ArrayDeque(listOf(false)),
        )
        val repository = SharedPreferencesCharacterGuideRepository(
            preferences = commitControlledPreferences,
            eligibleOnFirstInitialization = false,
        )

        assertFalse(repository.acknowledgeStatAllocationGuide())

        val status = repository.statAllocationGuideStatus()
        assertTrue(status.automaticDisplayEligible)
        assertFalse(status.acknowledged)
    }

    private class CommitControlledSharedPreferences(
        private val delegate: SharedPreferences,
        private val commitResults: ArrayDeque<Boolean>,
    ) : SharedPreferences by delegate {
        override fun edit(): SharedPreferences.Editor {
            val delegateEditor = delegate.edit()
            return object : SharedPreferences.Editor by delegateEditor {
                override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                    delegateEditor.putBoolean(key, value)
                    return this
                }

                override fun commit(): Boolean {
                    val shouldCommit = if (commitResults.isEmpty()) {
                        true
                    } else {
                        commitResults.removeFirst()
                    }
                    return shouldCommit && delegateEditor.commit()
                }
            }
        }
    }

    private companion object {
        private const val PREFERENCES_NAME = "todo_quest_character_guides"
        private const val AUTOMATIC_ELIGIBILITY_KEY = "stat_allocation_auto_eligible_v1"
        private const val ACKNOWLEDGED_KEY = "stat_allocation_acknowledged_v1"
    }
}
