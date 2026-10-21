package com.kisskh.tv.player

import android.annotation.SuppressLint
import android.net.Uri
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.kisskh.tv.R
import com.kisskh.tv.api.SubtitleFile
import com.kisskh.tv.ui.components.KhButton
import com.kisskh.tv.ui.components.KhChip
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.isTelevision
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** How often progress is written while a title is on screen. Pause, stop and exit save as well. */
private const val PROGRESS_PERIOD_MS = 10_000L

@UnstableApi
@Composable
fun VideoPlayer(
    url: String,
    subs: List<SubtitleFile>,
    resumeMs: Long,
    title: String,
    label: String,
    onProgress: (pos: Long, dur: Long) -> Unit,
    onPlaybackError: (String) -> Unit,
    /** Leave the player (same as system Back). */
    onBack: () -> Unit,
    /** Leave the player and go to the Home screen. */
    onHome: () -> Unit,
    /** Open the episode list. */
    onEpisodes: () -> Unit,
    /** Play the next episode, or null when there is none (the button is hidden). */
    onNext: (() -> Unit)?,
    /** Play the previous episode, or null when there is none. */
    onPrev: (() -> Unit)? = null,
    /** Open the server list. Hidden when null. */
    onServers: (() -> Unit)? = null,
    /** When false, Back is left to the screen (an error is already covering the video). */
    hideControlsOnBack: Boolean = true,
    /** Sent with the video and the subtitle files. KissKH stays on kisskh.co. */
    requestHeaders: Map<String, String> = PlayerHeaders.Kisskh,
    /** Movies have no episode list, so the button is hidden. */
    showEpisodes: Boolean = true,
) {
    val context = LocalContext.current
    val reportProgress by rememberUpdatedState(onProgress)
    val reportError by rememberUpdatedState(onPlaybackError)
    val player = remember(url, requestHeaders) {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14) Chrome/130 Safari/537.36")
            .setDefaultRequestProperties(requestHeaders)
            .setAllowCrossProtocolRedirects(true)
        // DefaultDataSource keeps file:// working for the local decrypted subtitle files.
        val dataSource = DefaultDataSource.Factory(context, http)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .apply {
                val subConfigs = subs.map { s ->
                    val path = s.uri.substringBefore('?')
                    val mime = if (path.endsWith(".vtt", ignoreCase = true)) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(s.uri))
                        .setMimeType(mime)
                        .setLabel(s.label)
                        .setLanguage(s.language)
                        .apply {
                            if (s.language.startsWith("en")) setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        }
                        .build()
                }
                setMediaItem(
                    MediaItem.Builder()
                        .setUri(url)
                        .setSubtitleConfigurations(subConfigs)
                        .build(),
                )
                if (resumeMs > 0) seekTo(resumeMs)
                playWhenReady = true
                prepare()
            }
    }

    val playerView = remember { mutableStateOf<PlayerView?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var showTracks by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) reportProgress(player.currentPosition, player.duration)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    reportProgress(player.currentPosition, player.duration)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("KissKH", "playback error ${error.errorCodeName}", error)
                reportError(playbackMessage(error))
            }
        }
        player.addListener(listener)
        onDispose {
            reportProgress(player.currentPosition, player.duration)
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (isActive) {
            delay(PROGRESS_PERIOD_MS)
            reportProgress(player.currentPosition, player.duration)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                reportProgress(player.currentPosition, player.duration)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Back hides the chrome first. A second Back leaves the player.
    BackHandler(enabled = hideControlsOnBack && controlsVisible) {
        playerView.value?.hideController()
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                val view = LayoutInflater.from(viewContext)
                    .inflate(R.layout.kh_player_view, null, false) as PlayerView
                view.player = player
                view.subtitleView?.setApplyEmbeddedStyles(true)
                view.setControllerVisibilityListener(object : PlayerView.ControllerVisibilityListener {
                    override fun onVisibilityChanged(visibility: Int) {
                        controlsVisible = visibility == View.VISIBLE
                    }
                })
                view.findViewById<TextView>(R.id.kh_player_title).text = title
                view.findViewById<TextView>(R.id.kh_player_label).text = label
                view.showController()
                view.requestFocus()
                if (!isTelevision(viewContext)) view.enableTouchGestures(player)
                playerView.value = view
                view
            },
            update = { view ->
                view.findViewById<TextView>(R.id.kh_player_title).text = title
                view.findViewById<TextView>(R.id.kh_player_label).text = label
                view.findViewById<View>(R.id.kh_player_back).setOnClickListener { onBack() }
                view.findViewById<View>(R.id.kh_player_home).setOnClickListener { onHome() }
                view.findViewById<View>(R.id.kh_player_episodes).apply {
                    visibility = if (showEpisodes) View.VISIBLE else View.GONE
                    setOnClickListener { if (showEpisodes) onEpisodes() }
                }
                view.findViewById<View>(R.id.kh_player_prev).apply {
                    visibility = if (onPrev != null) View.VISIBLE else View.GONE
                    setOnClickListener { onPrev?.invoke() }
                }
                view.findViewById<View>(R.id.kh_player_next).apply {
                    visibility = if (onNext != null) View.VISIBLE else View.GONE
                    setOnClickListener { onNext?.invoke() }
                }
                view.findViewById<View>(R.id.kh_player_servers).apply {
                    visibility = if (onServers != null) View.VISIBLE else View.GONE
                    setOnClickListener { onServers?.invoke() }
                }
                view.findViewById<View>(R.id.kh_player_tracks).setOnClickListener { showTracks = true }
            },
        )
        if (showTracks) {
            TrackSheet(player, onClose = { showTracks = false })
        }
    }
}

/**
 * Phone touch, like YouTube and Netflix: one tap shows or hides the controls, a double tap on the left
 * or right half skips back or forward 10 s. The controls' own buttons still get their taps first.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@SuppressLint("ClickableViewAccessibility")
private fun PlayerView.enableTouchGestures(player: Player) {
    // TalkBack's click goes through performClick, which then also hides the controls.
    controllerHideOnTouch = true
    val detector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (isControllerFullyVisible) hideController() else showController()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (e.x < width / 2f) player.seekBack() else player.seekForward()
                return true
            }
        },
    )
    setOnTouchListener { _, event -> detector.onTouchEvent(event) }
}

object PlayerHeaders {
    val Kisskh = mapOf("Referer" to "https://kisskh.co/", "Origin" to "https://kisskh.co")
    val CineJoy = mapOf("Referer" to "https://cinejoy.pk/", "Origin" to "https://cinejoy.pk")
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun TrackSheet(player: ExoPlayer, onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Audio, subtitles and quality", color = KhColors.TextPrimary)
        KhButton("Close", onClick = onClose, primary = true)
        KhChip(
            text = "Subtitles off",
            onClick = {
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
                onClose()
            },
        )
        player.currentTracks.groups.forEach { group ->
            val heading = when (group.type) {
                C.TRACK_TYPE_VIDEO -> "Quality"
                C.TRACK_TYPE_AUDIO -> "Audio"
                C.TRACK_TYPE_TEXT -> "Subtitles"
                else -> return@forEach
            }
            for (index in 0 until group.length) {
                if (!group.isTrackSupported(index)) continue
                val format = group.getTrackFormat(index)
                val name = when (group.type) {
                    C.TRACK_TYPE_VIDEO -> if (format.height > 0) "${format.height}p" else format.label ?: "Video"
                    else -> format.label ?: format.language ?: heading
                }
                KhChip(
                    text = "$heading · $name",
                    selected = group.isTrackSelected(index),
                    onClick = {
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(group.type, false)
                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                            .build()
                        onClose()
                    },
                )
            }
        }
    }
}

private fun playbackMessage(error: PlaybackException): String = when (error.errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    -> "Can't reach the stream. Check the connection and try again."
    else -> "Playback failed."
}
