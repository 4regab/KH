package com.kisskh.tv.download

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.MetadataRetriever
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.kisskh.tv.api.SubtitleFile
import com.kisskh.tv.api.media.AudioCheck
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.media.SniffedStream
import com.kisskh.tv.api.media.SubFetcher
import com.kisskh.tv.api.media.SubLists
import com.kisskh.tv.api.source.EpisodeRef
import com.kisskh.tv.api.source.PlayTarget
import com.kisskh.tv.api.source.TitleCard
import com.kisskh.tv.player.PlayerHeaders
import com.kisskh.tv.player.webUserAgent
import com.kisskh.tv.player.wantsEnglishAudio
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.playerOverlayLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TAG = "DownloadResolver"

/** How long the audio check may take before the stream is accepted without it. */
private const val PROBE_TIMEOUT_MS = 20_000L

internal const val WEBSITE_ONLY_MESSAGE = "This episode can only play in its website player, so it can't be downloaded."
private const val NOT_FOUND_MESSAGE = "Couldn't find a stream to download. Try again later."

/** The result of looking for a stream to download. */
internal sealed interface Resolution {
    data class Ready(val offer: DownloadOffer) : Resolution

    /** Nothing can be downloaded; [message] says why in one short line. */
    data class Unavailable(val message: String) : Resolution
}

/**
 * Finds the stream of one episode or movie without showing a player. KissKH asks its own API.
 * Every other source gets its server list; the first server with a ready HLS or MP4 address wins,
 * and if there is none, the page servers are loaded out of sight one by one ([sniff]) until one
 * asks for a stream.
 *
 * [sniff] loads one page target and returns the stream it asked for, or null when it failed or
 * took too long.
 */
internal class StreamResolver(
    private val vm: AppViewModel,
    private val sniff: suspend (target: PlayTarget, userAgent: String?) -> SniffedStream?,
) {
    suspend fun resolve(library: Library, card: TitleCard, episode: EpisodeRef, episodes: List<EpisodeRef>): Resolution {
        val id = downloadIdFor(library, card, episode) ?: return Resolution.Unavailable(WEBSITE_ONLY_MESSAGE)
        return if (library == Library.KISSKH) kisskh(id, card, episode, episodes) else other(id, library, card, episode)
    }

    private suspend fun kisskh(id: String, card: TitleCard, episode: EpisodeRef, episodes: List<EpisodeRef>): Resolution {
        val episodeId = episode.id.toIntOrNull() ?: return Resolution.Unavailable(WEBSITE_ONLY_MESSAGE)
        val url = vm.repo.streamUrl(episodeId) ?: return Resolution.Unavailable("This episode isn't released yet.")
        val subs = try {
            vm.repo.subtitles(episodeId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w(TAG, "no subtitles for $episodeId", failure)
            emptyList()
        }
        return Resolution.Ready(
            DownloadOffer(
                id = id,
                url = url,
                headers = PlayerHeaders.Kisskh,
                subs = subs,
                title = card.name.ifBlank { "KH" },
                label = playerOverlayLabel(kisskhLabel(card, episode, episodes)),
                poster = previewFor(card, episode),
                source = Library.KISSKH.label,
                kind = KIND_EPISODE,
                episodeId = episodeId,
                dramaId = card.id.toIntOrNull() ?: 0,
                episodeNumber = episode.number.toFloat(),
            ),
        )
    }

    private suspend fun other(id: String, library: Library, card: TitleCard, episode: EpisodeRef): Resolution {
        val dest = watchDest(library, card, episode)
        val resolved = vm.shelf.resolve(library, dest)
        val plan = vm.shelf.plan(library, resolved.card, resolved.episode, dest.audio)
        if (plan.targets.isEmpty()) return Resolution.Unavailable("No server has this episode.")

        fun offer(url: String, headers: Map<String, String>, subs: List<SubtitleFile>) = Resolution.Ready(
            DownloadOffer(
                id = id,
                url = url,
                headers = headers,
                subs = subs,
                title = plan.title.ifBlank { dest.title },
                label = plan.label.ifBlank { dest.label },
                poster = previewFor(card, episode) ?: plan.poster,
                source = library.label,
                kind = if (dest.kind == MediaKind.MOVIE) KIND_MOVIE else KIND_EPISODE,
                progressKey = dest.progressKey,
            ),
        )

        plan.targets.firstOrNull { it.direct != null }?.let { target ->
            val headers = target.headers.ifEmpty { mapOf("Referer" to target.referer) }
            return offer(target.direct.orEmpty(), headers, target.subs + extraSubs(target))
        }
        val pages = plan.targets.filter { it.page != null && it.sniff }
        if (pages.isEmpty()) return Resolution.Unavailable(WEBSITE_ONLY_MESSAGE)
        val wantEnglish = wantsEnglishAudio(dest)
        // The first stream with foreign audio only: used when no server has English, better than nothing.
        var fallback: Resolution.Ready? = null
        for (target in pages) {
            val stream = sniff(target, webUserAgent(library)) ?: continue
            val headers = stream.headers.ifEmpty { mapOf("Referer" to target.referer) }
            val ready = offer(stream.url, headers, target.subs + extraSubs(target))
            if (wantEnglish) {
                val languages = audioLanguages(stream.url, headers)
                if (languages != null && AudioCheck.foreignOnly(languages)) {
                    Log.i(TAG, "skipped ${target.name}: audio is ${languages.joinToString(",")}")
                    if (fallback == null) fallback = ready
                    continue
                }
            }
            return ready
        }
        return fallback ?: Resolution.Unavailable(NOT_FOUND_MESSAGE)
    }

    /**
     * The audio languages of a stream, read without playing it. Null when the stream can't be read
     * in time: the caller then accepts the stream, so a slow probe never blocks a download.
     */
    @androidx.annotation.OptIn(UnstableApi::class)
    private suspend fun audioLanguages(url: String, headers: Map<String, String>): List<String?>? {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(headers["User-Agent"] ?: DEFAULT_USER_AGENT)
            .setDefaultRequestProperties(headers - "User-Agent")
            .setAllowCrossProtocolRedirects(true)
        val future = try {
            MetadataRetriever.retrieveMetadata(DefaultMediaSourceFactory(http), MediaItem.fromUri(url))
        } catch (failure: Exception) {
            Log.w(TAG, "audio probe could not start", failure)
            return null
        }
        return withTimeoutOrNull(PROBE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation { future.cancel(true) }
                future.addListener(
                    {
                        val languages = try {
                            val groups = future.get()
                            (0 until groups.length).map { groups[it] }
                                .filter { it.type == C.TRACK_TYPE_AUDIO }
                                .flatMap { g -> (0 until g.length).map { g.getFormat(it).language } }
                        } catch (failure: Exception) {
                            Log.w(TAG, "audio probe failed", failure)
                            null
                        }
                        cont.resume(languages)
                    },
                    { it.run() },
                )
            }
        }
    }

    /** Subtitle lists some servers keep apart from the video (VidZee, VidRock). A failure is no subtitles. */
    private suspend fun extraSubs(target: PlayTarget): List<SubtitleFile> {
        val url = target.subsUrl ?: return emptyList()
        return withContext(Dispatchers.IO) { SubFetcher().load(url, target.referer) { SubLists.parseLabelFile(it) } }
    }
}
