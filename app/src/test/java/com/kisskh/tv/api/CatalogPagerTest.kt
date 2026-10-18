package com.kisskh.tv.api

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogPagerTest {
    private val series = CatalogQuery(ContentType.SERIES)
    private val movies = CatalogQuery(ContentType.MOVIE)

    private fun TestScope.pager(src: FakeCatalogSource, q: CatalogQuery = series, size: Int = 2) =
        CatalogPager(src, backgroundScope, q, pageSize = size)

    private fun CatalogPager.ids() = state.value.items.map { it.id }

    @Test
    fun startsEmptyAndIdle() = runTest {
        val p = pager(FakeCatalogSource { _, _, _ -> error("not called") })
        val s = p.state.value
        assertEquals(series, s.query)
        assertTrue(s.items.isEmpty())
        assertEquals(0, s.page)
        assertFalse(s.loading)
        assertFalse(s.endReached)
        assertNull(s.error)
        assertFalse(s.isEmpty) // nothing has been asked yet, so this is not "no results"
    }

    @Test
    fun loadsPagesInOrderUntilTheEnd() = runTest {
        val src = FakeCatalogSource { _, page, size -> fakePage(total = 5, page = page, size = size) }
        val p = pager(src)

        p.loadNext(); runCurrent()
        assertEquals(listOf(1, 2), p.ids())
        assertEquals(1, p.state.value.page)
        assertEquals(5, p.state.value.totalCount)
        assertFalse(p.state.value.endReached)

        p.loadNext(); runCurrent()
        p.loadNext(); runCurrent()
        assertEquals(listOf(1, 2, 3, 4, 5), p.ids())
        assertEquals(3, p.state.value.page)
        assertTrue(p.state.value.endReached)

        p.loadNext(); runCurrent() // after the end: no more requests
        assertEquals(listOf(1, 2, 3), src.calls.map { it.page })
        assertTrue(src.calls.all { it.size == 2 && it.query == series })
    }

    @Test
    fun loadingFlagIsOnWhileARequestRuns() = runTest {
        val gate = CompletableDeferred<ListPage>()
        val p = pager(FakeCatalogSource { _, _, _ -> gate.await() })

        p.loadNext(); runCurrent()
        assertTrue(p.state.value.loading)
        assertTrue(p.state.value.isInitialLoading)

        gate.complete(fakePage(total = 4, page = 1, size = 2))
        runCurrent()
        assertFalse(p.state.value.loading)
        assertEquals(listOf(1, 2), p.ids())
    }

    @Test
    fun duplicateLoadNextCallsMakeOneRequest() = runTest {
        val gate = CompletableDeferred<ListPage>()
        val src = FakeCatalogSource { _, _, _ -> gate.await() }
        val p = pager(src)

        p.loadNext(); p.loadNext(); p.loadNext(); runCurrent()
        p.loadNext(); runCurrent()
        assertEquals(1, src.calls.size)

        gate.complete(fakePage(total = 10, page = 1, size = 2))
        runCurrent()
        assertEquals(1, src.calls.size)
        assertEquals(1, p.state.value.page)
    }

    @Test
    fun itemsAreUniqueById() = runTest {
        // page 2 repeats id 2 and id 3 shows up twice inside page 2
        val pages = mapOf(
            1 to ListPage(totalCount = 6, data = shows(1..2)),
            2 to ListPage(totalCount = 6, data = shows(2..3) + shows(3..3)),
        )
        val p = pager(FakeCatalogSource { _, page, _ -> pages.getValue(page) })

        p.loadNext(); runCurrent()
        p.loadNext(); runCurrent()
        assertEquals(listOf(1, 2, 3), p.ids())
        assertEquals(2, p.state.value.page)
    }

    @Test
    fun emptyFirstPageIsEmptyNotError() = runTest {
        val p = pager(FakeCatalogSource { _, page, size -> fakePage(total = 0, page = page, size = size) })
        p.loadNext(); runCurrent()
        val s = p.state.value
        assertTrue(s.isEmpty)
        assertTrue(s.endReached)
        assertNull(s.error)
        assertFalse(s.isInitialError)
    }

    @Test
    fun emptyPageEndsTheListEvenIfTotalCountSaysMore() = runTest {
        val src = FakeCatalogSource { _, page, _ ->
            if (page == 1) ListPage(totalCount = 100, data = shows(1..2)) else ListPage(totalCount = 100)
        }
        val p = pager(src)
        p.loadNext(); runCurrent()
        p.loadNext(); runCurrent()
        assertTrue(p.state.value.endReached)
        assertEquals(listOf(1, 2), p.ids())
    }

    @Test
    fun failureIsAnErrorNotEmptyAndRetryRecovers() = runTest {
        var fail = true
        val src = FakeCatalogSource { _, page, size ->
            if (fail) throw IOException("offline") else fakePage(total = 3, page = page, size = size)
        }
        val p = pager(src)

        p.loadNext(); runCurrent()
        var s = p.state.value
        assertNotNull(s.error)
        assertTrue(s.isInitialError)
        assertFalse(s.isEmpty)
        assertFalse(s.loading)
        assertEquals(1, src.calls.size)

        p.loadNext(); runCurrent() // scrolling must not hammer a failing server
        assertEquals(1, src.calls.size)

        fail = false
        p.retry(); runCurrent()
        s = p.state.value
        assertNull(s.error)
        assertEquals(listOf(1, 2), p.ids())
        assertEquals(listOf(1, 1), src.calls.map { it.page }) // retried page 1, not page 2
    }

    @Test
    fun failureOnALaterPageKeepsLoadedItems() = runTest {
        var fail = false
        val src = FakeCatalogSource { _, page, size ->
            if (fail && page == 2) throw IOException("blip") else fakePage(total = 6, page = page, size = size)
        }
        val p = pager(src)
        p.loadNext(); runCurrent()
        fail = true
        p.loadNext(); runCurrent()

        val s = p.state.value
        assertNotNull(s.error)
        assertFalse(s.isInitialError) // the list is still there
        assertEquals(listOf(1, 2), p.ids())
        assertEquals(1, s.page)

        fail = false
        p.retry(); runCurrent()
        assertEquals(listOf(1, 2, 3, 4), p.ids())
        assertNull(p.state.value.error)
    }

    @Test
    fun retryDoesNothingWithoutAnError() = runTest {
        val src = FakeCatalogSource { _, page, size -> fakePage(total = 6, page = page, size = size) }
        val p = pager(src)
        p.retry(); runCurrent()
        assertTrue(src.calls.isEmpty())
    }

    @Test
    fun changingTheQueryResetsAndLoadsPageOne() = runTest {
        val src = FakeCatalogSource { q, page, size ->
            fakePage(total = 4, page = page, size = size, base = if (q.type == ContentType.MOVIE) 100 else 0)
        }
        val p = pager(src)
        p.loadNext(); runCurrent()
        assertEquals(listOf(1, 2), p.ids())

        p.setQuery(movies); runCurrent()
        assertEquals(movies, p.state.value.query)
        assertEquals(listOf(101, 102), p.ids()) // nothing from the old query
        assertEquals(1, p.state.value.page)
        assertEquals(FakeCatalogSource.Call(movies, page = 1, size = 2), src.calls.last())
    }

    @Test
    fun settingTheSameQueryDoesNothing() = runTest {
        val src = FakeCatalogSource { _, page, size -> fakePage(total = 4, page = page, size = size) }
        val p = pager(src)
        p.loadNext(); runCurrent()
        p.setQuery(series.copy()); runCurrent()
        assertEquals(1, src.calls.size)
        assertEquals(listOf(1, 2), p.ids())
    }

    @Test
    fun lateAnswerForAnOldQueryIsIgnored() = runTest {
        val oldGate = CompletableDeferred<ListPage>()
        val newGate = CompletableDeferred<ListPage>()
        val src = FakeCatalogSource { q, _, _ ->
            // the old answer still arrives after the query changed (it ignores cancellation)
            if (q == series) lateAnswer(oldGate) else newGate.await()
        }
        val p = pager(src)

        p.loadNext(); runCurrent() // request for `series` is waiting
        p.setQuery(movies); runCurrent() // user changed the filter; request for `movies` is waiting
        assertEquals(2, src.calls.size)

        newGate.complete(fakePage(total = 2, page = 1, size = 2, base = 100))
        runCurrent()
        assertEquals(listOf(101, 102), p.ids())

        oldGate.complete(fakePage(total = 9, page = 1, size = 2)) // arrives late
        runCurrent()
        assertEquals(movies, p.state.value.query)
        assertEquals(listOf(101, 102), p.ids()) // not mixed
        assertEquals(2, p.state.value.totalCount)
        assertFalse(p.state.value.loading)
    }

    @Test
    fun lateAnswerBeforeTheNewOneIsAlsoIgnored() = runTest {
        val oldGate = CompletableDeferred<ListPage>()
        val newGate = CompletableDeferred<ListPage>()
        val src = FakeCatalogSource { q, _, _ -> if (q == series) lateAnswer(oldGate) else newGate.await() }
        val p = pager(src)

        p.loadNext(); runCurrent()
        p.setQuery(movies); runCurrent()
        oldGate.complete(fakePage(total = 9, page = 1, size = 2)); runCurrent()

        // the old answer must neither show up nor clear the new request's loading flag
        assertTrue(p.ids().isEmpty())
        assertTrue(p.state.value.loading)

        newGate.complete(fakePage(total = 2, page = 1, size = 2, base = 100)); runCurrent()
        assertEquals(listOf(101, 102), p.ids())
    }

    @Test
    fun oldFailureDoesNotShowOnTheNewQuery() = runTest {
        val oldGate = CompletableDeferred<ListPage>()
        val src = FakeCatalogSource { q, page, size ->
            if (q == series) {
                try { lateAnswer(oldGate) } catch (e: IllegalStateException) { throw IOException("old failed") }
            } else fakePage(total = 2, page = page, size = size, base = 100)
        }
        val p = pager(src)
        p.loadNext(); runCurrent()
        p.setQuery(movies); runCurrent()
        oldGate.completeExceptionally(IllegalStateException("boom")); runCurrent()

        assertNull(p.state.value.error)
        assertEquals(listOf(101, 102), p.ids())
    }

    @Test
    fun cancelStopsLoadingAndDropsTheAnswer() = runTest {
        val gate = CompletableDeferred<ListPage>()
        val p = pager(FakeCatalogSource { _, _, _ -> lateAnswer(gate) })
        p.loadNext(); runCurrent()
        p.cancel()
        assertFalse(p.state.value.loading)

        gate.complete(fakePage(total = 4, page = 1, size = 2)); runCurrent()
        assertTrue(p.ids().isEmpty())
    }
}
