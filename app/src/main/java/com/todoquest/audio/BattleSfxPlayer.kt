package com.todoquest.audio

import com.todoquest.domain.repository.BattleSfxSettingsRepository

enum class BattleSfx {
    PLAYER_ATTACK,
    MONSTER_ATTACK,
    PLAYER_HIT,
    MONSTER_HIT,
    MONSTER_DEFEATED,
    PLAYER_DEFEATED,
}

data class SfxPlaybackKey(
    val eventId: String,
    val effect: BattleSfx,
)

interface BattleSfxPlayer {
    fun play(effect: BattleSfx, eventId: String)

    fun release()
}

class ConfiguredBattleSfxPlayer(
    private val settingsRepository: BattleSfxSettingsRepository,
    private val delegate: BattleSfxPlayer,
) : BattleSfxPlayer {
    private val lock = Any()
    private val consumedKeys = LinkedHashSet<SfxPlaybackKey>()
    private var released = false

    override fun play(effect: BattleSfx, eventId: String) {
        if (eventId.isBlank()) return
        val key = SfxPlaybackKey(eventId = eventId, effect = effect)
        val canPlay = synchronized(lock) {
            if (!consumedKeys.add(key)) return@synchronized false
            if (consumedKeys.size > MAX_CONSUMED_KEYS) {
                val oldest = consumedKeys.iterator()
                if (oldest.hasNext()) {
                    oldest.next()
                    oldest.remove()
                }
            }
            !released
        }
        if (!canPlay || !settingsRepository.isEnabled.value) return

        try {
            delegate.play(effect = effect, eventId = eventId)
        } catch (_: Throwable) {
            // Audio is transient presentation and must not fail the combat timeline.
        }
    }

    override fun release() {
        val shouldRelease = synchronized(lock) {
            if (released) {
                false
            } else {
                released = true
                true
            }
        }
        if (!shouldRelease) return
        try {
            delegate.release()
        } catch (_: Throwable) {
            // A broken audio backend must not escape application teardown.
        }
    }

    private companion object {
        private const val MAX_CONSUMED_KEYS = 256
    }
}

object NoOpBattleSfxPlayer : BattleSfxPlayer {
    override fun play(effect: BattleSfx, eventId: String) = Unit

    override fun release() = Unit
}
