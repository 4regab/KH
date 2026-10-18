package com.kisskh.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.kisskh.tv.player.VideoPlayer
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.UiState
import com.kisskh.tv.ui.onTap
import com.kisskh.tv.ui.shell.KhApp
import com.kisskh.tv.ui.theme.KhTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KhTheme {
                KhApp(vm = vm, onExit = { finish() }) { player -> PlayerHost(vm, player) }
            }
        }
    }
}

// Player screen: still the stage 1 version (messages and Retry). Stage 3 restyles it.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RetryMessage(message: String, onRetry: () -> Unit) {
    Column(Modifier.padding(32.dp)) {
        Text(message, fontSize = 18.sp)
        Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp).onTap(onRetry)) { Text("Retry") }
    }
}

@UnstableApi
@Composable
private fun PlayerHost(vm: AppViewModel, dest: Dest.Player) {
    val state by vm.playerState.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            UiState.Loading -> Text("Preparing \"${dest.title}\"...", fontSize = 18.sp, modifier = Modifier.padding(32.dp))
            is UiState.Empty -> Text(s.message ?: "Not available.", fontSize = 18.sp, modifier = Modifier.padding(32.dp))
            is UiState.Error -> RetryMessage(s.message) { vm.retryPlayer() }
            is UiState.Success -> VideoPlayer(
                url = s.data.url,
                subs = s.data.subs,
                resumeMs = s.data.resumeMs,
                onProgress = { pos, dur -> vm.savePosition(dest.episodeId, pos, dur) },
            )
        }
    }
}
