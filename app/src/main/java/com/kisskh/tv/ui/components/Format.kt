package com.kisskh.tv.ui.components

import java.util.Locale

/** 5.0 -> "5", 29.6 -> "29.6". Episode numbers can be fractional. */
fun formatEpisodeNumber(number: Float): String =
    if (number % 1f == 0f) number.toLong().toString() else number.toString()

/** 754000 -> "12:34", 4_000_000 -> "1:06:40". */
fun formatPosition(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
}

/** "2026-10-08T17:34:37" -> "2026-10-08". Null when there is no date. */
fun dateOnly(raw: String?): String? = raw?.substringBefore('T')?.trim()?.takeIf { it.isNotEmpty() }

/** "2026-10-08T17:34:37" -> "2026". Null when the text does not start with a year. */
fun yearOf(raw: String?): String? = dateOnly(raw)?.take(4)?.takeIf { y -> y.length == 4 && y.all { it.isDigit() } }
