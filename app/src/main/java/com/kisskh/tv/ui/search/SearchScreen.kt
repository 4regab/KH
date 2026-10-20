@file:OptIn(ExperimentalComposeUiApi::class)

package com.kisskh.tv.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.kisskh.tv.R
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.FocusKeys
import com.kisskh.tv.ui.UiState
import com.kisskh.tv.ui.catalog.GridScreen
import com.kisskh.tv.api.Show
import com.kisskh.tv.ui.components.EmptyState
import com.kisskh.tv.ui.components.ErrorState
import com.kisskh.tv.ui.components.FocusTracker
import com.kisskh.tv.ui.components.FocusedTitleLine
import com.kisskh.tv.ui.components.LoadingState
import com.kisskh.tv.ui.components.rememberFocusedYear
import com.kisskh.tv.ui.components.RegisterFocusSaver
import com.kisskh.tv.ui.components.focusIfRestoring
import com.kisskh.tv.ui.components.rememberFocusRestore
import com.kisskh.tv.ui.components.revealIndex
import com.kisskh.tv.ui.components.screenStart
import com.kisskh.tv.ui.theme.FormFactor
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhFocusable
import com.kisskh.tv.ui.theme.KhTheme
import com.kisskh.tv.ui.theme.khFocusBorder
import com.kisskh.tv.ui.theme.rememberKhFocusProgress

private const val SEARCH_FIELD = "search:field"
private const val SEARCH_CLEAR = "search:clear"

/**
 * Search as you type. The text and the answer live in `vm.search`, so leaving for a detail page and
 * coming back finds the same query, the same results and the same focused card.
 *
 * - The search key on the keyboard moves focus into the results (as soon as they arrive).
 * - On a remote, Down moves into the results; Left and Right leave the field once the cursor is at its edge.
 * - "No results" and "the request failed" are different screens; the second has Retry.
 */
@Composable
fun SearchScreen(vm: AppViewModel, dest: Dest.Search) {
    val search = vm.search
    val state by search.state.collectAsStateWithLifecycle()
    // The field keeps its own text so typing never waits on the flow; the controller gets every change.
    var field by remember { mutableStateOf(TextFieldValue(state.query, TextRange(state.query.length))) }

    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = dest.scrollIndex)
    val tracker = remember { FocusTracker() }
    val restore = rememberFocusRestore(
        remember {
            val shown = (state.result as? UiState.Success)?.data.orEmpty()
            dest.focusKey?.takeIf { key -> key == SEARCH_FIELD || shown.any { FocusKeys.show(it.id) == key } } ?: SEARCH_FIELD
        },
    )
    RegisterFocusSaver(tracker, restore) { vm.saveFocus(tracker.key, gridState.firstVisibleItemIndex) }

    val fieldRequester = remember { FocusRequester() }
    val firstResult = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var focusResultsWhenReady by remember { mutableStateOf(false) }
    val tv = KhTheme.formFactor == FormFactor.TV
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    // Back closes the keyboard before it leaves Search.
    BackHandler(enabled = imeOpen) { keyboard?.hide() }

    var focusedShow by remember { mutableStateOf<Show?>(null) }
    val focusedYear = rememberFocusedYear(vm, if (tv) focusedShow?.id else null)

    // The keyboard's search key was pressed: go to the results, now or as soon as they arrive.
    LaunchedEffect(focusResultsWhenReady, state.result) {
        if (!focusResultsWhenReady) return@LaunchedEffect
        when (state.result) {
            is UiState.Success -> {
                focusResultsWhenReady = false
                withFrameNanos { }
                runCatching { firstResult.requestFocus() }
            }
            UiState.Loading -> Unit
            else -> focusResultsWhenReady = false
        }
    }

    // A new query means a new list: start at the top. (Not on first composition, which restores the old scroll.)
    var shownQuery by remember { mutableStateOf(state.query.trim()) }
    LaunchedEffect(state.query.trim()) {
        if (state.query.trim() != shownQuery) {
            shownQuery = state.query.trim()
            gridState.scrollToItem(0)
        }
    }

    // Back from a detail page: make sure the remembered card is on screen so it can take focus.
    LaunchedEffect(Unit) {
        val shown = (state.result as? UiState.Success)?.data.orEmpty()
        val index = shown.indexOfFirst { FocusKeys.show(it.id) == restore.key }
        if (index >= 0) gridState.revealIndex(index)
    }

    Column(Modifier.fillMaxSize().padding(top = KhTheme.spacing.safeVertical)) {
        Column(
            Modifier.padding(start = screenStart(), end = KhTheme.spacing.safeHorizontal),
            verticalArrangement = Arrangement.spacedBy(KhTheme.spacing.s),
        ) {
            Text("Search", style = if (tv) KhTheme.typography.heading else KhTheme.typography.headline)
            SearchField(
                value = field,
                onValueChange = { next ->
                    val textChanged = next.text != field.text
                    field = next
                    if (textChanged) search.setQuery(next.text)
                },
                onClear = {
                    field = TextFieldValue("")
                    search.clear()
                    runCatching { fieldRequester.requestFocus() }
                },
                onSearch = {
                    keyboard?.hide()
                    focusResultsWhenReady = true
                },
                fieldModifier = Modifier
                    .focusIfRestoring(restore, SEARCH_FIELD)
                    .focusRequester(fieldRequester)
                    .onFocusChanged { if (it.isFocused) tracker.key = SEARCH_FIELD },
                clearModifier = Modifier.onFocusChanged { if (it.isFocused) tracker.key = SEARCH_CLEAR },
                pill = !tv,
            )
            if (tv && state.result is UiState.Success) {
                FocusedTitleLine(
                    title = focusedShow?.title,
                    year = focusedYear,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val r = state.result) {
                null -> EmptyState("Search series, movies and anime")
                UiState.Loading -> LoadingState()
                is UiState.Empty -> EmptyState("No results for \u201C${state.query.trim()}\u201D")
                is UiState.Error -> ErrorState(r.message, onRetry = { search.retry() })
                is UiState.Success -> GridScreen(
                    items = r.data,
                    state = gridState,
                    restore = restore,
                    tracker = tracker,
                    onOpen = { show ->
                        vm.saveFocus(FocusKeys.show(show.id), gridState.firstVisibleItemIndex)
                        vm.push(Dest.Detail(show.id))
                    },
                    // The search answer has no type field, so no type is shown on the cards.
                    firstItemRequester = firstResult,
                    onFocusedShow = { focusedShow = it },
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    fieldModifier: Modifier,
    clearModifier: Modifier,
    pill: Boolean,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val progress = rememberKhFocusProgress(focused)
    val focusManager = LocalFocusManager.current
    val shape = if (pill) KhTheme.shapes.chip else KhTheme.shapes.field

    Row(
        Modifier.padding(KhTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KhTheme.spacing.s),
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(if (pill) 48.dp else 52.dp)
                .then(
                    if (pill && !focused) {
                        Modifier.border(1.dp, KhColors.ChipOutline, shape)
                    } else {
                        Modifier
                    },
                )
                .khFocusBorder({ progress.value }, shape)
                .clip(shape)
                .background(KhColors.Surface)
                .padding(horizontal = if (pill) 18.dp else KhTheme.spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_nav_search), contentDescription = null, tint = KhColors.TextSecondary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(KhTheme.spacing.s + 4.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.text.isEmpty()) {
                    Text("Search titles", style = KhTheme.typography.body, color = KhColors.TextSecondary)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = KhTheme.typography.body.copy(color = KhColors.TextPrimary),
                    cursorBrush = SolidColor(KhColors.TextPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    interactionSource = interaction,
                    modifier = fieldModifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Search" }
                        .onPreviewKeyEvent { e ->
                            // A text field swallows the arrow keys for the cursor. Let them also leave the field.
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val sel = value.selection
                            when (e.key) {
                                Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                                Key.DirectionLeft ->
                                    sel.collapsed && sel.start == 0 && focusManager.moveFocus(FocusDirection.Left)
                                Key.DirectionRight ->
                                    sel.collapsed && sel.end == value.text.length && focusManager.moveFocus(FocusDirection.Right)
                                else -> false
                            }
                        },
                )
            }
        }
        if (value.text.isNotEmpty()) {
            KhFocusable(
                onClick = onClear,
                modifier = clearModifier.size(52.dp).semantics { contentDescription = "Clear search" },
                shape = KhTheme.shapes.chip,
                scaleAndLift = false,
            ) { focused ->
                Box(
                    Modifier.fillMaxSize().background(if (focused) KhColors.Elevated else KhColors.Surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Clear, contentDescription = null, tint = KhColors.TextPrimary, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}
