package com.kisskh.tv.download

import com.kisskh.tv.api.SubtitleFile

/**
 * What a player hands over to make its video downloadable: the stream, how to reach it, and what to
 * show in the Downloads list. The player shows a Download button only when it has one of these.
 */
data class DownloadOffer(
    /** Stable per movie or episode. See [DownloadIds]. */
    val id: String,
    val url: String,
    /** Sent with every request of the download. */
    val headers: Map<String, String>,
    /** Subtitle files, `http(s)` or `file://`. They are copied into app storage when the download starts. */
    val subs: List<SubtitleFile>,
    val title: String,
    /** For example "S1 E2 · Name". Blank for a movie. */
    val label: String,
    val poster: String?,
    /** The name of the source, for example "CineWave". */
    val source: String,
    val kind: String,
    /** Progress key for every source except KissKH. */
    val progressKey: String? = null,
    /** KissKH only. */
    val episodeId: Int = 0,
    val dramaId: Int = 0,
    val episodeNumber: Float = 0f,
) {
    fun toMeta(savedSubs: List<DownloadSub>, now: Long) = DownloadMeta(
        id = id,
        title = title,
        label = label.ifBlank { if (kind == KIND_MOVIE) "Movie" else "" },
        poster = poster,
        source = source,
        kind = kind,
        progressKey = progressKey,
        episodeId = episodeId,
        dramaId = dramaId,
        episodeNumber = episodeNumber,
        subs = savedSubs,
        headers = headers,
        createdAt = now,
    )
}
