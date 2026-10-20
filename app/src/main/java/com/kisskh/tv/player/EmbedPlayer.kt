@file:OptIn(ExperimentalComposeUiApi::class)

package com.kisskh.tv.player

import android.annotation.SuppressLint
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Text
import com.kisskh.tv.api.media.EmbedFailover
import com.kisskh.tv.api.media.EmbedNav
import com.kisskh.tv.api.media.EmbedPage
import com.kisskh.tv.api.media.EmbedPolicy
import com.kisskh.tv.api.media.EmbedServers
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.components.KhButton
import com.kisskh.tv.ui.components.KhChip
import com.kisskh.tv.ui.media.rememberNextEpisode
import com.kisskh.tv.ui.theme.FormFactor
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhTheme
import java.io.ByteArrayInputStream
import org.json.JSONObject

/**
 * Full-screen page for one CineWave server. Popups are blocked. A main-frame error, an HTTP
 * error, or a known ad host moves to the next server. The bar under the page is there so a
 * remote can leave the page: Down leaves the video, Up returns to it.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EmbedPlayer(vm: AppViewModel, dest: Dest.EmbedPlayer) {
    val servers = EmbedServers.playable
    var index by remember(dest.tmdbId, dest.season, dest.episode) { mutableIntStateOf(0) }
    var note by remember(dest.tmdbId, dest.season, dest.episode) { mutableStateOf<String?>(null) }
    var gaveUp by remember(dest.tmdbId, dest.season, dest.episode) { mutableStateOf(false) }
    var stamp by remember(dest.tmdbId, dest.season, dest.episode) { mutableIntStateOf(0) }
    val tried = remember(dest.tmdbId, dest.season, dest.episode) { LinkedHashSet<Int>() }
    val gate = remember { LoadGate() }
    val web = remember { mutableStateOf<WebView?>(null) }
    val barFocus = remember { FocusRequester() }
    val down = remember { Hook() }
    val next = rememberNextEpisode(vm, dest.kind, dest.tmdbId, dest.season, dest.episode)

    LaunchedEffect(dest.progressKey) {
        vm.media.progress.remember(dest.progressKey, dest.title, dest.poster)
    }
    LaunchedEffect(Unit) { runCatching { barFocus.requestFocus() } }

    fun pick(next: Int) {
        if (next !in servers.indices) return
        tried.clear()
        gaveUp = false
        note = null
        if (index == next) stamp++ else index = next
    }

    fun failCurrent(reason: String) {
        if (gaveUp) return
        val current = index
        tried += current
        Log.w(TAG, "${servers[current].name} failed: $reason")
        val next = EmbedFailover.next(current, servers.size, tried)
        if (next == null) {
            gaveUp = true
            note = "None of the servers loaded."
        } else {
            note = "Couldn't load ${servers[current].name} ($reason). Trying ${servers[next].name}."
            index = next
        }
    }

    gate.onFail = { reason -> failCurrent(reason) }
    val page = servers[index].url(dest.kind, dest.tmdbId, dest.season, dest.episode)
    val tagged = "$stamp:$page"

    BackHandler { vm.onBack() }

    Column(Modifier.fillMaxSize().background(KhColors.Background)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                // Compose sees the remote's keys before the WebView does. OK becomes a tap in the middle of the
                // page and Down moves to the bar. Arrow keys other than Down go on to the page (players seek).
                modifier = Modifier.fillMaxSize().onPreviewKeyEvent { e ->
                    when (e.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) web.value?.tapCenter()
                            true
                        }
                        Key.DirectionDown -> {
                            if (e.type == KeyEventType.KeyDown) down.run()
                            true
                        }
                        else -> false
                    }
                },
                factory = { context ->
                    WebView(context).apply {
                        // Without this, Compose adds the view with wrap-content size and every page that
                        // sizes itself with `height: 100%` (every embed player) lays out 0 pixels tall.
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        setBackgroundColor(android.graphics.Color.BLACK)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.setSupportMultipleWindows(false)
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        settings.userAgentString = USER_AGENT
                        isFocusable = true
                        isFocusableInTouchMode = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webChromeClient = object : WebChromeClient() {
                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: android.os.Message?,
                            ): Boolean = false
                        }
                        webViewClient = EmbedClient(gate)
                        web.value = this
                    }
                },
                update = { view ->
                    if (view.tag != tagged) {
                        view.tag = tagged
                        gate.begin(page)
                        view.loadUrl(page, mapOf("Referer" to REFERER))
                    }
                },
            )
            if (gaveUp) {
                Column(
                    Modifier.fillMaxSize().background(KhColors.Background).padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(note ?: "None of the servers loaded.", style = KhTheme.typography.title, color = KhColors.TextPrimary)
                    KhButton("Try again", primary = true, onClick = { pick(0) })
                    KhButton("Back", onClick = { vm.onBack() })
                    KhButton("Home", onClick = { vm.goHome() })
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(KhColors.Surface)
                .onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionUp) {
                        web.value?.requestFocus()
                        true
                    } else {
                        false
                    }
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // A polite live region, so a screen reader announces it when a server fails and the next one starts.
            Text(
                listOfNotNull(servers[index].name, note).joinToString(" · "),
                style = KhTheme.typography.body,
                color = KhColors.TextPrimary,
                maxLines = 2,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Text(
                if (KhTheme.formFactor == FormFactor.TV) {
                    "OK plays or pauses. Down: servers. Up: video. No picture? Try another server."
                } else {
                    "Tap the video to play. No picture? Try another server."
                },
                style = KhTheme.typography.caption,
                color = KhColors.TextSecondary,
                maxLines = 2,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                itemsIndexed(servers, key = { _, server -> server.code }) { i, server ->
                    KhChip(
                        text = server.code,
                        selected = i == index,
                        onClick = { pick(i) },
                        modifier = Modifier.semantics {
                            contentDescription = server.name
                            selected = i == index
                        },
                    )
                }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KhButton("Back", onClick = { vm.onBack() }, modifier = Modifier.focusRequester(barFocus))
                KhButton("Home", onClick = { vm.goHome() })
                if (next != null) {
                    KhButton("Next episode", onClick = {
                        vm.switchEmbed(dest.copy(episode = next, episodeName = ""))
                    })
                }
            }
        }
    }
    down.run = { runCatching { barFocus.requestFocus() } }
}

private class Hook {
    var run: () -> Unit = {}
}

private class LoadGate {
    var started: String = ""
    private var used = false
    private var generation = 0
    var onFail: (String) -> Unit = {}

    fun begin(url: String) {
        started = url
        used = false
        generation++
    }

    fun fail(reason: String) {
        if (used) return
        used = true
        onFail(reason)
    }

    /**
     * A page can load fine and still have nothing to play: the site sent us to its home page, or the page
     * says it can't find the title. Single-page sites draw that a moment after load, so look twice.
     */
    fun finished(view: WebView, url: String?) {
        if (used) return
        if (url != null && EmbedPage.blanked(url)) {
            fail("the page closed itself")
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
    }

    private fun inspect(view: WebView, mine: Int) {
        view.evaluateJavascript(PAGE_SCRIPT) { raw ->
            if (mine != generation || used) return@evaluateJavascript
            val page = runCatching { JSONObject(raw) }.getOrNull() ?: return@evaluateJavascript
            if (EmbedPage.unavailable(page.optString("t"), page.optInt("v"))) fail("page says it isn't available")
        }
    }

    fun navigate(url: String, mainFrame: Boolean): Boolean = when (EmbedPolicy.decide(url, mainFrame)) {
        EmbedNav.ALLOW -> false
        EmbedNav.BLOCK -> true
        EmbedNav.FAIL -> {
            fail("ad redirect")
            true
        }
    }
}

private class EmbedClient(private val gate: LoadGate) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        gate.navigate(request.url?.toString().orEmpty(), request.isForMainFrame)

    @Deprecated("Kept for API 23")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = gate.navigate(url, true)

    override fun onPageFinished(view: WebView, url: String?) {
        gate.finished(view, url)
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) gate.fail(EmbedPage.reason(error.description?.toString().orEmpty()))
    }

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
        if (request.isForMainFrame && errorResponse.statusCode >= 400) {
            gate.fail("HTTP ${errorResponse.statusCode}")
        }
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
 * A remote has no pointer. OK taps the middle of the page: embed players put their big Play button
 * there, and a tap on the playing video pauses or resumes it. Arrow keys still reach the page as keys.
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

private const val TAG = "KhEmbed"
private const val TAP_MILLIS = 60L
private val CHECK_DELAYS_MS = listOf(4_000L, 11_000L)
private const val PAGE_SCRIPT =
    "(function(){var b=document.body;return {t:b?b.innerText.slice(0,700):'',v:document.querySelectorAll('video,iframe,canvas').length}})()"
private const val REFERER = "https://watch.cinewave.qzz.io/"
private const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
