package com.kisskh.tv

import android.graphics.Color as AndroidColor
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
import com.kisskh.tv.player.VideoPlayer
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.UiState
import com.kisskh.tv.ui.components.KhButton
import com.kisskh.tv.ui.components.LoadingState
import com.kisskh.tv.ui.playerOverlayLabel
import com.kisskh.tv.ui.shell.KhApp
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent {
            KhTheme {
                KhApp(vm = vm, onExit = { finish() }) { player -> PlayerHost(vm, player) }
            }
        }
    }
}

@UnstableApi
@Composable
private fun PlayerHost(vm: AppViewModel, dest: Dest.Player) {
    val state by vm.playerState.collectAsStateWithLifecycle()
    var failed by remember(dest.episodeId) { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val s = state) {
            UiState.Loading -> LoadingState(message = "Preparing")
            is UiState.Empty -> PlayerMessage(
                message = s.message ?: "This episode isn't released yet.",
                onBack = { vm.onBack() },
            )
            is UiState.Error -> PlayerMessage(
                message = s.message,
                onRetry = { vm.retryPlayer() },
                onBack = { vm.onBack() },
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
                    hideControlsOnBack = failed == null,
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
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.78f)),
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
        }
    }
}
