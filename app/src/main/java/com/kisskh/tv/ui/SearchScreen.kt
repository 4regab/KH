package com.kisskh.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.kisskh.tv.ui.theme.KhColors

// Stage 1 stand-in: keeps search working on the new SearchController. Stage 2 replaces this screen.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(vm: AppViewModel) {
    val state by vm.search.state.collectAsStateWithLifecycle()
    // The field keeps its own text so typing never waits on the flow; the controller gets every change.
    var query by remember { mutableStateOf(vm.search.state.value.query) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Search", fontSize = 22.sp, modifier = Modifier.padding(bottom = 8.dp))
        BasicTextField(
            value = query,
            onValueChange = { query = it; vm.search.setQuery(it) },
            singleLine = true,
            textStyle = TextStyle(color = KhColors.TextPrimary, fontSize = 18.sp),
            cursorBrush = SolidColor(KhColors.Brand),
            modifier = Modifier.fillMaxWidth().background(KhColors.Surface).padding(12.dp),
        )
        when (val r = state.result) {
            null -> Text("Type to search.", color = KhColors.TextSecondary, modifier = Modifier.padding(top = 12.dp))
            UiState.Loading -> Text("Searching...", color = KhColors.TextSecondary, modifier = Modifier.padding(top = 12.dp))
            is UiState.Empty -> Text("No results.", color = KhColors.TextSecondary, modifier = Modifier.padding(top = 12.dp))
            is UiState.Error -> Column(Modifier.padding(top = 12.dp)) {
                Text(r.message)
                val retry = { vm.search.retry() }
                Button(onClick = retry, modifier = Modifier.padding(top = 8.dp).onTap(retry)) { Text("Retry") }
            }
            is UiState.Success -> LazyVerticalGrid(
                columns = GridCells.Adaptive(130.dp),
                modifier = Modifier.fillMaxSize().padding(top = 12.dp),
            ) {
                items(r.data, key = { it.id }) { show -> PosterCard(show) { vm.push(Dest.Detail(show.id)) } }
            }
        }
    }
}
