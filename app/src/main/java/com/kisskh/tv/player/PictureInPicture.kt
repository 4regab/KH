package com.kisskh.tv.player

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import com.kisskh.tv.R
import com.kisskh.tv.ui.theme.isTelevision
import kotlin.math.roundToInt

/**
 * Picture-in-Picture for the built-in player only. The web players never use it.
 *
 * [VideoPlayer] says which player is on screen. [MainActivity] and the functions below turn that into
 * the Android PiP window: when it may start, its shape, and its three buttons.
 */
object Pip {
    /** The built-in player that is on screen and not failed, or null. Only [VideoPlayer] sets it. */
    var player: Player? by mutableStateOf(null)

    /**
     * True while the window is in picture-in-picture. [MainActivity] keeps it in step with the system.
     * The layout keeps its pre-PiP shape and the screens draw only the video while it is true.
     */
    var active: Boolean by mutableStateOf(false)
}

/** True when this device can show PiP: Android 8+, the system feature, and not a television. */
fun canPictureInPicture(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) &&
        !isTelevision(context)

/** True when the built-in player is on screen and playing, or about to play (loading more). */
@androidx.annotation.OptIn(UnstableApi::class)
fun pipPlaying(): Boolean = Pip.player?.let { !Util.shouldShowPlayButton(it) } ?: false

/**
 * Tells Android what the PiP window looks like, and whether Home may start it (Android 12+).
 * Call it whenever the playing state or the video shape changes.
 */
fun Activity.updatePipParams() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !canPictureInPicture(this)) return
    // Android can refuse while the activity is closing, and that is fine.
    runCatching { setPictureInPictureParams(pipParams()) }
}

/** Starts PiP now: the player's PiP button, and Home on Android 8 to 11. Does nothing without a built-in player. */
fun Activity.enterPip() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !canPictureInPicture(this) || Pip.player == null) return
    // Android can refuse (for example, while the activity is closing), and that is fine.
    runCatching { enterPictureInPictureMode(pipParams()) }
}

private const val ACTION_REWIND = "com.kisskh.tv.pip.REWIND"
private const val ACTION_PLAY_PAUSE = "com.kisskh.tv.pip.PLAY_PAUSE"
private const val ACTION_FORWARD = "com.kisskh.tv.pip.FORWARD"

// Android only allows a window between these two shapes (wide 2.39:1 and tall 1:2.39).
private const val MAX_ASPECT = 2.39f

@RequiresApi(Build.VERSION_CODES.O)
@androidx.annotation.OptIn(UnstableApi::class)
private fun Activity.pipParams(): PictureInPictureParams {
    val player = Pip.player
    val playing = pipPlaying()
    val builder = PictureInPictureParams.Builder().setAspectRatio(pipAspect(player))
    if (player != null) {
        builder.setActions(
            listOf(
                pipAction(R.drawable.ic_player_rew, R.string.player_rewind, ACTION_REWIND, 1),
                if (playing) {
                    pipAction(R.drawable.ic_player_pause, R.string.player_pause, ACTION_PLAY_PAUSE, 2)
                } else {
                    pipAction(R.drawable.ic_player_play, R.string.player_play, ACTION_PLAY_PAUSE, 2)
                },
                pipAction(R.drawable.ic_player_ffwd, R.string.player_forward, ACTION_FORWARD, 3),
            ),
        )
    }
    // Android 12+ starts PiP by itself when the viewer leaves, but only while the video plays.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setAutoEnterEnabled(playing)
    return builder.build()
}

/** The video's shape, kept inside what Android allows. 16:9 until the size is known. */
private fun pipAspect(player: Player?): Rational {
    val size = player?.videoSize
    if (size == null || size.width == 0 || size.height == 0) return Rational(16, 9)
    val ratio = size.width * size.pixelWidthHeightRatio / size.height
    return when {
        ratio > MAX_ASPECT -> Rational(239, 100)
        ratio < 1f / MAX_ASPECT -> Rational(100, 239)
        else -> Rational((ratio * 1000).roundToInt(), 1000)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun Activity.pipAction(@DrawableRes icon: Int, @StringRes title: Int, action: String, code: Int): RemoteAction {
    val text = getString(title)
    // Explicit package and FLAG_IMMUTABLE: only this app's receiver can get it, and nobody can change it.
    val intent = PendingIntent.getBroadcast(
        this,
        code,
        Intent(action).setPackage(packageName),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    return RemoteAction(Icon.createWithResource(this, icon), text, text, intent)
}

/** Handles the PiP window's buttons: back 10 s, play or pause, forward 10 s. */
class PipActions : BroadcastReceiver() {
    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        val player = Pip.player ?: return
        when (intent.action) {
            ACTION_REWIND -> player.seekBack()
            ACTION_FORWARD -> player.seekForward()
            ACTION_PLAY_PAUSE -> Util.handlePlayPauseButtonAction(player)
        }
    }

    /** Listens at run time, for this app only. Call [Context.unregisterReceiver] when the activity ends. */
    fun register(context: Context) {
        val filter = IntentFilter().apply {
            addAction(ACTION_REWIND)
            addAction(ACTION_PLAY_PAUSE)
            addAction(ACTION_FORWARD)
        }
        ContextCompat.registerReceiver(context, this, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
}
