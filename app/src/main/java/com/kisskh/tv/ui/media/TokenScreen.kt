package com.kisskh.tv.ui.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.components.KhButton
import com.kisskh.tv.ui.components.screenStart
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhTheme

@Composable
fun TokenScreen(vm: AppViewModel) {
    val saved by vm.media.token.collectAsStateWithLifecycle()
    var draft by remember(saved) { mutableStateOf(saved) }
    Column(
        Modifier.fillMaxSize().padding(start = screenStart(), end = 32.dp, top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Febbox token", style = KhTheme.typography.heading, color = KhColors.TextPrimary)
        Text(
            "CineJoy asks Febbox for the video. Paste the token from the site (saved in the browser as cinejoy_fem_token). Without it, Febbox refuses the stream.",
            style = KhTheme.typography.body,
            color = KhColors.TextSecondary,
        )
        KhField(
            value = draft,
            onValueChange = { draft = it },
            hint = "Token",
            modifier = Modifier.fillMaxWidth(),
        )
        KhButton(
            "Save",
            primary = true,
            onClick = {
                vm.media.saveToken(draft)
                vm.onBack()
            },
        )
        if (saved.isNotEmpty()) {
            KhButton("Clear token", onClick = {
                draft = ""
                vm.media.saveToken("")
            })
        }
    }
}
