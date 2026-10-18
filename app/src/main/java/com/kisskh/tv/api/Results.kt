package com.kisskh.tv.api

import kotlinx.coroutines.CancellationException

/**
 * Like `runCatching`, but a cancelled coroutine is never turned into a failure.
 * Use this around suspend calls so cancelling a request really cancels it.
 */
inline fun <T> suspendCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
