package com.todoquest.data.local

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.todoquest.domain.model.StatusEffectType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class StatusEffectDaoTest {
    private lateinit var database: TodoQuestDatabase
    private lateinit var dao: StatusEffectDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            TodoQuestDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.statusEffectDao()
        runBlocking {
            database.characterProfileDao().upsert(profile())
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun applyAndRefreshUpsertKeepOneEffectPerCharacterAndType() = runTest {
        val applied = statusEffect()
        dao.upsertStatusEffect(applied)

        assertEquals(applied, dao.getStatusEffect(CHARACTER_ID, EFFECT_TYPE))
        assertEquals(listOf(applied), dao.observeActiveStatusEffects(CHARACTER_ID).first())

        val refreshed = applied.copy(
            appliedAtEpochMillis = 2_000L,
            expiresAtEpochMillis = 88_400_000L,
            remainingRecoveryCompletions = 3,
            revision = 2L,
            lastMutationId = "monster-attack:refresh",
        )
        dao.upsertStatusEffect(refreshed)

        assertEquals(refreshed, dao.getStatusEffect(CHARACTER_ID, EFFECT_TYPE))
        assertEquals(1, dao.observeActiveStatusEffects(CHARACTER_ID).first().size)
    }

    @Test
    fun recoveryOccurrenceIsCreditedOncePerRevisionTaskAndOccurrenceDate() = runTest {
        dao.upsertStatusEffect(statusEffect())
        val first = recoveryOccurrence()

        assertTrue(dao.insertRecoveryOccurrence(first) != -1L)
        assertEquals(-1L, dao.insertRecoveryOccurrence(first))
        assertTrue(
            dao.insertRecoveryOccurrence(
                first.copy(occurrenceDateEpochDay = first.occurrenceDateEpochDay + 1L),
            ) != -1L,
        )

        dao.upsertStatusEffect(
            statusEffect().copy(
                revision = 2L,
                lastMutationId = "monster-attack:new-revision",
            ),
        )
        assertTrue(
            dao.insertRecoveryOccurrence(first.copy(revision = 2L)) != -1L,
        )
        assertEquals(3, dao.getRecoveryOccurrences(CHARACTER_ID, EFFECT_TYPE).size)
    }

    @Test
    fun decrementAndDeactivateOperationsRejectStaleRevisionAndInactiveRows() = runTest {
        dao.upsertStatusEffect(statusEffect())

        assertEquals(
            0,
            dao.decrementRemainingRecoveryCompletions(
                characterId = CHARACTER_ID,
                effectType = EFFECT_TYPE,
                revision = 2L,
                lastMutationId = "completion:stale",
            ),
        )
        assertEquals(
            1,
            dao.decrementRemainingRecoveryCompletions(
                characterId = CHARACTER_ID,
                effectType = EFFECT_TYPE,
                revision = 1L,
                lastMutationId = "completion:one",
            ),
        )
        assertEquals(2, dao.getStatusEffect(CHARACTER_ID, EFFECT_TYPE)?.remainingRecoveryCompletions)
        assertEquals(
            0,
            dao.deactivateIfRecovered(
                characterId = CHARACTER_ID,
                effectType = EFFECT_TYPE,
                revision = 1L,
                lastMutationId = "completion:early-clear",
            ),
        )

        repeat(2) { index ->
            assertEquals(
                1,
                dao.decrementRemainingRecoveryCompletions(
                    characterId = CHARACTER_ID,
                    effectType = EFFECT_TYPE,
                    revision = 1L,
                    lastMutationId = "completion:${index + 2}",
                ),
            )
        }
        assertEquals(
            1,
            dao.deactivateIfRecovered(
                characterId = CHARACTER_ID,
                effectType = EFFECT_TYPE,
                revision = 1L,
                lastMutationId = "completion:clear",
            ),
        )
        assertFalse(dao.getStatusEffect(CHARACTER_ID, EFFECT_TYPE)!!.active)
        assertEquals(0, dao.getStatusEffect(CHARACTER_ID, EFFECT_TYPE)!!.remainingRecoveryCompletions)
        assertEquals(emptyList<CharacterStatusEffectEntity>(), dao.getActiveStatusEffects(CHARACTER_ID))
        assertEquals(
            0,
            dao.decrementRemainingRecoveryCompletions(
                characterId = CHARACTER_ID,
                effectType = EFFECT_TYPE,
                revision = 1L,
                lastMutationId = "completion:after-clear",
            ),
        )
    }

    @Test
    fun expirationUsesInclusiveBoundaryAndDoesNotChangeFutureOrInactiveEffects() = runTest {
        dao.upsertStatusEffect(statusEffect())

        assertEquals(
            0,
            dao.deactivateIfExpired(
                characterId = CHARACTER_ID,
                effectType = EFFECT_TYPE,
                revision = 1L,
                nowEpochMillis = 86_399_999L,
                lastMutationId = "clock:early",
            ),
        )
        assertEquals(
            1,
            dao.deactivateIfExpired(
                characterId = CHARACTER_ID,
                effectType = EFFECT_TYPE,
                revision = 1L,
                nowEpochMillis = 86_400_000L,
                lastMutationId = "clock:expired",
            ),
        )
        assertNull(dao.observeActiveStatusEffects(CHARACTER_ID).first().singleOrNull())
        assertEquals(
            0,
            dao.deactivateIfExpired(
                characterId = CHARACTER_ID,
                effectType = EFFECT_TYPE,
                revision = 1L,
                nowEpochMillis = 86_400_001L,
                lastMutationId = "clock:again",
            ),
        )
    }

    private fun profile() = CharacterProfileEntity(
        id = CHARACTER_ID,
        totalXp = 0L,
        currentGold = 0L,
        strength = 5,
        vitality = 5,
        focus = 5,
        willpower = 5,
        unspentStatPoints = 0,
        hasUsedFreeStatReset = false,
    )

    private fun statusEffect() = CharacterStatusEffectEntity(
        characterId = CHARACTER_ID,
        effectType = EFFECT_TYPE,
        definitionVersion = 1,
        appliedAtEpochMillis = 0L,
        expiresAtEpochMillis = 86_400_000L,
        remainingRecoveryCompletions = 3,
        active = true,
        revision = 1L,
        lastMutationId = "monster-attack:apply",
    )

    private fun recoveryOccurrence() = StatusEffectRecoveryOccurrenceEntity(
        characterId = CHARACTER_ID,
        effectType = EFFECT_TYPE,
        revision = 1L,
        taskId = 7L,
        occurrenceDateEpochDay = 20_000L,
    )

    private companion object {
        const val CHARACTER_ID = 1L
        val EFFECT_TYPE = StatusEffectType.SEVERE_INJURY.name
    }
}
