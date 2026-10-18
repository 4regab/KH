package com.kisskh.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailScreen(vm: AppViewModel) {
    val drama by vm.detail.collectAsStateWithLifecycle()
    val d = drama
    if (d == null) {
        Box(Modifier.fillMaxSize()) { Text("Loading...", color = Color.White, modifier = Modifier.padding(32.dp)) }
        return
    }
    Row(Modifier.fillMaxSize().padding(32.dp)) {
        AsyncImage(
            model = thumbUrl(d.thumbnail),
            contentDescription = d.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(240.dp).height(360.dp),
        )
        Column(Modifier.fillMaxWidth().padding(start = 24.dp)) {
            Text(d.title ?: "", color = Color.White, fontSize = 26.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(d.type, d.country, d.status).joinToString("  •  "),
                color = Color(0xFFBBBBBB), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                d.description ?: "", color = Color(0xFFDDDDDD), fontSize = 13.sp,
                maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp),
            )
            Text("Episodes", color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(d.episodes) { index, ep ->
                    val num = if (ep.number % 1f == 0f) ep.number.toInt().toString() else ep.number.toString()
                    val focusRequester = remember { FocusRequester() }
                    if (index == 0) {
                        LaunchedEffect(d.id) { runCatching { focusRequester.requestFocus() } }
                    }
                    Card(
                        onClick = { vm.play(ep.id, "${d.title} — EP $num") },
                        modifier = Modifier.fillMaxWidth().then(
                            if (index == 0) Modifier.focusRequester(focusRequester) else Modifier
                        ),
                    ) {
                        Text("Episode $num", color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun GridScreen(title: String, items: List<com.kisskh.tv.api.Show>, onEnd: () -> Unit, onClick: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text(title, color = Color.White, fontSize = 22.sp, modifier = Modifier.padding(bottom = 8.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(6), modifier = Modifier.fillMaxSize()) {
            items(items) { show ->
                PosterCard(show) { onClick(show.id) }
            }
            item { androidx.compose.runtime.LaunchedEffect(items.size) { onEnd() } }
        }
    }
}
