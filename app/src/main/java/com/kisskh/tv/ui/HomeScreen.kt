package com.kisskh.tv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.kisskh.tv.R

// Stage 1 stand-in: keeps the app working on the new navigation model. Stage 2 replaces this screen.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel) {
    val state by vm.home.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(start = 24.dp, top = 24.dp, end = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.kh_wordmark),
                    contentDescription = stringResource(R.string.kh_logo_description),
                    modifier = Modifier.height(32.dp).padding(end = 24.dp),
                )
                for (tab in RootTab.entries.filter { it != RootTab.HOME }) {
                    val open = { vm.selectTab(tab) }
                    Button(onClick = open, modifier = Modifier.padding(end = 12.dp).onTap(open)) { Text(tab.label) }
                }
            }
        }
        when (val s = state) {
            UiState.Loading -> item { Text("Loading...", modifier = Modifier.padding(32.dp)) }
            is UiState.Empty -> item { Text(s.message ?: "Nothing to show.", modifier = Modifier.padding(32.dp)) }
            is UiState.Error -> item {
                Column(Modifier.fillMaxWidth().padding(32.dp)) {
                    Text(s.message, fontSize = 18.sp)
                    val retry = { vm.retryHome() }
                    Button(onClick = retry, modifier = Modifier.padding(top = 12.dp).onTap(retry)) { Text("Retry") }
                }
            }
            is UiState.Success -> {
                if (s.data.failedRows.isNotEmpty()) item {
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Could not load: ${s.data.failedRows.joinToString()}", modifier = Modifier.padding(end = 12.dp))
                        val retry = { vm.retryHome() }
                        Button(onClick = retry, modifier = Modifier.onTap(retry)) { Text("Retry") }
                    }
                }
                items(s.data.rows) { row ->
                    Text(row.title, fontSize = 20.sp, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(row.items, key = { it.id }) { show ->
                            PosterCard(show) { vm.push(Dest.Detail(show.id)) }
                        }
                    }
                }
            }
        }
    }
}
