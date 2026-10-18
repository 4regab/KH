package com.kisskh.tv.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.UiState
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhMotion
import com.kisskh.tv.ui.theme.KhTheme
import kotlinx.coroutines.delay

/**
 * Year of [showId], loaded from the title detail after the billboard has settled.
 * Rapid focus changes cancel the request. Null until the detail arrives, and when [showId] is null.
 */
@Composable
fun rememberFocusedYear(vm: AppViewModel, showId: Int?): String? {
    var year by remember(showId) { mutableStateOf<String?>(null) }
    LaunchedEffect(showId) {
        year = null
        val id = showId ?: return@LaunchedEffect
        delay(KhMotion.BillboardSettleMillis)
        vm.detail(id).collect { state ->
            year = (state as? UiState.Success)?.data?.let { yearOf(it.releaseDate) }
        }
    }
    return year
}

/** The line above a TV grid: the focused title, and the year once it is known. */
@Composable
fun FocusedTitleLine(title: String?, year: String?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.CenterStart) {
        if (!title.isNullOrBlank()) {
            Text(
                text = if (year.isNullOrBlank()) title else "$title    $year",
                style = KhTheme.typography.title,
                color = KhColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
