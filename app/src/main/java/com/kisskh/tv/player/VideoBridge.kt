package com.kisskh.tv.player

import android.os.SystemClock
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.kisskh.tv.ui.theme.KhColors
import org.json.JSONObject

/** What the page's video reports: position and length in seconds, and whether it is paused. */
data class VideoClock(val position: Double, val duration: Double, val paused: Boolean)

/**
 * A line to the page's video, whatever frame it plays in. Embed players put the video in another
 * site's frame that the app's own scripts cannot reach, so a tiny script is added to every frame. It
 * reports the biggest video's time twice a second and obeys seek, play/pause and fit/fill.
 *
 * Needs WebView's document-start script and message listener features (WebView 87 and up). On older
 * WebViews [install] returns false and the player keeps its tap-only controls.
 */
class VideoBridge(private val onClock: (VideoClock?) -> Unit) {
    private var proxy: JavaScriptReplyProxy? = null
    private var proxyDuration = 0.0
    private var heardAt = 0L
    private var zoom: Boolean? = null
    fun install(view: WebView): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
        ) return false
        WebViewCompat.addWebMessageListener(view, NAME, setOf("*")) { _, message, _, _, reply ->
            val data = runCatching { JSONObject(message.data ?: return@addWebMessageListener) }.getOrNull()
                ?: return@addWebMessageListener
            val clock = VideoClock(data.optDouble("t"), data.optDouble("d"), data.optBoolean("p", true))
            if (!clock.duration.isFinite() || clock.duration <= 0.0) return@addWebMessageListener
            val now = SystemClock.uptimeMillis()
            // Several frames can hold a video (an ad, a preview). Follow the longest, or whichever still talks.
            val stale = now - heardAt > STALE_MS
            if (reply == proxy || stale || clock.duration > proxyDuration) {
                val fresh = reply != proxy
                proxy = reply
                proxyDuration = clock.duration
                heardAt = now
                onClock(clock)
                // A new page or frame starts at the site's own look: give it the chosen one.
                if (fresh) sendZoom()
            }
        }
        WebViewCompat.addDocumentStartJavaScript(view, SCRIPT, setOf("*"))
        return true
    }

    /** Forget the old page's video. Call when another page starts loading. */
    fun reset() {
        proxy = null
        proxyDuration = 0.0
        heardAt = 0L
        onClock(null)
    }

    /** True while a video has reported in the last few seconds. */
    val live: Boolean get() = proxy != null && SystemClock.uptimeMillis() - heardAt < STALE_MS

    fun seekTo(seconds: Double) = send("{\"c\":\"seek\",\"t\":$seconds}")

    fun toggle() = send("{\"c\":\"toggle\"}")

    /** Pause the page's video (the app is leaving the screen). */
    fun pause() = send("{\"c\":\"pause\"}")

    /**
     * Make every video in the page's frame fill its box (cropped, `object-fit: cover`) or fit inside it
     * (`contain`). Remembered, and sent again to each new page. Until this is first called the page keeps its own look.
     * ponytail: only the frame that holds the longest video hears it; a video in a second frame is left alone.
     */
    fun setZoom(fill: Boolean) {
        zoom = fill
        sendZoom()
    }

    private fun sendZoom() {
        zoom?.let { send("{\"c\":\"zoom\",\"f\":$it}") }
    }

    private fun send(json: String): Boolean {
        val target = proxy ?: return false
        return runCatching { target.postMessage(json) }.isSuccess
    }

    private companion object {
        const val NAME = "khBridge"
        const val STALE_MS = 3_000L
        const val SCRIPT = """
(function(){
  if (window.__khv) return; window.__khv = 1;
  function vid(){
    var best = null, area = -1, list = document.getElementsByTagName('video');
    for (var i = 0; i < list.length; i++) { var v = list[i], a = v.clientWidth * v.clientHeight; if (a > area) { area = a; best = v; } }
    return best;
  }
  function send(){
    var b = window.khBridge, v = vid();
    if (!b || !v || !isFinite(v.duration) || v.duration <= 0) return;
    b.postMessage(JSON.stringify({ t: v.currentTime, d: v.duration, p: v.paused }));
  }
  setInterval(send, 500);
  var fillAll = null;
  function fit(){
    if (fillAll === null) return;
    var list = document.getElementsByTagName('video');
    for (var i = 0; i < list.length; i++) list[i].style.setProperty('object-fit', fillAll ? 'cover' : 'contain', 'important');
  }
  setInterval(fit, 500);
  function listen(){
    var b = window.khBridge;
    if (!b) { setTimeout(listen, 300); return; }
    b.onmessage = function(e){
      var m, v = vid();
      try { m = JSON.parse(e.data); } catch (x) { return; }
      if (m.c === 'zoom') { fillAll = !!m.f; fit(); return; }
      if (!v) return;
      if (m.c === 'seek') { v.currentTime = Math.max(0, Math.min(v.duration - 1, m.t)); send(); }
      else if (m.c === 'pause') { v.pause(); send(); }
      else if (m.c === 'toggle') { if (v.paused) { var p = v.play(); if (p && p.catch) p.catch(function(){}); } else v.pause(); send(); }
    };
  }
  listen();
})();
"""
    }
}

/** "1:02:03" or "4:05". */
fun clockText(seconds: Double): String {
    val s = seconds.coerceAtLeast(0.0).toLong()
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** Seconds per Left/Right press on the seek bar: 10 s, then faster the longer the key is held. */
fun seekStep(repeatCount: Int): Double = when {
    repeatCount < 6 -> 10.0
    repeatCount < 20 -> 30.0
    else -> 60.0
}

/**
 * The time bar, like the KissKH player's. On a remote: focus it, Left and Right move the marker (holding
 * speeds up), and the jump happens when the key is let go. On a phone: tap or drag along it.
 * Screen readers can set it directly.
 */
@Composable
fun SeekBar(
    clock: VideoClock,
    onSeek: (Double) -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var target by remember { mutableStateOf<Double?>(null) }
    var focused by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf<Double?>(null) }
    val shown = (dragging ?: target ?: clock.position).coerceIn(0.0, clock.duration)
    val fraction = (shown / clock.duration).toFloat().coerceIn(0f, 1f)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(clockText(shown), color = KhColors.TextPrimary, fontSize = 15.sp, modifier = Modifier.padding(end = 14.dp))
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .height(32.dp)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocus()
                    if (!it.isFocused && target != null) {
                        onSeek(target!!)
                        target = null
                    }
                }
                .onPreviewKeyEvent { e ->
                    val dir = when (e.key) {
                        Key.DirectionLeft -> -1
                        Key.DirectionRight -> 1
                        else -> return@onPreviewKeyEvent false
                    }
                    when (e.type) {
                        KeyEventType.KeyDown -> {
                            onFocus()
                            val from = target ?: clock.position
                            target = (from + dir * seekStep(e.nativeKeyEvent.repeatCount)).coerceIn(0.0, clock.duration - 1)
                        }
                        KeyEventType.KeyUp -> target?.let {
                            onSeek(it)
                            target = null
                        }
                    }
                    true
                }
                .focusable()
                .semantics {
                    contentDescription = "Position ${clockText(shown)} of ${clockText(clock.duration)}"
                    progressBarRangeInfo = ProgressBarRangeInfo(shown.toFloat(), 0f..clock.duration.toFloat())
                    setProgress { value ->
                        onSeek(value.toDouble())
                        true
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val width = maxWidth
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (focused) 6.dp else 4.dp)
                    .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(3.dp)),
            ) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(KhColors.Brand, RoundedCornerShape(3.dp)))
            }
            val knob = if (focused) 18.dp else 12.dp
            Box(
                Modifier
                    .offset(x = width * fraction - knob / 2)
                    .size(knob)
                    .background(if (focused) Color.White else KhColors.Brand, CircleShape),
            )
            // Touch: tap a spot or drag along the bar.
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(clock.duration) {
                        detectTapGestures { p ->
                            onFocus()
                            onSeek((p.x / size.width).coerceIn(0f, 1f) * clock.duration)
                        }
                    }
                    .pointerInput(clock.duration) {
                        detectHorizontalDragGestures(
                            onDragStart = { p -> dragging = (p.x / size.width).coerceIn(0f, 1f) * clock.duration },
                            onDragEnd = {
                                dragging?.let(onSeek)
                                dragging = null
                            },
                            onDragCancel = { dragging = null },
                        ) { change, _ ->
                            onFocus()
                            dragging = (change.position.x / size.width).coerceIn(0f, 1f) * clock.duration
                        }
                    },
            )
        }
        Text(clockText(clock.duration), color = KhColors.TextSecondary, fontSize = 15.sp, modifier = Modifier.padding(start = 14.dp))
    }
}
