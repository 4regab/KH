package com.kisskh.tv.player

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.kisskh.tv.api.SubtitleFile

@UnstableApi
@Composable
fun VideoPlayer(
    url: String,
    subs: List<SubtitleFile>,
    resumeMs: Long,
    onProgress: (pos: Long, dur: Long) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            val subConfigs = subs.map { s ->
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(s.uri))
                    .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                    .setLabel(s.label)
                    .setLanguage(s.language)
                    .apply { if (s.language.startsWith("en")) setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT) }
                    .build()
            }
            val item = MediaItem.Builder()
                .setUri(url)
                .setSubtitleConfigurations(subConfigs)
                .build()
            setMediaItem(item)
            if (resumeMs > 0) seekTo(resumeMs)
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onProgress(player.currentPosition, player.duration)
            player.release()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    useController = true
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    subtitleView?.setApplyEmbeddedStyles(true)
                }
            },
        )
    }
}
