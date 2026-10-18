package com.kisskh.tv.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kisskh.tv.ui.AppViewModel
import com.kisskh.tv.ui.Dest
import com.kisskh.tv.ui.NavState
import com.kisskh.tv.ui.catalog.CatalogScreen
import com.kisskh.tv.ui.catalog.CountriesScreen
import com.kisskh.tv.ui.components.FocusSaver
import com.kisskh.tv.ui.components.KhScrollDefaults
import com.kisskh.tv.ui.components.LocalFocusSaver
import com.kisskh.tv.ui.detail.DetailScreen
import com.kisskh.tv.ui.home.HomeScreen
import com.kisskh.tv.ui.search.SearchScreen
import com.kisskh.tv.ui.theme.KhColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The whole app UI: the nav rail on the left and the current screen next to it.
 * The rail is hidden while a video plays.
 *
 * Back, in this order: leave the nav rail for the content, close what a screen has open (filter
 * list: the screen handles it first), go up one screen (a tab goes to Home), and finally exit.
 */
@Composable
fun KhApp(
    vm: AppViewModel,
    onExit: () -> Unit,
    player: @Composable (Dest.Player) -> Unit,
) {
    val nav by vm.nav.collectAsStateWithLifecycle()
    val current = nav.current
    val showRail = current !is Dest.Player
    val focusManager = LocalFocusManager.current
    val saver = remember { FocusSaver() }
    var railFocused by remember { mutableStateOf(false) }
    LaunchedEffect(showRail) { if (!showRail) railFocused = false }

    val scope = rememberCoroutineScope()
    // Rail to content: the screen puts focus back on its last item. If that item is gone, fall back to the nearest one.
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
        android.util.Log.d("KHdbg", "BackHandler railFocused=$railFocused") // KHDBG
        if (showRail && railFocused && enterContent()) return@BackHandler
        if (!vm.onBack()) onExit()
    }

    KhScrollDefaults {
        CompositionLocalProvider(LocalFocusSaver provides saver) {
            Box(Modifier.fillMaxSize().background(KhColors.Background).onPreviewKeyEvent { android.util.Log.d("KHdbg", "key ${it.nativeKeyEvent.keyCode} act=${it.nativeKeyEvent.action} rep=${it.nativeKeyEvent.repeatCount}"); false }) { // KHDBG
                // A new key per screen: each screen starts with fresh state, and a screen that stays (filters changing) keeps its own.
                key(screenKey(nav)) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(start = if (showRail) railCollapsedWidth() else 0.dp),
                    ) {
                        when (val d = current) {
                            is Dest.Home -> HomeScreen(vm, d)
                            is Dest.Search -> SearchScreen(vm, d)
                            is Dest.Catalog -> CatalogScreen(vm, d)
                            is Dest.Countries -> CountriesScreen(vm, d)
                            is Dest.Detail -> DetailScreen(vm, d)
                            is Dest.Player -> player(d)
                        }
                    }
                }
                if (showRail) {
                    NavRail(
                        active = nav.activeTab,
                        onSelect = { tab ->
                            saver.save?.invoke() // so the screen can be restored when the user comes back to it
                            vm.selectTab(tab)
                        },
                        onFocusChanged = { railFocused = it },
                        onEnterContent = enterContent,
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                }
            }
        }
    }
}

/** Same key = same screen, even when its filters or focus change. A different key = a new screen. */
private fun screenKey(nav: NavState): String = when (val d = nav.current) {
    is Dest.Home -> "home"
    is Dest.Search -> "search"
    is Dest.Catalog -> d.pagerKey
    is Dest.Countries -> "countries"
    is Dest.Detail -> "detail:${d.dramaId}:${nav.stack.size}"
    is Dest.Player -> "player"
}
