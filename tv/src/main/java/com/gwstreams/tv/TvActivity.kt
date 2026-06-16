package com.gwstreams.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.gwstreams.tv.ui.TvContentItem
import com.gwstreams.tv.ui.TvSection
import com.gwstreams.tv.ui.TvViewModel
import com.gwstreams.tv.ui.live.TvLiveScreen
import com.gwstreams.tv.ui.live.TvLoginScreen
import com.gwstreams.tv.ui.player.TvPlayerScreen
import com.gwstreams.tv.ui.settings.TvSettingsScreen
import com.gwstreams.tv.ui.theme.GWSTvTheme
import kotlinx.coroutines.delay

private sealed interface TvScreen {
    data object Login : TvScreen
    data object Browse : TvScreen
    data class Player(val item: TvContentItem) : TvScreen
}

class TvActivity : ComponentActivity() {
    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GWSTvTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val vm: TvViewModel = viewModel()
                    var screen by remember { mutableStateOf<TvScreen>(TvScreen.Login) }

                    // Ticking clock for live progress bars.
                    var nowSec by remember { mutableStateOf(System.currentTimeMillis() / 1000) }
                    LaunchedEffect(Unit) {
                        while (true) { delay(30_000); nowSec = System.currentTimeMillis() / 1000 }
                    }

                    val state by vm.state.collectAsState()

                    // Auto-login (from saved creds) flips loggedIn in state — react to it.
                    LaunchedEffect(state.loggedIn) {
                        if (state.loggedIn && screen is TvScreen.Login) {
                            screen = TvScreen.Browse
                        }
                    }

                    when (val s = screen) {
                        TvScreen.Login -> TvLoginScreen(
                            vm = vm,
                            onLoggedIn = { screen = TvScreen.Browse }
                        )

                        TvScreen.Browse -> {
                            if (state.section == TvSection.SETTINGS) {
                                BackHandler { vm.selectSection(TvSection.LIVE) }
                                TvSettingsScreen(
                                    vm = vm,
                                    onLogout = { screen = TvScreen.Login }
                                )
                            } else {
                                TvLiveScreen(
                                    vm = vm,
                                    nowSec = nowSec,
                                    onPlay = { screen = TvScreen.Player(it) }
                                )
                            }
                        }

                        is TvScreen.Player -> TvPlayerScreen(
                            item = s.item,
                            bufferSeconds = state.settings.bufferSeconds,
                            onBack = { screen = TvScreen.Browse }
                        )
                    }
                }
            }
        }
    }
}
