package com.kisskh.tv.ui.shelf.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kisskh.tv.api.ReleaseDates
import com.kisskh.tv.api.TitleMark
import com.kisskh.tv.api.source.TitleDetail
import com.kisskh.tv.ui.components.Artwork
import com.kisskh.tv.ui.components.HeroBackdrop
import com.kisskh.tv.ui.components.KhActionButton
import com.kisskh.tv.ui.components.edge
import com.kisskh.tv.ui.components.isTv
import com.kisskh.tv.ui.shelf.MainAction
import com.kisskh.tv.ui.shelf.SavedActions
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhFocusable
import com.kisskh.tv.ui.theme.KhGradients
import com.kisskh.tv.ui.theme.KhMotion
import com.kisskh.tv.ui.theme.KhTheme

/*
 * The top of a title page.
 *
 * TV: key art on the right 70% (fading into the page on the left and bottom), and on the left the
 * logo or title, the facts, genres, synopsis, tagline and one row of actions.
 *
 * Phone and tablet: 16:9 art at the top, then the logo or title, facts, a full-width Play button,
 * the icon-over-label My List / Watched / Download row, and a synopsis that folds to three lines.
 */

/** The smallest text size allowed on a phone. */
private const val MIN_PHONE_SP = 12f

/** [KhTheme] caption, but never under 12 sp (the phone scale would make it 11.9). */
@Composable
internal fun detailCaption(): TextStyle {
    val caption = KhTheme.typography.caption
    return if (caption.fontSize.value < MIN_PHONE_SP) caption.copy(fontSize = MIN_PHONE_SP.sp, lineHeight = 16.sp) else caption
}

/** Height of the TV header for a screen [screenHeight] tall: most of the screen, so the first episodes peek below. */
internal fun detailHeaderHeight(screenHeight: Dp): Dp = (screenHeight * 0.84f).coerceIn(340.dp, 520.dp)

/** Everything the header shows that is not the title itself. */
internal class HeaderActions(
    val action: MainAction,
    val playFocus: FocusRequester,
    val inList: Boolean,
    val watched: Boolean,
    val onToggleList: () -> Unit,
    val onToggleWatched: () -> Unit,
    val download: (@Composable (Modifier, Boolean) -> Unit)?,
)

@Composable
internal fun DetailHeader(
    detail: TitleDetail,
    facts: MetaFacts,
    mark: TitleMark?,
    actions: HeaderActions,
    headerHeight: Dp,
) {
    if (isTv()) TvHeader(detail, facts, mark, actions, headerHeight) else PhoneHeader(detail, facts, mark, actions)
}

@Composable
private fun TvHeader(detail: TitleDetail, facts: MetaFacts, mark: TitleMark?, actions: HeaderActions, height: Dp) {
    val card = detail.card
    // On a short screen the tagline and the tall logo are dropped so nothing runs off the top.
    val roomy = height >= 440.dp
    Box(Modifier.fillMaxWidth().heightIn(min = height)) {
        HeroBackdrop(detail.backdrop ?: card.artwork, card.name, Modifier.matchParentSize())
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = edge() + 8.dp, top = 48.dp, bottom = KhTheme.spacing.focusBleed + 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TitleLogo(detail.logo, card.name, if (roomy) 140.dp else 96.dp, 420.dp, KhTheme.typography.display)
                MetaLine(facts)
                SubtitleLine(detail)
                MarkNote(mark)
                if (detail.description.isNotBlank()) {
                    Text(detail.description, style = KhTheme.typography.synopsis, color = KhColors.TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                val tagline = detail.tagline?.trim()?.takeIf { roomy && it.isNotEmpty() }
                if (tagline != null) {
                    Text(tagline, style = KhTheme.typography.meta, color = KhColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayButton(actions, Modifier.widthIn(min = 200.dp))
                SavedActions(
                    actions.inList, actions.watched, actions.onToggleList, actions.onToggleWatched,
                    buttonWidth = 150.dp, download = actions.download,
                )
            }
        }
    }
}

@Composable
private fun PhoneHeader(detail: TitleDetail, facts: MetaFacts, mark: TitleMark?, actions: HeaderActions) {
    val card = detail.card
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().heightIn(max = 360.dp).aspectRatio(16f / 9f)) {
            Artwork(detail.backdrop ?: card.artwork, card.name, Modifier.fillMaxSize(), fadeMillis = 200, titleOnFailure = false)
            Box(Modifier.fillMaxWidth().height(96.dp).background(Brush.verticalGradient(listOf(KhColors.ScrimMedium, Color.Transparent))))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.45f).background(KhGradients.BottomFade))
        }
        // On a tablet the column stops at 640 dp instead of stretching the buttons across the screen.
        Column(
            Modifier.padding(horizontal = edge()).widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TitleLogo(detail.logo, card.name, 72.dp, 260.dp, KhTheme.typography.headline)
            MetaLine(facts)
            SubtitleLine(detail)
            MarkNote(mark)
            PlayButton(actions, Modifier.fillMaxWidth().padding(top = 6.dp), height = 48.dp)
            SavedActions(
                actions.inList, actions.watched, actions.onToggleList, actions.onToggleWatched,
                download = actions.download,
            )
            if (detail.description.isNotBlank()) ExpandableSynopsis(detail.description)
        }
    }
}

/** Play, Resume S2 E5, or the grey "Coming soon". Focus starts here. */
@Composable
private fun PlayButton(actions: HeaderActions, modifier: Modifier, height: Dp = 44.dp) {
    val action = actions.action
    KhActionButton(
        action.label,
        onClick = action.play,
        primary = !action.dimmed,
        icon = if (action.dimmed) null else Icons.Filled.PlayArrow,
        height = height,
        modifier = modifier.alpha(if (action.dimmed) 0.7f else 1f).focusRequester(actions.playFocus),
    )
}

/** The line under a title's facts that says why it may not play: the release date, or that every server failed. */
@Composable
internal fun MarkNote(mark: TitleMark?, modifier: Modifier = Modifier) {
    val text = when (mark) {
        is TitleMark.ComingSoon -> "Coming ${ReleaseDates.long(mark.date) ?: mark.date}"
        TitleMark.Unavailable -> "Not available right now \u2014 every server failed. Try again later."
        null -> return
    }
    Text(text, style = detailCaption(), color = KhColors.TextPrimary, modifier = modifier)
}

/**
 * The title as its logo when the source has one (a transparent PNG), as text until the logo has
 * loaded and when it fails. While a logo is on its way the box keeps [height], so the lines below do not jump.
 */
@Composable
internal fun TitleLogo(logo: String?, name: String, height: Dp, maxWidth: Dp, textStyle: TextStyle, modifier: Modifier = Modifier) {
    var shown by remember(logo) { mutableStateOf(false) }
    var failed by remember(logo) { mutableStateOf(logo.isNullOrBlank()) }
    if (failed) {
        Text(name, style = textStyle, color = KhColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = modifier)
        return
    }
    Box(
        modifier.height(height).widthIn(max = maxWidth).fillMaxWidth().semantics { contentDescription = name },
        contentAlignment = Alignment.BottomStart,
    ) {
        if (!shown) Text(name, style = textStyle, color = KhColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val context = LocalContext.current
        val request = remember(logo) { ImageRequest.Builder(context).data(logo).crossfade(KhMotion.PopoverMillis).build() }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            alignment = Alignment.BottomStart,
            onSuccess = { shown = true },
            onError = { failed = true },
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (shown) 1f else 0f },
        )
    }
}

/**
 * Year, a small outlined rating badge, length, "3 Seasons" or "24 Episodes", and the score with a
 * star. A missing fact is left out, never shown as a blank.
 */
@Composable
internal fun MetaLine(facts: MetaFacts, modifier: Modifier = Modifier) {
    if (facts.isEmpty) return
    val style = KhTheme.typography.meta
    val parts = buildList<@Composable () -> Unit> {
        facts.year?.let { add { Text(it, style = style, color = KhColors.TextPrimary, maxLines = 1) } }
        facts.certification?.let { add { CertificationBadge(it, style) } }
        facts.runtime?.let { add { Text(it, style = style, color = KhColors.TextPrimary, maxLines = 1) } }
        facts.extent?.let { add { Text(it, style = style, color = KhColors.TextPrimary, maxLines = 1) } }
        facts.score?.let { add { Score(it, style) } }
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        parts.forEachIndexed { index, part ->
            if (index > 0) Text("\u00B7", style = style, color = KhColors.TextSecondary, modifier = Modifier.padding(horizontal = 6.dp))
            part()
        }
    }
}

@Composable
private fun CertificationBadge(text: String, base: TextStyle) {
    // 86% of the facts text, but never under 12 sp.
    val size = maxOf(base.fontSize.value * 0.86f, MIN_PHONE_SP)
    Text(
        text,
        style = base.copy(fontSize = size.sp, lineHeight = TextUnit.Unspecified),
        color = KhColors.TextPrimary,
        maxLines = 1,
        modifier = Modifier
            .semantics { contentDescription = "Rated $text" }
            .border(1.dp, KhColors.TextSecondary.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
private fun Score(score: String, style: TextStyle) {
    Row(
        Modifier.semantics(mergeDescendants = true) { contentDescription = "Score $score out of 10" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = KhColors.TextPrimary, modifier = Modifier.size(16.dp))
        Text(" $score", style = style, color = KhColors.TextPrimary, maxLines = 1)
    }
}

/** "Drama · Thriller · Crime", or kind, country and status when the source has no genres. */
@Composable
internal fun SubtitleLine(detail: TitleDetail, modifier: Modifier = Modifier) {
    val parts = subtitleParts(detail)
    if (parts.isEmpty()) return
    Text(joinDots(parts), style = KhTheme.typography.meta, color = KhColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** The phone synopsis: three lines, and a "More" toggle only when there is more to show. */
@Composable
private fun ExpandableSynopsis(text: String) {
    var expanded by remember(text) { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }
    KhFocusable(
        onClick = { expanded = !expanded },
        enabled = overflows || expanded,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(tween(KhMotion.CrossfadeMillis, easing = KhMotion.EaseOutStrong))
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
        shape = KhTheme.shapes.button,
        scaleAndLift = false,
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            Text(
                text,
                style = KhTheme.typography.synopsis,
                color = KhColors.TextSecondary,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
            )
            if (overflows || expanded) {
                Text(if (expanded) "Less" else "More", style = detailCaption(), color = KhColors.TextPrimary, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
