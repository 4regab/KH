package com.kisskh.tv.ui

import com.kisskh.tv.api.Show
import com.kisskh.tv.api.suspendCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * [query] is the text exactly as typed (bind it to the text field).
 * [result] is null before any search (blank query), then Loading, Success, Empty or Error.
 * Empty means "no matches". Error means the request failed. They are never mixed up.
 */
data class SearchState(
    val query: String = "",
    val result: UiState<List<Show>>? = null,
)

/**
 * Search-as-you-type. Waits [debounceMs] after the last key, then searches. A newer query cancels
 * an older one, and an answer for an older query is dropped even if it arrives late.
 * Call from the main thread.
 */
class SearchController(
    private val scope: CoroutineScope,
    private val debounceMs: Long = DEBOUNCE_MS,
    private val search: suspend (String) -> List<Show>,
) {
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var job: Job? = null
    private var generation = 0

    /** Call on every text change. */
    fun setQuery(text: String) {
        val trimmed = text.trim()
        val current = _state.value
        // Only spaces changed and the search is fine: keep it going.
        if (trimmed.isNotEmpty() && trimmed == current.query.trim() &&
            current.result != null && current.result !is UiState.Error
        ) {
            _state.value = current.copy(query = text)
            return
        }
        job?.cancel()
        generation++
        if (trimmed.isEmpty()) {
            _state.value = SearchState(query = text)
            return
        }
        _state.value = SearchState(query = text, result = UiState.Loading)
        run(trimmed, debounceMs)
    }

    /** Clear the text and the results. */
    fun clear() = setQuery("")

    /** Search again right away after an Error. */
    fun retry() {
        val current = _state.value
        if (current.result !is UiState.Error) return
        job?.cancel()
        generation++
        _state.value = current.copy(result = UiState.Loading)
        run(current.query.trim(), 0)
    }

    private fun run(query: String, waitMs: Long) {
        val mine = generation
        job = scope.launch {
            delay(waitMs)
            val outcome = suspendCatching { search(query) }
            if (mine != generation) return@launch // a newer query took over
            val result: UiState<List<Show>> = outcome.fold(
                onSuccess = { found ->
                    // Unique ids: screens use them as lazy-list keys.
                    found.distinctBy { it.id }.let { if (it.isEmpty()) UiState.Empty() else UiState.Success(it) }
                },
                onFailure = { uiError(it) },
            )
            _state.value = _state.value.copy(result = result)
        }
    }

    companion object {
        const val DEBOUNCE_MS = 350L
    }
}
