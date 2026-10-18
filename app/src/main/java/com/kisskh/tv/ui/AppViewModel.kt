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
import com.kisskh.tv.api.Repository
import com.kisskh.tv.api.Show
import com.kisskh.tv.api.SubtitleFile
import com.kisskh.tv.api.availableTabs
import com.kisskh.tv.api.suspendCatching
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One titled row on the Home screen. */
data class HomeRow(val title: String, val items: List<Show>)

/** Home content. [failedRows] names rows that could not load, so the screen can offer Retry. */
data class HomeContent(val rows: List<HomeRow>, val failedRows: List<String>)

data class PlayerData(val url: String, val subs: List<SubtitleFile>, val resumeMs: Long)

/**
 * App-wide state. Everything here is main-thread only.
 *
 * - Navigation: [nav], [push], [selectTab], [onBack], [saveFocus], [setCatalogQuery].
 * - Catalog lists: [pager], [countryTabs].
 * - Loading: [home], [detail], [search], [playerState] (all end in Success, Empty or Error, never stuck).
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repository(app)

    // ---------------------------------------------------------------- navigation

    private val _nav = MutableStateFlow(NavState())

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

    /** @return true if we handled back, false if the app should exit. */
    fun onBack(): Boolean {
        val before = _nav.value
        val after = before.pop() ?: return false
        _nav.value = after
        if (before.current is Dest.Player) stopPlayback()
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
            val rows = ArrayList<HomeRow>()
            val failed = ArrayList<String>()
            var lastError: Throwable? = null
            suspend fun row(title: String, load: suspend () -> List<Show>) {
                suspendCatching { load() }
                    .onSuccess { shows ->
                        // distinctBy: stage 2 uses show ids as lazy-list keys, and duplicates would crash those.
                        if (shows.isNotEmpty()) rows.add(HomeRow(title, shows.distinctBy { it.id }))
                    }
                    .onFailure {
                        Log.w("KissKH", "row '$title' failed", it)
                        failed.add(title)
                        lastError = it
                    }
            }
            row("Latest") { repo.api.lastUpdate() }
            row("Popular") { repo.api.mostView() }
            row("Top Rating") { repo.api.topRating() }
            row("Anime") { repo.api.animate() }
            row("Upcoming") { repo.api.upcoming() }
            _home.value = when {
                rows.isNotEmpty() -> UiState.Success(HomeContent(rows, failed))
                lastError != null -> uiError(lastError!!)
                else -> UiState.Empty()
            }
        }
    }

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

    // Kept after Back so the final progress save (from the player's dispose) still knows the title.
    private var nowPlaying: Dest.Player? = null

    /** Open the player for [player] and find its stream. */
    fun play(player: Dest.Player) {
        nowPlaying = player
        push(player)
        loadStream(player)
    }

    fun retryPlayer() {
        val p = _nav.value.current as? Dest.Player ?: return
        loadStream(p)
    }

    private fun loadStream(p: Dest.Player) {
        playerJob?.cancel()
        _player.value = UiState.Loading
        playerJob = viewModelScope.launch {
            val url = suspendCatching { repo.streamUrl(p.episodeId) }.getOrElse {
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
        val p = nowPlaying
        if (p != null && p.episodeId == episodeId) {
            repo.history.record(p.dramaId, episodeId, p.episodeNumber, p.title, p.thumbnail, positionMs, durationMs)
        } else {
            repo.savePosition(episodeId, positionMs, durationMs)
        }
    }
}
