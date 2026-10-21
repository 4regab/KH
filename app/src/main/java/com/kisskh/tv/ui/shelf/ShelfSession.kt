package com.kisskh.tv.ui.shelf

import com.kisskh.tv.api.Repository
import com.kisskh.tv.api.media.FebboxClient
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.api.media.MediaProgress
import com.kisskh.tv.api.media.TmdbClient
import com.kisskh.tv.api.source.EpisodeRef
import com.kisskh.tv.api.source.PlayPlan
import com.kisskh.tv.api.source.ShelfRow
import com.kisskh.tv.api.source.SourceCatalog
import com.kisskh.tv.api.source.TitleCard
import com.kisskh.tv.api.source.TitleDetail
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.UiState
import com.kisskh.tv.ui.errorMessage
import com.kisskh.tv.ui.uiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BrowseState(
    val items: List<TitleCard> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = true,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
)

/**
 * Loads every source through [SourceCatalog] into the shared shelves.
 * KissKH resume keys are not written here.
 */
class ShelfSession(
    private val scope: CoroutineScope,
    private val repo: Repository,
    val progress: MediaProgress,
    private val cinejoyToken: () -> String,
    private val tmdb: TmdbClient = TmdbClient(),
    private val febbox: FebboxClient = FebboxClient(),
) {
    private val homes = HashMap<Library, MutableStateFlow<UiState<List<ShelfRow>>>>()
    private val homeJobs = HashMap<Library, Job>()
    private val browses = HashMap<String, MutableStateFlow<BrowseState>>()
    private val browseJobs = HashMap<String, Job>()
    private val details = HashMap<String, MutableStateFlow<UiState<TitleDetail>>>()
    private val seasons = HashMap<String, MutableStateFlow<UiState<List<EpisodeRef>>>>()
    private val episodeLists = HashMap<String, List<EpisodeRef>>()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _allSources = MutableStateFlow(false)
    val allSources: StateFlow<Boolean> = _allSources.asStateFlow()
    private val _results = MutableStateFlow<UiState<List<TitleCard>>>(UiState.Empty("Type a title."))
    val results: StateFlow<UiState<List<TitleCard>>> = _results.asStateFlow()
    private var searchJob: Job? = null
    private var searchLibrary: Library = Library.KISSKH

    fun home(library: Library): StateFlow<UiState<List<ShelfRow>>> =
        homes.getOrPut(library) { MutableStateFlow<UiState<List<ShelfRow>>>(UiState.Loading).also { loadHome(library, it) } }

    fun retryHome(library: Library) {
        val flow = homes.getOrPut(library) { MutableStateFlow(UiState.Loading) }
        loadHome(library, flow)
    }

    fun refreshContinue(library: Library) {
        val flow = homes[library] ?: return
        val success = flow.value as? UiState.Success ?: return
        val remote = success.data.filter { it.title != "Continue" }
        val local = continueRow(library)
        flow.value = UiState.Success(listOfNotNull(local) + remote)
    }

    private fun loadHome(library: Library, flow: MutableStateFlow<UiState<List<ShelfRow>>>) {
        homeJobs[library]?.cancel()
        flow.value = UiState.Loading
        homeJobs[library] = scope.launch {
            val result = io { SourceCatalog.home(library, repo, tmdb) }
            flow.value = result.fold(
                onSuccess = { rows ->
                    val merged = if (library == Library.KISSKH) {
                        rows
                    } else {
                        listOfNotNull(continueRow(library)) + rows.filter { it.title != "Continue" }
                    }
                    if (merged.isEmpty()) UiState.Error("Nothing to show right now.") else UiState.Success(merged)
                },
                onFailure = { uiError(it) },
            )
        }
    }

    private fun continueRow(library: Library): ShelfRow? {
        if (library == Library.KISSKH) return null
        val items = progress.entries().filter { it.source == library.key }.take(20).map { entry ->
            val kind = if (entry.kind == "movie") MediaKind.MOVIE else MediaKind.TV
            TitleCard(
                source = library,
                id = entry.token,
                kind = if (library == Library.ANISUGE || library == Library.NEXUS) MediaKind.ANIME else kind,
                name = entry.title,
                poster = entry.poster,
                backdrop = entry.poster,
                year = null,
                overview = null,
                resumeEpisode = entry.episode.coerceAtLeast(1).toString(),
                resumeSeason = entry.season.coerceAtLeast(1),
                resumeNumber = entry.episode.coerceAtLeast(1),
            )
        }
        return items.takeIf { it.isNotEmpty() }?.let { ShelfRow("Continue", it) }
    }

    fun browse(library: Library, kind: MediaKind): StateFlow<BrowseState> {
        val key = "${library.key}:${kind.name}"
        return browses.getOrPut(key) { MutableStateFlow(BrowseState()).also { loadBrowse(library, kind, it, 1) } }
    }

    fun retryBrowse(library: Library, kind: MediaKind) {
        val key = "${library.key}:${kind.name}"
        val flow = browses.getOrPut(key) { MutableStateFlow(BrowseState()) }
        loadBrowse(library, kind, flow, 1)
    }

    fun loadMore(library: Library, kind: MediaKind) {
        val key = "${library.key}:${kind.name}"
        val flow = browses[key] ?: return
        val cur = flow.value
        if (cur.loading || cur.loadingMore || !cur.hasMore || cur.items.isEmpty()) return
        flow.value = cur.copy(loadingMore = true, error = null)
        browseJobs[key]?.cancel()
        browseJobs[key] = scope.launch {
            val next = cur.page + 1
            val result = io { SourceCatalog.browse(library, kind, next, repo, tmdb) }
            val now = flow.value
            flow.value = result.fold(
                onSuccess = { page ->
                    now.copy(items = now.items + page.items, page = page.page, hasMore = page.hasMore, loadingMore = false, error = null)
                },
                onFailure = { now.copy(loadingMore = false, error = errorMessage(it)) },
            )
        }
    }

    private fun loadBrowse(library: Library, kind: MediaKind, flow: MutableStateFlow<BrowseState>, page: Int) {
        val key = "${library.key}:${kind.name}"
        browseJobs[key]?.cancel()
        flow.value = BrowseState()
        browseJobs[key] = scope.launch {
            val result = io { SourceCatalog.browse(library, kind, page, repo, tmdb) }
            flow.value = result.fold(
                onSuccess = { loaded ->
                    BrowseState(items = loaded.items, page = loaded.page.coerceAtLeast(1), hasMore = loaded.hasMore, loading = false)
                },
                onFailure = { BrowseState(loading = false, hasMore = false, error = errorMessage(it)) },
            )
        }
    }

    fun detail(library: Library, id: String, kind: MediaKind, slug: String, name: String, poster: String?): StateFlow<UiState<TitleDetail>> {
        val key = "${library.key}:$id:$kind"
        return details.getOrPut(key) {
            MutableStateFlow<UiState<TitleDetail>>(UiState.Loading).also { loadDetail(library, id, kind, slug, name, poster, it) }
        }
    }

    fun retryDetail(library: Library, id: String, kind: MediaKind, slug: String, name: String, poster: String?) {
        val key = "${library.key}:$id:$kind"
        val flow = details.getOrPut(key) { MutableStateFlow(UiState.Loading) }
        loadDetail(library, id, kind, slug, name, poster, flow)
    }

    private fun loadDetail(
        library: Library,
        id: String,
        kind: MediaKind,
        slug: String,
        name: String,
        poster: String?,
        flow: MutableStateFlow<UiState<TitleDetail>>,
    ) {
        flow.value = UiState.Loading
        scope.launch {
            flow.value = io { SourceCatalog.detail(library, id, kind, slug, name, poster, repo, tmdb) }
                .fold(onSuccess = { UiState.Success(it) }, onFailure = { uiError(it) })
        }
    }

    fun season(library: Library, id: String, number: Int): StateFlow<UiState<List<EpisodeRef>>> {
        val key = "${library.key}:$id:$number"
        return seasons.getOrPut(key) {
            MutableStateFlow<UiState<List<EpisodeRef>>>(UiState.Loading).also { loadSeason(library, id, number, key, it) }
        }
    }

    fun retrySeason(library: Library, id: String, number: Int) {
        val key = "${library.key}:$id:$number"
        val flow = seasons.getOrPut(key) { MutableStateFlow(UiState.Loading) }
        loadSeason(library, id, number, key, flow)
    }

    private fun loadSeason(library: Library, id: String, number: Int, key: String, flow: MutableStateFlow<UiState<List<EpisodeRef>>>) {
        flow.value = UiState.Loading
        scope.launch {
            val result = io { SourceCatalog.episodes(library, id, number, tmdb) }
            flow.value = result.fold(
                onSuccess = { eps ->
                    episodeLists[key] = eps
                    if (eps.isEmpty()) UiState.Empty("No episodes in this season.") else UiState.Success(eps)
                },
                onFailure = { uiError(it) },
            )
        }
    }

    fun episodesOf(library: Library, id: String, number: Int): List<EpisodeRef> =
        episodeLists["${library.key}:$id:$number"].orEmpty()

    fun rememberEpisodes(library: Library, id: String, season: Int, episodes: List<EpisodeRef>) {
        episodeLists["${library.key}:$id:$season"] = episodes
    }

    fun setQuery(library: Library, value: String, all: Boolean? = null) {
        searchLibrary = library
        _query.value = value
        if (all != null) _allSources.value = all
        searchJob?.cancel()
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            _results.value = UiState.Empty("Type a title.")
            return
        }
        val everywhere = _allSources.value
        searchJob = scope.launch {
            delay(400)
            if (_query.value.trim() != trimmed) return@launch
            _results.value = UiState.Loading
            _results.value = io {
                if (!everywhere) {
                    SourceCatalog.search(library, trimmed, repo, tmdb)
                } else {
                    val found = ArrayList<TitleCard>()
                    for (source in Library.entries) {
                        val part = try {
                            SourceCatalog.search(source, trimmed, repo, tmdb)
                        } catch (_: Exception) {
                            emptyList()
                        }
                        found += part.take(12).map { card -> card.copy(meta = source.label) }
                    }
                    found
                }
            }.fold(
                onSuccess = { if (it.isEmpty()) UiState.Empty("No titles match.") else UiState.Success(it) },
                onFailure = { uiError(it) },
            )
        }
    }

    data class Resolved(val card: TitleCard, val episode: EpisodeRef, val previous: EpisodeRef?, val next: EpisodeRef?)

    /** Fills in the episode the player needs when Continue only stored a number. */
    suspend fun resolve(library: Library, dest: Dest.Watch): Resolved = withContext(Dispatchers.IO) {
        val card = TitleCard(
            source = library,
            id = dest.titleId,
            kind = dest.kind,
            name = dest.title.ifBlank { library.label },
            poster = dest.poster,
            backdrop = dest.poster,
            year = null,
            overview = null,
            slug = dest.slug,
        )
        if (dest.kind == MediaKind.MOVIE) {
            val episode = EpisodeRef(dest.titleId, 0, 1, "Movie", dest.poster)
            return@withContext Resolved(card, episode, null, null)
        }
        if (dest.episodeId.isNotBlank() || dest.episodeToken.isNotBlank()) {
            val episode = EpisodeRef(
                id = dest.episodeId.ifBlank { dest.episode.toString() },
                season = dest.season.coerceAtLeast(1),
                number = dest.episode.coerceAtLeast(1),
                name = dest.episodeName,
                still = dest.poster,
                slug = dest.episodeSlug,
                token = dest.episodeToken,
            )
            val known = episodeLists["${library.key}:${dest.titleId}:${episode.season}"].orEmpty()
            val index = known.indexOfFirst { it.id == episode.id || it.number == episode.number }
            return@withContext Resolved(card, episode, known.getOrNull(index - 1), known.getOrNull(index + 1))
        }
        val detail = SourceCatalog.detail(library, dest.titleId, dest.kind, dest.slug, dest.title, dest.poster, repo, tmdb)
        val season = detail.seasons.firstOrNull { it.number == dest.season } ?: detail.seasons.firstOrNull()
        var episodes = season?.episodes.orEmpty()
        if (episodes.isEmpty() && season != null && season.number > 0) {
            episodes = SourceCatalog.episodes(library, dest.titleId, season.number, tmdb)
        }
        if (season != null) episodeLists["${library.key}:${dest.titleId}:${season.number}"] = episodes
        val episode = episodes.firstOrNull { it.number == dest.episode } ?: episodes.firstOrNull()
            ?: EpisodeRef(dest.titleId, dest.season.coerceAtLeast(1), dest.episode.coerceAtLeast(1), dest.episodeName, dest.poster)
        val index = episodes.indexOfFirst { it.id == episode.id }
        Resolved(card.copy(name = detail.card.name, poster = detail.card.poster ?: card.poster, slug = detail.card.slug.ifBlank { card.slug }), episode, episodes.getOrNull(index - 1), episodes.getOrNull(index + 1))
    }

    suspend fun plan(library: Library, card: TitleCard, episode: EpisodeRef, audio: String): PlayPlan =
        withContext(Dispatchers.IO) {
            SourceCatalog.play(library, card, episode, audio, cinejoyToken(), febbox)
        }

    private suspend fun <T> io(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            Result.failure(error)
        }
    }
}
