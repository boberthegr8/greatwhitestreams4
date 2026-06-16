package com.gwstreams.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gwstreams.app.data.model.Episode
import com.gwstreams.app.data.repo.Session
import com.gwstreams.app.ui.detail.DetailSheet
import com.gwstreams.app.ui.downloads.DownloadsScreen
import com.gwstreams.app.ui.downloads.DownloadsViewModel
import com.gwstreams.app.ui.home.ContentItem
import com.gwstreams.app.ui.home.HomeScreen
import com.gwstreams.app.ui.home.HomeViewModel
import com.gwstreams.app.ui.home.Tab
import com.gwstreams.app.ui.login.LoginScreen
import com.gwstreams.app.ui.player.PlayerScreen
import com.gwstreams.app.ui.player.PlayTarget
import com.gwstreams.app.ui.theme.GWStreamsTheme
import kotlinx.coroutines.launch

private sealed interface Screen {
    data object Login : Screen
    data object Home : Screen
    data object Downloads : Screen
    data class Player(val target: PlayTarget) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            GWStreamsTheme {
                Surface(Modifier.fillMaxSize()) {
                    val context = LocalContext.current
                    val scope = rememberCoroutineScope()
                    var screen by remember { mutableStateOf<Screen>(Screen.Login) }
                    var detailItem by remember { mutableStateOf<ContentItem?>(null) }
                    val homeVm: HomeViewModel = viewModel()
                    val downloadsVm: DownloadsViewModel = viewModel()

                    val notifLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { }
                    LaunchedEffect(screen) {
                        if (screen == Screen.Home && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val granted = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!granted) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }

                    fun playLive(item: ContentItem) {
                        homeVm.recordWatch(item)
                        homeVm.prefetchAround(item)
                        screen = Screen.Player(PlayTarget.fromItem(item))
                    }

                    // Play a movie/episode locally if downloaded, else stream.
                    fun playOrStream(target: PlayTarget, id: Int, kind: Tab) {
                        scope.launch {
                            val local = downloadsVm.localUriFor(id, kind)
                            screen = Screen.Player(
                                if (local != null)
                                    PlayTarget.fromLocal(target.title, local, id)
                                else target
                            )
                        }
                    }

                    when (val s = screen) {
                        Screen.Login -> LoginScreen(onLoggedIn = { screen = Screen.Home })

                        Screen.Home -> {
                            HomeScreen(
                                onOpen = { item ->
                                    when (item.kind) {
                                        Tab.LIVE -> playLive(item)
                                        else -> detailItem = item
                                    }
                                },
                                onOpenWatch = { w -> screen = Screen.Player(PlayTarget.fromWatch(w)) },
                                onOpenDownloads = { screen = Screen.Downloads },
                                onLastChannel = {
                                    val lastId = homeVm.lastChannelId()
                                    val item = homeVm.state.value.items.firstOrNull { it.id == lastId }
                                    if (item != null) playLive(item)
                                },
                                vm = homeVm
                            )

                            detailItem?.let { item ->
                                DetailSheet(
                                    item = item,
                                    onPlayMovie = { m ->
                                        homeVm.recordWatch(m)
                                        detailItem = null
                                        playOrStream(PlayTarget.fromItem(m), m.id, Tab.MOVIES)
                                    },
                                    onPlayEpisode = { ep: Episode ->
                                        homeVm.recordWatch(item)
                                        detailItem = null
                                        val epId = ep.id.toIntOrNull() ?: item.id
                                        val streamTarget = PlayTarget(
                                            title = ep.title ?: item.title,
                                            url = Session.seriesUrl(epId, ep.containerExtension),
                                            streamId = epId,
                                            isLive = false
                                        )
                                        playOrStream(streamTarget, epId, Tab.SERIES)
                                    },
                                    onDownloadMovie = { m ->
                                        downloadsVm.downloadItem(m)
                                    },
                                    onDownloadEpisode = { ep: Episode ->
                                        val epId = ep.id.toIntOrNull() ?: item.id
                                        downloadsVm.download(
                                            id = epId, kind = Tab.SERIES,
                                            title = ep.title ?: item.title,
                                            image = item.image, ext = ep.containerExtension
                                        )
                                    },
                                    onDismiss = { detailItem = null }
                                )
                            }
                        }

                        Screen.Downloads -> DownloadsScreen(
                            onBack = { screen = Screen.Home },
                            onPlayLocal = { entry ->
                                entry.localUri?.let { uri ->
                                    screen = Screen.Player(PlayTarget.fromLocal(entry.title, uri, entry.id))
                                }
                            },
                            vm = downloadsVm
                        )

                        is Screen.Player -> PlayerScreen(
                            target = s.target,
                            homeVm = homeVm,
                            onBack = { screen = Screen.Home }
                        )
                    }
                }
            }
        }
    }
}
