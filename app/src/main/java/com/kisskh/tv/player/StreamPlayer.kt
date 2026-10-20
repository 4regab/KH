package com.kisskh.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Text
import com.kisskh.tv.api.SubtitleFile
import com.kisskh.tv.api.media.CineJoyWeb
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.media.SniffedStream
import com.kisskh.tv.api.media.SubFetcher
import com.kisskh.tv.api.media.SubLists
import com.kisskh.tv.api.media.TokenRequired
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.UiState
import com.kisskh.tv.ui.components.KhButton
import com.kisskh.tv.ui.components.LoadingState
import com.kisskh.tv.ui.media.KhField
import com.kisskh.tv.ui.media.StreamRequest
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun StreamPlayerScreen(vm: AppViewModel, dest: Dest.StreamPlayer) {
    val request = remember(dest.progressKey, dest.title, dest.poster) { dest.toRequest() }
    val nav by vm.nav.collectAsStateWithLifecycle()
    val showEpisodes = !dest.kind.isMovie && nav.stack.dropLast(1).lastOrNull() is Dest.MediaDetail
    val token by vm.media.token.collectAsStateWithLifecycle()
    var pasteToken by remember(request) { mutableStateOf(false) }
    // Febbox is optional on CineJoy (it adds 4K). Without a token the site's own servers play.
    if (token.isBlank() && !pasteToken) {
        CineJoyWebPlayer(vm, dest, request, showEpisodes, onUseToken = { pasteToken = true })
        return
    }
    LaunchedEffect(request) { vm.media.loadStream(request) }
    val state by vm.media.stream.collectAsStateWithLifecycle()
    var failed by remember(request) { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val s = state) {
            UiState.Loading -> {
                LoadingState(message = "Finding a stream")
                KhButton("Back", onClick = { vm.onBack() }, modifier = Modifier.align(Alignment.TopStart).padding(24.dp))
            }
            is UiState.Empty -> Message(s.message ?: "Febbox returned no video for this title.", onBack = { vm.onBack() }, onHome = { vm.goHome() })
            is UiState.Error -> if (s.cause is TokenRequired) {
                TokenPrompt(
                    message = s.message,
                    onSave = { token ->
                        vm.media.saveToken(token)
                        vm.media.loadStream(request)
                    },
                    onBack = { vm.onBack() },
                    onHome = { vm.goHome() },
                )
            } else {
                Message(
                    message = s.message,
                    onRetry = { vm.media.loadStream(request) },
                    onBack = { vm.onBack() },
                    onHome = { vm.goHome() },
                )
            }
            is UiState.Success -> {
                val quality = s.data.quality.takeIf { it.isNotBlank() }
                VideoPlayer(
                    url = s.data.url,
                    subs = s.data.subs,
                    resumeMs = s.data.resumeMs,
                    title = dest.title.ifBlank { "CineJoy" },
                    label = listOfNotNull(dest.label, quality).joinToString(" · "),
                    onProgress = { pos, dur -> vm.media.saveStream(request, pos, dur) },
                    onPlaybackError = { failed = it },
                    onBack = { vm.onBack() },
                    onHome = { vm.goHome() },
                    onEpisodes = { vm.onBack() },
                    onNext = dest.nextEpisode?.let { next ->
                        {
                            vm.switchStream(dest.copy(episode = next, episodeName = "", nextEpisode = null))
                        }
                    },
                    hideControlsOnBack = failed == null,
                    requestHeaders = PlayerHeaders.CineJoy,
                    showEpisodes = showEpisodes,
                )
                val message = failed
                if (message != null) {
                    Message(
                        message = message,
                        onRetry = {
                            failed = null
                            vm.media.loadStream(request)
                        },
                        onBack = { vm.onBack() },
                        onHome = { vm.goHome() },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.78f)),
                    )
                }
            }
        }
    }
}

/**
 * CineJoy without a Febbox token: its watch page picks a server (Nebula, Lisbon, Solara, Athens)
 * and its player asks for an HLS playlist, which is handed to the native player. The page's
 * stream request is signed in WebAssembly, so the app lets the page make it instead of copying it.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun CineJoyWebPlayer(
    vm: AppViewModel,
    dest: Dest.StreamPlayer,
    request: StreamRequest,
    showEpisodes: Boolean,
    onUseToken: () -> Unit,
) {
    val page = CineJoyWeb.watchUrl(request.movie, request.id, request.season, request.episode)
    var stamp by remember(request) { mutableIntStateOf(0) }
    var stream by remember(request) { mutableStateOf<SniffedStream?>(null) }
    var ready by remember(request) { mutableStateOf<Pair<SniffedStream, List<SubtitleFile>>?>(null) }
    var failed by remember(request) { mutableStateOf<String?>(null) }
    val fetcher = remember { SubFetcher() }

    LaunchedEffect(request) { vm.media.progress.remember(request.key, request.title, request.poster) }
    LaunchedEffect(request, stamp) {
        delay(CINEJOY_TIMEOUT_MS)
        if (stream == null && failed == null) failed = "CineJoy didn't hand over a stream for this title."
    }
    LaunchedEffect(stream) {
        val found = stream ?: return@LaunchedEffect
        val subs = withContext(Dispatchers.IO) {
            fetcher.load(
                CineJoyWeb.subtitlesUrl(request.movie, request.id, request.season, request.episode),
                "${CineJoyWeb.SITE}/",
                SubLists::parseWing,
            )
        }
        if (stream == found) ready = found to subs
    }

    fun retry() {
        failed = null
        ready = null
        stream = null
        stamp++
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val playing = ready
        if (playing != null) {
            val resume = remember(playing) { vm.media.progress.position(request.key) }
            VideoPlayer(
                url = playing.first.url,
                subs = playing.second,
                resumeMs = resume,
                title = dest.title.ifBlank { "CineJoy" },
                label = dest.label,
                onProgress = { pos, dur -> vm.media.saveStream(request, pos, dur) },
                onPlaybackError = { failed = it },
                onBack = { vm.onBack() },
                onHome = { vm.goHome() },
                onEpisodes = { vm.onBack() },
                onNext = dest.nextEpisode?.let { next ->
                    { vm.switchStream(dest.copy(episode = next, episodeName = "", nextEpisode = null)) }
                },
                hideControlsOnBack = failed == null,
                requestHeaders = playing.first.headers,
                showEpisodes = showEpisodes,
            )
        } else if (failed == null) {
            SniffWebView(
                page = page,
                referer = null,
                loadKey = "$stamp:$page",
                onStream = { stream = it },
                onFail = { failed = "Couldn't load CineJoy." },
                modifier = Modifier.fillMaxSize(),
            )
            // The page only has to run; the viewer sees the app's loading state over it.
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                LoadingState(message = "Finding a stream")
                KhButton("Back", onClick = { vm.onBack() }, modifier = Modifier.align(Alignment.TopStart).padding(24.dp))
            }
        }
        val message = failed
        if (message != null) {
            Message(
                message = message,
                onRetry = { retry() },
                onBack = { vm.onBack() },
                onHome = { vm.goHome() },
                onToken = onUseToken,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.78f)),
            )
        }
    }
}

private const val CINEJOY_TIMEOUT_MS = 35_000L

private fun Dest.StreamPlayer.toRequest() = StreamRequest(
    key = progressKey,
    title = title,
    poster = poster,
    movie = kind == MediaKind.MOVIE,
    id = tmdbId,
    season = season,
    episode = episode,
)

@Composable
private fun TokenPrompt(
    message: String,
    onSave: (String) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = KhTheme.typography.title, color = KhColors.TextPrimary, textAlign = TextAlign.Center)
        KhField(draft, { draft = it }, "Febbox token", Modifier.fillMaxWidth())
        KhButton("Save and play", primary = true, onClick = { onSave(draft) }, enabled = draft.isNotBlank())
        KhButton("Back", onClick = onBack)
        KhButton("Home", onClick = onHome)
    }
}

@Composable
private fun Message(
    message: String,
    onBack: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onToken: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(message, style = KhTheme.typography.title, color = KhColors.TextPrimary, textAlign = TextAlign.Center)
            if (onRetry != null) KhButton("Retry", onClick = onRetry, primary = true)
            if (onToken != null) KhButton("Use a Febbox token", onClick = onToken)
            KhButton("Back", onClick = onBack)
            KhButton("Home", onClick = onHome)
        }
    }
}
