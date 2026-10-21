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
) = playTitle(library, kind, details.title, season, episode?.number ?: 1, episode?.name.orEmpty())

/**
 * Play [title] straight from a tile or the billboard, without its detail page. A series with saved
 * progress resumes there; otherwise it starts at [season] episode [episode].
 */
fun AppViewModel.playTitle(
    library: Library,
    kind: MediaKind,
    title: TmdbTitle,
    season: Int = 1,
    episode: Int = 1,
    episodeName: String = "",
) {
    val movie = kind.isMovie
    val playback = if (movie) MediaKind.MOVIE else MediaKind.TV
    val number = if (movie) 1 else episode.coerceAtLeast(1)
    val seasonNumber = if (movie) 1 else season.coerceAtLeast(1)
    val poster = title.poster ?: title.artwork
    val name = if (movie) "" else episodeName
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
    // An episode that has not aired yet is not offered: no server has it.
    return if (index >= 0) episodes.getOrNull(index + 1)?.takeIf { it.aired() }?.number else null
}

fun kindFor(row: String, title: TmdbTitle): MediaKind = when {
    row == "Anime" -> MediaKind.ANIME
    title.mediaType == "tv" -> MediaKind.TV
    else -> MediaKind.MOVIE
}

/** The newest resumable entry for [title] in [library], if the viewer stopped part way. */
fun AppViewModel.savedEntry(library: Library, title: TmdbTitle): MediaEntry? =
    media.progress.entries().firstOrNull {
        it.source == library.key && it.id == title.id && it.kind == title.mediaType && it.resumable
    }

/** Play, or resume where the viewer stopped. Used by the billboard and the detail page's main button. */
fun AppViewModel.playOrResume(library: Library, kind: MediaKind, title: TmdbTitle) {
    val saved = savedEntry(library, title)
    if (saved != null) resumeEntry(saved) else playTitle(library, kind, title)
}

/** "Resume" or "Play", plus the episode for a series ("Resume S1 E3"). */
fun playLabel(saved: MediaEntry?, movie: Boolean): String = when {
    saved == null -> "Play"
    movie -> "Resume"
    else -> "Resume S${saved.season} E${saved.episode}"
}
