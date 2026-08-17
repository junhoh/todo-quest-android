package com.todoquest.audio

import android.app.Activity
import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.util.Log
import com.todoquest.R
import java.util.EnumMap

class AndroidBattleSfxPlayer internal constructor(
    context: Context,
    backendFactory: BattleSfxBackendFactory,
    private val foregroundGate: BattleSfxForegroundGate,
    private val logger: BattleSfxLogger,
) : BattleSfxPlayer {
    constructor(context: Context) : this(
        context = context.applicationContext,
        backendFactory = AndroidSoundPoolBackendFactory,
        foregroundGate = ActivityLifecycleBattleSfxForegroundGate(
            requireApplication(context.applicationContext),
        ),
        logger = AndroidBattleSfxLogger,
    )

    private val applicationContext = context.applicationContext
    private val lock = Any()
    private val sampleIdsByEffect = EnumMap<BattleSfx, Int>(BattleSfx::class.java)
    private val loadStatusBySampleId = mutableMapOf<Int, Int>()
    private var released = false
    private val backend = backendFactory.create(
        audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build(),
        maxStreams = MAX_STREAMS,
    )

    init {
        backend.setOnLoadCompleteListener(
            BattleSfxLoadListener { sampleId, status ->
                synchronized(lock) {
                    if (!released) {
                        loadStatusBySampleId[sampleId] = status
                    }
                }
                if (status != LOAD_SUCCESS) {
                    logger.log(
                        "Battle SFX load failed for sample=$sampleId status=$status",
                        failure = null,
                    )
                }
            },
        )
        RESOURCES_BY_EFFECT.forEach { (effect, resourceId) ->
            val sampleId = try {
                backend.load(applicationContext, resourceId, LOAD_PRIORITY)
            } catch (failure: Throwable) {
                logger.log("Battle SFX load threw for effect=$effect", failure)
                null
            }
            if (sampleId == null) {
                return@forEach
            }
            if (sampleId > 0) {
                synchronized(lock) {
                    sampleIdsByEffect[effect] = sampleId
                }
            } else if (sampleId == 0) {
                logger.log(
                    "Battle SFX load returned no sample for effect=$effect",
                    failure = null,
                )
            }
        }
    }

    override fun play(effect: BattleSfx, eventId: String) {
        synchronized(lock) {
            if (released || !foregroundGate.isForeground) return
            val sampleId = sampleIdsByEffect[effect] ?: return
            if (loadStatusBySampleId[sampleId] != LOAD_SUCCESS) return
            try {
                val streamId = backend.play(
                    sampleId = sampleId,
                    leftVolume = PLAYBACK_VOLUME,
                    rightVolume = PLAYBACK_VOLUME,
                    priority = PLAYBACK_PRIORITY,
                    loop = NO_LOOP,
                    rate = NORMAL_RATE,
                )
                if (streamId == 0) {
                    logger.log(
                        "Battle SFX play returned no stream for effect=$effect event=$eventId",
                        failure = null,
                    )
                }
            } catch (failure: Throwable) {
                logger.log("Battle SFX play failed for effect=$effect event=$eventId", failure)
            }
        }
    }

    override fun release() {
        synchronized(lock) {
            if (released) return
            released = true
            sampleIdsByEffect.clear()
            loadStatusBySampleId.clear()
        }
        try {
            backend.setOnLoadCompleteListener(null)
        } catch (failure: Throwable) {
            logger.log("Battle SFX load callback cleanup failed", failure)
        }
        try {
            backend.release()
        } catch (failure: Throwable) {
            logger.log("Battle SFX backend release failed", failure)
        }
        try {
            foregroundGate.release()
        } catch (failure: Throwable) {
            logger.log("Battle SFX foreground callback cleanup failed", failure)
        }
    }

    private companion object {
        private const val MAX_STREAMS = 6
        private const val LOAD_PRIORITY = 1
        private const val LOAD_SUCCESS = 0
        private const val PLAYBACK_VOLUME = 1f
        private const val PLAYBACK_PRIORITY = 1
        private const val NO_LOOP = 0
        private const val NORMAL_RATE = 1f

        private val RESOURCES_BY_EFFECT = linkedMapOf(
            BattleSfx.PLAYER_ATTACK to R.raw.sfx_player_attack,
            BattleSfx.MONSTER_ATTACK to R.raw.sfx_monster_attack,
            BattleSfx.PLAYER_HIT to R.raw.sfx_player_hit,
            BattleSfx.MONSTER_HIT to R.raw.sfx_monster_hit,
            BattleSfx.MONSTER_DEFEATED to R.raw.sfx_monster_defeated,
            BattleSfx.PLAYER_DEFEATED to R.raw.sfx_player_defeated,
        )

        private fun requireApplication(context: Context): Application =
            context as? Application
                ?: error("Battle SFX foreground tracking requires an application context")
    }
}

internal fun interface BattleSfxLoadListener {
    fun onLoadComplete(sampleId: Int, status: Int)
}

internal interface BattleSfxBackend {
    fun setOnLoadCompleteListener(listener: BattleSfxLoadListener?)

    fun load(context: Context, resourceId: Int, priority: Int): Int

    fun play(
        sampleId: Int,
        leftVolume: Float,
        rightVolume: Float,
        priority: Int,
        loop: Int,
        rate: Float,
    ): Int

    fun release()
}

internal fun interface BattleSfxBackendFactory {
    fun create(audioAttributes: AudioAttributes, maxStreams: Int): BattleSfxBackend
}

private object AndroidSoundPoolBackendFactory : BattleSfxBackendFactory {
    override fun create(
        audioAttributes: AudioAttributes,
        maxStreams: Int,
    ): BattleSfxBackend = AndroidSoundPoolBackend(
        SoundPool.Builder()
            .setAudioAttributes(audioAttributes)
            .setMaxStreams(maxStreams)
            .build(),
    )
}

private class AndroidSoundPoolBackend(
    private val soundPool: SoundPool,
) : BattleSfxBackend {
    override fun setOnLoadCompleteListener(listener: BattleSfxLoadListener?) {
        if (listener == null) {
            soundPool.setOnLoadCompleteListener(null)
        } else {
            soundPool.setOnLoadCompleteListener { _, sampleId, status ->
                listener.onLoadComplete(sampleId, status)
            }
        }
    }

    override fun load(context: Context, resourceId: Int, priority: Int): Int =
        soundPool.load(context, resourceId, priority)

    override fun play(
        sampleId: Int,
        leftVolume: Float,
        rightVolume: Float,
        priority: Int,
        loop: Int,
        rate: Float,
    ): Int = soundPool.play(
        sampleId,
        leftVolume,
        rightVolume,
        priority,
        loop,
        rate,
    )

    override fun release() {
        soundPool.release()
    }
}

internal interface BattleSfxForegroundGate {
    val isForeground: Boolean

    fun release()
}

internal class ActivityLifecycleBattleSfxForegroundGate(
    private val application: Application,
) : BattleSfxForegroundGate, Application.ActivityLifecycleCallbacks {
    private val lock = Any()
    private var resumedActivityCount = 0
    private var released = false

    init {
        application.registerActivityLifecycleCallbacks(this)
    }

    override val isForeground: Boolean
        get() = synchronized(lock) { !released && resumedActivityCount > 0 }

    override fun onActivityResumed(activity: Activity) {
        synchronized(lock) {
            if (!released) resumedActivityCount += 1
        }
    }

    override fun onActivityPaused(activity: Activity) {
        synchronized(lock) {
            if (!released) resumedActivityCount = (resumedActivityCount - 1).coerceAtLeast(0)
        }
    }

    override fun release() {
        val shouldUnregister = synchronized(lock) {
            if (released) {
                false
            } else {
                released = true
                resumedActivityCount = 0
                true
            }
        }
        if (shouldUnregister) {
            application.unregisterActivityLifecycleCallbacks(this)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}

internal fun interface BattleSfxLogger {
    fun log(message: String, failure: Throwable?)
}

private object AndroidBattleSfxLogger : BattleSfxLogger {
    override fun log(message: String, failure: Throwable?) {
        if (failure == null) {
            Log.w(TAG, message)
        } else {
            Log.w(TAG, message, failure)
        }
    }

    private const val TAG = "TodoQuestBattleSfx"
}
