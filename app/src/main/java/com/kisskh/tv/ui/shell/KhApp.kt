package com.kisskh.tv.ui.shell

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kisskh.tv.api.media.Library
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.NavState
import com.kisskh.tv.ui.RootTab
import com.kisskh.tv.ui.catalog.CatalogScreen
import com.kisskh.tv.ui.catalog.CountriesScreen
import com.kisskh.tv.ui.components.FocusSaver
import com.kisskh.tv.ui.components.KhScrollDefaults
import com.kisskh.tv.ui.components.LocalFocusSaver
import com.kisskh.tv.ui.components.rememberIsTelevision
import com.kisskh.tv.ui.detail.DetailScreen
import com.kisskh.tv.ui.home.HomeScreen
import com.kisskh.tv.ui.labelFor
import com.kisskh.tv.ui.tabs
import com.kisskh.tv.ui.media.MediaBrowseScreen
import com.kisskh.tv.ui.media.MediaDetailScreen
import com.kisskh.tv.ui.media.MediaHomeScreen
import com.kisskh.tv.ui.media.MediaSearchScreen
import com.kisskh.tv.ui.media.TokenScreen
import com.kisskh.tv.ui.search.SearchScreen
import com.kisskh.tv.player.EmbedPlayer
import com.kisskh.tv.player.StreamPlayerScreen
import com.kisskh.tv.player.WatchScreen
import com.kisskh.tv.ui.shelf.ShelfBrowse
import com.kisskh.tv.ui.shelf.ShelfDetail
import com.kisskh.tv.ui.shelf.ShelfHome
import com.kisskh.tv.ui.shelf.ShelfSearch
import com.kisskh.tv.ui.theme.FormFactor
import com.kisskh.tv.ui.theme.KhColors
import com.kisskh.tv.ui.theme.KhMotion
import com.kisskh.tv.ui.theme.KhTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * TV: a collapsed icon rail that expands over the page. Phone: a bottom bar, and on Home a top bar
 * drawn by the home screen. Playback is full screen on both.
 *
 * Back, in order: leave the rail, close whatever the screen has open (the screen handles that
 * first), go up one screen, then exit.
 */
@Composable
fun KhApp(
    vm: AppViewModel,
    onExit: () -> Unit,
    player: @Composable (Dest.Player) -> Unit,
) {
    val nav by vm.nav.collectAsStateWithLifecycle()
    val current = nav.current
    val tv = KhTheme.formFactor == FormFactor.TV
    val playing = current is Dest.Player || current is Dest.EmbedPlayer || current is Dest.StreamPlayer || current is Dest.Watch
    val showRail = tv && !playing
    val focusManager = LocalFocusManager.current
    val saver = remember { FocusSaver() }
    var railFocused by remember { mutableStateOf(false) }
    LaunchedEffect(showRail) { if (!showRail) railFocused = false }
    LockPlaybackLandscape(playing)

    val scope = rememberCoroutineScope()
    val enterContent: () -> Boolean = {
        val screenEnter = saver.enter
        if (screenEnter != null && screenEnter()) {
            scope.launch {
                delay(300)
                if (railFocused) focusManager.moveFocus(FocusDirection.Right)
            }
            true
        } else {
            focusManager.moveFocus(FocusDirection.Right)
        }
    }

    BackHandler {
        if (showRail && railFocused && enterContent()) return@BackHandler
        if (!vm.onBack()) onExit()
    }

    KhScrollDefaults {
        CompositionLocalProvider(LocalFocusSaver provides saver) {
            if (tv) {
                TvScaffold(
                    vm = vm,
                    nav = nav,
                    showRail = showRail,
                    railFocused = railFocused,
                    onRailFocus = { railFocused = it },
                    onEnterContent = enterContent,
                    onSelectTab = { tab ->
                        saver.save?.invoke()
                        vm.selectTab(tab)
                    },
                    onSelectLibrary = { library ->
                        saver.save?.invoke()
                        vm.selectLibrary(library)
                    },
                    player = player,
                )
            } else {
                PhoneScaffold(
                    vm = vm,
                    nav = nav,
                    onSelectTab = { tab ->
                        saver.save?.invoke()
                        vm.selectTab(tab)
                    },
                    onSelectLibrary = { library ->
                        saver.save?.invoke()
                        vm.selectLibrary(library)
                    },
                    player = player,
                )
            }
        }
    }
}

/** On a phone, playback is landscape. A television is already landscape, so it is left alone. */
@Composable
private fun LockPlaybackLandscape(playing: Boolean) {
    val television = rememberIsTelevision()
    val activity = LocalContext.current as? Activity
    SideEffect {
        if (television || activity == null) return@SideEffect
        activity.requestedOrientation = if (playing) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

@Composable
private fun TvScaffold(
    vm: AppViewModel,
    nav: NavState,
    showRail: Boolean,
    railFocused: Boolean,
    onRailFocus: (Boolean) -> Unit,
    onEnterContent: () -> Boolean,
    onSelectTab: (RootTab) -> Unit,
    onSelectLibrary: (Library) -> Unit,
    player: @Composable (Dest.Player) -> Unit,
) {
    val scrim by animateFloatAsState(
        targetValue = if (railFocused) 1f else 0f,
        animationSpec = tween(KhMotion.RailExpandMillis, easing = KhMotion.EaseOutStrong),
        label = "railScrim",
    )
    Box(Modifier.fillMaxSize().background(KhColors.Background)) {
        ScreenBody(
            vm = vm,
            nav = nav,
            player = player,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = if (showRail) RailCollapsedWidth else 0.dp),
        )
        if (showRail && scrim > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = scrim }
                    .background(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = 0.90f),
                            0.55f to Color.Transparent,
                        ),
                    ),
            )
        }
        if (showRail) {
            NavRail(
                active = nav.activeTab,
                tabs = nav.library.tabs(),
                library = nav.library,
                labelOf = { it.labelFor(nav.library) },
                onSelect = onSelectTab,
                onSelectLibrary = onSelectLibrary,
                onFocusChanged = onRailFocus,
                onEnterContent = onEnterContent,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
    }
}

@Composable
private fun PhoneScaffold(
    vm: AppViewModel,
    nav: NavState,
    onSelectTab: (RootTab) -> Unit,
    onSelectLibrary: (Library) -> Unit,
    player: @Composable (Dest.Player) -> Unit,
) {
    val current = nav.current
    val showNav = current !is Dest.Player && current !is Dest.Detail && current !is Dest.TitlePage &&
        current !is Dest.EmbedPlayer && current !is Dest.StreamPlayer && current !is Dest.MediaDetail &&
        current !is Dest.Watch
    val padStatus = current !is Dest.Home && current !is Dest.Detail && current !is Dest.TitlePage &&
        current !is Dest.Player && current !is Dest.EmbedPlayer && current !is Dest.StreamPlayer &&
        current !is Dest.Watch
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottom = when {
        !showNav && (current is Dest.Player || current is Dest.EmbedPlayer || current is Dest.StreamPlayer || current is Dest.Watch) -> 0.dp
        showNav -> PhoneNavHeight + SourceBarHeight + navInset
        else -> navInset
    }
    Box(Modifier.fillMaxSize().background(KhColors.Background)) {
        ScreenBody(
            vm = vm,
            nav = nav,
            player = player,
            modifier = Modifier
                .fillMaxSize()
                .then(if (padStatus) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier)
                .padding(bottom = bottom),
        )
        if (showNav) {
            Column(Modifier.align(Alignment.BottomCenter)) {
                SourceBar(library = nav.library, onSelect = onSelectLibrary)
                KhBottomNav(
                    active = nav.activeTab,
                    tabs = nav.library.tabs(),
                    labelOf = { it.labelFor(nav.library) },
                    onSelect = onSelectTab,
                )
            }
        }
    }
}

@Composable
private fun ScreenBody(
    vm: AppViewModel,
    nav: NavState,
    player: @Composable (Dest.Player) -> Unit,
    modifier: Modifier,
) {
    key(screenKey(nav)) {
        Box(modifier) {
            when (val d = nav.current) {
                is Dest.Home -> ShelfHome(vm, nav.library)
                is Dest.Search -> ShelfSearch(vm, nav.library)
                is Dest.Movies -> ShelfBrowse(vm, nav.library, com.kisskh.tv.api.media.MediaKind.MOVIE)
                is Dest.Series -> ShelfBrowse(vm, nav.library, com.kisskh.tv.api.media.MediaKind.TV)
                is Dest.Shelf -> ShelfBrowse(vm, nav.library, d.kind)
                is Dest.Catalog -> CatalogScreen(vm, d)
                is Dest.Countries -> CountriesScreen(vm, d)
                is Dest.Detail -> ShelfDetail(vm, nav.library, Dest.TitlePage(d.dramaId.toString(), com.kisskh.tv.api.media.MediaKind.TV))
                is Dest.TitlePage -> ShelfDetail(vm, nav.library, d)
                is Dest.Player -> player(d)
                is Dest.MediaBrowse -> MediaBrowseScreen(vm, d)
                is Dest.MediaDetail -> MediaDetailScreen(vm, nav.library, d)
                is Dest.EmbedPlayer -> EmbedPlayer(vm, d)
                is Dest.StreamPlayer -> StreamPlayerScreen(vm, d)
                is Dest.Watch -> WatchScreen(vm, d)
                is Dest.Token -> TokenScreen(vm)
            }
        }
    }
}

/** Same key = same screen, even when its filters or focus change. A different key = a new screen. */
private fun screenKey(nav: NavState): String {
    val body = when (val d = nav.current) {
        is Dest.Home -> "home"
        is Dest.Search -> "search"
        is Dest.Catalog -> d.pagerKey
        is Dest.Countries -> "countries"
        is Dest.Detail -> "detail:${d.dramaId}:${nav.stack.size}"
        is Dest.Player -> "player"
        is Dest.Shelf -> "shelf:${d.kind}"
        is Dest.TitlePage -> "title:${d.id}:${nav.stack.size}"
        is Dest.MediaBrowse -> "browse:${d.browseKey}"
        is Dest.MediaDetail -> "mdetail:${d.kind}:${d.tmdbId}:${nav.stack.size}"
        is Dest.EmbedPlayer -> "embed:${d.tmdbId}:${d.season}:${d.episode}"
        is Dest.StreamPlayer -> "stream:${d.progressKey}"
        is Dest.Watch -> "watch:${d.progressKey}"
        is Dest.Token -> "token"
    }
    return "${nav.library.name}:$body"
}
