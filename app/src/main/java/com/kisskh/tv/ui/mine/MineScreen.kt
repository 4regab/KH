package com.kisskh.tv.ui.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.download.downloadsSection
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhTheme

/**
 * "My KH": the viewer's own titles across every source. Each section adds its own lazy items:
 * [savedSections] (My List and Watched) and [downloadsSection] (offline copies).
 */
@Composable
fun MineScreen(vm: AppViewModel, library: Library) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "mine-title") {
            Text(
                "My KH",
                style = KhTheme.typography.headline,
                color = KhColors.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
            )
        }
        downloadsSection(vm)
        savedSections(vm, library)
    }
}
