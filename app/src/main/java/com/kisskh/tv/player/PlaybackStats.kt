package com.kisskh.tv.player

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks

/**
 * One line per event in logcat (tag `KhPlayback`), so a test run can measure what the viewer saw:
 * time to first frame, how often and how long it buffered, and that the position kept moving.
 * Nothing is sent anywhere.
 */
internal class PlaybackStats(url: String) {
    private val started = SystemClock.elapsedRealtime()
    private var firstFrameAt = 0L
    private var bufferingSince = 0L
    private var stalls = 0
    private var stalledMs = 0L
    private val host = runCatching { android.net.Uri.parse(url).host }.getOrNull().orEmpty()

    init {
        Log.i(TAG, "open host=$host")
    }

    fun firstFrame() {
        if (firstFrameAt != 0L) return
        firstFrameAt = SystemClock.elapsedRealtime()
        Log.i(TAG, "first-frame startupMs=${firstFrameAt - started} host=$host")
    }

    fun state(state: Int, positionMs: Long) {
        val now = SystemClock.elapsedRealtime()
        when (state) {
            Player.STATE_BUFFERING -> if (bufferingSince == 0L) {
                bufferingSince = now
                // Buffering before the first frame is startup, not a stall.
                if (firstFrameAt != 0L) stalls++
            }
            else -> if (bufferingSince != 0L) {
                if (firstFrameAt != 0L) stalledMs += now - bufferingSince
                bufferingSince = 0L
            }
        }
        Log.i(TAG, "state=${name(state)} pos=$positionMs stalls=$stalls stalledMs=$stalledMs")
    }

    private var tracksLogged = false

    /**
     * Once per stream: the audio and subtitle languages on offer, `*` on the ones playing, so a test
     * run can check that English was picked. Example: `audio=en*,ja text=en*,es`.
     */
    fun tracks(tracks: Tracks, preferEnglishAudio: Boolean) {
        if (tracksLogged) return
        tracksLogged = true
        fun describe(type: Int) = tracks.groups.filter { it.type == type }
            .flatMap { g -> (0 until g.length).map { (g.getTrackFormat(it).language ?: "und") + if (g.isTrackSelected(it)) "*" else "" } }
            .ifEmpty { listOf("none") }
            .joinToString(",")
        Log.i(TAG, "tracks audio=${describe(C.TRACK_TYPE_AUDIO)} text=${describe(C.TRACK_TYPE_TEXT)} preferEnglishAudio=$preferEnglishAudio host=$host")
    }

    fun error(code: String) = Log.w(TAG, "error=$code host=$host")

    fun end(positionMs: Long, durationMs: Long) {
        val watched = SystemClock.elapsedRealtime() - started
        Log.i(TAG, "close pos=$positionMs dur=$durationMs openMs=$watched stalls=$stalls stalledMs=$stalledMs host=$host")
    }

    companion object {
        const val TAG = "KhPlayback"

        fun tick(player: Player) {
            Log.i(
                TAG,
                "tick pos=${player.currentPosition} dur=${player.duration} playing=${player.isPlaying} " +
                    "state=${name(player.playbackState)} buffered=${player.bufferedPosition}",
            )
        }

        private fun name(state: Int) = when (state) {
            Player.STATE_IDLE -> "idle"
            Player.STATE_BUFFERING -> "buffering"
            Player.STATE_READY -> "ready"
            Player.STATE_ENDED -> "ended"
            else -> state.toString()
        }
    }
}
