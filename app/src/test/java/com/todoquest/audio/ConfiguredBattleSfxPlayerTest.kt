package com.todoquest.audio

import com.todoquest.domain.repository.BattleSfxSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfiguredBattleSfxPlayerTest {
    @Test
    fun enabledSettingPlaysEachEventAndEffectIdentityOnlyOnce() {
        val settings = FakeBattleSfxSettingsRepository(enabled = true)
        val delegate = RecordingBattleSfxPlayer()
        val player = ConfiguredBattleSfxPlayer(settings, delegate)

        player.play(BattleSfx.PLAYER_ATTACK, "combat-1")
        player.play(BattleSfx.PLAYER_ATTACK, "combat-1")
        player.play(BattleSfx.MONSTER_HIT, "combat-1")

        assertEquals(
            listOf(
                SfxPlaybackKey("combat-1", BattleSfx.PLAYER_ATTACK),
                SfxPlaybackKey("combat-1", BattleSfx.MONSTER_HIT),
            ),
            delegate.played,
        )
    }

    @Test
    fun mutedKeyIsConsumedBeforeSettingCheckAndDoesNotReplayWhenEnabled() {
        val settings = FakeBattleSfxSettingsRepository(enabled = false)
        val delegate = RecordingBattleSfxPlayer()
        val player = ConfiguredBattleSfxPlayer(settings, delegate)

        player.play(BattleSfx.PLAYER_HIT, "muted-event")
        settings.setEnabled(true)
        player.play(BattleSfx.PLAYER_HIT, "muted-event")
        player.play(BattleSfx.PLAYER_HIT, "fresh-event")

        assertEquals(
            listOf(SfxPlaybackKey("fresh-event", BattleSfx.PLAYER_HIT)),
            delegate.played,
        )
    }

    @Test
    fun blankEventIdsAreIgnoredWithoutCallingTheDelegate() {
        val delegate = RecordingBattleSfxPlayer()
        val player = ConfiguredBattleSfxPlayer(
            settingsRepository = FakeBattleSfxSettingsRepository(enabled = true),
            delegate = delegate,
        )

        player.play(BattleSfx.MONSTER_ATTACK, "")
        player.play(BattleSfx.MONSTER_ATTACK, "   ")

        assertTrue(delegate.played.isEmpty())
    }

    @Test
    fun cacheKeepsTheNewest256KeysAndEvictsOnlyTheOldestInsertion() {
        val delegate = RecordingBattleSfxPlayer()
        val player = ConfiguredBattleSfxPlayer(
            settingsRepository = FakeBattleSfxSettingsRepository(enabled = true),
            delegate = delegate,
        )
        repeat(257) { index ->
            player.play(BattleSfx.PLAYER_ATTACK, "event-$index")
        }

        player.play(BattleSfx.PLAYER_ATTACK, "event-1")
        player.play(BattleSfx.PLAYER_ATTACK, "event-0")

        assertEquals(258, delegate.played.size)
        assertEquals(
            SfxPlaybackKey("event-0", BattleSfx.PLAYER_ATTACK),
            delegate.played.last(),
        )
    }

    @Test
    fun delegateFailureIsIsolatedAfterTheKeyHasBeenConsumed() {
        val delegate = RecordingBattleSfxPlayer(
            playFailure = IllegalStateException("bad delegate"),
        )
        val player = ConfiguredBattleSfxPlayer(
            settingsRepository = FakeBattleSfxSettingsRepository(enabled = true),
            delegate = delegate,
        )

        player.play(BattleSfx.PLAYER_DEFEATED, "failure-event")
        delegate.playFailure = null
        player.play(BattleSfx.PLAYER_DEFEATED, "failure-event")

        assertEquals(1, delegate.playAttempts)
        assertTrue(delegate.played.isEmpty())
    }

    @Test
    fun releaseIsIdempotentAndDelegateFailuresDoNotEscape() {
        val delegate = RecordingBattleSfxPlayer(
            releaseFailure = IllegalStateException("release failed"),
        )
        val player = ConfiguredBattleSfxPlayer(
            settingsRepository = FakeBattleSfxSettingsRepository(enabled = true),
            delegate = delegate,
        )

        player.release()
        player.release()
        player.play(BattleSfx.MONSTER_DEFEATED, "after-release")

        assertEquals(1, delegate.releaseAttempts)
        assertEquals(0, delegate.playAttempts)
    }

    @Test
    fun noOpPlayerAcceptsPlayAndRepeatedRelease() {
        NoOpBattleSfxPlayer.play(BattleSfx.MONSTER_HIT, "event")
        NoOpBattleSfxPlayer.release()
        NoOpBattleSfxPlayer.release()
    }

    private class FakeBattleSfxSettingsRepository(
        enabled: Boolean,
    ) : BattleSfxSettingsRepository {
        private val mutableEnabled = MutableStateFlow(enabled)
        override val isEnabled: StateFlow<Boolean> = mutableEnabled

        override fun setEnabled(enabled: Boolean): Boolean {
            mutableEnabled.value = enabled
            return true
        }
    }

    private class RecordingBattleSfxPlayer(
        var playFailure: Throwable? = null,
        var releaseFailure: Throwable? = null,
    ) : BattleSfxPlayer {
        val played = mutableListOf<SfxPlaybackKey>()
        var playAttempts: Int = 0
            private set
        var releaseAttempts: Int = 0
            private set

        override fun play(effect: BattleSfx, eventId: String) {
            playAttempts += 1
            playFailure?.let { throw it }
            played += SfxPlaybackKey(eventId, effect)
        }

        override fun release() {
            releaseAttempts += 1
            releaseFailure?.let { throw it }
        }
    }
}
