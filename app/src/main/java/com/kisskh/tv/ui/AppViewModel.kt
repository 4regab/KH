package com.kisskh.tv.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kisskh.tv.api.CatalogPager
import com.kisskh.tv.api.CatalogQuery
import com.kisskh.tv.api.ContentType
import com.kisskh.tv.api.Country
import com.kisskh.tv.api.Drama
import com.kisskh.tv.api.Episode
import com.kisskh.tv.api.Repository
import com.kisskh.tv.api.Show
import com.kisskh.tv.api.SortOrder
import com.kisskh.tv.api.SubtitleFile
import com.kisskh.tv.api.availableTabs
import com.kisskh.tv.api.suspendCatching
import com.kisskh.tv.api.media.DEFAULT_LIBRARY
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.ui.home.HomeRails
import com.kisskh.tv.ui.media.MediaSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One titled row on the Home screen. */
data class HomeRow(val title: String, val items: List<Show>)

/**
 * Home content. [failedRows] names rows that could not load, so the screen can offer Retry.
 * [featured] is the title for the big area at the top: the first title of the site's own featured
 * list (DramaList/Show), or the first of Latest when that list fails. Null if there is neither.
 */
data class HomeContent(val rows: List<HomeRow>, val failedRows: List<String>, val featured: Show? = null)

data class PlayerData(val url: String, val subs: List<SubtitleFile>, val resumeMs: Long)

/** An episode that was checked and has a stream, with the stream address it had at that moment. */
data class Playable(val episode: Episode, val url: String)

/** How many of a title's lowest-numbered episodes [AppViewModel.findPlayable] checks. */
private const val START_CHECKS = 3

/** Titles per filtered Home rail. */
private const val HOME_FILTERED_ROW_SIZE = 20

/**
 * App-wide state. Everything here is main-thread only.
 *
 * - Navigation: [nav], [push], [selectTab], [onBack], [saveFocus], [setCatalogQuery].
 * - Catalog lists: [pager], [countryTabs].
 * - Loading: [home], [detail], [search], [playerState] (all end in Success, Empty or Error, never stuck).
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repository(app)
    val media = MediaSession(app, viewModelScope)

    // ---------------------------------------------------------------- navigation

    // The app opens on CineWave. KissKH and CineJoy are one press away on the source switch.
    private val _nav = MutableStateFlow(NavState(library = DEFAULT_LIBRARY))

    /** The back stack. Draw `nav.current`; highlight `nav.activeTab` on the rail. */
    val nav: StateFlow<NavState> = _nav.asStateFlow()

    /** Open [dest] on top of the current screen. */
    fun push(dest: Dest) {
        _nav.update { it.push(dest) }
    }

    /** Rail selection. See [NavState.selectTab]. */
    fun selectTab(tab: RootTab) {
        _nav.update { it.selectTab(tab) }
        prunePagers()
    }

    /** KissKH, CineWave, or CineJoy. See [NavState.selectLibrary]. */
    fun selectLibrary(library: Library) {
        val playing = _nav.value.current
        _nav.update { it.selectLibrary(library) }
        stopIfPlaying(playing)
        prunePagers()
    }

    /** @return true if we handled back, false if the app should exit. */
    fun onBack(): Boolean {
        val before = _nav.value
        val after = before.pop() ?: return false
        _nav.value = after
        stopIfPlaying(before.current)
        prunePagers()
        return true
    }

    /**
     * Remember where focus and scroll were on the current screen. Call it right before leaving
     * (a click that opens another screen), not on every focus change.
     */
    fun saveFocus(focusKey: String?, scrollIndex: Int? = null) {
        _nav.update { state ->
            state.updateCurrent { (it as? Dest.Restorable)?.withFocus(focusKey, scrollIndex) ?: it }
        }
    }

    // ---------------------------------------------------------------- catalog lists

    private val pagers = HashMap<String, CatalogPager>()

    /**
     * The pager behind a Series, Movies or country screen. The first call for a destination starts
     * loading page 1. Later calls return the same pager, so coming back finds the pages already loaded.
     * If [dest] has a different query than the pager, the pager switches to it and reloads.
     * Safe to call again and again.
     */
    fun pager(dest: Dest.Catalog): CatalogPager {
        val existing = pagers[dest.pagerKey]
        if (existing != null) {
            existing.setQuery(dest.query)
            return existing
        }
        return CatalogPager(repo, viewModelScope, dest.query).also {
            pagers[dest.pagerKey] = it
            it.loadNext()
        }
    }

    /**
     * Change the filters of the current catalog screen (type and country are fixed by the screen).
     * Resets paging, drops answers still on the way for the old filters, and reloads.
     */
    fun setCatalogQuery(transform: (CatalogQuery) -> CatalogQuery) {
        val dest = _nav.value.current as? Dest.Catalog ?: return
        val next = dest.withQuery(transform(dest.query))
        if (next == dest) return
        _nav.update { it.updateCurrent { next } }
        pager(next)
    }

    private fun prunePagers() {
        val keep = _nav.value.catalogKeys()
        val it = pagers.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.key !in keep) {
                e.value.cancel()
                it.remove()
            }
        }
    }

    // ponytail: cached for the life of the app; a country's tab list is not refreshed until restart.
    private val tabFlows = HashMap<Country, MutableStateFlow<UiState<List<ContentType>>>>()

    /**
     * Tabs for a country page (All, TV Series, Movies) with empty ones left out.
     * Empty means the country has no titles at all.
     */
    fun countryTabs(country: Country): StateFlow<UiState<List<ContentType>>> =
        tabFlows.getOrPut(country) {
            MutableStateFlow<UiState<List<ContentType>>>(UiState.Loading).also { loadTabs(country, it) }
        }

    fun retryCountryTabs(country: Country) {
        val flow = tabFlows[country] ?: return
        if (flow.value is UiState.Error) loadTabs(country, flow)
    }

    private fun loadTabs(country: Country, flow: MutableStateFlow<UiState<List<ContentType>>>) {
        flow.value = UiState.Loading
        viewModelScope.launch {
            flow.value = suspendCatching { repo.availableTabs(country) }.fold(
                onSuccess = { if (it.isEmpty()) UiState.Empty() else UiState.Success(it) },
                onFailure = { uiError(it) },
            )
        }
    }

    // ---------------------------------------------------------------- home

    private val _home = MutableStateFlow<UiState<HomeContent>>(UiState.Loading)
    val home: StateFlow<UiState<HomeContent>> = _home.asStateFlow()
    private var homeJob: Job? = null

    init {
        loadHome()
    }

    /** Reload Home (after an Error, or to retry rows that failed). */
    fun retryHome() = loadHome()

    private fun loadHome() {
        homeJob?.cancel()
        _home.value = UiState.Loading
        homeJob = viewModelScope.launch {
            // Global feeds accept no type or country filter. Korean Series, Chinese Series and Movies
            // come from DramaList/List, whose filters are verified (see Catalog.kt). Order is HomeRails.feeds.
            val specs = HomeRails.feeds.map { title ->
                RowSpec(title) {
                    when (title) {
                        HomeRails.LATEST -> repo.api.lastUpdate()
                        HomeRails.POPULAR -> repo.api.mostView()
                        HomeRails.TOP_RATING -> repo.api.topRating()
                        HomeRails.ANIME -> repo.api.animate()
                        HomeRails.UPCOMING -> repo.api.upcoming()
                        HomeRails.KOREAN_SERIES -> repo.list(
                            CatalogQuery(ContentType.SERIES, Country.SOUTH_KOREA, order = SortOrder.POPULAR),
                            1,
                            HOME_FILTERED_ROW_SIZE,
                        ).data
                        HomeRails.CHINESE_SERIES -> repo.list(
                            CatalogQuery(ContentType.SERIES, Country.CHINA, order = SortOrder.POPULAR),
                            1,
                            HOME_FILTERED_ROW_SIZE,
                        ).data
                        HomeRails.MOVIES -> repo.list(
                            CatalogQuery(ContentType.MOVIE, order = SortOrder.POPULAR),
                            1,
                            HOME_FILTERED_ROW_SIZE,
                        ).data
                        else -> emptyList()
                    }
                }
            }
            val (outcomes, featuredList) = coroutineScope {
                val rowJobs = specs.map { spec -> async { suspendCatching { spec.load() } } }
                val featuredJob = async { suspendCatching { repo.api.show() } }
                rowJobs.awaitAll() to featuredJob.await()
            }
            val rows = ArrayList<HomeRow>()
            val failed = ArrayList<String>()
            var lastError: Throwable? = null
            specs.forEachIndexed { i, spec ->
                outcomes[i]
                    .onSuccess { shows ->
                        // distinctBy: show ids are lazy-list keys, and duplicates would crash those.
                        if (shows.isNotEmpty()) rows.add(HomeRow(spec.title, shows.distinctBy { it.id }))
                    }
                    .onFailure {
                        Log.w("KissKH", "row '${spec.title}' failed", it)
                        failed.add(spec.title)
                        lastError = it
                    }
            }
            featuredList.onFailure { Log.w("KissKH", "featured list failed", it) }
            // The same title every time: first of the site's featured list, else first of Latest.
            val featured = featuredList.getOrNull()?.firstOrNull()
                ?: rows.firstOrNull { it.title == "Latest" }?.items?.firstOrNull()
            _home.value = when {
                rows.isNotEmpty() -> UiState.Success(HomeContent(rows, failed, featured))
                lastError != null -> uiError(lastError!!)
                else -> UiState.Empty()
            }
        }
    }

    private class RowSpec(val title: String, val load: suspend () -> List<Show>)

    // ---------------------------------------------------------------- detail

    // ponytail: kept for the life of the app so Back shows a title at once. One small entry per title opened.
    private val details = HashMap<Int, MutableStateFlow<UiState<Drama>>>()

    /** Title detail. Starts loading on first use. Error has a Retry via [retryDetail]. */
    fun detail(dramaId: Int): StateFlow<UiState<Drama>> =
        details.getOrPut(dramaId) {
            MutableStateFlow<UiState<Drama>>(UiState.Loading).also { loadDetail(dramaId, it) }
        }

    fun retryDetail(dramaId: Int) {
        val flow = details[dramaId] ?: return
        if (flow.value is UiState.Error) loadDetail(dramaId, flow)
    }

    // ponytail: kept for the life of the app, like details. Re-checked on its own when a failed detail is retried.
    private val playables = HashMap<Int, MutableStateFlow<UiState<Playable>>>()

    /**
     * The first episode of this title that can be played now (see [findPlayable]), for the Home
     * "Watch" button. Loading until the detail is in. Empty when nothing is released yet. Error when
     * the detail or the check failed.
     */
    fun playable(dramaId: Int): StateFlow<UiState<Playable>> =
        playables.getOrPut(dramaId) {
            MutableStateFlow<UiState<Playable>>(UiState.Loading).also { flow ->
                viewModelScope.launch {
                    detail(dramaId).collectLatest { d ->
                        flow.value = when (d) {
                            UiState.Loading -> UiState.Loading
                            is UiState.Error -> d
                            is UiState.Empty -> UiState.Empty(d.message)
                            is UiState.Success -> {
                                flow.value = UiState.Loading
                                findPlayable(d.data).fold(
                                    onSuccess = { p -> if (p != null) UiState.Success(p) else UiState.Empty("Not released yet.") },
                                    onFailure = { uiError(it) },
                                )
                            }
                        }
                    }
                }
            }
        }

    /**
     * Looks for an episode to start with: the lowest-numbered one that has a stream. Episodes come
     * newest first, so they are sorted here. Checks at most [START_CHECKS] of them, because a title
     * that is not out yet has nothing playable anyway. Null means none of them is released.
     * Fails if a check fails (for example no network).
     */
    suspend fun findPlayable(drama: Drama): Result<Playable?> = suspendCatching {
        var found: Playable? = null
        for (ep in drama.episodes.sortedBy { it.number }.take(START_CHECKS)) {
            val url = repo.streamUrl(ep.id)
            if (url != null) {
                found = Playable(ep, url)
                break
            }
        }
        found
    }

    private fun loadDetail(dramaId: Int, flow: MutableStateFlow<UiState<Drama>>) {
        flow.value = UiState.Loading
        viewModelScope.launch {
            flow.value = repo.dramaResult(dramaId).fold(
                onSuccess = { UiState.Success(it) },
                onFailure = {
                    Log.w("KissKH", "detail $dramaId failed", it)
                    uiError(it)
                },
            )
        }
    }

    // ---------------------------------------------------------------- search

    /** Search-as-you-type. Lives as long as the app, so the query and results survive navigation. */
    val search = SearchController(viewModelScope) { repo.api.search(it.trim()) }

    // ---------------------------------------------------------------- playback

    private val _player = MutableStateFlow<UiState<PlayerData>>(UiState.Loading)

    /**
     * Stream for the current Player screen. Loading while the link is found, Success with the
     * stream, Empty when the episode is not released yet, Error (with [retryPlayer]) otherwise.
     */
    val playerState: StateFlow<UiState<PlayerData>> = _player.asStateFlow()

    private var playerJob: Job? = null

    // Every episode opened this session, so the final save from a player that is being replaced
    // (another episode picked, Home pressed) still knows its title.
    private val played = HashMap<Int, Dest.Player>()

    /**
     * Open the player for [player] and find its stream. Pass [streamUrl] when the stream was just
     * looked up (the Detail screen checks before it opens the player), to skip a second lookup.
     */
    fun play(player: Dest.Player, streamUrl: String? = null) {
        played[player.episodeId] = player
        push(player)
        loadStream(player, streamUrl)
    }

    /** From inside the player: play [player] in place of the current episode, so Back still goes to the title page. */
    fun switchEpisode(player: Dest.Player) {
        if (_nav.value.current !is Dest.Player) return
        played[player.episodeId] = player
        _nav.update { it.updateCurrent { player } }
        loadStream(player)
    }

    /** From inside the player: stop and go to Home. */
    fun goHome() {
        val playing = _nav.value.current
        _nav.update { it.selectTab(RootTab.HOME) }
        stopIfPlaying(playing)
        prunePagers()
    }

    fun switchStream(dest: Dest.StreamPlayer) {
        _nav.update { state ->
            if (state.current is Dest.StreamPlayer) state.updateCurrent { dest } else state.push(dest)
        }
    }

    fun switchEmbed(dest: Dest.EmbedPlayer) {
        _nav.update { state ->
            if (state.current is Dest.EmbedPlayer) state.updateCurrent { dest } else state.push(dest)
        }
    }

    fun setMediaSort(latest: Boolean) {
        _nav.update { state ->
            state.updateCurrent { current ->
                if (current is Dest.MediaBrowse) current.withSort(latest) else current
            }
        }
    }

    private fun stopIfPlaying(dest: Dest) {
        if (dest is Dest.Player) stopPlayback()
        if (dest is Dest.StreamPlayer) media.cancelStream()
    }

    fun retryPlayer() {
        val p = _nav.value.current as? Dest.Player ?: return
        loadStream(p)
    }

    private fun loadStream(p: Dest.Player, known: String? = null) {
        playerJob?.cancel()
        _player.value = UiState.Loading
        playerJob = viewModelScope.launch {
            val url = known ?: suspendCatching { repo.streamUrl(p.episodeId) }.getOrElse {
                Log.w("KissKH", "stream ${p.episodeId} failed", it)
                _player.value = uiError(it)
                return@launch
            }
            if (url == null) {
                _player.value = UiState.Empty("This episode isn't released yet.")
                return@launch
            }
            val subs = suspendCatching { repo.subtitles(p.episodeId) }.getOrDefault(emptyList())
            _player.value = UiState.Success(PlayerData(url, subs, repo.loadPosition(p.episodeId)))
        }
    }

    private fun stopPlayback() {
        playerJob?.cancel()
        _player.value = UiState.Loading
    }

    /**
     * Save playback progress. Safe to call often, and after Back. Updates the episode position and,
     * for the episode opened with [play], the title's watch history.
     */
    fun savePosition(episodeId: Int, positionMs: Long, durationMs: Long) {
        val p = played[episodeId]
        if (p != null) {
            repo.history.record(p.dramaId, episodeId, p.episodeNumber, p.title, p.thumbnail, positionMs, durationMs)
        } else {
            repo.savePosition(episodeId, positionMs, durationMs)
        }
    }
}
