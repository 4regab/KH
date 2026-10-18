package com.kisskh.tv.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Everything a catalog screen needs to draw itself. Read it from [CatalogPager.state].
 *
 * - [page] is the last page that loaded (0 = nothing yet).
 * - [totalCount] is what the server says matches the query.
 * - [error] set means the last request failed. Items already loaded stay.
 */
data class CatalogState(
    val query: CatalogQuery,
    val items: List<Show> = emptyList(),
    val page: Int = 0,
    val totalCount: Int = 0,
    val endReached: Boolean = false,
    val loading: Boolean = false,
    val error: Throwable? = null,
) {
    /** First page is loading: show a full-screen spinner. */
    val isInitialLoading: Boolean get() = loading && items.isEmpty()

    /** A later page is loading: show a small spinner at the end of the list. */
    val isLoadingMore: Boolean get() = loading && items.isNotEmpty()

    /** First page failed: show an error with Retry (this is not "no results"). */
    val isInitialError: Boolean get() = error != null && items.isEmpty()

    /** The server answered and has nothing for this query. */
    val isEmpty: Boolean get() = endReached && items.isEmpty() && error == null
}

/**
 * Loads a filtered catalog page by page.
 *
 * - [loadNext] loads page 1 on the first call, then page 2, 3, ... It does nothing while a request is
 *   running, after the end, or after a failure (call [retry] for that). So a list can call it every
 *   time its last item shows without hammering the server.
 * - [setQuery] with a different query drops the items, cancels the running request and loads page 1.
 *   A late answer for the old query is thrown away.
 * - Items are unique by id.
 *
 * Create it with a long-lived scope (the ViewModel scope). Call from the main thread.
 */
class CatalogPager(
    private val source: CatalogSource,
    private val scope: CoroutineScope,
    initialQuery: CatalogQuery,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
) {
    private class Request(val query: CatalogQuery, val page: Int, val generation: Int)

    private val lock = Any()
    private var generation = 0
    private var job: Job? = null
    private val _state = MutableStateFlow(CatalogState(initialQuery))

    val state: StateFlow<CatalogState> = _state.asStateFlow()

    /** The query the current state belongs to. */
    val query: CatalogQuery get() = _state.value.query

    /** Switch filters. No-op if [query] is the current one. Otherwise resets and loads page 1. */
    fun setQuery(query: CatalogQuery) {
        synchronized(lock) {
            if (query == _state.value.query) return
            job?.cancel()
            job = null
            generation++
            _state.value = CatalogState(query)
        }
        loadNext()
    }

    /** Load the next page. See the class comment for when this does nothing. */
    fun loadNext() {
        synchronized(lock) {
            val s = _state.value
            if (s.loading || s.endReached || s.error != null) return
            val request = Request(s.query, s.page + 1, generation)
            _state.value = s.copy(loading = true)
            job = scope.launch { fetch(request) }
        }
    }

    /** Try again after a failure. Does nothing if the last request did not fail. */
    fun retry() {
        synchronized(lock) {
            val s = _state.value
            if (s.error == null) return
            _state.value = s.copy(error = null)
        }
        loadNext()
    }

    /** Stop any running request and ignore its answer. The state keeps what it has. */
    fun cancel() {
        synchronized(lock) {
            job?.cancel()
            job = null
            generation++
            _state.value = _state.value.copy(loading = false)
        }
    }

    private suspend fun fetch(request: Request) {
        val result = try {
            Result.success(source.list(request.query, request.page, pageSize))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
        synchronized(lock) {
            val s = _state.value
            // Stale: the query changed (or the pager was cancelled) while this request was running.
            if (request.generation != generation || request.query != s.query) return
            _state.value = result.fold(
                onSuccess = { page ->
                    val seen = s.items.mapTo(HashSet()) { it.id }
                    s.copy(
                        items = s.items + page.data.filter { seen.add(it.id) },
                        page = request.page,
                        totalCount = page.totalCount,
                        endReached = page.data.isEmpty() || request.page * pageSize >= page.totalCount,
                        loading = false,
                        error = null,
                    )
                },
                onFailure = { s.copy(loading = false, error = it) },
            )
        }
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 40
    }
}
