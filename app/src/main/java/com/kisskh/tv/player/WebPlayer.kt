@file:OptIn(ExperimentalComposeUiApi::class)

package com.kisskh.tv.player

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.kisskh.tv.R
import com.kisskh.tv.api.media.EmbedFailover
import com.kisskh.tv.api.media.EmbedNav
import com.kisskh.tv.api.media.EmbedPage
import com.kisskh.tv.api.media.EmbedPolicy
import com.kisskh.tv.ui.components.KhButton
import com.kisskh.tv.ui.components.KhChip
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhFocusable
import com.kisskh.tv.ui.theme.KhMotion
import com.kisskh.tv.ui.theme.KhTheme
import com.kisskh.tv.ui.theme.isTelevision
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.ByteArrayInputStream

/** One page the web player can show. [code] is the short chip label, [name] the spoken one. */
data class WebSource(val name: String, val code: String, val url: String)

/**
 * A full-screen web page that plays video, with the same overlay as the KissKH player: title, Back,
 * Home and Episodes at the top, Next and Servers at the bottom right. The page fills the screen. The
 * overlay fades out after [HIDE_AFTER_MS] and comes back with any key.
 *
 * Keys, with the overlay hidden: OK taps the middle of the page (play or pause), Left and Right go to
 * the page (players seek), Up or Down show the overlay. With the overlay shown, keys move between its
 * buttons, and Back hides it before leaving.
 *
 * [sources] are tried in order. A page that fails to load, returns an HTTP error, redirects to a known
 * ad host, sends us to its home page, blanks itself, or shows a short "not found" page moves to the next.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebPlayer(
    key: Any,
    title: String,
    label: String,
    sources: List<WebSource>,
    referer: String?,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onEpisodes: (() -> Unit)?,
    onNext: (() -> Unit)?,
    userAgent: String? = DESKTOP_UA,
    /** Opens the caller's own server list. When null, the player lists [sources] itself. */
    onServers: (() -> Unit)? = null,
    /** Called when every source failed, instead of showing the player's own error. */
    onFailed: ((String) -> Unit)? = null,
) {
    var index by remember(key) { mutableIntStateOf(0) }
    var note by remember(key) { mutableStateOf<String?>(null) }
    var gaveUp by remember(key) { mutableStateOf(false) }
    var stamp by remember(key) { mutableIntStateOf(0) }
    var picking by remember(key) { mutableStateOf(false) }
    val tried = remember(key) { LinkedHashSet<Int>() }
    val gate = remember { LoadGate() }
    val web = remember { mutableStateOf<WebView?>(null) }

    var overlay by remember { mutableStateOf(true) }
    var lastKey by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    val playFocus = remember { FocusRequester() }
    var overlayFocused by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val audio = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
    var playing by remember { mutableStateOf(false) }
    var clock by remember { mutableStateOf<VideoClock?>(null) }
    val bridge = remember { VideoBridge { clock = it } }
    var fill by remember { mutableStateOf(PlayerZoom.isFill(context)) }
    var zoomHint by remember { mutableStateOf<String?>(null) }
    // False when the WebView is too old to carry the bridge: the Fit / Fill button would do nothing, so hide it.
    var zoomable by remember { mutableStateOf(false) }

    fun setZoom(next: Boolean) {
        if (next == fill) return
        fill = next
        PlayerZoom.save(context, next)
        bridge.setZoom(next)
        zoomHint = PlayerZoom.hint(next)
    }
    // A saved Fill reaches each page as soon as its video reports in. Fit is the page's own look, left alone.
    LaunchedEffect(bridge) { if (fill) bridge.setZoom(true) }

    /** Play or pause: through the video itself when it can be reached, else a tap on the page. */
    fun toggle() {
        if (bridge.live && bridge.toggle()) return
        web.value?.let {
            it.tapCenter()
            gate.tapped(it)
        }
    }

    fun seekBy(seconds: Double) {
        val c = clock ?: return
        bridge.seekTo((c.position + seconds).coerceIn(0.0, c.duration - 1))
    }
    val serverFocus = remember { FocusRequester() }
    // Input, not layout: a tablet in landscape gets the TV layout but is still used by touch.
    val tv = remember { isTelevision(context) }

    fun poke() {
        lastKey = SystemClock.uptimeMillis()
        overlay = true
    }

    fun pick(next: Int) {
        if (next !in sources.indices) return
        tried.clear()
        gaveUp = false
        note = null
        picking = false
        if (index == next) stamp++ else index = next
        poke()
    }

    var reloads by remember(key) { mutableIntStateOf(0) }

    fun failCurrent(reason: String, retrySame: Boolean = false) {
        if (gaveUp) return
        // A page that blanked itself usually works on a second load, and the site keeps its own position.
        if (retrySame && reloads < SAME_SOURCE_RETRIES) {
            reloads++
            stamp++
            return
        }
        reloads = 0
        val current = index
        tried += current
        Log.w(TAG, "${sources[current].name} failed: $reason")
        val next = EmbedFailover.next(current, sources.size, tried)
        if (next == null && onFailed != null) {
            onFailed(reason)
            return
        }
        if (next == null) {
            gaveUp = true
            note = if (sources.size == 1) {
                "${sources[current].name} can't play this title right now ($reason)."
            } else {
                "None of the servers could play this title."
            }
        } else {
            note = "${sources[current].name}: $reason. Trying ${sources[next].name}."
            index = next
        }
        poke()
    }
    gate.onFail = { reason, retrySame -> failCurrent(reason, retrySame) }

    // Hide the overlay when nothing has been pressed for a while. Never while choosing a server or on the error.
    LaunchedEffect(overlay, lastKey, picking, gaveUp) {
        if (!overlay || picking || gaveUp) return@LaunchedEffect
        delay(if (tv) HIDE_AFTER_MS else TOUCH_HIDE_AFTER_MS)
        overlay = false
        runCatching { web.value?.requestFocus() }
    }
    // When the overlay appears, put focus on Play / Pause (as in the KissKH player), so OK plays the video at
    // once. The button is not laid out on the first frame, and a page that finishes loading takes focus, so
    // ask again a few times.
    // While the overlay is up, focus must stay on one of its buttons: the page grabs focus when it finishes
    // loading, which would leave the remote driving an invisible page. Pull it back to Play / Pause.
    LaunchedEffect(overlay, picking, gaveUp) {
        if (!overlay || picking || gaveUp || !tv) return@LaunchedEffect
        while (true) {
            delay(FOCUS_STEP_MS)
            if (!overlayFocused) runCatching { playFocus.requestFocus() }
        }
    }
    // The video often plays inside another site's frame, which the app cannot read. Sound playing is the
    // signal instead. ponytail: a muted video reads as paused; the button still toggles it either way.
    LaunchedEffect(overlay) {
        while (overlay) {
            playing = clock?.takeIf { bridge.live }?.let { !it.paused } ?: audio.isMusicActive
            delay(PLAY_POLL_MS)
        }
    }
    LaunchedEffect(picking) {
        if (!picking) return@LaunchedEffect
        repeat(FOCUS_TRIES) {
            delay(FOCUS_STEP_MS)
            if (runCatching { serverFocus.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    BackHandler {
        when {
            picking -> { picking = false; poke() }
            overlay && !gaveUp -> { overlay = false; runCatching { web.value?.requestFocus() } }
            else -> onBack()
        }
    }

    // Leaving the app pauses the page's video, as the native player does.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    bridge.pause()
                    web.value?.onPause()
                }
                androidx.lifecycle.Lifecycle.Event.ON_START -> web.value?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val source = sources[index]
    val tagged = "$stamp:${source.url}"
    val latestReferer by rememberUpdatedState(referer)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Any key shows the overlay and restarts its timer. Keys still go on to whatever has focus.
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown) {
                    val wasHidden = !overlay
                    lastKey = SystemClock.uptimeMillis()
                    if (wasHidden && (e.key == Key.DirectionUp || e.key == Key.DirectionDown)) {
                        overlay = true
                        return@onPreviewKeyEvent true
                    }
                    // Like the KissKH player: with the controls hidden, Left and Right skip 10 s.
                    if (wasHidden && bridge.live && (e.key == Key.DirectionLeft || e.key == Key.DirectionRight)) {
                        seekBy(if (e.key == Key.DirectionLeft) -SKIP_SECONDS else SKIP_SECONDS)
                        overlay = true
                        return@onPreviewKeyEvent true
                    }
                }
                false
            },
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { e ->
                    when (e.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) toggle()
                            true
                        }
                        else -> false
                    }
                }
                // Every touch still reaches the page. On a touch screen only a touch in the top strip shows our
                // bar: the rest of the page is the site's own controls (captions, seek), which must stay uncovered.
                // A pinch is read here too, never consumed. ponytail: a page that zooms itself on pinch will do
                // both; the Fit / Fill button is the fallback.
                .pointerInput(tv) {
                    awaitPointerEventScope {
                        var pinch = 1f
                        while (true) {
                            val event = awaitPointerEvent()
                            val now = SystemClock.uptimeMillis()
                            if (tv || event.changes.any { it.position.y < TOUCH_STRIP.toPx() }) {
                                lastKey = now
                                overlay = true
                            }
                            if (!tv && zoomable) {
                                if (event.changes.count { it.pressed } >= 2) {
                                    pinch *= event.calculateZoom()
                                } else if (pinch != 1f) {
                                    setZoom(PlayerZoom.after(fill, pinch))
                                    pinch = 1f
                                }
                            }
                        }
                    }
                },
            factory = { context ->
                WebView(context).apply {
                    // Without this, Compose adds the view with wrap-content size and pages that size
                    // themselves with `height: 100%` (every embed player) lay out 0 pixels tall.
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setSupportMultipleWindows(false)
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    if (userAgent != null) settings.userAgentString = userAgent
                    isFocusable = true
                    isFocusableInTouchMode = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webChromeClient = object : ShieldChromeClient() {
                        // Not copied to the system log. Logging every message slows a weak TV enough that some
                        // players' devtools checks (they time console calls) fire and blank the page.
                        override fun onConsoleMessage(message: android.webkit.ConsoleMessage?): Boolean = true
                    }
                    webViewClient = EmbedClient(gate)
                    AdShield.install(this)
                    zoomable = bridge.install(this)
                    web.value = this
                }
            },
            update = { view ->
                if (view.tag != tagged) {
                    view.tag = tagged
                    gate.begin(source.url)
                    bridge.reset()
                    val headers = latestReferer?.let { mapOf("Referer" to it) } ?: emptyMap()
                    view.loadUrl(source.url, headers)
                }
            },
            // Without this the page keeps playing sound after the player is closed.
            onRelease = { view ->
                web.value = null
                view.stopLoading()
                view.loadUrl("about:blank")
                view.destroy()
            },
        )

        AnimatedVisibility(
            visible = overlay || gaveUp,
            enter = fadeIn(tween(KhMotion.RailExpandMillis, easing = KhMotion.EaseOutStrong)),
            exit = fadeOut(tween(KhMotion.RailCollapseMillis, easing = KhMotion.EaseOutStrong)),
        ) {
            Box(Modifier.fillMaxSize().onFocusChanged { overlayFocused = it.hasFocus }) {
                if (!tv) {
                    // Touch: one light bar at the top. The site's own play button, time bar and captions
                    // menu stay visible and tappable below it.
                    Box(
                        Modifier.fillMaxWidth().height(TOUCH_STRIP + 24.dp)
                            .background(Brush.verticalGradient(0f to TOUCH_SCRIM, 0.55f to TOUCH_SCRIM, 1f to Color.Transparent)),
                    )
                    // WatchScreen already ends its label with the server name; the other callers do not.
                    val server = source.name.takeUnless { label == it || label.endsWith(" · $it") }
                    TopBar(
                        title = title,
                        label = listOfNotNull(label.ifBlank { null }, server, note).joinToString(" · "),
                        onBack = onBack,
                        onHome = onHome,
                        onEpisodes = onEpisodes,
                        onFocus = ::poke,
                        announce = true,
                    ) {
                        if (zoomable && !gaveUp) {
                            ZoomButton(fill, { setZoom(!fill); poke() }, ::poke)
                            Spacer(Modifier.width(4.dp))
                        }
                        if (onNext != null) {
                            RoundIcon(R.drawable.ic_player_next, R.string.player_next, onNext, ::poke)
                            Spacer(Modifier.width(4.dp))
                        }
                        if (sources.size > 1 || onServers != null) {
                            KhChip("Servers", onClick = { if (onServers != null) onServers() else picking = true })
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                } else {
                    // Near-solid where our text sits, so the page's own title and control bar never show through it.
                    Box(
                        Modifier.fillMaxWidth().height(150.dp)
                            .background(Brush.verticalGradient(0f to BAND, 0.6f to BAND, 1f to Color.Transparent)),
                    )
                    Box(
                        Modifier.fillMaxWidth().height(210.dp).align(Alignment.BottomCenter)
                            .background(Brush.verticalGradient(0f to Color.Transparent, 0.4f to BAND, 1f to BAND)),
                    )
                    TopBar(
                        title = title,
                        label = label,
                        onBack = onBack,
                        onHome = onHome,
                        onEpisodes = onEpisodes,
                        onFocus = ::poke,
                    )
                    if (!gaveUp) {
                        val known = clock != null
                        Row(
                            Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(28.dp),
                        ) {
                            if (known) RoundIcon(R.drawable.ic_player_rew, R.string.player_rewind, { seekBy(-SKIP_SECONDS); poke() }, ::poke, big = true)
                            PlayPause(
                                playing = playing,
                                onClick = {
                                    toggle()
                                    poke()
                                },
                                onFocus = ::poke,
                                modifier = Modifier.focusRequester(playFocus),
                            )
                            if (known) RoundIcon(R.drawable.ic_player_ffwd, R.string.player_forward, { seekBy(SKIP_SECONDS); poke() }, ::poke, big = true)
                        }
                    }
                    BottomBar(
                        status = listOfNotNull(source.name, note).joinToString(" · "),
                        clock = clock?.takeIf { !gaveUp },
                        onSeek = { bridge.seekTo(it); poke() },
                        hint = listOfNotNull(
                            if (clock != null) "OK plays or pauses · Left and Right skip 10 s" else "OK plays or pauses",
                            "No picture? Choose another server".takeIf { sources.size > 1 },
                        ).joinToString(" · "),
                        showServers = sources.size > 1 || onServers != null,
                        onServers = { if (onServers != null) onServers() else picking = true },
                        onNext = onNext,
                        fill = fill.takeIf { zoomable },
                        onZoom = { setZoom(!fill); poke() },
                        onFocus = ::poke,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }

        ZoomHint(zoomHint, onDone = { zoomHint = null }, modifier = Modifier.align(BiasAlignment(0f, -0.55f)))

        if (picking) {
            ServerSheet(
                sources = sources,
                selected = index,
                onPick = ::pick,
                firstFocus = serverFocus,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        if (gaveUp) {
            Column(
                Modifier.fillMaxSize().background(KhColors.Background).padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(note ?: "None of the servers loaded.", style = KhTheme.typography.title, color = KhColors.TextPrimary)
                val retry = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { retry.requestFocus() } }
                KhButton("Try again", primary = true, onClick = { pick(0) }, modifier = Modifier.focusRequester(retry))
                KhButton("Back", onClick = onBack)
                KhButton("Home", onClick = onHome)
            }
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    label: String,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onEpisodes: (() -> Unit)?,
    onFocus: () -> Unit,
    /** Read the label out when it changes (touch screens show the server status there). */
    announce: Boolean = false,
    /** Buttons placed before Home. */
    extra: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 36.dp, top = 28.dp, end = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIcon(R.drawable.ic_player_back, R.string.player_back, onBack, onFocus)
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(title, color = KhColors.TextPrimary, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (label.isNotBlank()) {
                Text(
                    label,
                    color = KhColors.TextSecondary,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (announce) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier,
                )
            }
        }
        extra()
        RoundIcon(R.drawable.ic_player_home, R.string.player_home, onHome, onFocus)
        if (onEpisodes != null) {
            Spacer(Modifier.width(4.dp))
            RoundIcon(R.drawable.ic_player_episodes, R.string.player_episodes, onEpisodes, onFocus)
        }
    }
}

@Composable
private fun BottomBar(
    status: String,
    clock: VideoClock?,
    onSeek: (Double) -> Unit,
    hint: String,
    showServers: Boolean,
    onServers: () -> Unit,
    onNext: (() -> Unit)?,
    /** Fit / Fill state, or null when the page's video can't be zoomed (no button). */
    fill: Boolean?,
    onZoom: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(start = 36.dp, end = 28.dp, bottom = 22.dp)) {
      if (clock != null) {
          SeekBar(clock = clock, onSeek = onSeek, onFocus = onFocus, modifier = Modifier.padding(end = 8.dp, bottom = 10.dp))
      }
      Row(
          Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
      ) {
          Column(Modifier.weight(1f)) {
              // A polite live region: a screen reader says it when a server fails and the next one starts.
              Text(
                  status,
                  color = KhColors.TextPrimary,
                  fontSize = 16.sp,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
              )
              Text(hint, color = KhColors.TextSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          if (fill != null) {
              ZoomButton(fill, onZoom, onFocus)
              Spacer(Modifier.width(8.dp))
          }
          if (onNext != null) {
              RoundIcon(R.drawable.ic_player_next, R.string.player_next, onNext, onFocus)
              Spacer(Modifier.width(8.dp))
          }
          if (showServers) {
              KhButton("Servers", onClick = onServers, modifier = Modifier.onFocusChanged { if (it.isFocused) onFocus() })
          }
      }
    }
}

/** The Fit / Fill button: shows what a press will do (Fill while the video fits, Fit while it fills). */
@Composable
private fun ZoomButton(fill: Boolean, onClick: () -> Unit, onFocus: () -> Unit) {
    RoundIcon(
        if (fill) R.drawable.ic_player_zoom_fit else R.drawable.ic_player_zoom_fill,
        if (fill) R.string.player_zoom_fit else R.string.player_zoom_fill,
        onClick,
        onFocus,
    )
}

/** The 96 dp Play / Pause in the middle, the same size and place as the KissKH player's. OK on it taps the page. */
@Composable
private fun PlayPause(playing: Boolean, onClick: () -> Unit, onFocus: () -> Unit, modifier: Modifier = Modifier) {
    val label = androidx.compose.ui.res.stringResource(if (playing) R.string.player_pause else R.string.player_play)
    KhFocusable(
        onClick = onClick,
        modifier = modifier
            .size(96.dp)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .semantics { contentDescription = label },
        shape = CircleShape,
        scaleAndLift = false,
    ) { focused ->
        Box(
            Modifier.fillMaxSize().background(if (focused) Color(0x40FFFFFF) else Color(0x8C000000), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(if (playing) R.drawable.ic_player_pause else R.drawable.ic_player_play),
                contentDescription = null,
                tint = KhColors.TextPrimary,
                modifier = Modifier.size(56.dp),
            )
        }
    }
}

/** A 48 dp round icon button, the same size and focus look as the KissKH player's. */
@Composable
private fun RoundIcon(
    @DrawableRes icon: Int,
    description: Int,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
    big: Boolean = false,
) {
    val label = androidx.compose.ui.res.stringResource(description)
    KhFocusable(
        onClick = onClick,
        modifier = modifier
            .size(if (big) 72.dp else 48.dp)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .semantics { contentDescription = label },
        shape = CircleShape,
        scaleAndLift = false,
    ) { focused ->
        Box(
            Modifier.fillMaxSize().background(if (focused) Color(0x33FFFFFF) else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = KhColors.TextPrimary, modifier = Modifier.size(if (big) 40.dp else 28.dp))
        }
    }
}

@Composable
private fun ServerSheet(
    sources: List<WebSource>,
    selected: Int,
    onPick: (Int) -> Unit,
    firstFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE09090B), Color(0xEE09090B))))
            .padding(top = 48.dp, bottom = 28.dp),
    ) {
        Text(
            "Servers",
            style = KhTheme.typography.rowTitle,
            color = KhColors.TextPrimary,
            modifier = Modifier.padding(start = 36.dp, bottom = 12.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(sources, key = { _, s -> s.code }) { i, s ->
                KhChip(
                    text = s.name,
                    selected = i == selected,
                    onClick = { onPick(i) },
                    modifier = Modifier
                        .then(if (i == selected) Modifier.focusRequester(firstFocus) else Modifier)
                        .semantics { this.selected = i == selected },
                )
            }
        }
    }
}

private class LoadGate {
    var started: String = ""
    private var used = false
    private var generation = 0

    /** True once the current page has finished loading. From then on the page may not move to another site. */
    private var settled = false
    var onFail: (String, Boolean) -> Unit = { _, _ -> }

    fun begin(url: String) {
        started = url
        used = false
        settled = false
        generation++
    }

    /** A new page is loading (a form post, a same-site link): it gets the same grace to redirect as the first. */
    fun pageStarted() {
        settled = false
    }

    fun fail(reason: String, retrySame: Boolean = false) {
        if (used) return
        used = true
        onFail(reason, retrySame)
    }

    /**
     * A page can load fine and still have nothing to play: the site sent us to its home page, or the page
     * says it can't find the title. Single-page sites draw that a moment after load, so look twice.
     */
    fun finished(view: WebView, url: String?) {
        settled = true
        if (used) return
        if (url != null && EmbedPage.blanked(url)) {
            fail("the page closed itself", retrySame = true)
            return
        }
        if (url != null && EmbedPage.sentHome(started, url)) {
            fail("sent to its home page")
            return
        }
        val mine = generation
        for (delay in CHECK_DELAYS_MS) {
            view.postDelayed({ if (mine == generation && !used) inspect(view, mine) }, delay)
        }
        view.postDelayed({ if (mine == generation && !used) checkStall(view, mine) }, STALL_AFTER_LOAD_MS)
    }

    /** The viewer pressed OK. If the video has still not started a while later, the server is not sending it. */
    fun tapped(view: WebView) {
        if (used) return
        val mine = generation
        view.postDelayed({ if (mine == generation && !used) checkStall(view, mine) }, STALL_AFTER_TAP_MS)
    }

    private fun checkStall(view: WebView, mine: Int) {
        view.evaluateJavascript(STALL_SCRIPT) { raw ->
            if (mine == generation && !used && raw == "true") fail("the server isn't sending video")
        }
    }

    private fun inspect(view: WebView, mine: Int) {
        view.evaluateJavascript(PAGE_SCRIPT) { raw ->
            if (mine != generation || used) return@evaluateJavascript
            val page = runCatching { JSONObject(raw) }.getOrNull() ?: return@evaluateJavascript
            if (EmbedPage.unavailable(page.optString("t"), page.optInt("v"))) fail("page says it isn't available")
        }
    }

    fun navigate(view: WebView, url: String, mainFrame: Boolean, redirect: Boolean): Boolean {
        // A settled page (ad script, popup, tap on a hidden ad) tried to take over the player: stay on it.
        if (mainFrame && EmbedPolicy.hijack(view.url, url, settled, redirect)) {
            Log.w(TAG, "blocked a move to another site: ${Uri.parse(url).host}")
            return true
        }
        return when (EmbedPolicy.decide(url, mainFrame)) {
            EmbedNav.ALLOW -> false
            EmbedNav.BLOCK -> {
                Log.w(TAG, "blocked a navigation: ${Uri.parse(url).scheme}://${Uri.parse(url).host}")
                true
            }
            EmbedNav.FAIL -> {
                fail("ad redirect")
                true
            }
        }
    }
}

private class EmbedClient(private val gate: LoadGate) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val redirect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && request.isRedirect
        return gate.navigate(view, request.url?.toString().orEmpty(), request.isForMainFrame, redirect)
    }

    @Deprecated("Kept for API 23")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = gate.navigate(view, url, true, redirect = false)

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        gate.pageStarted()
    }

    override fun onPageFinished(view: WebView, url: String?) {
        gate.finished(view, url)
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) gate.fail(EmbedPage.reason(error.description?.toString().orEmpty()))
    }

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
        if (request.isForMainFrame && errorResponse.statusCode >= 400) gate.fail("HTTP ${errorResponse.statusCode}")
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            gate.fail("the page crashed")
            return true
        }
        return false
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val host = request.url?.host ?: return null
        if (!EmbedPolicy.blocked(host)) return null
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }
}

/**
 * A remote has no pointer. OK taps the middle of the page: players put their big Play button there, and
 * a tap on the playing video pauses or resumes it.
 */
private fun WebView.tapCenter() {
    val x = width / 2f
    val y = height / 2f
    val start = SystemClock.uptimeMillis()
    // Chromium drops touches that do not come from a finger on a touchscreen, so say so.
    val pointer = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER })
    val coords = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = 1f })
    for ((action, at) in listOf(MotionEvent.ACTION_DOWN to start, MotionEvent.ACTION_UP to start + TAP_MILLIS)) {
        val event = MotionEvent.obtain(start, at, action, 1, pointer, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        dispatchTouchEvent(event)
        event.recycle()
    }
}

private const val TAG = "KhWebPlayer"
private const val TAP_MILLIS = 60L
private const val SAME_SOURCE_RETRIES = 2
private const val FOCUS_TRIES = 6
private const val FOCUS_STEP_MS = 120L
private const val PLAY_POLL_MS = 500L
private const val SKIP_SECONDS = 10.0

/** The overlay's top and bottom bands: the KH background at 95%. */
private val BAND = Color(0xF209090B)

/** Same as the KissKH player's controller timeout. */
private const val HIDE_AFTER_MS = 4_000L

/** Touch screens: the top bar goes sooner, since the page under it has its own controls. */
private const val TOUCH_HIDE_AFTER_MS = 3_000L

/** Touch screens: a touch this close to the top shows our bar. It is the bar's own height. */
private val TOUCH_STRIP = 96.dp

/** Touch screens: the top bar's scrim, light enough to see the video through. */
private val TOUCH_SCRIM = Color(0x99000000)
private val CHECK_DELAYS_MS = listOf(4_000L, 11_000L)
private const val STALL_AFTER_LOAD_MS = 30_000L
private const val STALL_AFTER_TAP_MS = 20_000L

/**
 * True when the page has a video that is trying to play (playing, or its stream attached) yet has no
 * frame and has not moved. A video still waiting for its first tap, or inside another frame, is left alone.
 */
private const val STALL_SCRIPT =
    "(function(){var v=document.querySelector('video');if(!v)return false;" +
        "var trying=!v.paused||(v.currentSrc||'').indexOf('blob:')===0;" +
        "return trying&&v.readyState<2&&v.currentTime===0})()"
private const val PAGE_SCRIPT =
    "(function(){var b=document.body;return {t:b?b.innerText.slice(0,700):'',v:document.querySelectorAll('video,iframe,canvas').length}})()"

/** Embed hosts serve their desktop player to this. */
const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
