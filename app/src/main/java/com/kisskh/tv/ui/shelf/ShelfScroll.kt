package com.kisskh.tv.ui.shelf

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.kisskh.tv.ui.AppViewModel

@Composable
internal fun rememberSavedList(vm: AppViewModel, key: String): LazyListState {
    val saved = vm.shelf.scrolls[key]
    val state = rememberLazyListState(saved?.first ?: 0, saved?.second ?: 0)
    DisposableEffect(key) {
        onDispose { vm.shelf.scrolls[key] = state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
    }
    return state
}

@Composable
internal fun rememberSavedGrid(vm: AppViewModel, key: String): LazyGridState {
    val saved = vm.shelf.scrolls[key]
    val state = rememberLazyGridState(saved?.first ?: 0, saved?.second ?: 0)
    DisposableEffect(key) {
        onDispose { vm.shelf.scrolls[key] = state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
    }
    return state
}
