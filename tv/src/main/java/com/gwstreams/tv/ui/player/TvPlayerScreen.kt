package com.gwstreams.tv.ui.player

import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.DefaultAnalyticsCollector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.UnrecognizedInputFormatException
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.gwstreams.app.data.repo.Session
import com.gwstreams.app.data.repo.XtreamRepository
import com.gwstreams.tv.ui.TvContentItem
import com.gwstreams.tv.ui.TvSection
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@UnstableApi
@Composable
fun TvPlayerScreen(
    item: TvContentItem,
    items: List<TvContentItem> = emptyList(),
    bufferSeconds: Int,
    onBack: () -> Unit,
    onZap: (TvContentItem?) -> Unit = {}
) {
    val context = LocalContext.current
    val repo = remember { XtreamRepository() }

    var currentBuffer by remember { mutableIntStateOf(bufferSeconds) }
    var isReconnecting by remember { mutableStateOf(false) }
    var reconnectAttempts by remember { mutableIntStateOf(0) }
    var reconnectTrigger by remember { mutableIntStateOf(0) }
    var reconnectDelayMs by remember { mutableLongStateOf(0L) }
    var showBottomOsd by remember { mutableStateOf(false) }
    var osdHideTick by remember { mutableLongStateOf(0L) }
    var focusedOsdControl by remember { mutableStateOf<String?>(null) }
    var focusedChannelId by remember { mutableStateOf<Int?>(null) }
    val rootFocusRequester = remember { FocusRequester() }
    val defaultOsdFocusRequester = remember { FocusRequester() }

    val initialUrl = remember(item) {
        when (item.section) {
            TvSection.LIVE -> Session.liveUrl(item.id)
            TvSection.MOVIES -> Session.vodUrl(item.id, item.containerExt)
            TvSection.SERIES -> Session.seriesUrl(item.id, item.containerExt)
            TvSection.SETTINGS, TvSection.SEARCH -> ""
        }
    }
    val startsLive = item.section == TvSection.LIVE

    var activeUrl by remember(item) { mutableStateOf(initialUrl) }
    var isLivePlayback by remember(item) { mutableStateOf(startsLive) }

    fun bumpOsdTimer() {
        osdHideTick = System.currentTimeMillis()
    }

    fun revealOsd() {
        showBottomOsd = true
        bumpOsdTimer()
    }

    fun requestReconnect(fromPlaybackFailure: Boolean, userInitiated: Boolean = false) {
        if (activeUrl.isBlank()) return
        reconnectDelayMs = when {
            userInitiated -> 0L
            fromPlaybackFailure -> {
                reconnectAttempts += 1
                (1_500L shl (reconnectAttempts - 1).coerceAtMost(3)).coerceAtMost(12_000L)
            }
            else -> 1_500L
        }
        isReconnecting = true
        reconnectTrigger += 1
        revealOsd()
    }

    val exoPlayer = remember(currentBuffer, activeUrl) {
        val configuredBufferMs = (currentBuffer * 1000).coerceAtLeast(35_000)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                3_000,
                configuredBufferMs,
                1_500,
                3_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setTunnelingEnabled(true))
        }
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("GWStreams")
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(20_000)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAnalyticsCollector(DefaultAnalyticsCollector(Clock.DEFAULT))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setSpatializationBehavior(C.SPATIALIZATION_BEHAVIOR_AUTO)
                    .build(),
                true
            )
            .build()
            .apply {
                playbackParameters = PlaybackParameters(1f, 1f)
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                setWakeMode(C.WAKE_MODE_NETWORK)
                setMediaSource(buildMediaSource(mediaSourceFactory, repo, activeUrl, isLivePlayback))
                prepare()
                playWhenReady = true

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        super.onPlaybackStateChanged(playbackState)
                        when (playbackState) {
                            Player.STATE_READY -> {
                                reconnectAttempts = 0
                                isReconnecting = false
                            }
                            Player.STATE_ENDED -> {
                                if (isLivePlayback) {
                                    requestReconnect(fromPlaybackFailure = true)
                                }
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        super.onPlayerError(error)
                        if (error.cause is UnrecognizedInputFormatException) {
                            Toast.makeText(context, "Unsupported stream format for this channel.", Toast.LENGTH_LONG).show()
                            return
                        }
                        requestReconnect(fromPlaybackFailure = true)
                    }
                })
            }
    }

    fun seekBy(deltaMs: Long) {
        val duration = exoPlayer.duration
        val unclamped = exoPlayer.currentPosition + deltaMs
        val bounded = when {
            duration > 0 -> unclamped.coerceIn(0L, duration)
            else -> unclamped.coerceAtLeast(0L)
        }
        exoPlayer.seekTo(bounded)
        revealOsd()
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    LaunchedEffect(item.id, activeUrl) {
        rootFocusRequester.requestFocus()
        revealOsd()
    }

    LaunchedEffect(reconnectTrigger, exoPlayer) {
        if (reconnectTrigger == 0) return@LaunchedEffect
        if (reconnectDelayMs > 0) delay(reconnectDelayMs)
        exoPlayer.stop()
        exoPlayer.setMediaSource(buildPlayerMediaSource(context, repo, activeUrl, isLivePlayback), true)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    LaunchedEffect(showBottomOsd, item.id, activeUrl, isLivePlayback) {
        if (showBottomOsd) {
            delay(75)
            defaultOsdFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(showBottomOsd, osdHideTick) {
        if (showBottomOsd) {
            delay(5_000)
            showBottomOsd = false
            focusedOsdControl = null
            focusedChannelId = null
        }
    }

    BackHandler {
        if (showBottomOsd) {
            showBottomOsd = false
            focusedOsdControl = null
            focusedChannelId = null
        } else {
            onBack()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .onPreviewKeyEvent { event ->
                val nativeEvent = event.nativeKeyEvent
                if (nativeEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false

                bumpOsdTimer()
                val childOsdFocusActive = focusedOsdControl != null || focusedChannelId != null
                when (nativeEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (!showBottomOsd) {
                            revealOsd()
                            return@onPreviewKeyEvent true
                        }
                    }

                    KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_MENU -> {
                        if (!showBottomOsd) {
                            revealOsd()
                            return@onPreviewKeyEvent true
                        }
                    }

                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_ESCAPE -> {
                        if (showBottomOsd) {
                            showBottomOsd = false
                            focusedOsdControl = null
                            focusedChannelId = null
                            return@onPreviewKeyEvent true
                        }
                    }

                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        when {
                            !isLivePlayback && !childOsdFocusActive -> {
                                seekBy(-10_000)
                                return@onPreviewKeyEvent true
                            }
                            isLivePlayback && !showBottomOsd -> {
                                revealOsd()
                                return@onPreviewKeyEvent true
                            }
                        }
                    }

                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        when {
                            !isLivePlayback && !childOsdFocusActive -> {
                                seekBy(10_000)
                                return@onPreviewKeyEvent true
                            }
                            isLivePlayback && !showBottomOsd -> {
                                revealOsd()
                                return@onPreviewKeyEvent true
                            }
                        }
                    }

                    KeyEvent.KEYCODE_CHANNEL_UP,
                    KeyEvent.KEYCODE_PAGE_UP -> {
                        val currentIndex = items.indexOfFirst { it.id == item.id }
                        if (currentIndex != -1 && items.isNotEmpty()) {
                            val nextIndex = (currentIndex - 1 + items.size) % items.size
                            onZap(items[nextIndex])
                        }
                        return@onPreviewKeyEvent true
                    }

                    KeyEvent.KEYCODE_CHANNEL_DOWN,
                    KeyEvent.KEYCODE_PAGE_DOWN -> {
                        val currentIndex = items.indexOfFirst { it.id == item.id }
                        if (currentIndex != -1 && items.isNotEmpty()) {
                            val nextIndex = (currentIndex + 1) % items.size
                            onZap(items[nextIndex])
                        }
                        return@onPreviewKeyEvent true
                    }
                }
                false
            }
            .focusable()
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                }
            },
            update = {
                it.player = exoPlayer
                it.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isReconnecting) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF00BCD4))
                    Spacer(Modifier.height(16.dp))
                    Text("Reconnecting stream…", color = Color.White)
                }
            }
        }

        AnimatedVisibility(
            visible = showBottomOsd,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xDD000000))
                        )
                    )
                    .padding(horizontal = 32.dp, vertical = 24.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.title,
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                shadow = Shadow(color = Color.Black, blurRadius = 4f)
                            ),
                            maxLines = 1
                        )
                        Text(
                            if (isLivePlayback) "LIVE" else "Playback",
                            color = if (isLivePlayback) Color.Red else Color(0xFF00BCD4),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OsdIconButton(
                        key = "guide",
                        icon = Icons.Filled.List,
                        label = if (isLivePlayback) "TV Guide" else "Back",
                        focusRequester = if (!startsLive) defaultOsdFocusRequester else null,
                        onFocusChange = { focused ->
                            focusedOsdControl = if (focused) "guide" else focusedOsdControl.takeUnless { it == "guide" }
                        },
                        onClick = onBack
                    )
                    OsdIconButton(
                        key = "reconnect",
                        icon = Icons.Filled.Refresh,
                        label = if (isLivePlayback) "Reconnect" else "Restart",
                        focusRequester = if (startsLive) defaultOsdFocusRequester else null,
                        onFocusChange = { focused ->
                            focusedOsdControl = if (focused) "reconnect" else focusedOsdControl.takeUnless { it == "reconnect" }
                        },
                        onClick = {
                            requestReconnect(fromPlaybackFailure = false, userInitiated = true)
                        }
                    )
                    OsdIconButton(
                        key = "buffer",
                        icon = Icons.Filled.Speed,
                        label = "Buffer ${currentBuffer}s",
                        onFocusChange = { focused ->
                            focusedOsdControl = if (focused) "buffer" else focusedOsdControl.takeUnless { it == "buffer" }
                        },
                        onClick = {
                            currentBuffer = when (currentBuffer) {
                                2 -> 5
                                5 -> 15
                                15 -> 30
                                else -> 2
                            }
                            reconnectAttempts = 0
                            requestReconnect(fromPlaybackFailure = false, userInitiated = true)
                        }
                    )
                    if (startsLive && item.hasCatchup) {
                        OsdIconButton(
                            key = "catchup",
                            icon = Icons.Filled.Replay,
                            label = if (isLivePlayback) "Catch-up" else "Go Live",
                            onFocusChange = { focused ->
                                focusedOsdControl = if (focused) "catchup" else focusedOsdControl.takeUnless { it == "catchup" }
                            },
                            onClick = {
                                if (isLivePlayback) {
                                    val startUtc = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).apply {
                                        timeZone = TimeZone.getTimeZone("UTC")
                                    }.format(Date(System.currentTimeMillis() - 3_600_000))
                                    activeUrl = Session.archiveUrl(item.id, startUtc, 60)
                                    isLivePlayback = false
                                    revealOsd()
                                } else {
                                    activeUrl = initialUrl
                                    isLivePlayback = startsLive
                                    requestReconnect(fromPlaybackFailure = false, userInitiated = true)
                                }
                            }
                        )
                    }
                    OsdIconButton(
                        key = "controls",
                        icon = Icons.Filled.Settings,
                        label = if (isLivePlayback) "Seek ±10s" else "Playback",
                        onFocusChange = { focused ->
                            focusedOsdControl = if (focused) "controls" else focusedOsdControl.takeUnless { it == "controls" }
                        },
                        onClick = {
                            revealOsd()
                            if (!isLivePlayback) {
                                Toast.makeText(context, "Use left/right to seek 10 seconds.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                if (startsLive && items.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(items, key = { it.id }) { channel ->
                            var isThumbFocused by remember { mutableStateOf(false) }
                            Surface(
                                onClick = { onZap(channel) },
                                color = if (isThumbFocused) Color(0xFF00BCD4) else Color(0xFF1A222C),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .width(120.dp)
                                    .aspectRatio(16f / 9f)
                                    .onFocusChanged {
                                        isThumbFocused = it.isFocused
                                        focusedChannelId = when {
                                            it.isFocused -> channel.id
                                            focusedChannelId == channel.id -> null
                                            else -> focusedChannelId
                                        }
                                    }
                                    .border(
                                        if (isThumbFocused) 2.dp else 0.dp,
                                        Color.White,
                                        RoundedCornerShape(8.dp)
                                    )
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (channel.image?.isNotBlank() == true) {
                                        AsyncImage(
                                            model = channel.image,
                                            contentDescription = channel.title,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(if (isThumbFocused) 2.dp else 0.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        Text(
                                            channel.title,
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 2,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@UnstableApi
private fun buildPlayerMediaSource(
    context: android.content.Context,
    repo: XtreamRepository,
    url: String,
    isLivePlayback: Boolean
): MediaSource {
    val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setUserAgent("GWStreams")
        .setConnectTimeoutMs(8_000)
        .setReadTimeoutMs(20_000)
    val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
    val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
    return buildMediaSource(mediaSourceFactory, repo, url, isLivePlayback)
}

@UnstableApi
private fun buildMediaSource(
    mediaSourceFactory: DefaultMediaSourceFactory,
    repo: XtreamRepository,
    url: String,
    isLivePlayback: Boolean
): MediaSource {
    val builder = MediaItem.Builder().setUri(url)
    when {
        url.endsWith(".m3u8", ignoreCase = true) -> builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        isLivePlayback -> builder.setMimeType(repo.inferLiveMimeType(url) ?: MimeTypes.VIDEO_MP2T)
    }
    return mediaSourceFactory.createMediaSource(builder.build())
}

@Composable
private fun OsdIconButton(
    key: String,
    icon: ImageVector,
    label: String,
    focusRequester: FocusRequester? = null,
    onFocusChange: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                isFocused = it.isFocused
                onFocusChange(it.isFocused)
            }
            .focusable()
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color(0xFF00BCD4) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp)
            .clickable(onClick = onClick)
    ) {
        Icon(
            icon,
            contentDescription = key,
            tint = if (isFocused) Color.White else Color.LightGray,
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (isFocused) Color.White else Color.LightGray, style = MaterialTheme.typography.labelSmall)
    }
}
