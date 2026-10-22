package com.kisskh.tv.player

import android.annotation.SuppressLint
import android.net.Uri
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.kisskh.tv.R
import com.kisskh.tv.api.SubtitleFile
import com.kisskh.tv.api.media.SubLists
import com.kisskh.tv.download.OfflineSource
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhTheme
import com.kisskh.tv.ui.theme.isTelevision
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

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
    /**
     * True starts multi-audio streams in English. Anime passes false: its sub or dub choice is the
     * source's own audio setting, and KissKH dramas keep their original audio.
     */
    preferEnglishAudio: Boolean = true,
    /** Play a saved copy from the offline cache instead of [url]. */
    offline: OfflineSource? = null,
) {
    val context = LocalContext.current
    val reportProgress by rememberUpdatedState(onProgress)
    val reportError by rememberUpdatedState(onPlaybackError)
    val player = remember(url, requestHeaders, offline) {
        val http = DefaultHttpDataSource.Factory()
            // A sniffed stream carries the page's own user agent; some CDNs check it matches.
            .setUserAgent(requestHeaders["User-Agent"] ?: "Mozilla/5.0 (Linux; Android 14) Chrome/130 Safari/537.36")
            .setDefaultRequestProperties(requestHeaders - "User-Agent")
            .setAllowCrossProtocolRedirects(true)
        // DefaultDataSource keeps file:// working for the local decrypted subtitle files.
        val dataSource = offline?.dataSourceFactory ?: DefaultDataSource.Factory(context, http)
        // Calls and other apps pause the video (audio focus), and unplugging headphones pauses it too.
        val attributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setAudioAttributes(attributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .apply {
                // Always the best rendition the source offers, never a lower one picked for bandwidth.
                // English by default: English subtitles whenever the stream has them, and English audio
                // on multi-audio streams unless the caller keeps the original audio ([preferEnglishAudio]).
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setForceHighestSupportedBitrate(true)
                    .setPreferredTextLanguage("en")
                    .apply { if (preferEnglishAudio) setPreferredAudioLanguage("en") }
                    .build()
                // Only one English track gets the default flag, or Media3 may pick none of them.
                val defaultIndex = subs.indexOfFirst { subtitleLanguage(it).startsWith("en") }
                val subConfigs = subs.mapIndexed { i, s ->
                    val path = s.uri.substringBefore('?')
                    val mime = if (path.endsWith(".vtt", ignoreCase = true)) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(s.uri))
                        .setMimeType(mime)
                        .setLabel(s.label)
                        .setLanguage(subtitleLanguage(s))
                        .apply {
                            if (i == defaultIndex) setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        }
                        .build()
                }
                setMediaItem(
                    (offline?.mediaItem?.buildUpon() ?: MediaItem.Builder().setUri(url))
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
        val stats = PlaybackStats(url)
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) reportProgress(player.currentPosition, player.duration)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                stats.state(playbackState, player.currentPosition)
                if (playbackState == Player.STATE_ENDED) {
                    reportProgress(player.currentPosition, player.duration)
                }
            }

            override fun onRenderedFirstFrame() {
                stats.firstFrame()
                stats.tracks(player.currentTracks, preferEnglishAudio)
            }

            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("KissKH", "playback error ${error.errorCodeName}", error)
                stats.error(error.errorCodeName)
                reportError(playbackMessage(error))
            }
        }
        player.addListener(listener)
        onDispose {
            reportProgress(player.currentPosition, player.duration)
            stats.end(player.currentPosition, player.duration)
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        var ticks = 0
        while (isActive) {
            delay(PROGRESS_PERIOD_MS / 2)
            PlaybackStats.tick(player)
            if (++ticks % 2 == 0) reportProgress(player.currentPosition, player.duration)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                reportProgress(player.currentPosition, player.duration)
                // Leaving the app (Home, another app, screen off) pauses, as on Netflix. Play resumes.
                player.pause()
                playerView.value?.showController()
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

/** Subtitles (with Off), then audio and quality when the stream has more than one. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun TrackSheet(player: ExoPlayer, onClose: () -> Unit) {
    val groups = player.currentTracks.groups
    val text = groups.filter { it.type == C.TRACK_TYPE_TEXT }
    val audio = groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    val video = groups.filter { it.type == C.TRACK_TYPE_VIDEO }
    val params = player.trackSelectionParameters
    val textOff = C.TRACK_TYPE_TEXT in params.disabledTrackTypes ||
        text.none { g -> (0 until g.length).any { g.isTrackSelected(it) } }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    fun select(group: Tracks.Group, index: Int) {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(group.type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
            .build()
        onClose()
    }

    PlayerSheet(title = "Audio & subtitles", onClose = onClose) {
        SheetHeading("Subtitles")
        if (text.isEmpty()) {
            Text("No subtitles for this video.", style = KhTheme.typography.caption, color = KhColors.TextSecondary)
        } else {
            SheetOption("Off", selected = textOff, modifier = Modifier.focusRequester(first)) {
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
                onClose()
            }
            text.forEach { group ->
                for (index in 0 until group.length) {
                    if (!group.isTrackSupported(index)) continue
                    val format = group.getTrackFormat(index)
                    SheetOption(trackName(format.label, format.language, "Subtitles"), selected = !textOff && group.isTrackSelected(index)) {
                        select(group, index)
                    }
                }
            }
        }
        val voices = audio.flatMap { g -> (0 until g.length).filter { g.isTrackSupported(it) }.map { g to it } }
        if (voices.size > 1) {
            SheetHeading("Audio")
            voices.forEach { (group, index) ->
                val format = group.getTrackFormat(index)
                SheetOption(trackName(format.label, format.language, "Audio"), selected = group.isTrackSelected(index)) { select(group, index) }
            }
        }
        val renditions = video.flatMap { g -> (0 until g.length).filter { g.isTrackSupported(it) }.map { g to it } }
        if (renditions.size > 1) {
            SheetHeading("Quality")
            val auto = params.overrides.keys.none { it.type == C.TRACK_TYPE_VIDEO }
            SheetOption("Best available", selected = auto) {
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                    .build()
                onClose()
            }
            renditions.sortedByDescending { (g, i) -> g.getTrackFormat(i).height }.forEach { (group, index) ->
                val format = group.getTrackFormat(index)
                val name = if (format.height > 0) "${format.height}p" else format.label ?: "Video"
                SheetOption(name, selected = !auto && group.isTrackSelected(index)) { select(group, index) }
            }
        }
    }
}

/** A track's label, else its language in English ("ko" -> "Korean"), else [fallback]. */
private fun trackName(label: String?, language: String?, fallback: String): String {
    val name = label?.takeIf { it.isNotBlank() }
        ?: language?.takeIf { it.isNotBlank() && it != "und" }?.let { code ->
            Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH).ifBlank { code }
        }
    return name ?: fallback
}

private fun subtitleLanguage(s: SubtitleFile): String = SubLists.code(s.language, s.label)

private fun playbackMessage(error: PlaybackException): String = when (error.errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    -> "Can't reach the stream. Check your connection and try again."
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
    -> "This server refused the video."
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    -> "This video can't be played on this device."
    else -> "Playback stopped unexpectedly."
}
