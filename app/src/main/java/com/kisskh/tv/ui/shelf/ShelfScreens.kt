package com.kisskh.tv.ui.shelf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.source.EpisodeRef
import com.kisskh.tv.api.source.TitleCard
import com.kisskh.tv.api.source.TitleDetail
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.UiState
import com.kisskh.tv.ui.components.Artwork
import com.kisskh.tv.ui.components.EmptyState
import com.kisskh.tv.ui.components.ErrorState
import com.kisskh.tv.ui.components.KhButton
import com.kisskh.tv.ui.components.KhChip
import com.kisskh.tv.ui.components.LoadingState
import com.kisskh.tv.ui.components.cardGap
import com.kisskh.tv.ui.components.gridCardMinWidth
import com.kisskh.tv.ui.components.heroHeight
import com.kisskh.tv.ui.components.screenContentPadding
import com.kisskh.tv.ui.components.screenStart
import com.kisskh.tv.ui.media.KhField
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhFocusable
import com.kisskh.tv.ui.theme.KhTheme

@Composable
fun ShelfHome(vm: AppViewModel, library: Library) {
    val state by remember(library) { vm.shelf.home(library) }.collectAsStateWithLifecycle()
    when (val s = state) {
        UiState.Loading -> LoadingState(message = "Loading ${library.label}")
        is UiState.Error -> ErrorState(s.message, onRetry = { vm.shelf.retryHome(library) }, autoFocus = true)
        is UiState.Empty -> EmptyState(s.message ?: "Nothing to show right now.")
        is UiState.Success -> {
            LazyColumn(contentPadding = screenContentPadding(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                items(s.data, key = { it.title }) { row ->
                    Text(
                        row.title,
                        style = KhTheme.typography.rowTitle,
                        color = KhColors.TextPrimary,
                        modifier = Modifier.padding(start = screenStart(), bottom = 8.dp),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(start = screenStart(), end = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(cardGap()),
                    ) {
                        items(row.items, key = { "${it.source.key}:${it.kind}:${it.id}:${it.resumeEpisode}" }) { card ->
                            ShelfCard(card, Modifier.width(220.dp)) { vm.openCard(card, library) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShelfBrowse(vm: AppViewModel, library: Library, kind: MediaKind) {
    val state by remember(library, kind) { vm.shelf.browse(library, kind) }.collectAsStateWithLifecycle()
    val grid = rememberLazyGridState()
    val title = when (kind) {
        MediaKind.MOVIE -> "Movies"
        MediaKind.TV -> if (library == Library.KISSKH) "TV Series" else "TV Shows"
        MediaKind.ANIME -> "Anime"
    }
    Column(Modifier.fillMaxSize()) {
        Text(title, style = KhTheme.typography.title, color = KhColors.TextPrimary, modifier = Modifier.padding(start = screenStart(), top = 16.dp, bottom = 8.dp))
        when {
            state.loading -> LoadingState(message = "Loading $title")
            state.error != null && state.items.isEmpty() -> ErrorState(state.error!!, onRetry = { vm.shelf.retryBrowse(library, kind) }, autoFocus = true)
            state.items.isEmpty() -> EmptyState("Nothing in $title yet.")
            else -> {
                val tail = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                LaunchedEffect(tail, state.items.size, state.hasMore) {
                    if (state.hasMore && tail >= state.items.size - 6) vm.shelf.loadMore(library, kind)
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(gridCardMinWidth()),
                    state = grid,
                    contentPadding = screenContentPadding(),
                    horizontalArrangement = Arrangement.spacedBy(cardGap()),
                    verticalArrangement = Arrangement.spacedBy(cardGap()),
                ) {
                    itemsIndexed(state.items, key = { index, card -> "$index:${card.id}" }) { _, card ->
                        ShelfCard(card, Modifier.fillMaxWidth(), showName = true) { vm.openCard(card, library) }
                    }
                }
            }
        }
    }
}

@Composable
fun ShelfSearch(vm: AppViewModel, library: Library) {
    val query by vm.shelf.query.collectAsStateWithLifecycle()
    val all by vm.shelf.allSources.collectAsStateWithLifecycle()
    val results by vm.shelf.results.collectAsStateWithLifecycle()
    LaunchedEffect(library) {
        if (query.isNotBlank() && !all) vm.shelf.setQuery(library, query)
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = screenStart(), end = 24.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            KhField(value = query, onValueChange = { vm.shelf.setQuery(library, it) }, hint = "Search ${library.label}", modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KhChip("This source", selected = !all, onClick = { vm.shelf.setQuery(library, query, all = false) })
                KhChip("All sources", selected = all, onClick = { vm.shelf.setQuery(library, query, all = true) })
            }
        }
        when (val s = results) {
            UiState.Loading -> LoadingState(message = "Searching")
            is UiState.Empty -> EmptyState(s.message ?: "Type a title.")
            is UiState.Error -> ErrorState(s.message, onRetry = { vm.shelf.setQuery(library, query, all = all) })
            is UiState.Success -> LazyVerticalGrid(
                columns = GridCells.Adaptive(gridCardMinWidth()),
                contentPadding = screenContentPadding(),
                horizontalArrangement = Arrangement.spacedBy(cardGap()),
                verticalArrangement = Arrangement.spacedBy(cardGap()),
            ) {
                itemsIndexed(s.data, key = { index, card -> "$index:${card.source.key}:${card.id}" }) { _, card ->
                    ShelfCard(card, Modifier.fillMaxWidth(), showName = true) { vm.openCard(card, card.source) }
                }
            }
        }
    }
}

@Composable
fun ShelfDetail(vm: AppViewModel, library: Library, dest: Dest.TitlePage) {
    val state by remember(library, dest.id, dest.kind) {
        vm.shelf.detail(library, dest.id, dest.kind, dest.slug, dest.name, dest.poster)
    }.collectAsStateWithLifecycle()
    when (val s = state) {
        UiState.Loading -> LoadingState(message = "Loading")
        is UiState.Error -> ErrorState(s.message, onRetry = { vm.shelf.retryDetail(library, dest.id, dest.kind, dest.slug, dest.name, dest.poster) }, autoFocus = true)
        is UiState.Empty -> EmptyState(s.message ?: "This title is not available.")
        is UiState.Success -> DetailBody(vm, library, s.data)
    }
}

@Composable
private fun DetailBody(vm: AppViewModel, library: Library, detail: TitleDetail) {
    val card = detail.card
    val movie = card.kind == MediaKind.MOVIE
    var seasonNumber by remember(card.id) { mutableIntStateOf(detail.seasons.firstOrNull { it.number > 0 }?.number ?: detail.seasons.firstOrNull()?.number ?: 1) }
    val selected = detail.seasons.firstOrNull { it.number == seasonNumber } ?: detail.seasons.firstOrNull()
    val inline = selected?.episodes.orEmpty()
    val remote by remember(library, card.id, seasonNumber, inline.isEmpty() && !movie) {
        if (inline.isNotEmpty() || movie) {
            vm.shelf.rememberEpisodes(library, card.id, seasonNumber, inline)
        }
        if (inline.isEmpty() && !movie) vm.shelf.season(library, card.id, seasonNumber)
        else kotlinx.coroutines.flow.MutableStateFlow(UiState.Success(inline))
    }.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Box(Modifier.fillMaxWidth().height(heroHeight())) {
                Artwork(card.artwork, card.name, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(KhColors.Scrim.copy(alpha = 0.35f)))
                Column(Modifier.align(Alignment.BottomStart).padding(start = screenStart(), end = 24.dp, bottom = 20.dp)) {
                    Text(card.name, style = KhTheme.typography.title, color = KhColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val meta = listOfNotNull(card.year, detail.certification, card.meta).joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(meta, color = KhColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (detail.description.isNotBlank()) {
                        Text(detail.description, color = KhColors.TextSecondary, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                    }
                    val first = (remote as? UiState.Success)?.data?.firstOrNull() ?: detail.playable
                    if (first != null) {
                        KhButton(
                            text = if (movie) "Play" else "Play ${first.name}",
                            onClick = { vm.playTitle(library, card, first, episodesOf(remote, inline)) },
                            primary = true,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
        }
        if (!movie && detail.seasons.size > 1) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(start = screenStart(), end = 24.dp, top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(detail.seasons, key = { it.number }) { season ->
                        KhChip(season.name, selected = season.number == seasonNumber, onClick = { seasonNumber = season.number })
                    }
                }
            }
        }
        if (!movie) {
            when (val eps = remote) {
                UiState.Loading -> item { LoadingState(message = "Loading episodes", modifier = Modifier.fillMaxWidth().height(160.dp)) }
                is UiState.Error -> item {
                    ErrorState(eps.message, onRetry = { vm.shelf.retrySeason(library, card.id, seasonNumber) }, modifier = Modifier.fillMaxWidth().height(180.dp))
                }
                is UiState.Empty -> item { EmptyState(eps.message ?: "No episodes in this season.", modifier = Modifier.fillMaxWidth().height(120.dp)) }
                is UiState.Success -> {
                    vm.shelf.rememberEpisodes(library, card.id, seasonNumber, eps.data)
                    itemsIndexed(eps.data, key = { _, ep -> ep.id + ":" + ep.number }) { _, episode ->
                        EpisodeRow(episode) { vm.playTitle(library, card, episode, eps.data) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: EpisodeRef, onClick: () -> Unit) {
    KhFocusable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = screenStart(), vertical = 4.dp).semantics { contentDescription = episode.name },
        shape = KhTheme.shapes.action,
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(episode.still, episode.name, Modifier.width(120.dp).aspectRatio(16f / 9f))
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(episode.name, color = KhColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val marks = listOfNotNull(if (episode.sub) "Sub" else null, if (episode.dub) "Dub" else null)
                if (marks.isNotEmpty()) Text(marks.joinToString(" · "), color = KhColors.TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ShelfCard(card: TitleCard, modifier: Modifier, showName: Boolean = false, onClick: () -> Unit) {
    KhFocusable(
        onClick = onClick,
        modifier = modifier.onFocusChanged { }.semantics { contentDescription = card.name },
        shape = KhTheme.shapes.art,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                Artwork(card.artwork, card.name, Modifier.fillMaxSize())
            }
            if (showName || card.meta != null) {
                Text(
                    card.name,
                    color = KhColors.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
                )
                if (!card.meta.isNullOrBlank()) {
                    Text(card.meta, color = KhColors.TextSecondary, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(start = 2.dp))
                }
            }
        }
    }
}

private fun episodesOf(state: UiState<List<EpisodeRef>>, inline: List<EpisodeRef>): List<EpisodeRef> =
    (state as? UiState.Success)?.data ?: inline

private fun AppViewModel.openCard(card: TitleCard, library: Library) {
    if (card.resumeEpisode != null && library == Library.KISSKH) {
        play(
            Dest.Player(
                episodeId = card.resumeEpisode.toIntOrNull() ?: return,
                dramaId = card.id.toIntOrNull() ?: return,
                title = card.name,
                label = card.meta ?: "Episode",
                episodeNumber = card.resumeNumber.toFloat(),
                thumbnail = card.poster,
            ),
        )
        return
    }
    if (card.resumeEpisode != null) {
        openWatch(
            Dest.Watch(
                source = card.source.key,
                titleId = card.id,
                kind = card.kind,
                slug = card.slug,
                season = card.resumeSeason.coerceAtLeast(1),
                episode = card.resumeNumber.coerceAtLeast(1),
                title = card.name,
                poster = card.poster,
            ),
        )
        return
    }
    if (card.source != library) selectLibrary(card.source)
    push(Dest.TitlePage(card.id, card.kind, card.slug, card.name, card.poster))
}

private fun AppViewModel.playTitle(library: Library, card: TitleCard, episode: EpisodeRef, episodes: List<EpisodeRef>) {
    shelf.rememberEpisodes(library, card.id, episode.season, episodes)
    if (library == Library.KISSKH) {
        val episodeId = episode.id.toIntOrNull() ?: return
        val dramaId = card.id.toIntOrNull() ?: return
        play(Dest.Player(episodeId, dramaId, card.name, episode.name, episode.number.toFloat(), card.poster))
        return
    }
    openWatch(
        Dest.Watch(
            source = library.key,
            titleId = card.id,
            kind = card.kind,
            slug = card.slug,
            season = episode.season.coerceAtLeast(1),
            episode = episode.number.coerceAtLeast(1),
            episodeId = episode.id,
            episodeSlug = episode.slug,
            episodeToken = episode.token,
            episodeName = episode.name,
            title = card.name,
            poster = card.poster ?: card.backdrop,
        ),
    )
}
