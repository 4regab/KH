package com.kisskh.tv.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.kisskh.tv.api.media.TmdbTitle
import com.kisskh.tv.ui.components.Artwork
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhFocusable
import com.kisskh.tv.ui.theme.KhTheme

@Composable
fun MediaCard(
    title: TmdbTitle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showName: Boolean = false,
    progress: Float? = null,
    onFocused: () -> Unit = {},
) {
    KhFocusable(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { if (it.isFocused) onFocused() }
            .semantics { contentDescription = title.name },
        shape = KhTheme.shapes.art,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                Artwork(title.artwork, title.name, Modifier.fillMaxSize())
                if (progress != null) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(KhColors.TextPrimary.copy(alpha = 0.3f)),
                    )
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(KhColors.Brand),
                    )
                }
            }
            if (showName) {
                Text(
                    title.name,
                    color = KhColors.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
                )
            }
        }
    }
}

@Composable
fun KhField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(color = KhColors.TextPrimary, fontSize = 18.sp),
        cursorBrush = SolidColor(KhColors.TextPrimary),
        singleLine = true,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .background(KhColors.Surface, KhTheme.shapes.action)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(hint, color = if (focused) KhColors.TextSecondary else KhColors.TextSecondary.copy(alpha = 0.7f), fontSize = 18.sp)
                }
                inner()
            }
        },
    )
}
