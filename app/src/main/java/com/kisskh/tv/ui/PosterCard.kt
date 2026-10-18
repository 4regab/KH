package com.kisskh.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.kisskh.tv.api.Show

/** A focusable poster with the title under it. Focus scaling is built into the TV Card. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PosterCard(show: Show, onClick: () -> Unit) {
    Column(Modifier.width(150.dp).padding(8.dp)) {
        Card(
            onClick = onClick,
            scale = CardDefaults.scale(focusedScale = 1.1f),
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).onTap(onClick),
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = thumbUrl(show.thumbnail),
                    contentDescription = show.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                val badge = show.label?.takeIf { it.isNotBlank() }
                if (badge != null) {
                    Text(
                        badge, color = Color.White, fontSize = 10.sp, maxLines = 1,
                        modifier = Modifier.align(Alignment.TopStart)
                            .background(Color(0xCC000000)).padding(horizontal = 4.dp),
                    )
                }
            }
        }
        Text(
            show.title ?: "", color = Color.White, fontSize = 12.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(top = 4.dp),
        )
    }
}

/** Some thumbnails come proxied through serveproxy; use them as-is, they load fine. */
fun thumbUrl(raw: String?): String = raw ?: ""
