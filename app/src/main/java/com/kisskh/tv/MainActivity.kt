package com.kisskh.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import com.kisskh.tv.player.VideoPlayer
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.DetailScreen
import com.kisskh.tv.ui.GridScreen
import com.kisskh.tv.ui.HomeScreen
import com.kisskh.tv.ui.Screen
import com.kisskh.tv.ui.SearchScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    @OptIn(ExperimentalTvMaterial3Api::class, UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize(), colors = androidx.tv.material3.SurfaceDefaults.colors(containerColor = Color.Black)) {
                    val screen by vm.screen.collectAsStateWithLifecycle()

                    BackHandler(enabled = true) {
                        if (!vm.onBack()) finish()
                    }

                    when (val s = screen) {
                        is Screen.Home -> HomeScreen(vm)
                        is Screen.Search -> SearchScreen(vm)
                        is Screen.Browse -> BrowseHost(vm)
                        is Screen.Detail -> DetailScreen(vm)
                        is Screen.Player -> PlayerHost(vm, s)
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseHost(vm: AppViewModel) {
    val items by vm.browseItems.collectAsStateWithLifecycle()
    GridScreen(
        title = "Browse — Popular",
        items = items,
        onEnd = { vm.loadMoreBrowse() },
        onClick = { vm.go(Screen.Detail(it)) },
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerHost(vm: AppViewModel, s: Screen.Player) {
    val data by vm.player.collectAsStateWithLifecycle()
    val error by vm.playerError.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        val d = data
        when {
            error != null -> Text(error ?: "", color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(32.dp))
            d == null -> Text("Preparing \"${s.title}\"...", color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(32.dp))
            else -> VideoPlayer(
                url = d.url,
                subs = d.subs,
                resumeMs = d.resumeMs,
                onProgress = { pos, dur -> vm.savePosition(s.episodeId, pos, dur) },
            )
        }
    }
}
