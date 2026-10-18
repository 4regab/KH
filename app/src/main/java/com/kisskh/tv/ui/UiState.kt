package com.kisskh.tv.ui

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import retrofit2.HttpException
import java.io.IOException

/**
 * What a screen shows for something it loads. Every load ends in one of these, so a screen
 * is never stuck on [Loading].
 *
 * - [Empty]: the server answered and there is nothing to show (not a failure).
 * - [Error]: the request failed. Show [Error.message] and a Retry button.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Empty(val message: String? = null) : UiState<Nothing>
    data class Error(val message: String, val cause: Throwable? = null) : UiState<Nothing>
}

/** Short, plain text for a failed request. */
fun errorMessage(t: Throwable): String = when (t) {
    is IOException -> "Can't reach the server. Check your connection and try again."
    is HttpException -> "The server returned an error (${t.code()}). Try again."
    is JsonDataException, is JsonEncodingException -> "The server sent a reply the app can't read. Try again."
    else -> t.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Try again."
}

/** [UiState.Error] built from a throwable. */
fun uiError(cause: Throwable): UiState.Error = UiState.Error(errorMessage(cause), cause)
