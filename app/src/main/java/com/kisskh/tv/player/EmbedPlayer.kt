package com.kisskh.tv.player

import android.webkit.WebView
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Text
import com.kisskh.tv.api.SubtitleFile
import com.kisskh.tv.api.media.EmbedFailover
import com.kisskh.tv.api.media.EmbedServers
import com.kisskh.tv.api.media.SniffedStream
import com.kisskh.tv.api.media.StreamSniff
import com.kisskh.tv.api.media.SubFetcher
import com.kisskh.tv.api.media.SubLists
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.components.KhButton
import com.kisskh.tv.ui.components.KhChip
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * One CineWave server at a time. The server's page is loaded in a WebView; as soon as its player
 * asks for an HLS playlist, the page is dropped and the playlist plays in the native player with
 * the page's Referer. A server that errors, hits a known ad host on the main frame, or shows no
 * stream within [SNIFF_TIMEOUT_MS] moves to the next one, unless the viewer picked it by hand.
 * Pages that never expose a playlist (Mapple, 111Movies) stay on screen as the player.
 * The bar under the page is there so a remote can leave the page: Down leaves the video, Up returns to it.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun EmbedPlayer(vm: AppViewModel, dest: Dest.EmbedPlayer) {
    val servers = EmbedServers.playable
    val title = dest.tmdbId to (dest.season to dest.episode)
    var index by remember(title) { mutableIntStateOf(0) }
    var note by remember(title) { mutableStateOf<String?>(null) }
    var gaveUp by remember(title) { mutableStateOf(false) }
    var stamp by remember(title) { mutableIntStateOf(0) }
    var auto by remember(title) { mutableStateOf(true) }
    var stream by remember(title) { mutableStateOf<SniffedStream?>(null) }
    var ready by remember(title) { mutableStateOf<Pair<SniffedStream, List<SubtitleFile>>?>(null) }
    var webOnly by remember(title) { mutableStateOf(emptySet<Int>()) }
    val tried = remember(title) { LinkedHashSet<Int>() }
    val web = remember { mutableStateOf<WebView?>(null) }
    val barFocus = remember { FocusRequester() }
    val down = remember { Hook() }
    val subs = remember { SubFetcher() }
    val nav by vm.nav.collectAsStateWithLifecycle()
    val showEpisodes = !dest.kind.isMovie && nav.stack.dropLast(1).lastOrNull() is Dest.MediaDetail

    LaunchedEffect(dest.progressKey) {
        vm.media.progress.remember(dest.progressKey, dest.title, dest.poster)
    }

    fun reset() {
        stream = null
        ready = null
    }

    fun pick(next: Int) {
        if (next !in servers.indices) return
        tried.clear()
        gaveUp = false
        note = null
        auto = false
        reset()
        if (index == next) stamp++ else index = next
    }

    fun failCurrent(reason: String) {
        if (gaveUp || ready != null) return
        val current = index
        tried += current
        reset()
        val next = if (auto) EmbedFailover.next(current, servers.size, tried) else null
        if (next == null) {
            gaveUp = auto
            note = if (auto) "None of the servers gave a stream." else "$reason ${servers[current].name}. Pick another server."
        } else {
            note = "$reason ${servers[current].name}. Trying ${servers[next].name}."
            index = next
        }
    }

    val server = servers[index]
    val page = server.url(dest.kind, dest.tmdbId, dest.season, dest.episode)
    val sniff = server.sniffs && index !in webOnly

    LaunchedEffect(title, index, stamp, sniff, auto) {
        if (!auto || !sniff) return@LaunchedEffect
        delay(SNIFF_TIMEOUT_MS)
        if (stream == null && ready == null) failCurrent("No stream from")
    }

    LaunchedEffect(stream) {
        val found = stream ?: return@LaunchedEffect
        val list = server.subsUrl(dest.kind, dest.tmdbId, dest.season, dest.episode)?.let { url ->
            withContext(Dispatchers.IO) {
                subs.load(url, StreamSniff.origin(page)?.let { "$it/" }, SubLists::parseLabelFile)
            }
        }.orEmpty()
        if (stream == found) ready = found to list
    }

    val playing = ready
    if (playing != null) {
        val resume = remember(playing) { vm.media.progress.position(dest.progressKey) }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VideoPlayer(
                url = playing.first.url,
                subs = playing.second,
                resumeMs = resume,
                title = dest.title.ifBlank { "CineWave" },
                label = listOf(dest.label, server.name).joinToString(" · "),
                onProgress = { pos, dur -> vm.media.progress.save(dest.progressKey, dest.title, dest.poster, pos, dur) },
                onPlaybackError = { message ->
                    // The playlist was found but would not play natively: show the server's own page.
                    webOnly = webOnly + index
                    reset()
                    note = "$message Showing ${server.name}'s own player."
                    stamp++
                },
                onBack = { vm.onBack() },
                onHome = { vm.goHome() },
                onEpisodes = { vm.onBack() },
                onNext = dest.nextEpisode?.let { next ->
                    { vm.switchEmbed(dest.copy(episode = next, episodeName = "", nextEpisode = null)) }
                },
                requestHeaders = playing.first.headers,
                showEpisodes = showEpisodes,
            )
        }
        return
    }

    LaunchedEffect(Unit) { runCatching { barFocus.requestFocus() } }
    BackHandler { vm.onBack() }

    Column(Modifier.fillMaxSize().background(KhColors.Background)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            SniffWebView(
                page = page,
                referer = REFERER,
                loadKey = "$stamp:$page",
                sniff = sniff,
                onStream = { found ->
                    web.value?.apply {
                        stopLoading()
                        loadUrl("about:blank")
                    }
                    note = "Found a stream on ${server.name}."
                    stream = found
                },
                onFail = { failCurrent("Couldn't load") },
                modifier = Modifier.fillMaxSize(),
                onKeyDown = { keyCode ->
                    if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                        down.run()
                        true
                    } else {
                        false
                    }
                },
                onCreated = { web.value = it },
            )
            if (gaveUp) {
                Column(
                    Modifier.fillMaxSize().background(KhColors.Background).padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(note ?: "None of the servers gave a stream.", style = KhTheme.typography.title, color = KhColors.TextPrimary)
                    KhButton("Try again", primary = true, onClick = {
                        pick(0)
                        auto = true
                    })
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
                listOfNotNull(server.name, note).joinToString(" · "),
                style = KhTheme.typography.body,
                color = KhColors.TextPrimary,
                maxLines = 2,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                itemsIndexed(servers, key = { _, s -> s.code }) { i, s ->
                    KhChip(
                        text = s.code,
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

/** Long enough for a slow TV to load the page and its player, short enough to not stare at black. */
private const val SNIFF_TIMEOUT_MS = 25_000L
private const val REFERER = "https://watch.cinewave.qzz.io/"
