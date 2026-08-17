package com.todoquest.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.todoquest.core.AppClock
import com.todoquest.data.local.CharacterProfileEntity
import com.todoquest.data.local.CharacterStatusEffectEntity
import com.todoquest.data.local.TodoQuestDatabase
import com.todoquest.domain.model.StatusEffectType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
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
class RoomStatusEffectRepositoryTest {
    private lateinit var database: TodoQuestDatabase
    private lateinit var clock: MutableClock
    private lateinit var repository: RoomStatusEffectRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            TodoQuestDatabase::class.java,
        ).allowMainThreadQueries().build()
        clock = MutableClock()
        repository = RoomStatusEffectRepository(database, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun observeMapsPersistentActiveEffectAndExactExpiryReconciliationSurvivesRecreation() = runTest {
        database.characterProfileDao().upsert(profile())
        database.statusEffectDao().upsertStatusEffect(severeInjury())

        assertEquals(
            listOf(StatusEffectType.SEVERE_INJURY),
            repository.observeActiveStatusEffects(CHARACTER_ID).first().map { it.type },
        )
        clock.instant = EXPIRES_AT.minusMillis(1L)
        assertEquals(0, repository.reconcileExpired(CHARACTER_ID))

        clock.instant = EXPIRES_AT
        assertEquals(1, repository.reconcileExpired(CHARACTER_ID))
        assertTrue(repository.observeActiveStatusEffects(CHARACTER_ID).first().isEmpty())

        val stored = database.statusEffectDao()
            .getStatusEffect(CHARACTER_ID, StatusEffectType.SEVERE_INJURY.name)!!
        assertFalse(stored.active)
        assertTrue(stored.lastMutationId.startsWith("status-effect:expired:"))

        repository = RoomStatusEffectRepository(database, clock)
        assertTrue(repository.observeActiveStatusEffects(CHARACTER_ID).first().isEmpty())
        assertEquals(0, repository.reconcileExpired(CHARACTER_ID))
    }

    @Test
    fun explicitRemovalRequiresCurrentRevisionAndPersistsItsMutationId() = runTest {
        database.characterProfileDao().upsert(profile())
        database.statusEffectDao().upsertStatusEffect(severeInjury())

        assertFalse(
            repository.removeStatusEffect(
                characterId = CHARACTER_ID,
                type = StatusEffectType.SEVERE_INJURY,
                revision = 2L,
                mutationId = "status-effect:removed:stale",
            ),
        )
        assertTrue(
            repository.removeStatusEffect(
                characterId = CHARACTER_ID,
                type = StatusEffectType.SEVERE_INJURY,
                revision = 1L,
                mutationId = "status-effect:removed:manual-test",
            ),
        )

        val stored = database.statusEffectDao()
            .getStatusEffect(CHARACTER_ID, StatusEffectType.SEVERE_INJURY.name)!!
        assertFalse(stored.active)
        assertEquals("status-effect:removed:manual-test", stored.lastMutationId)
        assertFalse(
            repository.removeStatusEffect(
                characterId = CHARACTER_ID,
                type = StatusEffectType.SEVERE_INJURY,
                revision = 1L,
                mutationId = "status-effect:removed:duplicate",
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

    private fun severeInjury() = CharacterStatusEffectEntity(
        characterId = CHARACTER_ID,
        effectType = StatusEffectType.SEVERE_INJURY.name,
        definitionVersion = 1,
        appliedAtEpochMillis = APPLIED_AT.toEpochMilli(),
        expiresAtEpochMillis = EXPIRES_AT.toEpochMilli(),
        remainingRecoveryCompletions = 3,
        active = true,
        revision = 1L,
        lastMutationId = "monster-attack:apply",
    )

    private class MutableClock : AppClock {
        override val zoneId: ZoneId = ZoneId.of("UTC")
        var instant: Instant = APPLIED_AT.plusSeconds(1L)

        override fun now(): Instant = instant

        override fun today(): LocalDate = instant.atZone(zoneId).toLocalDate()
    }

    private companion object {
        const val CHARACTER_ID = 1L
        val APPLIED_AT: Instant = Instant.parse("2026-07-14T09:00:00Z")
        val EXPIRES_AT: Instant = APPLIED_AT.plusSeconds(24L * 60L * 60L)
    }
}
