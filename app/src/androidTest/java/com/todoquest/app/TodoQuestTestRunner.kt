package com.todoquest.app

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import com.todoquest.audio.BattleSfx
import com.todoquest.audio.BattleSfxPlayer
import com.todoquest.audio.ConfiguredBattleSfxPlayer
import com.todoquest.audio.SfxPlaybackKey
import com.todoquest.domain.repository.BattleSfxSettingsRepository
import com.todoquest.notification.ExternalReminderSmokeFixture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TodoQuestTestRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle) {
        if (
            !arguments.containsKey(ARGUMENT_TEST_CLASS) &&
            !arguments.containsKey(ARGUMENT_TEST_ANNOTATION) &&
            !arguments.containsKey(ARGUMENT_NOT_TEST_ANNOTATION)
        ) {
            arguments.putString(
                ARGUMENT_NOT_TEST_ANNOTATION,
                ExternalReminderSmokeFixture::class.java.name,
            )
        }
        super.onCreate(arguments)
    }

    override fun newApplication(
        cl: ClassLoader,
        className: String,
        context: Context,
    ): Application = super.newApplication(
        cl,
        TodoQuestInstrumentedTestApplication::class.java.name,
        context,
    )

    override fun onStart() {
        (targetContext.applicationContext as? TodoQuestInstrumentedTestApplication)
            ?.resetBattleSfxTestState()
        val databaseFile = targetContext.getDatabasePath(DATABASE_NAME)
        if (databaseFile.exists() && !targetContext.deleteDatabase(DATABASE_NAME)) {
            throw IllegalStateException("Failed to reset test database: ${databaseFile.absolutePath}")
        }
        super.onStart()
    }

    private companion object {
        const val DATABASE_NAME = TodoQuestAppContainer.DATABASE_NAME
        const val ARGUMENT_TEST_CLASS = "class"
        const val ARGUMENT_TEST_ANNOTATION = "annotation"
        const val ARGUMENT_NOT_TEST_ANNOTATION = "notAnnotation"
    }
}

class TodoQuestInstrumentedTestApplication : Application(), TodoQuestContainerOwner {
    @Volatile
    private var testContainer: TodoQuestAppContainer? = null

    val battleSfxSettingsRepository = InMemoryBattleSfxSettingsRepository()
    val recordingBattleSfxPlayer = FakeBattleSfxPlayer()

    private val configuredBattleSfxPlayer = ConfiguredBattleSfxPlayer(
        settingsRepository = battleSfxSettingsRepository,
        delegate = recordingBattleSfxPlayer,
    )

    private val productionContainerDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        TodoQuestAppContainer.createForTest(
            context = this,
            battleSfxSettingsRepository = battleSfxSettingsRepository,
            battleSfxPlayer = configuredBattleSfxPlayer,
        )
    }
    private val productionContainer: TodoQuestAppContainer
        get() = productionContainerDelegate.value

    override val todoQuestContainer: TodoQuestAppContainer
        get() = testContainer ?: productionContainer

    fun installTestContainer(container: TodoQuestAppContainer?) {
        testContainer = container
    }

    fun resetBattleSfxTestState() {
        battleSfxSettingsRepository.reset(enabled = true)
        recordingBattleSfxPlayer.clear()
    }

    override fun onTerminate() {
        testContainer?.releaseAudio()
        if (productionContainerDelegate.isInitialized()) {
            productionContainer.releaseAudio()
        }
        super.onTerminate()
    }
}

class InMemoryBattleSfxSettingsRepository : BattleSfxSettingsRepository {
    private val mutableEnabled = MutableStateFlow(true)
    override val isEnabled: StateFlow<Boolean> = mutableEnabled

    override fun setEnabled(enabled: Boolean): Boolean {
        mutableEnabled.value = enabled
        return true
    }

    fun reset(enabled: Boolean) {
        mutableEnabled.value = enabled
    }
}

class FakeBattleSfxPlayer : BattleSfxPlayer {
    private val lock = Any()
    private val recordedRequests = mutableListOf<SfxPlaybackKey>()
    var releaseCalls: Int = 0
        private set

    override fun play(effect: BattleSfx, eventId: String) {
        synchronized(lock) {
            recordedRequests += SfxPlaybackKey(eventId = eventId, effect = effect)
        }
    }

    override fun release() {
        synchronized(lock) {
            releaseCalls += 1
        }
    }

    fun requests(): List<SfxPlaybackKey> = synchronized(lock) {
        recordedRequests.toList()
    }

    fun clear() {
        synchronized(lock) {
            recordedRequests.clear()
        }
    }
}
