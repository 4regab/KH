package com.kisskh.tv.download

import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKeys
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.source.EpisodeRef
import com.kisskh.tv.api.source.TitleCard
import com.kisskh.tv.ui.Dest

/*
 * The plain rules behind the title page's Download control. Nothing here touches Android, so it
 * runs in a JVM test.
 */

/** What the Download control shows. */
enum class DownloadStage { START, FINDING, QUEUED, DOWNLOADING, WAITING, PAUSED, RETRY, DONE }

/**
 * One look of the control. [percent] is 0 to 100, or -1 when the size is not known yet.
 */
data class DownloadUi(val stage: DownloadStage, val percent: Int = -1) {
    /** The words on the wide button (movie header). */
    val label: String
        get() = when (stage) {
            DownloadStage.START -> "Download"
            DownloadStage.FINDING -> "Finding stream"
            DownloadStage.QUEUED -> "Queued"
            DownloadStage.DOWNLOADING -> if (percent >= 0) "Downloading $percent%" else "Downloading"
            DownloadStage.WAITING -> "Waiting for network"
            DownloadStage.PAUSED -> "Paused"
            DownloadStage.RETRY -> "Retry download"
            DownloadStage.DONE -> "Downloaded"
        }

    /** What a screen reader says for [name] (an episode or a movie title). */
    fun description(name: String): String = when (stage) {
        DownloadStage.START -> "Download $name"
        DownloadStage.FINDING -> "Finding the stream for $name. Tap to stop."
        DownloadStage.QUEUED -> "Download of $name is queued. Tap for options."
        DownloadStage.DOWNLOADING -> "Downloading $name" + (if (percent >= 0) ", $percent percent" else "") + ". Tap for options."
        DownloadStage.WAITING -> "Download of $name is waiting for network. Tap for options."
        DownloadStage.PAUSED -> "Download of $name is paused. Tap for options."
        DownloadStage.RETRY -> "Download of $name failed. Tap to retry."
        DownloadStage.DONE -> "$name is downloaded. Tap for options."
    }
}

/**
 * Picks the look from the saved download ([item], null when there is none), whether the stream is
 * being found ([finding]) and whether the download was just handed to the service ([preparing]).
 * A failed download that is being started again counts as finding.
 */
fun downloadUi(item: DownloadItem?, finding: Boolean, preparing: Boolean): DownloadUi {
    val busy = finding || preparing
    if (item == null) return if (busy) DownloadUi(DownloadStage.FINDING) else DownloadUi(DownloadStage.START)
    val percent = if (item.percent < 0f) -1 else item.percent.toInt().coerceIn(0, 100)
    return when (item.phase) {
        DownloadPhase.COMPLETED -> DownloadUi(DownloadStage.DONE, 100)
        DownloadPhase.DOWNLOADING -> DownloadUi(DownloadStage.DOWNLOADING, percent)
        DownloadPhase.QUEUED -> DownloadUi(DownloadStage.QUEUED, percent)
        DownloadPhase.WAITING_NETWORK -> DownloadUi(DownloadStage.WAITING, percent)
        DownloadPhase.PAUSED -> DownloadUi(DownloadStage.PAUSED, percent)
        DownloadPhase.FAILED -> if (busy) DownloadUi(DownloadStage.FINDING) else DownloadUi(DownloadStage.RETRY)
    }
}

/**
 * The player destination for one episode or movie of [card]. The player (`ShelfScreens.playTitle`)
 * builds the same one, so the progress key, and with it the download id, never differs.
 */
internal fun watchDest(library: Library, card: TitleCard, episode: EpisodeRef): Dest.Watch = Dest.Watch(
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
)

/**
 * The id of the download for [episode], the same one the old in-player button used (see [DownloadIds]),
 * or null when this episode can't be downloaded at all.
 */
fun downloadIdFor(library: Library, card: TitleCard, episode: EpisodeRef): String? {
    if (library == Library.KISSKH) {
        if (card.id.toIntOrNull() == null) return null
        return episode.id.toIntOrNull()?.let(DownloadIds::kisskh)
    }
    return watchDest(library, card, episode).progressKey.takeIf(MediaKeys::valid)?.let(DownloadIds::media)
}

/** KissKH's line under the title, as the player shows it: "Movie", "Part 2" or the episode name. */
internal fun kisskhLabel(card: TitleCard, episode: EpisodeRef, episodes: List<EpisodeRef>): String =
    if (card.kind == MediaKind.MOVIE) {
        if (episodes.size > 1) "Part ${episodes.indexOf(episode) + 1}" else "Movie"
    } else {
        episode.name
    }

/**
 * The picture for the Downloads list, which shows it wide (16:9). A wide picture first: the
 * episode still for an episode, the backdrop for a movie. The portrait poster is the last resort.
 */
internal fun previewFor(card: TitleCard, episode: EpisodeRef): String? {
    val wide = if (card.kind == MediaKind.MOVIE) listOf(card.backdrop, episode.still) else listOf(episode.still, card.backdrop)
    return (wide + card.poster).firstOrNull { !it.isNullOrBlank() }
}
