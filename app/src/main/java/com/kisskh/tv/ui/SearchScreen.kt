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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text

@Composable
fun SearchScreen(vm: AppViewModel) {
    val results by vm.searchResults.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Search", color = Color.White, fontSize = 22.sp, modifier = Modifier.padding(bottom = 8.dp))
        BasicTextField(
            value = query,
            onValueChange = { query = it; vm.search(it) },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
            cursorBrush = SolidColor(Color(0xFFFF4081)),
            modifier = Modifier.fillMaxWidth()
                .background(Color(0xFF222222))
                .padding(12.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            modifier = Modifier.fillMaxSize().padding(top = 12.dp),
        ) {
            items(results) { show -> PosterCard(show) { vm.go(Screen.Detail(show.id)) } }
        }
    }
}
