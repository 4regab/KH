package com.kisskh.tv.ui.media

import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaEntry
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.media.TmdbEpisode
import com.kisskh.tv.api.media.TmdbTitle
import com.kisskh.tv.api.media.TmdbTitleDetails
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest

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
    next: Int?,
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
            Dest.StreamPlayer(title.name, poster, playback, title.id, seasonNumber, number, name, next),
        )
    } else {
        switchEmbed(
            Dest.EmbedPlayer(title.name, poster, playback, title.id, seasonNumber, number, name, next),
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

fun kindFor(row: String, title: TmdbTitle): MediaKind = when {
    row == "Anime" -> MediaKind.ANIME
    title.mediaType == "tv" -> MediaKind.TV
    else -> MediaKind.MOVIE
}
