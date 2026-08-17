package com.todoquest.audio

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioAttributes
import androidx.test.core.app.ApplicationProvider
import com.todoquest.R
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
class AndroidBattleSfxPlayerTest {
    private lateinit var context: Context
    private lateinit var backend: FakeSoundPoolBackend
    private lateinit var factory: FakeSoundPoolBackendFactory
    private lateinit var foregroundGate: FakeBattleSfxForegroundGate
    private lateinit var logger: FakeBattleSfxLogger

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        backend = FakeSoundPoolBackend()
        factory = FakeSoundPoolBackendFactory(backend)
        foregroundGate = FakeBattleSfxForegroundGate(isForeground = true)
        logger = FakeBattleSfxLogger()
    }

    @Test
    fun constructorUsesGameSonificationAttributesAndPreloadsSixResourcesExactlyOnce() {
        AndroidBattleSfxPlayer(
            context = ContextWrapper(context),
            backendFactory = factory,
            foregroundGate = foregroundGate,
            logger = logger,
        )

        assertEquals(AudioAttributes.USAGE_GAME, factory.attributes?.usage)
        assertEquals(
            AudioAttributes.CONTENT_TYPE_SONIFICATION,
            factory.attributes?.contentType,
        )
        assertEquals(6, factory.maxStreams)
        assertEquals(EXPECTED_RESOURCES.values.toList(), backend.loadRequests)
        assertEquals(6, backend.loadRequests.distinct().size)
        assertTrue(backend.loadContexts.all { it === context.applicationContext })
        assertEquals(1, backend.listenerRegistrations)
    }

    @Test
    fun requestsBeforeLoadCompletionAreDroppedAndLoadedEffectsUseTheirMappedSample() {
        val player = createPlayer()

        player.play(BattleSfx.PLAYER_ATTACK, "attack-before-load")
        assertTrue(backend.playRequests.isEmpty())

        backend.completeLoad(resourceId = R.raw.sfx_player_attack, status = 0)
        player.play(BattleSfx.PLAYER_ATTACK, "attack-loaded")

        assertEquals(
            listOf(
                PlayRequest(
                    sampleId = backend.sampleIdFor(R.raw.sfx_player_attack),
                    leftVolume = 1f,
                    rightVolume = 1f,
                    priority = 1,
                    loop = 0,
                    rate = 1f,
                ),
            ),
            backend.playRequests,
        )
        assertEquals(EXPECTED_RESOURCES.values.toList(), backend.loadRequests)
    }

    @Test
    fun eachEffectUsesItsOwnLoadedRawResourceWithoutReloadingDuringPlay() {
        val player = createPlayer()
        EXPECTED_RESOURCES.values.forEach { backend.completeLoad(it, status = 0) }

        EXPECTED_RESOURCES.keys.forEachIndexed { index, effect ->
            player.play(effect, "event-$index")
        }

        assertEquals(
            EXPECTED_RESOURCES.values.map { backend.sampleIdFor(it) },
            backend.playRequests.map(PlayRequest::sampleId),
        )
        assertEquals(6, backend.loadRequests.size)
    }

    @Test
    fun failedOrInvalidLoadsRemainUnavailableAndNeverPlay() {
        backend.zeroSampleResources += R.raw.sfx_player_defeated
        val player = createPlayer()
        backend.completeLoad(R.raw.sfx_monster_defeated, status = 1)

        player.play(BattleSfx.MONSTER_DEFEATED, "failed-load")
        player.play(BattleSfx.PLAYER_DEFEATED, "invalid-sample")

        assertTrue(backend.playRequests.isEmpty())
    }

    @Test
    fun oneLoadExceptionDoesNotPreventTheOtherResourcesFromLoading() {
        backend.throwingLoadResources += R.raw.sfx_monster_attack

        val player = createPlayer()
        backend.completeLoad(R.raw.sfx_player_hit, status = 0)
        player.play(BattleSfx.MONSTER_ATTACK, "load-exception")
        player.play(BattleSfx.PLAYER_HIT, "loaded-after-exception")

        assertEquals(EXPECTED_RESOURCES.values.toList(), backend.loadRequests)
        assertEquals(
            listOf(backend.sampleIdFor(R.raw.sfx_player_hit)),
            backend.playRequests.map(PlayRequest::sampleId),
        )
        assertEquals(1, logger.failures.size)
    }

    @Test
    fun playExceptionsAndZeroStreamIdsAreLoggedWithoutEscapingToTheCaller() {
        val player = createPlayer()
        backend.completeLoad(R.raw.sfx_monster_hit, status = 0)
        backend.playFailure = IllegalStateException("play failed")

        player.play(BattleSfx.MONSTER_HIT, "throwing-play")

        backend.playFailure = null
        backend.playResult = 0
        player.play(BattleSfx.MONSTER_HIT, "zero-stream")

        assertEquals(2, backend.playRequests.size)
        assertEquals(2, logger.failures.size)
    }

    @Test
    fun backgroundRequestsAreDroppedWithoutPendingPlaybackAfterForegroundReturn() {
        val player = createPlayer()
        backend.completeLoad(R.raw.sfx_player_hit, status = 0)

        foregroundGate.isForeground = false
        player.play(BattleSfx.PLAYER_HIT, "background-event")
        foregroundGate.isForeground = true

        assertTrue(backend.playRequests.isEmpty())

        player.play(BattleSfx.PLAYER_HIT, "new-foreground-event")
        assertEquals(1, backend.playRequests.size)
    }

    @Test
    fun releaseIsIdempotentUnregistersCallbacksAndDropsLaterRequests() {
        val player = createPlayer()
        backend.completeLoad(R.raw.sfx_player_attack, status = 0)

        player.release()
        player.release()
        player.play(BattleSfx.PLAYER_ATTACK, "after-release")

        assertEquals(1, backend.releaseCalls)
        assertEquals(1, backend.listenerClears)
        assertEquals(1, foregroundGate.releaseCalls)
        assertTrue(backend.playRequests.isEmpty())
    }

    @Test
    fun activityLifecycleGateTracksMultipleResumedActivitiesAndIgnoresCallbacksAfterRelease() {
        val application = context.applicationContext as Application
        val gate = ActivityLifecycleBattleSfxForegroundGate(application)
        val first = Activity()
        val second = Activity()

        assertFalse(gate.isForeground)
        gate.onActivityResumed(first)
        gate.onActivityResumed(second)
        gate.onActivityPaused(first)
        assertTrue(gate.isForeground)
        gate.onActivityPaused(second)
        assertFalse(gate.isForeground)

        gate.release()
        gate.onActivityResumed(first)
        assertFalse(gate.isForeground)
    }

    private fun createPlayer(): AndroidBattleSfxPlayer = AndroidBattleSfxPlayer(
        context = context,
        backendFactory = factory,
        foregroundGate = foregroundGate,
        logger = logger,
    )

    private class FakeSoundPoolBackendFactory(
        private val backend: FakeSoundPoolBackend,
    ) : BattleSfxBackendFactory {
        var attributes: AudioAttributes? = null
            private set
        var maxStreams: Int? = null
            private set

        override fun create(
            audioAttributes: AudioAttributes,
            maxStreams: Int,
        ): BattleSfxBackend {
            attributes = audioAttributes
            this.maxStreams = maxStreams
            return backend
        }
    }

    private class FakeSoundPoolBackend : BattleSfxBackend {
        val loadRequests = mutableListOf<Int>()
        val loadContexts = mutableListOf<Context>()
        val playRequests = mutableListOf<PlayRequest>()
        val zeroSampleResources = mutableSetOf<Int>()
        val throwingLoadResources = mutableSetOf<Int>()
        var playFailure: Throwable? = null
        var playResult: Int = 1
        var releaseCalls: Int = 0
            private set
        var listenerRegistrations: Int = 0
            private set
        var listenerClears: Int = 0
            private set
        private var listener: BattleSfxLoadListener? = null
        private val sampleIdsByResource = linkedMapOf<Int, Int>()

        override fun setOnLoadCompleteListener(listener: BattleSfxLoadListener?) {
            this.listener = listener
            if (listener == null) {
                listenerClears += 1
            } else {
                listenerRegistrations += 1
            }
        }

        override fun load(context: Context, resourceId: Int, priority: Int): Int {
            loadContexts += context
            loadRequests += resourceId
            if (resourceId in throwingLoadResources) {
                throw IllegalArgumentException("load failed for $resourceId")
            }
            if (resourceId in zeroSampleResources) return 0
            return sampleIdsByResource.getOrPut(resourceId) { sampleIdsByResource.size + 10 }
        }

        override fun play(
            sampleId: Int,
            leftVolume: Float,
            rightVolume: Float,
            priority: Int,
            loop: Int,
            rate: Float,
        ): Int {
            playRequests += PlayRequest(
                sampleId = sampleId,
                leftVolume = leftVolume,
                rightVolume = rightVolume,
                priority = priority,
                loop = loop,
                rate = rate,
            )
            playFailure?.let { throw it }
            return playResult
        }

        override fun release() {
            releaseCalls += 1
        }

        fun completeLoad(resourceId: Int, status: Int) {
            listener?.onLoadComplete(sampleIdFor(resourceId), status)
        }

        fun sampleIdFor(resourceId: Int): Int = checkNotNull(sampleIdsByResource[resourceId])
    }

    private class FakeBattleSfxForegroundGate(
        override var isForeground: Boolean,
    ) : BattleSfxForegroundGate {
        var releaseCalls: Int = 0
            private set

        override fun release() {
            releaseCalls += 1
        }
    }

    private class FakeBattleSfxLogger : BattleSfxLogger {
        val failures = mutableListOf<Throwable?>()

        override fun log(message: String, failure: Throwable?) {
            failures += failure
        }
    }

    private companion object {
        val EXPECTED_RESOURCES = linkedMapOf(
            BattleSfx.PLAYER_ATTACK to R.raw.sfx_player_attack,
            BattleSfx.MONSTER_ATTACK to R.raw.sfx_monster_attack,
            BattleSfx.PLAYER_HIT to R.raw.sfx_player_hit,
            BattleSfx.MONSTER_HIT to R.raw.sfx_monster_hit,
            BattleSfx.MONSTER_DEFEATED to R.raw.sfx_monster_defeated,
            BattleSfx.PLAYER_DEFEATED to R.raw.sfx_player_defeated,
        )
    }
}

internal data class PlayRequest(
    val sampleId: Int,
    val leftVolume: Float,
    val rightVolume: Float,
    val priority: Int,
    val loop: Int,
    val rate: Float,
)
