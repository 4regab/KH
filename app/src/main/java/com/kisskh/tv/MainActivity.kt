package com.kisskh.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.kisskh.tv.api.Country
import com.kisskh.tv.player.VideoPlayer
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.DetailScreen
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.FocusKeys
import com.kisskh.tv.ui.GridScreen
import com.kisskh.tv.ui.HomeScreen
import com.kisskh.tv.ui.SearchScreen
import com.kisskh.tv.ui.UiState
import com.kisskh.tv.ui.onTap
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhFocusable
import com.kisskh.tv.ui.theme.KhTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KhTheme {
                Box(Modifier.fillMaxSize().background(KhColors.Background)) {
                    val nav by vm.nav.collectAsStateWithLifecycle()

                    BackHandler(enabled = true) {
                        if (!vm.onBack()) finish()
                    }

                    when (val d = nav.current) {
                        is Dest.Home -> HomeScreen(vm)
                        is Dest.Search -> SearchScreen(vm)
                        is Dest.Catalog -> BrowseHost(vm, d)
                        is Dest.Countries -> CountriesHost(vm)
                        is Dest.Detail -> DetailScreen(vm, d)
                        is Dest.Player -> PlayerHost(vm, d)
                    }
                }
            }
        }
    }
}

// Stage 1 stand-ins (BrowseHost, CountriesHost): plain grid and list on the new pagers. Stage 2 replaces them.
@Composable
private fun BrowseHost(vm: AppViewModel, dest: Dest.Catalog) {
    val pager = remember(dest.pagerKey) { vm.pager(dest) }
    val state by pager.state.collectAsStateWithLifecycle()
    val title = when (dest) {
        is Dest.Series -> "TV Series"
        is Dest.Movies -> "Movies"
        is Dest.CountryCatalog -> dest.country.label
    }
    when {
        state.isInitialLoading -> Text("Loading...", modifier = Modifier.padding(32.dp))
        state.isInitialError -> RetryMessage("Could not load $title.") { pager.retry() }
        state.isEmpty -> Text("No $title found.", modifier = Modifier.padding(32.dp))
        else -> GridScreen(
            title = title,
            items = state.items,
            onEnd = { pager.loadNext() },
            onClick = {
                vm.saveFocus(FocusKeys.show(it))
                vm.push(Dest.Detail(it))
            },
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CountriesHost(vm: AppViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentPadding = PaddingValues(KhTheme.spacing.focusBleed), // room for the focus zoom and outline
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Countries", style = KhTheme.typography.headline) }
        items(Country.entries, key = { it.id }) { country ->
            KhFocusable(
                onClick = {
                    vm.saveFocus(FocusKeys.country(country))
                    vm.push(Dest.CountryCatalog(country))
                },
                modifier = Modifier.fillMaxWidth().height(56.dp).background(KhColors.Surface),
                shape = KhTheme.shapes.tile,
            ) {
                Text(
                    country.label,
                    style = KhTheme.typography.title,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp),
                )
            }
        }
    }
}

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
