@file:OptIn(ExperimentalComposeUiApi::class)

package com.kisskh.tv.player

import android.annotation.SuppressLint
import android.os.Build
import android.view.KeyEvent
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Text
import com.kisskh.tv.api.media.EmbedFailover
import com.kisskh.tv.api.media.EmbedNav
import com.kisskh.tv.api.media.EmbedPolicy
import com.kisskh.tv.api.media.EmbedServers
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.components.KhButton
import com.kisskh.tv.ui.components.KhChip
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhTheme
import java.io.ByteArrayInputStream
import java.net.URI

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

    fun failCurrent() {
        if (gaveUp) return
        val current = index
        tried += current
        val next = EmbedFailover.next(current, servers.size, tried)
        if (next == null) {
            gaveUp = true
            note = "None of the servers loaded."
        } else {
            note = "Couldn't load ${servers[current].name}. Trying ${servers[next].name}."
            index = next
        }
    }

    gate.onFail = { failCurrent() }
    val page = servers[index].url(dest.kind, dest.tmdbId, dest.season, dest.episode)
    val tagged = "$stamp:$page"

    BackHandler { vm.onBack() }

    Column(Modifier.fillMaxSize().background(KhColors.Background)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
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
                        setOnKeyListener { _, keyCode, event ->
                            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                                down.run()
                                true
                            } else {
                                false
                            }
                        }
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
            Text(
                listOfNotNull(servers[index].name, note).joinToString(" · "),
                style = KhTheme.typography.body,
                color = KhColors.TextPrimary,
                maxLines = 2,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                itemsIndexed(servers, key = { _, server -> server.code }) { i, server ->
                    KhChip(
                        text = server.code,
                        selected = i == index,
                        onClick = { pick(i) },
                    )
                }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KhButton("Back", onClick = { vm.onBack() }, modifier = Modifier.focusRequester(barFocus))
                KhButton("Home", onClick = { vm.goHome() })
                val next = dest.nextEpisode
                if (next != null) {
                    KhButton("Next episode", onClick = {
                        vm.switchEmbed(dest.copy(episode = next, episodeName = "", nextEpisode = null))
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
    var onFail: () -> Unit = {}

    fun begin(url: String) {
        started = url
        used = false
    }

    fun fail(url: String?) {
        if (used) return
        if (url != null && url != started && !sameHost(url, started)) return
        used = true
        onFail()
    }

    fun navigate(url: String, mainFrame: Boolean): Boolean = when (EmbedPolicy.decide(url, mainFrame)) {
        EmbedNav.ALLOW -> false
        EmbedNav.BLOCK -> true
        EmbedNav.FAIL -> {
            fail(url)
            true
        }
    }
}

private class EmbedClient(private val gate: LoadGate) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        gate.navigate(request.url?.toString().orEmpty(), request.isForMainFrame)

    @Deprecated("Kept for API 23")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = gate.navigate(url, true)

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) gate.fail(request.url?.toString())
    }

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
        if (request.isForMainFrame && errorResponse.statusCode >= 400) gate.fail(request.url?.toString())
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            gate.fail(gate.started)
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

private fun sameHost(a: String, b: String): Boolean {
    val left = runCatching { URI(a).host }.getOrNull() ?: return false
    val right = runCatching { URI(b).host }.getOrNull() ?: return false
    return left.equals(right, ignoreCase = true)
}

private const val REFERER = "https://watch.cinewave.qzz.io/"
private const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
