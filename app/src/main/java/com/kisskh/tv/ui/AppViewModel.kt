package com.kisskh.tv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kisskh.tv.api.Drama
import com.kisskh.tv.api.Repository
import com.kisskh.tv.api.Show
import com.kisskh.tv.api.SubtitleFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Browse : Screen
    data class Detail(val id: Int) : Screen
    data class Player(val episodeId: Int, val title: String) : Screen
}

data class Row(val title: String, val items: List<Show>)

data class PlayerData(val url: String, val subs: List<SubtitleFile>, val resumeMs: Long)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repository(app)

    val screen = MutableStateFlow<Screen>(Screen.Home)
    private val back = ArrayDeque<Screen>()

    val rows = MutableStateFlow<List<Row>>(emptyList())
    val homeError = MutableStateFlow<String?>(null)

    val detail = MutableStateFlow<Drama?>(null)

    private val _player = MutableStateFlow<PlayerData?>(null)
    val player: StateFlow<PlayerData?> = _player
    val playerError = MutableStateFlow<String?>(null)

    val searchResults = MutableStateFlow<List<Show>>(emptyList())

    init {
        loadHome()
    }

    fun go(s: Screen) {
        back.addLast(screen.value)
        screen.value = s
        when (s) {
            is Screen.Detail -> loadDetail(s.id)
            else -> {}
        }
    }

    /** @return true if we handled back, false if the app should exit. */
    fun onBack(): Boolean {
        val prev = back.removeLastOrNull() ?: return false
        _player.value = null
        playerError.value = null
        screen.value = prev
        return true
    }

    private fun loadHome() {
        viewModelScope.launch {
            try {
                val r = buildList {
                    runCatching { add(Row("Latest", repo.api.lastUpdate())) }
                    runCatching { add(Row("Popular", repo.api.mostView())) }
                    runCatching { add(Row("Top Rating", repo.api.topRating())) }
                    runCatching { add(Row("Anime", repo.api.animate())) }
                    runCatching { add(Row("Upcoming", repo.api.upcoming())) }
                }.filter { it.items.isNotEmpty() }
                rows.value = r
                if (r.isEmpty()) homeError.value = "Could not load. Check connection."
            } catch (e: Throwable) {
                homeError.value = e.message ?: "Load failed"
            }
        }
    }

    fun retryHome() { homeError.value = null; loadHome() }

    private fun loadDetail(id: Int) {
        detail.value = null
        viewModelScope.launch {
            detail.value = runCatching { repo.api.drama(id) }.getOrNull()
        }
    }

    fun search(q: String) {
        if (q.isBlank()) { searchResults.value = emptyList(); return }
        viewModelScope.launch {
            searchResults.value = runCatching { repo.api.search(q.trim()) }.getOrDefault(emptyList())
        }
    }

    fun play(episodeId: Int, title: String) {
        go(Screen.Player(episodeId, title))
        _player.value = null
        playerError.value = null
        viewModelScope.launch {
            val url = runCatching { repo.streamUrl(episodeId) }.getOrNull()
            if (url == null) {
                playerError.value = "This episode has no playable stream yet."
                return@launch
            }
            val subs = runCatching { repo.subtitles(episodeId) }.getOrDefault(emptyList())
            _player.value = PlayerData(url, subs, repo.loadPosition(episodeId))
        }
    }

    fun savePosition(episodeId: Int, pos: Long, dur: Long) = repo.savePosition(episodeId, pos, dur)

    // --- browse grid with paging ---
    val browseItems = MutableStateFlow<List<Show>>(emptyList())
    private var browsePage = 0
    private var browseTotal = Int.MAX_VALUE
    private var browseLoading = false
    var browseType = 0; var browseCountry = 0; var browseStatus = 0; var browseOrder = 1

    fun resetBrowse() {
        browseItems.value = emptyList(); browsePage = 0; browseTotal = Int.MAX_VALUE
        loadMoreBrowse()
    }

    fun loadMoreBrowse() {
        if (browseLoading || browseItems.value.size >= browseTotal) return
        browseLoading = true
        viewModelScope.launch {
            try {
                val p = browsePage + 1
                val res = repo.api.list(p, 40, browseType, 0, browseCountry, browseStatus, browseOrder)
                browseTotal = res.totalCount
                browsePage = p
                browseItems.value = browseItems.value + res.data
            } catch (_: Throwable) {
            } finally {
                browseLoading = false
            }
        }
    }
}
