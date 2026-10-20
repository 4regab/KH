package com.kisskh.tv.ui.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaEntry
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.media.TmdbEpisode
import com.kisskh.tv.api.media.TmdbTitleDetails
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.UiState
import com.kisskh.tv.ui.components.Artwork
import com.kisskh.tv.ui.components.EmptyState
import com.kisskh.tv.ui.components.ErrorState
import com.kisskh.tv.ui.components.KhButton
import com.kisskh.tv.ui.components.KhChip
import com.kisskh.tv.ui.components.LoadingState
import com.kisskh.tv.ui.components.heroHeight
import com.kisskh.tv.ui.components.screenStart
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhFocusable
import com.kisskh.tv.ui.theme.KhTheme

@Composable
fun MediaDetailScreen(vm: AppViewModel, library: Library, dest: Dest.MediaDetail) {
    val state by remember(dest.kind, dest.tmdbId) { vm.media.detail(dest.kind, dest.tmdbId) }.collectAsStateWithLifecycle()
    when (val s = state) {
        UiState.Loading -> LoadingState(message = "Loading")
        is UiState.Error -> ErrorState(s.message, onRetry = { vm.media.retryDetail(dest.kind, dest.tmdbId) }, autoFocus = true)
        is UiState.Empty -> EmptyState(s.message ?: "This title isn't available.")
        is UiState.Success -> DetailBody(vm, library, dest.kind, s.data)
    }
}

@Composable
private fun DetailBody(vm: AppViewModel, library: Library, kind: MediaKind, details: TmdbTitleDetails) {
    val title = details.title
    val saved = remember(library, title.id, vm.media.progress.entries().size) {
        vm.media.progress.entries().firstOrNull { it.source == library.key && it.id == title.id && it.resumable }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Artwork(
                title.artwork,
                title.name,
                Modifier.fillMaxWidth().height(heroHeight()),
                fitPortrait = true,
                titleOnFailure = false,
            )
        }
        item {
            Column(Modifier.padding(start = screenStart(), end = 32.dp, top = 16.dp)) {
                Text(title.name, style = KhTheme.typography.display, color = KhColors.TextPrimary, maxLines = 3)
                val meta = listOfNotNull(title.year, title.voteText(), details.certification).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(meta, style = KhTheme.typography.meta, color = KhColors.TextSecondary, modifier = Modifier.padding(top = 8.dp))
                }
                if (title.overview.isNotBlank()) {
                    Text(
                        title.overview,
                        style = KhTheme.typography.body,
                        color = KhColors.TextSecondary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                val note = if (library == Library.CINEJOY) {
                    "Plays the Febbox stream in the app when a token is saved."
                } else {
                    "Plays in the built-in browser. If a server fails, the next one is tried."
                }
                Text(note, style = KhTheme.typography.caption, color = KhColors.TextSecondary, modifier = Modifier.padding(top = 12.dp))
                if (kind.isMovie) {
                    KhButton(
                        text = if (saved != null) "Resume" else "Play",
                        onClick = { vm.playMedia(library, kind, details, season = 1, episode = null, next = null) },
                        primary = true,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
        if (!kind.isMovie) {
            item {
                SeasonBlock(vm, library, kind, details, saved)
            }
        }
        if (details.recommendations.isNotEmpty()) {
            item {
                Text(
                    "Recommendations",
                    style = KhTheme.typography.rowTitle,
                    color = KhColors.TextPrimary,
                    modifier = Modifier.padding(start = screenStart(), top = 20.dp, bottom = 8.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(start = screenStart(), end = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(details.recommendations, key = { "${it.mediaType}:${it.id}" }) { item ->
                        MediaCard(
                            title = item,
                            showName = true,
                            onClick = { vm.openTitle(item, if (item.mediaType == "tv") MediaKind.TV else MediaKind.MOVIE) },
                            modifier = Modifier.width(160.dp).padding(bottom = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonBlock(
    vm: AppViewModel,
    library: Library,
    kind: MediaKind,
    details: TmdbTitleDetails,
    saved: MediaEntry?,
) {
    val seasons = details.seasons
    if (seasons.isEmpty()) {
        EmptyState("No episodes yet.", modifier = Modifier.fillMaxWidth().height(120.dp))
        return
    }
    var seasonNumber by remember(details.title.id) {
        mutableIntStateOf(saved?.season?.takeIf { seasons.any { s -> s.number == it } } ?: seasons.first().number)
    }
    val seasonState by remember(details.title.id, seasonNumber) {
        vm.media.season(details.title.id, seasonNumber)
    }.collectAsStateWithLifecycle()
    Column(Modifier.padding(top = 16.dp)) {
        LazyRow(
            contentPadding = PaddingValues(start = screenStart(), end = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(seasons, key = { it.number }) { season ->
                KhChip(
                    text = season.name,
                    selected = season.number == seasonNumber,
                    onClick = { seasonNumber = season.number },
                )
            }
        }
        when (val s = seasonState) {
            UiState.Loading -> LoadingState(message = "Loading episodes", modifier = Modifier.fillMaxWidth().height(160.dp))
            is UiState.Error -> ErrorState(
                s.message,
                onRetry = { vm.media.retrySeason(details.title.id, seasonNumber) },
                modifier = Modifier.fillMaxWidth().height(180.dp),
            )
            is UiState.Empty -> EmptyState(s.message ?: "No episodes in this season.", modifier = Modifier.fillMaxWidth().height(120.dp))
            is UiState.Success -> {
                val episodes = s.data
                episodes.forEachIndexed { index, episode ->
                    val next = episodes.getOrNull(index + 1)?.number
                    val resumeHere = saved != null && saved.season == seasonNumber && saved.episode == episode.number
                    EpisodeRow(
                        episode = episode,
                        resume = resumeHere,
                        onClick = {
                            vm.playMedia(library, kind, details, seasonNumber, episode, next)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: TmdbEpisode, resume: Boolean, onClick: () -> Unit) {
    KhFocusable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        shape = KhTheme.shapes.action,
        scaleAndLift = false,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                episode.number.toString(),
                style = KhTheme.typography.title,
                color = KhColors.TextSecondary,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(episode.name, style = KhTheme.typography.title, color = KhColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val runtime = episode.runtime?.let { "$it min" }
                if (runtime != null) {
                    Text(runtime, style = KhTheme.typography.caption, color = KhColors.TextSecondary)
                }
            }
            if (resume) {
                Text("Resume", style = KhTheme.typography.label, color = KhColors.TextPrimary)
            }
        }
    }
}
