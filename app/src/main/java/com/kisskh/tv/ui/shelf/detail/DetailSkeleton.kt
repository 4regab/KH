package com.kisskh.tv.ui.shelf.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.kisskh.tv.api.media.MediaKind
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.components.Artwork
import com.kisskh.tv.ui.components.HeroBackdrop
import com.kisskh.tv.ui.components.edge
import com.kisskh.tv.ui.components.isTv
import com.kisskh.tv.ui.shelf.SkeletonBlock
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhGradients
import com.kisskh.tv.ui.theme.KhTheme

/**
 * What a title page shows while the title loads: the page in its final shape, with the tapped
 * card's picture and name already in place and grey blocks where the facts will go. Static, no
 * shimmer, no spinner. The Back button is drawn by the caller on top.
 */
@Composable
internal fun DetailSkeleton(dest: Dest.TitlePage) {
    val series = dest.kind != MediaKind.MOVIE
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val screenHeight = maxHeight
        Column(Modifier.fillMaxSize()) {
            if (isTv()) TvSkeleton(dest, detailHeaderHeight(screenHeight)) else PhoneSkeleton(dest)
            if (series) {
                SkeletonBlock(Modifier.padding(start = edge(), top = 24.dp, bottom = 8.dp).width(120.dp).height(20.dp))
                repeat(3) { EpisodeRowSkeleton() }
            }
        }
    }
}

@Composable
private fun TvSkeleton(dest: Dest.TitlePage, height: Dp) {
    Box(Modifier.fillMaxWidth().height(height)) {
        HeroBackdrop(dest.poster, dest.name, Modifier.fillMaxSize())
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = edge() + 8.dp, bottom = KhTheme.spacing.focusBleed + 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                dest.name,
                style = KhTheme.typography.display,
                color = KhColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 520.dp),
            )
            SkeletonBlock(Modifier.width(220.dp).height(16.dp))
            SkeletonBlock(Modifier.width(160.dp).height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonBlock(Modifier.width(500.dp).height(14.dp))
                SkeletonBlock(Modifier.width(460.dp).height(14.dp))
                SkeletonBlock(Modifier.width(300.dp).height(14.dp))
            }
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonBlock(Modifier.width(200.dp).height(44.dp))
                SkeletonBlock(Modifier.width(150.dp).height(48.dp))
                SkeletonBlock(Modifier.width(150.dp).height(48.dp))
            }
        }
    }
}

@Composable
private fun PhoneSkeleton(dest: Dest.TitlePage) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().heightIn(max = 360.dp).aspectRatio(16f / 9f)) {
            Artwork(dest.poster, dest.name, Modifier.fillMaxSize(), fadeMillis = 200, titleOnFailure = false)
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.45f).background(KhGradients.BottomFade))
        }
        Column(Modifier.padding(horizontal = edge()).widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (dest.name.isNotBlank()) {
                Text(dest.name, style = KhTheme.typography.headline, color = KhColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            SkeletonBlock(Modifier.width(200.dp).height(16.dp))
            SkeletonBlock(Modifier.fillMaxWidth().padding(top = 6.dp).height(48.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { SkeletonBlock(Modifier.weight(1f).height(64.dp)) }
            }
            SkeletonBlock(Modifier.fillMaxWidth().height(14.dp))
            SkeletonBlock(Modifier.fillMaxWidth().height(14.dp))
            SkeletonBlock(Modifier.width(180.dp).height(14.dp))
        }
    }
}
