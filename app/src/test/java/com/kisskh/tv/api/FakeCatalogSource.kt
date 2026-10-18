package com.kisskh.tv.api

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withContext

fun shows(ids: IntRange) = ids.map { Show(id = it, title = "t$it", thumbnail = null) }

/** The page the real API would return for [total] matches, with ids starting after [base]. */
fun fakePage(total: Int, page: Int, size: Int, base: Int = 0): ListPage {
    val first = (page - 1) * size + 1
    val last = minOf(page * size, total)
    val data = if (first > last) emptyList() else shows((base + first)..(base + last))
    return ListPage(page = page, pageSize = size, totalCount = total, data = data)
}

/** A [CatalogSource] for tests. Set [handler] to decide each answer; every call is recorded. */
class FakeCatalogSource(
    var handler: suspend (query: CatalogQuery, page: Int, size: Int) -> ListPage,
) : CatalogSource {
    data class Call(val query: CatalogQuery, val page: Int, val size: Int)

    val calls = mutableListOf<Call>()

    override suspend fun list(query: CatalogQuery, page: Int, pageSize: Int): ListPage {
        calls += Call(query, page, pageSize)
        return handler(query, page, pageSize)
    }
}

/**
 * Waits for [gate], even if the caller is cancelled in the meantime. This models a network answer
 * that arrives after the screen has already moved on.
 */
suspend fun lateAnswer(gate: CompletableDeferred<ListPage>): ListPage =
    withContext(NonCancellable) { gate.await() }
