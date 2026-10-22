package com.kisskh.tv.ui.media

import android.content.Context
import com.kisskh.tv.api.SubtitleFile
import com.kisskh.tv.api.media.CineJoyToken
import com.kisskh.tv.api.media.FebParse
import com.kisskh.tv.api.media.Febbox
import com.kisskh.tv.api.media.FebboxClient
import com.kisskh.tv.api.media.MediaProgress
import com.kisskh.tv.api.media.TmdbClient
import com.kisskh.tv.api.media.TmdbEpisode
import com.kisskh.tv.api.media.TokenRequired
import com.kisskh.tv.ui.UiState
import com.kisskh.tv.ui.uiError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StreamRequest(
    val key: String,
    val title: String,
    val poster: String?,
    val movie: Boolean,
    val id: Int,
    val season: Int,
    val episode: Int,
)

data class StreamReady(
    /** The [StreamRequest.key] this stream was resolved for, so a screen never plays another episode's stream. */
    val key: String,
    val url: String,
    val quality: String,
    val subs: List<SubtitleFile>,
    val resumeMs: Long,
)

/**
 * Watch progress, the CineJoy token, TMDB season lists and the CineJoy stream. KissKH does not go through here.
 */
class MediaSession(
    context: Context,
    private val scope: CoroutineScope,
    private val tmdb: TmdbClient = TmdbClient(),
    private val febbox: FebboxClient = FebboxClient(),
) {
    val progress = MediaProgress(context.getSharedPreferences(MediaProgress.PREFS, Context.MODE_PRIVATE))
    val tokens = CineJoyToken(context.getSharedPreferences(CineJoyToken.PREFS, Context.MODE_PRIVATE))

    private val _token = MutableStateFlow(tokens.get())
    val token: StateFlow<String> = _token.asStateFlow()

    fun saveToken(value: String) {
        tokens.set(value)
        _token.value = tokens.get()
    }

    // ---------------------------------------------------------------- seasons

    private val seasonFlows = HashMap<String, MutableStateFlow<UiState<List<TmdbEpisode>>>>()

    fun season(id: Int, number: Int): StateFlow<UiState<List<TmdbEpisode>>> {
        val key = "$id:$number"
        return seasonFlows.getOrPut(key) {
            MutableStateFlow<UiState<List<TmdbEpisode>>>(UiState.Loading).also { loadSeason(id, number, it) }
        }
    }

    private fun loadSeason(id: Int, number: Int, flow: MutableStateFlow<UiState<List<TmdbEpisode>>>) {
        flow.value = UiState.Loading
        scope.launch {
            flow.value = withContext(Dispatchers.IO) {
                runCatching { tmdb.season(id, number) }.fold(
                    onSuccess = { if (it.isEmpty()) UiState.Empty("No episodes in this season.") else UiState.Success(it) },
                    onFailure = { uiError(it) },
                )
            }
        }
    }

    // ---------------------------------------------------------------- cinejoy stream

    private val _stream = MutableStateFlow<UiState<StreamReady>>(UiState.Loading)
    val stream: StateFlow<UiState<StreamReady>> = _stream.asStateFlow()
    private var streamJob: Job? = null

    fun loadStream(request: StreamRequest) {
        streamJob?.cancel()
        _stream.value = UiState.Loading
        streamJob = scope.launch {
            _stream.value = withContext(Dispatchers.IO) {
                runCatching { resolve(request) }.fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { uiError(it) },
                )
            }
        }
    }

    fun cancelStream() {
        streamJob?.cancel()
        _stream.value = UiState.Loading
    }

    private fun resolve(request: StreamRequest): StreamReady {
        progress.remember(request.key, request.title, request.poster)
        val url = Febbox.requestUrl(request.movie, request.id, request.season, request.episode, tokens.get())
            ?: throw TokenRequired(
                "Febbox needs your own token: sign in at febbox.com, copy the value of its \"ui\" cookie, and paste it here.",
            )
        return when (val parsed = febbox.fetch(url)) {
            is FebParse.Ready -> StreamReady(
                key = request.key,
                url = parsed.url,
                quality = parsed.quality,
                subs = parsed.subtitles.map { SubtitleFile(it.url, it.label, it.lang) },
                resumeMs = progress.position(request.key),
            )
            is FebParse.Rejected -> {
                if ("token" in parsed.message.lowercase()) throw TokenRequired(parsed.message)
                throw IllegalStateException(parsed.message)
            }
            FebParse.Empty -> throw IllegalStateException("Febbox returned no video for this title.")
        }
    }

    fun saveStream(request: StreamRequest, positionMs: Long, durationMs: Long) {
        progress.save(request.key, request.title, request.poster, positionMs, durationMs)
    }
}
