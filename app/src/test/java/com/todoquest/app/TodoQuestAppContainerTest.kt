package com.todoquest.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.todoquest.audio.BattleSfx
import com.todoquest.audio.BattleSfxPlayer
import com.todoquest.audio.NoOpBattleSfxPlayer
import com.todoquest.core.SystemAppClock
import com.todoquest.data.local.TodoQuestDatabase
import com.todoquest.domain.model.CharacterStatGuideStatus
import com.todoquest.domain.repository.BattleSfxSettingsRepository
import com.todoquest.domain.repository.CharacterGuideRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TodoQuestAppContainerTest {
    private lateinit var context: Context
    private lateinit var database: TodoQuestDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            TodoQuestDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(DATABASE_EXISTENCE_TEST_NAME)
    }

    @Test
    fun containerPreservesInjectedCharacterGuideRepositoryAndScopesUseCases() {
        val repository = RecordingCharacterGuideRepository()

        val container = TodoQuestAppContainer(
            database = database,
            clock = SystemAppClock(),
            characterGuideRepository = repository,
        )

        assertSame(repository, container.characterGuideRepository)
        assertTrue(container.prepareCharacterStatGuideUseCase())
        assertTrue(container.acknowledgeCharacterStatGuideUseCase())
        assertTrue(repository.acknowledged)
    }

    @Test
    fun containerDefaultsToInactiveCharacterGuideRepository() {
        val container = TodoQuestAppContainer(
            database = database,
            clock = SystemAppClock(),
        )

        assertFalse(container.characterGuideRepository.statAllocationGuideStatus()
            .automaticDisplayEligible)
        assertFalse(container.prepareCharacterStatGuideUseCase())
        assertTrue(container.acknowledgeCharacterStatGuideUseCase())
    }

    @Test
    fun containerPreservesInjectedBattleAudioDependenciesAndReleasesPlayerOnce() {
        val settingsRepository = FakeBattleSfxSettingsRepository()
        val player = RecordingBattleSfxPlayer()

        val container = TodoQuestAppContainer(
            database = database,
            clock = SystemAppClock(),
            battleSfxSettingsRepository = settingsRepository,
            battleSfxPlayer = player,
        )

        assertSame(settingsRepository, container.battleSfxSettingsRepository)
        assertSame(player, container.battleSfxPlayer)

        container.releaseAudio()
        container.releaseAudio()

        assertEquals(1, player.releaseCalls)
    }

    @Test
    fun publicTestConstructorDefaultsToEnabledSettingsAndNoOpPlayer() {
        val container = TodoQuestAppContainer(
            database = database,
            clock = SystemAppClock(),
        )

        assertTrue(container.battleSfxSettingsRepository.isEnabled.value)
        assertSame(NoOpBattleSfxPlayer, container.battleSfxPlayer)
        container.releaseAudio()
    }

    @Test
    fun productionBattleAudioInitializationFailureFallsBackWithoutEscaping() {
        val failures = mutableListOf<String>()

        val settingsFailure = createProductionBattleSfxDependencies(
            context = context,
            settingsRepositoryFactory = {
                throw IllegalStateException("settings unavailable")
            },
            playerFactory = { error("player must not be created after settings failure") },
            onFailure = { operation, _ -> failures += operation },
        )

        assertTrue(settingsFailure.settingsRepository.isEnabled.value)
        assertSame(NoOpBattleSfxPlayer, settingsFailure.player)
        assertEquals(listOf("settings repository"), failures)

        failures.clear()
        val settingsRepository = FakeBattleSfxSettingsRepository()
        val playerFailure = createProductionBattleSfxDependencies(
            context = context,
            settingsRepositoryFactory = { settingsRepository },
            playerFactory = { throw IllegalStateException("sound pool unavailable") },
            onFailure = { operation, _ -> failures += operation },
        )

        assertSame(settingsRepository, playerFailure.settingsRepository)
        assertSame(NoOpBattleSfxPlayer, playerFailure.player)
        assertEquals(listOf("player"), failures)
    }

    @Test
    fun firstInitializationEligibilityUsesDatabaseFileExistence() {
        context.deleteDatabase(DATABASE_EXISTENCE_TEST_NAME)
        assertFalse(
            todoQuestDatabaseExists(
                context = context,
                databaseName = DATABASE_EXISTENCE_TEST_NAME,
            ),
        )

        val existingDatabase = Room.databaseBuilder(
            context,
            TodoQuestDatabase::class.java,
            DATABASE_EXISTENCE_TEST_NAME,
        ).build()
        existingDatabase.openHelper.writableDatabase

        assertTrue(
            todoQuestDatabaseExists(
                context = context,
                databaseName = DATABASE_EXISTENCE_TEST_NAME,
            ),
        )
        existingDatabase.close()
    }

    private class RecordingCharacterGuideRepository : CharacterGuideRepository {
        var acknowledged: Boolean = false

        override fun statAllocationGuideStatus(): CharacterStatGuideStatus =
            CharacterStatGuideStatus(
                automaticDisplayEligible = true,
                acknowledged = acknowledged,
            )

        override fun acknowledgeStatAllocationGuide(): Boolean {
            acknowledged = true
            return true
        }
    }

    private class FakeBattleSfxSettingsRepository : BattleSfxSettingsRepository {
        private val mutableEnabled = MutableStateFlow(true)
        override val isEnabled: StateFlow<Boolean> = mutableEnabled

        override fun setEnabled(enabled: Boolean): Boolean {
            mutableEnabled.value = enabled
            return true
        }
    }

    private class RecordingBattleSfxPlayer : BattleSfxPlayer {
        var releaseCalls = 0

        override fun play(effect: BattleSfx, eventId: String) = Unit

        override fun release() {
            releaseCalls += 1
        }
    }

    private companion object {
        const val DATABASE_EXISTENCE_TEST_NAME = "character-guide-existence-test.db"
    }
}
