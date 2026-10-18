package com.kisskh.tv.ui

import com.kisskh.tv.api.Show
import com.kisskh.tv.api.shows
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SearchControllerTest {
    private val calls = mutableListOf<String>()

    private fun TestScope.controller(search: suspend (String) -> List<Show>) =
        SearchController(backgroundScope) { q ->
            calls += q
            search(q)
        }

    private fun SearchController.result() = state.value.result

    // advanceUntilIdle() skips work that only backgroundScope has left, so move the clock past the debounce instead.
    private fun TestScope.settle() {
        advanceTimeBy(SearchController.DEBOUNCE_MS + 1)
        runCurrent()
    }

    @Test
    fun startsInTheInitialState() = runTest {
        val c = controller { error("not called") }
        assertEquals(SearchState(), c.state.value)
        assertNull(c.result())
    }

    @Test
    fun waitsForTheDebounceThenSearchesOnce() = runTest {
        val c = controller { shows(1..2) }
        c.setQuery("a"); advanceTimeBy(100)
        c.setQuery("ab"); advanceTimeBy(100)
        c.setQuery("abc")
        assertEquals(UiState.Loading, c.result())

        advanceTimeBy(SearchController.DEBOUNCE_MS - 1); runCurrent()
        assertTrue(calls.isEmpty()) // 349 ms after the last key: still waiting

        advanceTimeBy(1); runCurrent()
        assertEquals(listOf("abc"), calls)
        assertEquals(UiState.Success(shows(1..2)), c.result())
        assertEquals("abc", c.state.value.query)
    }

    @Test
    fun debounceIsAbout350Ms() {
        assertEquals(350L, SearchController.DEBOUNCE_MS)
    }

    @Test
    fun searchesWithTheTrimmedText() = runTest {
        val c = controller { shows(1..1) }
        c.setQuery("  love  "); settle()
        assertEquals(listOf("love"), calls)
        assertEquals("  love  ", c.state.value.query) // the text field keeps what was typed
    }

    @Test
    fun anOlderAnswerArrivingLateIsDropped() = runTest {
        val gateA = CompletableDeferred<List<Show>>()
        val gateAB = CompletableDeferred<List<Show>>()
        val c = controller { q -> withContext(NonCancellable) { (if (q == "a") gateA else gateAB).await() } }

        c.setQuery("a"); advanceTimeBy(SearchController.DEBOUNCE_MS); runCurrent() // request for "a" is out
        c.setQuery("ab"); advanceTimeBy(SearchController.DEBOUNCE_MS); runCurrent() // request for "ab" is out
        assertEquals(listOf("a", "ab"), calls)

        gateAB.complete(shows(10..11)); runCurrent()
        assertEquals(UiState.Success(shows(10..11)), c.result())

        gateA.complete(shows(1..3)); runCurrent() // late
        assertEquals(UiState.Success(shows(10..11)), c.result())
        assertEquals("ab", c.state.value.query)
    }

    @Test
    fun anOlderFailureArrivingLateIsDropped() = runTest {
        val gateA = CompletableDeferred<List<Show>>()
        val c = controller { q ->
            if (q == "a") withContext(NonCancellable) { gateA.await() } else shows(5..5)
        }
        c.setQuery("a"); advanceTimeBy(SearchController.DEBOUNCE_MS); runCurrent()
        c.setQuery("ab"); settle()
        gateA.completeExceptionally(IOException("old")); runCurrent()
        assertEquals(UiState.Success(shows(5..5)), c.result())
    }

    @Test
    fun noMatchesIsEmptyButFailureIsError() = runTest {
        val c = controller { q -> if (q == "none") emptyList() else throw IOException("offline") }

        c.setQuery("none"); settle()
        assertTrue(c.result() is UiState.Empty)

        c.setQuery("boom"); settle()
        val r = c.result()
        assertTrue(r is UiState.Error)
        assertTrue((r as UiState.Error).cause is IOException)
    }

    @Test
    fun retryAfterAnErrorSearchesAgainWithoutWaiting() = runTest {
        var online = false
        val c = controller { if (online) shows(1..2) else throw IOException("offline") }
        c.setQuery("x"); settle()
        assertTrue(c.result() is UiState.Error)

        online = true
        c.retry(); runCurrent() // no debounce wait
        assertEquals(UiState.Success(shows(1..2)), c.result())
        assertEquals(listOf("x", "x"), calls)
    }

    @Test
    fun retryDoesNothingWithoutAnError() = runTest {
        val c = controller { shows(1..1) }
        c.retry(); settle()
        c.setQuery("x"); settle()
        c.retry(); settle()
        assertEquals(listOf("x"), calls)
    }

    @Test
    fun blankQueryGoesBackToInitialAndDropsPendingWork() = runTest {
        val c = controller { shows(1..2) }
        c.setQuery("abc"); advanceTimeBy(100)
        c.setQuery("   "); settle()
        assertTrue(calls.isEmpty())
        assertNull(c.result())

        c.setQuery("abc"); settle()
        assertTrue(c.result() is UiState.Success)
        c.clear()
        assertEquals(SearchState(), c.state.value)
    }

    @Test
    fun clearingWhileARequestIsOutIgnoresItsAnswer() = runTest {
        val gate = CompletableDeferred<List<Show>>()
        val c = controller { withContext(NonCancellable) { gate.await() } }
        c.setQuery("abc"); advanceTimeBy(SearchController.DEBOUNCE_MS); runCurrent()
        c.clear()
        gate.complete(shows(1..2)); runCurrent()
        assertEquals(SearchState(), c.state.value)
    }

    @Test
    fun onlyTrailingSpacesDoNotRestartTheSearch() = runTest {
        val c = controller { shows(1..2) }
        c.setQuery("abc"); settle()
        c.setQuery("abc "); settle()
        assertEquals(listOf("abc"), calls)
        assertEquals("abc ", c.state.value.query)
        assertTrue(c.result() is UiState.Success)
    }

    @Test
    fun resultsHaveUniqueIds() = runTest {
        val c = controller { shows(1..3) + shows(2..4) }
        c.setQuery("x"); settle()
        assertEquals(UiState.Success(shows(1..4)), c.result())
    }
}
