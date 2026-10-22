package com.kisskh.tv.download

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Text
import com.kisskh.tv.api.SubtitleFile
import com.kisskh.tv.api.media.MediaKeys
import com.kisskh.tv.player.VideoPlayer
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.components.KhButton
import com.kisskh.tv.ui.components.LoadingState
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhTheme
import java.io.File

/**
 * Plays a finished download with no network, in the same player and controls as online playback.
 * Next, Previous, Servers and Episodes are hidden because they need the network.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun OfflinePlayer(vm: AppViewModel, dest: Dest.OfflinePlayer) {
    val loaded by vm.downloads.loaded.collectAsStateWithLifecycle()
    val items by vm.downloads.items.collectAsStateWithLifecycle()
    val item = items.firstOrNull { it.id == dest.downloadId }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            !loaded -> {
                LoadingState(message = "Opening download")
                KhButton("Back", onClick = { vm.onBack() }, modifier = Modifier.align(Alignment.TopStart).padding(24.dp))
            }
            item == null -> OfflineMessage("This download is gone.", onBack = { vm.onBack() })
            item.phase != DownloadPhase.COMPLETED ->
                OfflineMessage("This download isn't finished yet. ${item.status}.", onBack = { vm.onBack() })
            else -> Playing(vm, item.meta)
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun Playing(vm: AppViewModel, meta: DownloadMeta) {
    val source = remember(meta.id) { vm.downloads.offlineSource(meta.id) }
    var failed by remember(meta.id) { mutableStateOf(false) }
    if (source == null) {
        OfflineMessage("This download can't be opened. Remove it and download it again.", onBack = { vm.onBack() }, onRemove = { remove(vm, meta) })
        return
    }
    val subs = remember(meta.id) {
        meta.subs.filter { File(it.path).isFile }.map { SubtitleFile(File(it.path).toURI().toString(), it.label, it.language) }
    }
    // Same stores as online playback, so Continue Watching and resume carry over.
    val resume = remember(meta.id) { savedPosition(vm, meta) }
    VideoPlayer(
        url = source.mediaItem.localConfiguration?.uri?.toString().orEmpty(),
        subs = subs,
        resumeMs = resume,
        title = meta.title,
        label = listOf(meta.label, "Downloaded").filter { it.isNotBlank() }.joinToString(" \u00B7 "),
        onProgress = { pos, dur -> savePosition(vm, meta, pos, dur) },
        onPlaybackError = { failed = true },
        onBack = { vm.onBack() },
        onHome = { vm.goHome() },
        onEpisodes = {},
        onNext = null,
        onPrev = null,
        onServers = null,
        hideControlsOnBack = !failed,
        showEpisodes = false,
        offline = source,
    )
    if (failed) {
        OfflineMessage(
            "This download can't be played. It may be damaged. Remove it and download it again.",
            onBack = { vm.onBack() },
            onRemove = { remove(vm, meta) },
            modifier = Modifier.background(Color.Black.copy(alpha = 0.78f)),
        )
    }
}

private fun remove(vm: AppViewModel, meta: DownloadMeta) {
    vm.downloads.remove(meta.id)
    vm.onBack()
}

private fun savedPosition(vm: AppViewModel, meta: DownloadMeta): Long {
    val key = meta.progressKey
    return when {
        !key.isNullOrBlank() -> if (MediaKeys.valid(key)) vm.media.progress.position(key) else 0L
        else -> vm.repo.loadPosition(meta.episodeId)
    }
}

private fun savePosition(vm: AppViewModel, meta: DownloadMeta, positionMs: Long, durationMs: Long) {
    val key = meta.progressKey
    if (!key.isNullOrBlank()) {
        if (MediaKeys.valid(key)) vm.media.progress.save(key, meta.title, meta.poster, positionMs, durationMs)
    } else {
        vm.repo.history.record(meta.dramaId, meta.episodeId, meta.episodeNumber, meta.title, meta.poster, positionMs, durationMs)
    }
}

@Composable
private fun OfflineMessage(
    message: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(message, style = KhTheme.typography.title, color = KhColors.TextPrimary, textAlign = TextAlign.Center)
            KhButton("Back", onClick = onBack, primary = true)
            if (onRemove != null) KhButton("Remove download", onClick = onRemove)
        }
    }
}
