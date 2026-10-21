package com.kisskh.tv.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.kisskh.tv.R
import com.kisskh.tv.api.media.TmdbTitle
import com.kisskh.tv.ui.components.Artwork
import com.kisskh.tv.ui.theme.FormFactor
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhFocusable
import com.kisskh.tv.ui.theme.KhTheme
import com.kisskh.tv.ui.theme.khFocusBorder
import com.kisskh.tv.ui.theme.rememberKhFocusProgress

/** Text on artwork: a soft shadow keeps it readable on a bright frame. */
private val ArtTextShadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), 6f)

/** Clear at the top, near-black at the bottom, so the name over the art can be read. */
private val CardScrim = Brush.verticalGradient(
    0f to Color.Transparent,
    0.45f to Color.Black.copy(alpha = 0.25f),
    1f to Color.Black.copy(alpha = 0.88f),
)

/**
 * A title tile. Two shapes:
 *
 * - Landscape (default, TV rows and grids): the 16:9 backdrop with the name written on its lower
 *   left, like the boxart on Netflix TV. TMDB backdrops carry no text, so without the name a tile
 *   could not be told apart from its neighbours.
 * - [poster] (phone rows and grids): the 2:3 poster, which already shows the title. The name is
 *   only written on it when the title has no poster.
 *
 * [progress] (0..1) draws a red bar along the bottom for titles the viewer has started.
 * The spoken label is always the title name.
 */
@Composable
fun MediaCard(
    title: TmdbTitle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    poster: Boolean = false,
    progress: Float? = null,
    onFocused: () -> Unit = {},
) {
    val tv = KhTheme.formFactor == FormFactor.TV
    val url = if (poster) title.poster ?: title.artwork else title.artwork
    val writeName = !poster || title.poster == null
    KhFocusable(
        onClick = onClick,
        modifier = modifier
            .aspectRatio(if (poster) 2f / 3f else 16f / 9f)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .semantics { contentDescription = title.name },
        shape = KhTheme.shapes.art,
    ) {
        Box(Modifier.fillMaxSize()) {
            Artwork(url, title.name, Modifier.fillMaxSize(), titleOnFailure = false)
            if (writeName) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.62f).background(CardScrim))
                Text(
                    title.name,
                    style = TextStyle(
                        fontSize = if (tv) 15.sp else 13.sp,
                        lineHeight = if (tv) 18.sp else 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.1).sp,
                        shadow = ArtTextShadow,
                    ),
                    color = KhColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 10.dp, end = 10.dp, bottom = if (progress != null) 12.dp else 9.dp),
                )
            }
            if (progress != null) ProgressBar(progress, Modifier.align(Alignment.BottomStart))
        }
    }
}

/** The red "watched so far" bar along the bottom of a tile or episode still. */
@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, height: Dp = 3.dp) {
    Box(modifier.fillMaxWidth().height(height).background(Color.White.copy(alpha = 0.28f))) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(KhColors.Brand))
    }
}

/**
 * A one-line text field with the KH focus ring. The hint is also its spoken label. On a remote, Up and
 * Down always leave the field, so it never traps focus. Left and Right move the cursor, and leave the
 * field only while it is empty. [onAction] runs for the keyboard's action key ([imeAction]).
 * [leading] draws an icon before the text (the search glass).
 */
@Composable
fun KhField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
    onAction: () -> Unit = {},
    leading: Int? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val progress = rememberKhFocusProgress(focused)
    val focusManager = LocalFocusManager.current
    val shape = KhTheme.shapes.action
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(color = KhColors.TextPrimary, fontSize = 17.sp),
        cursorBrush = SolidColor(KhColors.TextPrimary),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onAction() }),
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = hint }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                    Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                    Key.DirectionLeft -> value.isEmpty() && focusManager.moveFocus(FocusDirection.Left)
                    Key.DirectionRight -> value.isEmpty() && focusManager.moveFocus(FocusDirection.Right)
                    else -> false
                }
            }
            .khFocusBorder({ progress.value }, shape)
            .background(KhColors.Elevated, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leading != null) {
                    Icon(
                        painterResource(leading),
                        contentDescription = null,
                        tint = KhColors.TextSecondary,
                        modifier = Modifier.padding(end = 10.dp).size(20.dp),
                    )
                }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(hint, color = KhColors.TextSecondary, fontSize = 17.sp, maxLines = 1)
                    }
                    inner()
                }
            }
        },
    )
}

private val KeyRows: List<List<Char>> = ("abcdefghijklmnopqrstuvwxyz1234567890").toList().chunked(6)

private val KeyWidth = 46.dp
private val KeyHeight = 40.dp
private val KeyGap = 4.dp

/**
 * The on-screen keyboard for TV search, laid out like the Netflix TV one: a row of Space and Delete,
 * then a-z and 0-9 six to a row. A remote types with it directly, so the system keyboard never
 * pops up over the results. Right from the last column moves into the results.
 */
@Composable
fun TvKeyboard(
    onType: (Char) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    firstKey: Modifier = Modifier,
) {
    val wide = KeyWidth * 2 + KeyGap
    Column(modifier, verticalArrangement = Arrangement.spacedBy(KeyGap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
            KeyboardKey(label = "Space", onClick = { onType(' ') }, width = wide, modifier = firstKey, spoken = "Space")
            KeyboardKey(icon = Icons.Filled.ArrowBack, onClick = onDelete, width = wide, spoken = "Delete")
            KeyboardKey(icon = Icons.Filled.Clear, onClick = onClear, width = wide, spoken = "Clear")
        }
        KeyRows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
                row.forEach { c -> KeyboardKey(label = c.toString(), onClick = { onType(c) }, spoken = c.toString()) }
            }
        }
    }
}

@Composable
private fun KeyboardKey(
    onClick: () -> Unit,
    spoken: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    icon: ImageVector? = null,
    width: Dp = KeyWidth,
) {
    KhFocusable(
        onClick = onClick,
        modifier = modifier
            .size(width, KeyHeight)
            .semantics { contentDescription = spoken },
        shape = KhTheme.shapes.button,
        scaleAndLift = false,
    ) { focused ->
        val fill = if (focused) KhColors.TextPrimary else Color.Transparent
        val ink = if (focused) KhColors.OnLight else KhColors.TextPrimary
        Box(Modifier.fillMaxSize().background(fill, KhTheme.shapes.button), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
            } else {
                Text(
                    label.orEmpty(),
                    color = ink,
                    fontSize = if ((label?.length ?: 0) > 1) 14.sp else 18.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The search glass, for the phone field. */
val SearchIcon: Int = R.drawable.ic_nav_search
