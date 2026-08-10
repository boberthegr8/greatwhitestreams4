package com.gwstreams.tv

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import coil.imageLoader
import com.gwstreams.tv.data.CrashReporter
import com.gwstreams.tv.ui.TvContentItem
import com.gwstreams.tv.ui.TvSection
import com.gwstreams.tv.ui.TvViewModel
import com.gwstreams.tv.ui.live.TvLiveScreen
import com.gwstreams.tv.ui.live.TvLoginScreen
import com.gwstreams.tv.ui.player.TvPlayerScreen
import com.gwstreams.tv.ui.settings.TvSettingsScreen
import com.gwstreams.tv.ui.theme.GWSTvTheme
import com.gwstreams.tv.ui.vod.TvSeriesDetailsScreen
import kotlinx.coroutines.delay

private sealed interface TvScreen {
    data object Login : TvScreen
    data object Browse : TvScreen
    data class Player(val item: TvContentItem) : TvScreen
    data class SeriesDetails(val item: TvContentItem) : TvScreen
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
class TvActivity : ComponentActivity() {
    private var isLivePlayerMode = false

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isLivePlayerMode) {
            enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().build())
        }
    }

    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFormat(android.graphics.PixelFormat.TRANSLUCENT)

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            CrashReporter.recordUncaughtException(applicationContext, thread, throwable)
            restartAfterCrash()
            defaultHandler?.let {
                runCatching { it.uncaughtException(thread, throwable) }
            }
            android.os.Process.killProcess(android.os.Process.myPid())
            kotlin.system.exitProcess(10)
        }

        setContent {
            GWSTvTheme {
                CompositionLocalProvider(androidx.compose.foundation.LocalOverscrollConfiguration provides null) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        val ctx = LocalContext.current
                        val vm: TvViewModel = viewModel()
                        val state by vm.state.collectAsState()

                        var isConnected by remember { mutableStateOf(true) }
                        var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
                        var isIdle by remember { mutableStateOf(false) }
                        var screen by remember { mutableStateOf<TvScreen>(TvScreen.Login) }
                        var playerReturnScreen by remember { mutableStateOf<TvScreen>(TvScreen.Browse) }
                        var playerReturnSection by remember { mutableStateOf(TvSection.LIVE) }

                        LaunchedEffect(Unit) {
                            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                            val callback = object : ConnectivityManager.NetworkCallback() {
                                override fun onAvailable(network: Network) {
                                    isConnected = true
                                }

                                override fun onLost(network: Network) {
                                    isConnected = false
                                }
                            }
                            cm.registerNetworkCallback(NetworkRequest.Builder().build(), callback)

                            while (true) {
                                delay(1000)
                                isIdle = (System.currentTimeMillis() - lastInteractionTime) > (5 * 60 * 1000)
                            }
                        }

                        val currentScreen = screen
                        LaunchedEffect(Unit) {
                            vm.checkForAppUpdate()
                        }
                        LaunchedEffect(currentScreen) {
                            val isLive = currentScreen is TvScreen.Player && currentScreen.item.section == TvSection.LIVE
                            isLivePlayerMode = isLive
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                setPictureInPictureParams(
                                    android.app.PictureInPictureParams.Builder()
                                        .setAutoEnterEnabled(isLive)
                                        .build()
                                )
                            }
                        }

                        LaunchedEffect(state.loggedIn, state.appUpdate.isDialogVisible) {
                            if (state.loggedIn && screen is TvScreen.Login && !state.appUpdate.isDialogVisible) {
                                screen = if (state.settings.cableBoxMode && state.items.isNotEmpty()) {
                                    val lastChannel = state.items.find { it.id == state.lastPlayedChannelId } ?: state.items.first()
                                    playerReturnScreen = TvScreen.Browse
                                    TvScreen.Player(lastChannel)
                                } else {
                                    TvScreen.Browse
                                }
                            }
                        }

                        var nowSec by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
                        LaunchedEffect(Unit) {
                            while (true) {
                                delay(30_000)
                                nowSec = System.currentTimeMillis() / 1000
                            }
                        }

                        Box(
                            Modifier
                                .fillMaxSize()
                                .onPreviewKeyEvent {
                                    lastInteractionTime = System.currentTimeMillis()
                                    false
                                }
                        ) {
                            when (val activeScreen = screen) {
                                TvScreen.Login -> {
                                    TvLoginScreen(
                                        vm = vm,
                                        onLoggedIn = { }
                                    )
                                }

                                TvScreen.Browse -> {
                                    if (state.section == TvSection.SETTINGS) {
                                        BackHandler { vm.selectSection(TvSection.LIVE) }
                                        TvSettingsScreen(
                                            vm = vm,
                                            onLogout = { screen = TvScreen.Login }
                                        )
                                    } else {
                                        var backPressedTime by remember { mutableLongStateOf(0L) }
                                        BackHandler {
                                            if (state.section != TvSection.LIVE) {
                                                vm.selectSection(TvSection.LIVE)
                                            } else {
                                                val now = System.currentTimeMillis()
                                                if (now - backPressedTime < 2000) {
                                                    (ctx as? android.app.Activity)?.finish()
                                                } else {
                                                    backPressedTime = now
                                                    android.widget.Toast.makeText(
                                                        ctx,
                                                        "Press back again to exit",
                                                        android.widget.Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        }
                                        TvLiveScreen(
                                            vm = vm,
                                            nowSec = nowSec,
                                            onPlay = {
                                                vm.onPlayItem(it)
                                                if (it.section == TvSection.SERIES) {
                                                    screen = TvScreen.SeriesDetails(it)
                                                } else {
                                                    playerReturnScreen = TvScreen.Browse
                                                    playerReturnSection = state.section
                                                    screen = TvScreen.Player(it)
                                                }
                                            }
                                        )
                                    }
                                }

                                is TvScreen.SeriesDetails -> {
                                    TvSeriesDetailsScreen(
                                        vm = vm,
                                        seriesId = activeScreen.item.id,
                                        seriesName = activeScreen.item.title,
                                        onPlay = { selectedEpisode ->
                                            playerReturnScreen = activeScreen
                                            screen = TvScreen.Player(selectedEpisode)
                                        },
                                        onBack = { screen = TvScreen.Browse }
                                    )
                                }

                                is TvScreen.Player -> {
                                    TvPlayerScreen(
                                        item = activeScreen.item,
                                        items = state.items,
                                        bufferSeconds = state.settings.bufferSeconds,
                                        isFavorite = activeScreen.item.id in state.favorites,
                                        onBack = {
                                            if (state.section != playerReturnSection) {
                                                vm.selectSection(playerReturnSection)
                                            }
                                            screen = playerReturnScreen
                                        },
                                        onZap = { nextItem ->
                                            if (nextItem != null) {
                                                vm.onPlayItem(nextItem)
                                                screen = TvScreen.Player(nextItem)
                                            }
                                        },
                                        onToggleFavorite = vm::toggleFavorite
                                    )
                                }
                            }

                            if (isIdle && !isLivePlayerMode) {
                                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))
                            }
                            if (!isConnected) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(Color.Red)
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No Internet Connection", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun restartAfterCrash() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        } ?: android.content.Intent(this, TvActivity::class.java).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            1001,
            launchIntent,
            android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
        alarmManager?.setExact(
            android.app.AlarmManager.RTC,
            System.currentTimeMillis() + 500,
            pendingIntent
        )
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            applicationContext.imageLoader.memoryCache?.clear()
            System.gc()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        }
    }
}
