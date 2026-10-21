package com.kisskh.tv.ui.media

import android.content.Context
import com.kisskh.tv.api.SubtitleFile
import com.kisskh.tv.api.media.CineJoyToken
import com.kisskh.tv.api.media.FebParse
import com.kisskh.tv.api.media.Febbox
import com.kisskh.tv.api.media.FebboxClient
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaEntry
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.media.MediaProgress
import com.kisskh.tv.api.media.TmdbClient
import com.kisskh.tv.api.media.TmdbEpisode
import com.kisskh.tv.api.media.TmdbPage
import com.kisskh.tv.api.media.TmdbPaths
import com.kisskh.tv.api.media.TmdbTitle
import com.kisskh.tv.api.media.TmdbTitleDetails
import com.kisskh.tv.api.media.TokenRequired
import com.kisskh.tv.ui.UiState
import com.kisskh.tv.ui.errorMessage
import com.kisskh.tv.ui.uiError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MediaRow(
    val title: String,
    val items: List<TmdbTitle>,
    /** Set for Continue. Same order as [items]. */
    val resumes: List<MediaEntry>? = null,
)

data class MediaHome(val rows: List<MediaRow>, val failed: List<String>)

data class MediaBrowseState(
    val items: List<TmdbTitle> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 1,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
)

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
    val url: String,
    val quality: String,
    val subs: List<SubtitleFile>,
    val resumeMs: Long,
)

/**
 * TMDB screens and the CineJoy stream. KissKH does not go through here.
 * Catalog calls are the ones CineWave and CineJoy make themselves.
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

    // ---------------------------------------------------------------- home

    private val homeFlows = HashMap<Library, MutableStateFlow<UiState<MediaHome>>>()
    private val homeJobs = HashMap<Library, Job>()

    fun home(library: Library): StateFlow<UiState<MediaHome>> =
        homeFlows.getOrPut(library) {
            MutableStateFlow<UiState<MediaHome>>(UiState.Loading).also { loadHome(library, it) }
        }

    fun retryHome(library: Library) {
        val flow = homeFlows.getOrPut(library) { MutableStateFlow(UiState.Loading) }
        loadHome(library, flow)
    }

    /** Rebuild Continue and Recent from disk without asking TMDB again. */
    fun refreshLocal(library: Library) {
        val flow = homeFlows[library] ?: return
        val success = flow.value as? UiState.Success ?: return
        val remote = success.data.rows.filter { it.resumes == null }
        flow.value = UiState.Success(success.data.copy(rows = localRows(library) + remote))
    }

    private fun loadHome(library: Library, flow: MutableStateFlow<UiState<MediaHome>>) {
        if (library == Library.KISSKH) return
        homeJobs[library]?.cancel()
        flow.value = UiState.Loading
        homeJobs[library] = scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                coroutineScope {
                    specs(library).map { spec ->
                        async { spec.title to runCatching { spec.load() } }
                    }.awaitAll()
                }
            }
            val rows = ArrayList<MediaRow>()
            val failed = ArrayList<String>()
            rows += localRows(library)
            for ((title, result) in loaded) {
                result.fold(
                    onSuccess = { items -> if (items.isNotEmpty()) rows += MediaRow(title, items) },
                    onFailure = { failed += title },
                )
            }
            flow.value = if (rows.isEmpty()) {
                UiState.Error(
                    if (failed.isEmpty()) "Nothing to show right now."
                    else "Couldn't load ${failed.joinToString(", ")}.",
                )
            } else {
                UiState.Success(MediaHome(rows, failed))
            }
        }
    }

    private fun localRows(library: Library): List<MediaRow> {
        val cont = progress.continuing(library.key).take(20)
        val recent = progress.recent(library.key).take(20)
        return listOfNotNull(
            cont.takeIf { it.isNotEmpty() }?.let { MediaRow("Continue", it.map { entry -> entry.asTitle() }, it) },
            recent.takeIf { it.isNotEmpty() }?.let { MediaRow("Recent", it.map { entry -> entry.asTitle() }) },
        )
    }

    private fun specs(library: Library): List<RowSpec> = when (library) {
        Library.KISSKH -> emptyList()
        Library.CINEWAVE -> listOf(
            RowSpec("Trending") { tmdb.trendingAll().items },
            RowSpec("Trending movies") { tmdb.trendingMovies().items },
            RowSpec("Trending TV") { tmdb.trendingTv().items },
            RowSpec("Anime") { tmdb.anime(1).items },
            RowSpec("Latest movies") { tmdb.discover("movie", true, 1, TmdbPaths.todayUtc()).items },
        )
        Library.CINEJOY -> listOf(
            RowSpec("Trending movies") { tmdb.trendingMovies().items },
            RowSpec("Trending TV") { tmdb.trendingTv().items },
            RowSpec("Popular movies") { tmdb.discover("movie", false, 1, TmdbPaths.todayUtc()).items },
            RowSpec("On the air") { tmdb.onTheAir().items },
        )
        Library.REELIX, Library.ANISUGE, Library.NEXUS -> emptyList()
    }

    // ---------------------------------------------------------------- browse

    private val browseFlows = HashMap<String, MutableStateFlow<MediaBrowseState>>()
    private val browseJobs = HashMap<String, Job>()

    fun browse(kind: MediaKind, latest: Boolean): StateFlow<MediaBrowseState> {
        val key = browseKey(kind, latest)
        return browseFlows.getOrPut(key) {
            MutableStateFlow(MediaBrowseState()).also { loadFirst(kind, latest, it) }
        }
    }

    fun retryBrowse(kind: MediaKind, latest: Boolean) {
        val key = browseKey(kind, latest)
        val flow = browseFlows.getOrPut(key) { MutableStateFlow(MediaBrowseState()) }
        loadFirst(kind, latest, flow)
    }

    fun loadMore(kind: MediaKind, latest: Boolean) {
        val key = browseKey(kind, latest)
        val flow = browseFlows[key] ?: return
        val cur = flow.value
        if (cur.loading || cur.loadingMore || cur.items.isEmpty() || cur.page >= cur.totalPages || cur.error != null) return
        flow.value = cur.copy(loadingMore = true)
        browseJobs[key]?.cancel()
        browseJobs[key] = scope.launch {
            val next = cur.page + 1
            val result = withContext(Dispatchers.IO) { runCatching { fetchBrowse(kind, latest, next) } }
            val now = flow.value
            flow.value = result.fold(
                onSuccess = { page ->
                    now.copy(
                        items = now.items + page.items,
                        page = page.page,
                        totalPages = page.totalPages,
                        loadingMore = false,
                        error = null,
                    )
                },
                onFailure = { now.copy(loadingMore = false, error = errorMessage(it)) },
            )
        }
    }

    fun retryMore(kind: MediaKind, latest: Boolean) {
        val flow = browseFlows[browseKey(kind, latest)] ?: return
        if (flow.value.error == null) return
        flow.value = flow.value.copy(error = null)
        loadMore(kind, latest)
    }

    private fun loadFirst(kind: MediaKind, latest: Boolean, flow: MutableStateFlow<MediaBrowseState>) {
        val key = browseKey(kind, latest)
        browseJobs[key]?.cancel()
        flow.value = MediaBrowseState()
        browseJobs[key] = scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { fetchBrowse(kind, latest, 1) } }
            flow.value = result.fold(
                onSuccess = { page ->
                    MediaBrowseState(
                        items = page.items,
                        page = page.page.coerceAtLeast(1),
                        totalPages = page.totalPages,
                        loading = false,
                    )
                },
                onFailure = { MediaBrowseState(loading = false, error = errorMessage(it)) },
            )
        }
    }

    private fun fetchBrowse(kind: MediaKind, latest: Boolean, page: Int): TmdbPage {
        val today = TmdbPaths.todayUtc()
        return when (kind) {
            MediaKind.ANIME -> tmdb.anime(page)
            MediaKind.MOVIE -> tmdb.discover("movie", latest, page, today)
            MediaKind.TV -> tmdb.discover("tv", latest, page, today)
        }
    }

    private fun browseKey(kind: MediaKind, latest: Boolean): String {
        val sort = if (kind == MediaKind.ANIME) "anime" else if (latest) "latest" else "popular"
        return "${kind.name}:$sort"
    }

    // ---------------------------------------------------------------- detail

    private val detailFlows = HashMap<String, MutableStateFlow<UiState<TmdbTitleDetails>>>()
    private val seasonFlows = HashMap<String, MutableStateFlow<UiState<List<TmdbEpisode>>>>()

    fun detail(kind: MediaKind, id: Int): StateFlow<UiState<TmdbTitleDetails>> {
        val key = "${kind.api()}:$id"
        return detailFlows.getOrPut(key) {
            MutableStateFlow<UiState<TmdbTitleDetails>>(UiState.Loading).also { loadDetail(kind, id, it) }
        }
    }

    fun retryDetail(kind: MediaKind, id: Int) {
        val key = "${kind.api()}:$id"
        val flow = detailFlows.getOrPut(key) { MutableStateFlow(UiState.Loading) }
        loadDetail(kind, id, flow)
    }

    private fun loadDetail(kind: MediaKind, id: Int, flow: MutableStateFlow<UiState<TmdbTitleDetails>>) {
        flow.value = UiState.Loading
        scope.launch {
            flow.value = withContext(Dispatchers.IO) {
                runCatching {
                    if (kind.isMovie) tmdb.movie(id) else tmdb.tv(id)
                }.fold(onSuccess = { UiState.Success(it) }, onFailure = { uiError(it) })
            }
        }
    }

    fun season(id: Int, number: Int): StateFlow<UiState<List<TmdbEpisode>>> {
        val key = "$id:$number"
        return seasonFlows.getOrPut(key) {
            MutableStateFlow<UiState<List<TmdbEpisode>>>(UiState.Loading).also { loadSeason(id, number, it) }
        }
    }

    fun retrySeason(id: Int, number: Int) {
        val key = "$id:$number"
        val flow = seasonFlows.getOrPut(key) { MutableStateFlow(UiState.Loading) }
        loadSeason(id, number, flow)
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

    // ---------------------------------------------------------------- search

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _results = MutableStateFlow<UiState<List<TmdbTitle>>>(UiState.Empty("Type a title."))
    val results: StateFlow<UiState<List<TmdbTitle>>> = _results.asStateFlow()
    private var searchJob: Job? = null

    fun setQuery(value: String, immediate: Boolean = false) {
        _query.value = value
        searchJob?.cancel()
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            _results.value = UiState.Empty("Type a title.")
            return
        }
        searchJob = scope.launch {
            if (!immediate) delay(400)
            if (_query.value.trim() != trimmed) return@launch
            _results.value = UiState.Loading
            _results.value = withContext(Dispatchers.IO) {
                runCatching { tmdb.search(trimmed) }.fold(
                    onSuccess = { if (it.isEmpty()) UiState.Empty("No movies or shows match.") else UiState.Success(it) },
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

private data class RowSpec(val title: String, val load: () -> List<TmdbTitle>)

private fun MediaEntry.asTitle(): TmdbTitle = TmdbTitle(
    id = id,
    mediaType = if (kind == "movie") "movie" else "tv",
    name = title,
    overview = "",
    poster = poster,
    backdrop = poster,
    vote = null,
    year = null,
)
