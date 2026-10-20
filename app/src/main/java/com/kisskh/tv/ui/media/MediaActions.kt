package com.kisskh.tv.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaEntry
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.media.TmdbEpisode
import com.kisskh.tv.api.media.TmdbTitle
import com.kisskh.tv.api.media.TmdbTitleDetails
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.UiState

fun AppViewModel.openTitle(title: TmdbTitle, from: MediaKind) {
    val kind = when {
        from == MediaKind.ANIME -> MediaKind.ANIME
        title.mediaType == "tv" -> MediaKind.TV
        else -> MediaKind.MOVIE
    }
    push(Dest.MediaDetail(kind, title.id))
}

fun AppViewModel.playMedia(
    library: Library,
    kind: MediaKind,
    details: TmdbTitleDetails,
    season: Int,
    episode: TmdbEpisode?,
) {
    val title = details.title
    val movie = kind.isMovie
    val playback = if (movie) MediaKind.MOVIE else MediaKind.TV
    val number = if (movie) 1 else episode?.number ?: 1
    val seasonNumber = if (movie) 1 else season.coerceAtLeast(1)
    val poster = title.poster ?: title.artwork
    val name = if (movie) "" else episode?.name.orEmpty()
    if (library == Library.CINEJOY) {
        switchStream(
            Dest.StreamPlayer(title.name, poster, playback, title.id, seasonNumber, number, name),
        )
    } else {
        switchEmbed(
            Dest.EmbedPlayer(title.name, poster, playback, title.id, seasonNumber, number, name),
        )
    }
}

fun AppViewModel.resumeEntry(entry: MediaEntry) {
    val movie = entry.kind == "movie"
    val kind = if (movie) MediaKind.MOVIE else MediaKind.TV
    val season = if (movie) 1 else entry.season.coerceAtLeast(1)
    val episode = if (movie) 1 else entry.episode.coerceAtLeast(1)
    if (entry.source == Library.CINEJOY.key) {
        switchStream(Dest.StreamPlayer(entry.title, entry.poster, kind, entry.id, season, episode))
    } else {
        switchEmbed(Dest.EmbedPlayer(entry.title, entry.poster, kind, entry.id, season, episode))
    }
}

/**
 * The episode after [episode] in the same season, or null for a movie, the last episode, or while the
 * season is still loading. It is read from the season list the title page already loaded, so every
 * episode reached by Next, Continue or Recent gets the same answer.
 */
@Composable
fun rememberNextEpisode(vm: AppViewModel, kind: MediaKind, tmdbId: Int, season: Int, episode: Int): Int? {
    if (kind.isMovie) return null
    val state by remember(tmdbId, season) { vm.media.season(tmdbId, season) }.collectAsStateWithLifecycle()
    val episodes = (state as? UiState.Success)?.data ?: return null
    val index = episodes.indexOfFirst { it.number == episode }
    return if (index >= 0) episodes.getOrNull(index + 1)?.number else null
}

fun kindFor(row: String, title: TmdbTitle): MediaKind = when {
    row == "Anime" -> MediaKind.ANIME
    title.mediaType == "tv" -> MediaKind.TV
    else -> MediaKind.MOVIE
}
