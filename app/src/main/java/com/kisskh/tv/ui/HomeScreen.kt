package com.kisskh.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val error by vm.homeError.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(start = 24.dp, top = 24.dp, end = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("KissKH TV", color = Color(0xFFFF4081), fontSize = 28.sp, modifier = Modifier.padding(end = 24.dp))
                val openSearch = { vm.go(Screen.Search) }
                val openBrowse = { vm.go(Screen.Browse); vm.resetBrowse() }
                Button(onClick = openSearch, modifier = Modifier.padding(end = 12.dp).onTap(openSearch)) { Text("Search") }
                Button(onClick = openBrowse, modifier = Modifier.onTap(openBrowse)) { Text("Browse") }
            }
        }
        if (error != null) {
            item {
                Column(Modifier.fillMaxWidth().padding(32.dp)) {
                    Text(error ?: "", color = Color.White, fontSize = 18.sp)
                    val retry = { vm.retryHome() }
                    Button(onClick = retry, modifier = Modifier.padding(top = 12.dp).onTap(retry)) { Text("Retry") }
                }
            }
        }
        items(rows) { row ->
            Text(row.title, color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(row.items) { show ->
                    PosterCard(show) { vm.go(Screen.Detail(show.id)) }
                }
            }
        }
    }
}
