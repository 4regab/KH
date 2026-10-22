package com.kisskh.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Text
import com.kisskh.tv.api.media.CineJoyWeb
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.media.TokenRequired
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.UiState
import com.kisskh.tv.ui.components.KhButton
import com.kisskh.tv.ui.components.LoadingState
import com.kisskh.tv.ui.media.rememberNextEpisode
import com.kisskh.tv.ui.media.StreamRequest
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun StreamPlayerScreen(vm: AppViewModel, dest: Dest.StreamPlayer) {
    val request = remember(dest.progressKey, dest.title, dest.poster) { dest.toRequest() }
    LaunchedEffect(request) { vm.media.loadStream(request) }
    val loaded by vm.media.stream.collectAsStateWithLifecycle()
    // The stream state is shared. Until this episode's own request lands, an older episode's result is not shown.
    val state = if (loaded is UiState.Success && (loaded as UiState.Success).data.key != request.key) UiState.Loading else loaded
    val next = rememberNextEpisode(vm, dest.kind, dest.tmdbId, dest.season, dest.episode)
    var failed by remember(request) { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val s = state) {
            UiState.Loading -> {
                LoadingState(message = "Finding a stream")
                KhButton("Back", onClick = { vm.onBack() }, modifier = Modifier.align(Alignment.TopStart).padding(24.dp))
            }
            is UiState.Empty -> Message(s.message ?: "Febbox returned no video for this title.", onBack = { vm.onBack() }, onHome = { vm.goHome() })
            // No token, or Febbox turned the token down: play CineJoy's own watch page, which finds a stream itself.
            is UiState.Error -> if (s.cause is TokenRequired) {
                CineJoyWebPlayer(vm, dest, next)
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
                    onNext = next?.let { episode ->
                        { vm.switchStream(dest.copy(episode = episode, episodeName = "")) }
                    },
                    hideControlsOnBack = failed == null,
                    requestHeaders = PlayerHeaders.CineJoy,
                    showEpisodes = false,
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

@Composable
private fun CineJoyWebPlayer(vm: AppViewModel, dest: Dest.StreamPlayer, next: Int?) {
    val movie = dest.kind == MediaKind.MOVIE
    val sources = remember(dest.progressKey) {
        listOf(WebSource("CineJoy", "CJ", CineJoyWeb.watchUrl(movie, dest.tmdbId, dest.season, dest.episode)))
    }
    WebPlayer(
        key = dest.progressKey,
        title = dest.title.ifBlank { "CineJoy" },
        label = dest.label,
        sources = sources,
        referer = null,
        onBack = { vm.onBack() },
        onHome = { vm.goHome() },
        onEpisodes = null,
        onNext = next?.let { episode -> { vm.switchStream(dest.copy(episode = episode, episodeName = "")) } },
        // The site picks its own layout from the browser. Its normal Android one fits a TV screen.
        userAgent = null,
    )
}

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
private fun Message(
    message: String,
    onBack: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(message, style = KhTheme.typography.title, color = KhColors.TextPrimary, textAlign = TextAlign.Center)
            if (onRetry != null) KhButton("Retry", onClick = onRetry, primary = true)
            KhButton("Back", onClick = onBack)
            KhButton("Home", onClick = onHome)
        }
    }
}
