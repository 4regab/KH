package com.kisskh.tv.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKeys
import com.kisskh.tv.api.media.TmdbTitle
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.UiState
import com.kisskh.tv.ui.components.Artwork
import com.kisskh.tv.ui.components.ErrorState
import com.kisskh.tv.ui.components.KhButton
import com.kisskh.tv.ui.components.KhCardSize
import com.kisskh.tv.ui.components.LoadingState
import com.kisskh.tv.ui.components.screenStart
import com.kisskh.tv.ui.theme.FormFactor
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhTheme

@Composable
fun MediaHomeScreen(vm: AppViewModel, library: Library) {
    val state by remember(library) { vm.media.home(library) }.collectAsStateWithLifecycle()
    LaunchedEffect(library) { vm.media.refreshLocal(library) }
    val tv = KhTheme.formFactor == FormFactor.TV
    when (val s = state) {
        UiState.Loading -> LoadingState(message = "Loading ${library.label}")
        is UiState.Error -> ErrorState(s.message, onRetry = { vm.media.retryHome(library) }, autoFocus = true)
        is UiState.Empty -> ErrorState(s.message ?: "Nothing to show right now.", onRetry = { vm.media.retryHome(library) })
        is UiState.Success -> if (tv) TvHome(vm, library, s.data) else PhoneHome(vm, library, s.data)
    }
}

@Composable
private fun TvHome(vm: AppViewModel, library: Library, home: MediaHome) {
    val first = home.rows.firstOrNull()?.items?.firstOrNull()
    var focused by remember(library) { mutableStateOf(first) }
    Column(Modifier.fillMaxSize()) {
        Billboard(focused ?: first, library, Modifier.fillMaxWidth().weight(0.42f))
        LazyColumn(Modifier.weight(0.58f).fillMaxWidth()) {
            // First, so a remote reaches it in one press instead of scrolling past every row.
            if (library == Library.CINEJOY) {
                item { TokenButton(onClick = { vm.push(Dest.Token()) }) }
            }
            items(home.rows, key = { it.title }) { row ->
                Text(
                    row.title,
                    style = KhTheme.typography.rowTitle,
                    color = KhColors.TextPrimary,
                    modifier = Modifier.padding(start = screenStart(), top = 8.dp, bottom = 4.dp),
                )
                MediaRail(vm, library, row, onFocused = { focused = it })
            }
            if (home.failed.isNotEmpty()) {
                item {
                    Row(
                        Modifier.padding(start = screenStart(), top = 8.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "Couldn't load ${home.failed.joinToString(", ")}.",
                            style = KhTheme.typography.body,
                            color = KhColors.TextSecondary,
                        )
                        KhButton("Retry", onClick = { vm.media.retryHome(library) }, primary = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneHome(vm: AppViewModel, library: Library, home: MediaHome) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = top, bottom = 12.dp)) {
        item {
            Text(
                library.label,
                style = KhTheme.typography.headline,
                color = KhColors.TextPrimary,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
            )
        }
        if (library == Library.CINEJOY) {
            item { TokenButton(onClick = { vm.push(Dest.Token()) }) }
        }
        items(home.rows, key = { it.title }) { row ->
            Text(
                row.title,
                style = KhTheme.typography.rowTitle,
                color = KhColors.TextPrimary,
                modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp),
            )
            MediaRail(vm, library, row, onFocused = {})
        }
        if (home.failed.isNotEmpty()) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Couldn't load ${home.failed.joinToString(", ")}.",
                        style = KhTheme.typography.body,
                        color = KhColors.TextSecondary,
                    )
                    KhButton("Retry", onClick = { vm.media.retryHome(library) }, primary = true)
                }
            }
        }
    }
}

@Composable
private fun TokenButton(onClick: () -> Unit) {
    KhButton(
        "Febbox token",
        onClick = onClick,
        modifier = Modifier.padding(start = screenStart(), top = 8.dp, bottom = 8.dp),
    )
}

@Composable
private fun Billboard(title: TmdbTitle?, library: Library, modifier: Modifier) {
    Box(modifier.background(KhColors.Surface)) {
        Artwork(title?.artwork, title?.name, Modifier.fillMaxSize(), titleOnFailure = false)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.35f to Color.Transparent,
                    1f to KhColors.Background,
                ),
            ),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(start = screenStart(), end = 48.dp, bottom = 16.dp)) {
            Text(library.label, style = KhTheme.typography.label, color = KhColors.TextSecondary)
            Spacer(Modifier.height(6.dp))
            Text(
                title?.name ?: library.label,
                style = KhTheme.typography.display,
                color = KhColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(title?.year, title?.voteText()).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, style = KhTheme.typography.meta, color = KhColors.TextSecondary, modifier = Modifier.padding(top = 6.dp))
            }
            val overview = title?.overview?.trim().orEmpty()
            if (overview.isNotEmpty()) {
                Text(
                    overview,
                    style = KhTheme.typography.synopsis,
                    color = KhColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun MediaRail(
    vm: AppViewModel,
    library: Library,
    row: MediaRow,
    onFocused: (TmdbTitle) -> Unit,
) {
    val names = KhTheme.formFactor != FormFactor.TV
    LazyRow(
        contentPadding = PaddingValues(start = screenStart(), end = 48.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(row.items.size) { index ->
            val title = row.items[index]
            val entry = row.resumes?.getOrNull(index)
            val progress = when {
                entry != null && entry.durationMs > 0 && entry.positionMs > 0 ->
                    entry.positionMs.toFloat() / entry.durationMs
                title.mediaType == "movie" -> vm.media.progress.fraction(MediaKeys.movie(library.key, title.id))
                else -> vm.media.progress.continuing(library.key)
                    .firstOrNull { it.id == title.id && it.kind == "tv" && it.durationMs > 0 }
                    ?.let { it.positionMs.toFloat() / it.durationMs }
            }
            MediaCard(
                title = title,
                showName = names,
                progress = progress,
                onClick = {
                    if (entry != null && entry.resumable) vm.resumeEntry(entry) else vm.openTitle(title, kindFor(row.title, title))
                },
                onFocused = { onFocused(title) },
                modifier = Modifier.width(if (names) KhCardSize.PhoneWidth else KhCardSize.TvWidth),
            )
        }
    }
}
