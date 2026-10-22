package com.kisskh.tv

import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import com.kisskh.tv.player.EpisodePicker
import com.kisskh.tv.player.Pip
import com.kisskh.tv.player.PipActions
import com.kisskh.tv.player.VideoPlayer
import com.kisskh.tv.player.canPictureInPicture
import com.kisskh.tv.player.enterPip
import com.kisskh.tv.player.pipPlaying
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.UiState
import com.kisskh.tv.ui.components.KhButton
import com.kisskh.tv.ui.components.LoadingState
import com.kisskh.tv.ui.orderedEpisodes
import com.kisskh.tv.ui.playerDest
import com.kisskh.tv.ui.playerOverlayLabel
import com.kisskh.tv.ui.shell.KhApp
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private val pipActions = PipActions()

    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        if (canPictureInPicture(this)) pipActions.register(this)
        addOnPictureInPictureModeChangedListener { syncPip() }
        setContent {
            KhTheme {
                Box(Modifier.fillMaxSize()) {
                    KhApp(vm = vm, onExit = { finish() }) { player -> PlayerHost(vm, player) }
                    // In PiP only the video shows. With no video behind it (loading, an error), show plain black.
                    if (Pip.active && Pip.player == null) Box(Modifier.fillMaxSize().background(Color.Black))
                }
            }
        }
    }

    override fun onDestroy() {
        if (canPictureInPicture(this)) unregisterReceiver(pipActions)
        super.onDestroy()
    }

    // Android 8 to 11 have no automatic PiP, so leaving the app starts it here. Android 12+ starts it by itself.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && pipPlaying()) enterPip()
    }

    // The system already says "in PiP" when the activity pauses, before the window changes size and
    // before the PiP callback arrives. Asking it at each of these points keeps the layout frozen in time.
    private fun syncPip() {
        Pip.active = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode
    }

    override fun onPause() {
        super.onPause()
        syncPip()
    }

    override fun onResume() {
        super.onResume()
        syncPip()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        syncPip()
    }
}

@UnstableApi
@Composable
private fun PlayerHost(vm: AppViewModel, dest: Dest.Player) {
    val state by vm.playerState.collectAsStateWithLifecycle()
    var failed by remember(dest.episodeId) { mutableStateOf<String?>(null) }
    var showEpisodes by remember(dest.episodeId) { mutableStateOf(false) }
    // The title's detail (loaded once, shared with the title page) gives the episode list and the next episode.
    val detail by remember(dest.dramaId) { vm.detail(dest.dramaId) }.collectAsStateWithLifecycle()
    val drama = (detail as? UiState.Success)?.data
    val next = remember(drama, dest.episodeId) {
        val ordered = drama?.orderedEpisodes().orEmpty()
        val i = ordered.indexOfFirst { it.id == dest.episodeId }
        if (drama != null && i >= 0) ordered.getOrNull(i + 1)?.let { drama to it } else null
    }
    val previous = remember(drama, dest.episodeId) {
        val ordered = drama?.orderedEpisodes().orEmpty()
        val i = ordered.indexOfFirst { it.id == dest.episodeId }
        if (drama != null && i > 0) ordered.getOrNull(i - 1)?.let { drama to it } else null
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val s = state) {
            UiState.Loading -> {
                LoadingState(message = "Preparing")
                KhButton("Back", onClick = { vm.onBack() }, modifier = Modifier.align(Alignment.TopStart).padding(24.dp))
            }
            is UiState.Empty -> PlayerMessage(
                message = s.message ?: "This episode isn't released yet.",
                onBack = { vm.onBack() },
                onHome = { vm.goHome() },
            )
            is UiState.Error -> PlayerMessage(
                message = s.message,
                onRetry = { vm.retryPlayer() },
                onBack = { vm.onBack() },
                onHome = { vm.goHome() },
            )
            is UiState.Success -> {
                VideoPlayer(
                    url = s.data.url,
                    subs = s.data.subs,
                    resumeMs = s.data.resumeMs,
                    title = dest.title.ifBlank { "KH" },
                    label = playerOverlayLabel(dest.label),
                    onProgress = { pos, dur -> vm.savePosition(dest.episodeId, pos, dur) },
                    onPlaybackError = { failed = it },
                    onBack = { vm.onBack() },
                    onHome = { vm.goHome() },
                    onEpisodes = { showEpisodes = true },
                    onNext = next?.let { (d, ep) -> { vm.switchEpisode(d.playerDest(ep)) } },
                    onPrev = previous?.let { (d, ep) -> { vm.switchEpisode(d.playerDest(ep)) } },
                    hideControlsOnBack = failed == null,
                    // KissKH dramas keep their original audio; English subtitles are preferred in the player.
                    preferEnglishAudio = false,
                )
                val message = failed
                if (message != null) {
                    PlayerMessage(
                        message = message,
                        onRetry = {
                            failed = null
                            vm.retryPlayer()
                        },
                        onBack = { vm.onBack() },
                        onHome = { vm.goHome() },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.78f)),
                    )
                }
                if (showEpisodes && !Pip.active) {
                    EpisodePicker(
                        vm = vm,
                        dramaId = dest.dramaId,
                        currentEpisodeId = dest.episodeId,
                        onPick = { d, ep ->
                            showEpisodes = false
                            if (ep.id != dest.episodeId) vm.switchEpisode(d.playerDest(ep))
                        },
                        onClose = { showEpisodes = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerMessage(
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
            Text(
                message,
                style = KhTheme.typography.title,
                color = KhColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            if (onRetry != null) {
                KhButton("Retry", onClick = onRetry, primary = true)
            }
            KhButton("Back", onClick = onBack)
            KhButton("Home", onClick = onHome)
        }
    }
}
