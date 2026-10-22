package com.kisskh.tv.ui.shelf

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.key
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.source.EpisodeRef
import com.kisskh.tv.api.source.TitleCard
import com.kisskh.tv.api.source.progressKey
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.components.Artwork

/** The newest progress entry for [card] on [library]: season and episode (0 for a movie), or null. */
internal data class LastWatched(val season: Int, val episode: Int, val episodeId: String?, val resumable: Boolean, val finished: Boolean)

internal fun AppViewModel.lastWatched(library: Library, card: TitleCard): LastWatched? {
    if (library == Library.KISSKH) {
        val entry = repo.history.entry(card.id.toIntOrNull() ?: return null) ?: return null
        return LastWatched(1, entry.episodeNumber.toInt(), entry.episodeId.toString(), entry.resumable, entry.finished)
    }
    val entry = media.progress.entries().firstOrNull { it.source == library.key && it.token == card.id } ?: return null
    return LastWatched(entry.season, entry.episode, null, entry.resumable, entry.finished)
}

/** 0..1 for an episode the viewer started, null otherwise. */
internal fun AppViewModel.episodeProgress(library: Library, card: TitleCard, episode: EpisodeRef): Float? {
    if (library == Library.KISSKH) {
        val entry = repo.history.entry(card.id.toIntOrNull() ?: return null) ?: return null
        return entry.progress?.takeIf { entry.episodeId.toString() == episode.id && entry.resumable }
    }
    val movie = card.kind == MediaKind.MOVIE
    return media.progress.fraction(progressKey(library.key, if (movie) MediaKind.MOVIE else card.kind, card.id, episode.season, episode.number))
}

internal fun mainAction(
    vm: AppViewModel,
    library: Library,
    card: TitleCard,
    movie: Boolean,
    season: Int,
    episodes: List<EpisodeRef>,
    last: LastWatched?,
): MainAction {
    if (movie) {
        val episode = episodes.firstOrNull { last?.episodeId != null && it.id == last.episodeId } ?: episodes.firstOrNull()
            ?: EpisodeRef(card.id, 0, 1, "Movie", card.artwork)
        val label = if (last?.resumable == true) "Resume" else "Play"
        return MainAction(label) { vm.playTitle(library, card, episode, episodes) }
    }
    val tag: (Int, Int) -> String = { s, e -> if (library == Library.KISSKH || card.kind == MediaKind.ANIME) "E$e" else "S$s E$e" }
    if (last != null && !last.finished && last.episode > 0) {
        val known = episodes.firstOrNull { (last.episodeId != null && it.id == last.episodeId) || (it.number == last.episode && it.season.coerceAtLeast(1) == last.season.coerceAtLeast(1)) }
        val label = (if (last.resumable) "Resume " else "Play ") + tag(last.season.coerceAtLeast(1), last.episode)
        return MainAction(label) {
            if (known != null) vm.playTitle(library, card, known, episodes) else vm.playNumbers(library, card, last.season, last.episode)
        }
    }
    if (last != null && last.finished) {
        val index = episodes.indexOfFirst { it.number == last.episode && it.season.coerceAtLeast(1) == last.season.coerceAtLeast(1) }
        val next = episodes.getOrNull(index + 1)?.takeIf { index >= 0 }
        if (next != null) return MainAction("Play " + tag(next.season.coerceAtLeast(1), next.number)) { vm.playTitle(library, card, next, episodes) }
    }
    val first = episodes.firstOrNull()
    return if (first != null) {
        MainAction("Play " + tag(first.season.coerceAtLeast(1), first.number)) { vm.playTitle(library, card, first, episodes) }
    } else {
        MainAction("Play " + tag(season.coerceAtLeast(1), 1)) { vm.playNumbers(library, card, season, 1) }
    }
}

internal fun AppViewModel.openCard(card: TitleCard, library: Library, autoplay: Boolean = false) {
    if (card.resumeEpisode != null && card.source == Library.KISSKH) {
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
    push(Dest.TitlePage(card.id, card.kind, card.slug, card.name, card.backdrop ?: card.poster, autoplay = autoplay))
}

/** Play season [season] episode [number] when the episode list is not loaded; the player looks it up. */
private fun AppViewModel.playNumbers(library: Library, card: TitleCard, season: Int, number: Int) {
    openWatch(
        Dest.Watch(
            source = library.key,
            titleId = card.id,
            kind = card.kind,
            slug = card.slug,
            season = season.coerceAtLeast(1),
            episode = number.coerceAtLeast(1),
            title = card.name,
            poster = card.poster ?: card.backdrop,
        ),
    )
}

internal fun AppViewModel.playTitle(library: Library, card: TitleCard, episode: EpisodeRef, episodes: List<EpisodeRef>) {
    shelf.rememberEpisodes(library, card.id, episode.season, episodes)
    if (library == Library.KISSKH) {
        val episodeId = episode.id.toIntOrNull() ?: return
        val dramaId = card.id.toIntOrNull() ?: return
        val label = if (card.kind == MediaKind.MOVIE) {
            if (episodes.size > 1) "Part ${episodes.indexOf(episode) + 1}" else "Movie"
        } else {
            episode.name
        }
        play(Dest.Player(episodeId, dramaId, card.name, label, episode.number.toFloat(), card.poster))
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
