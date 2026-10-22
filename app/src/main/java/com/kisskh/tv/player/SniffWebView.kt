package com.kisskh.tv.player

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.kisskh.tv.api.media.EmbedNav
import com.kisskh.tv.api.media.EmbedPolicy
import com.kisskh.tv.api.media.SniffedStream
import com.kisskh.tv.api.media.StreamSniff
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A WebView that loads [page] and reports the first HLS playlist the page's player asks for.
 * A main-frame error, an HTTP error on the page itself, or a known ad host on the main frame
 * calls [onFail] once per load. [loadKey] changing reloads the page.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun SniffWebView(
    page: String,
    referer: String?,
    loadKey: String,
    onStream: (SniffedStream) -> Unit,
    onFail: () -> Unit,
    modifier: Modifier = Modifier,
    sniff: Boolean = true,
    onKeyDown: ((Int) -> Boolean)? = null,
    onCreated: (WebView) -> Unit = {},
    /** Null keeps the WebView's own (mobile) user agent. */
    userAgent: String? = WEB_USER_AGENT,
) {
    val gate = remember { SniffGate() }
    gate.onStream = onStream
    gate.onFail = onFail
    gate.sniff = sniff
    AndroidView(
        modifier = modifier,
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
                if (userAgent != null) settings.userAgentString = userAgent
                gate.userAgent = settings.userAgentString
                isFocusable = true
                isFocusableInTouchMode = true
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webChromeClient = ShieldChromeClient()
                webViewClient = SniffClient(gate)
                AdShield.install(this)
                if (onKeyDown != null) {
                    setOnKeyListener { _, keyCode, event ->
                        event.action == KeyEvent.ACTION_DOWN && onKeyDown(keyCode)
                    }
                }
                onCreated(this)
            }
        },
        update = { view ->
            if (view.tag != loadKey) {
                view.tag = loadKey
                gate.begin(page)
                if (referer != null) view.loadUrl(page, mapOf("Referer" to referer)) else view.loadUrl(page)
            }
        },
        onRelease = { view ->
            view.stopLoading()
            view.loadUrl("about:blank")
            view.destroy()
        },
    )
}

/** Per-load state shared between the composable and the WebView's threads. */
internal class SniffGate {
    @Volatile var started: String = ""
    @Volatile var sniff: Boolean = true
    @Volatile var userAgent: String = WEB_USER_AGENT
    var onStream: (SniffedStream) -> Unit = {}
    var onFail: () -> Unit = {}
    private val failed = AtomicBoolean(false)
    private val found = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())

    /** True once the current page has finished loading. From then on the page may not move to another site. */
    @Volatile private var settled = false

    fun begin(url: String) {
        started = url
        failed.set(false)
        found.set(false)
        settled = false
    }

    /** A new page is loading (a form post, a same-site link): it gets the same grace to redirect as the first. */
    fun pageStarted() {
        settled = false
    }

    fun pageFinished() {
        settled = true
    }

    fun fail() {
        if (found.get() || !failed.compareAndSet(false, true)) return
        main.post { onFail() }
    }

    /** Called on the WebView's network thread. */
    fun request(url: String, headers: Map<String, String>) {
        if (!sniff || !StreamSniff.isStream(url) || !found.compareAndSet(false, true)) return
        val stream = SniffedStream(url, StreamSniff.headers(headers, started) + ("User-Agent" to userAgent))
        main.post { onStream(stream) }
    }

    fun navigate(currentUrl: String?, url: String, mainFrame: Boolean, redirect: Boolean): Boolean {
        // A settled page (ad script, popup) tried to take over the page: stay on it.
        if (mainFrame && EmbedPolicy.hijack(currentUrl, url, settled, redirect)) {
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
                fail()
                true
            }
        }
    }
}

private class SniffClient(private val gate: SniffGate) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val redirect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && request.isRedirect
        return gate.navigate(view.url, request.url?.toString().orEmpty(), request.isForMainFrame, redirect)
    }

    @Deprecated("Kept for API 23")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = gate.navigate(view.url, url, true, redirect = false)

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        gate.pageStarted()
    }

    override fun onPageFinished(view: WebView, url: String?) {
        gate.pageFinished()
    }

    // A redirect to a new domain (vidfast.pro -> vidfast.vc) is still the page, so any main-frame
    // error counts. Before, errors after a cross-domain redirect were ignored and left a black page.
    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) gate.fail()
    }

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
        if (request.isForMainFrame && errorResponse.statusCode >= 400) gate.fail()
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            gate.fail()
            return true
        }
        return false
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url ?: return null
        val host = url.host ?: return null
        if (EmbedPolicy.blocked(host)) {
            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        }
        gate.request(url.toString(), request.requestHeaders.orEmpty())
        return null
    }
}

internal const val WEB_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

private const val TAG = "KhSniffWebView"
