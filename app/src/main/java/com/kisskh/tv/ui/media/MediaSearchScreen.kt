package com.kisskh.tv.ui.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.UiState
import com.kisskh.tv.ui.components.EmptyState
import com.kisskh.tv.ui.components.ErrorState
import com.kisskh.tv.ui.components.KhButton
import com.kisskh.tv.ui.components.LoadingState
import com.kisskh.tv.ui.components.cardGap
import com.kisskh.tv.ui.components.gridCardMinWidth
import com.kisskh.tv.ui.components.screenContentPadding
import com.kisskh.tv.ui.components.screenStart
import com.kisskh.tv.ui.theme.FormFactor
import com.kisskh.tv.ui.theme.KhTheme

@Composable
fun MediaSearchScreen(vm: AppViewModel) {
    val query by vm.media.query.collectAsStateWithLifecycle()
    val results by vm.media.results.collectAsStateWithLifecycle()
    val names = KhTheme.formFactor != FormFactor.TV
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = screenStart(), end = 24.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            KhField(
                value = query,
                onValueChange = { vm.media.setQuery(it) },
                hint = "Movies and TV shows",
                modifier = Modifier.fillMaxWidth(),
                imeAction = ImeAction.Search,
                onAction = { vm.media.setQuery(query, immediate = true) },
            )
            KhButton("Search", onClick = { vm.media.setQuery(query, immediate = true) }, primary = true)
        }
        when (val s = results) {
            UiState.Loading -> LoadingState(message = "Searching")
            is UiState.Error -> ErrorState(s.message, onRetry = { vm.media.setQuery(query, immediate = true) })
            is UiState.Empty -> EmptyState(s.message ?: "Type a title.")
            is UiState.Success -> LazyVerticalGrid(
                columns = GridCells.Adaptive(gridCardMinWidth()),
                modifier = Modifier.fillMaxSize(),
                contentPadding = screenContentPadding(),
                horizontalArrangement = Arrangement.spacedBy(cardGap()),
                verticalArrangement = Arrangement.spacedBy(cardGap()),
            ) {
                items(s.data, key = { "${it.mediaType}:${it.id}" }) { item ->
                    MediaCard(
                        title = item,
                        showName = names,
                        onClick = {
                            vm.openTitle(item, if (item.mediaType == "tv") MediaKind.TV else MediaKind.MOVIE)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
