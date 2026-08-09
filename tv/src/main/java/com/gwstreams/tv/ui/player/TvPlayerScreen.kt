package com.gwstreams.tv.ui.player

import android.view.KeyEvent
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
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.List
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.DefaultAnalyticsCollector
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.UnrecognizedInputFormatException
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.gwstreams.app.data.repo.Session
import com.gwstreams.tv.ui.TvContentItem
import com.gwstreams.tv.ui.TvSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val coroutineScope = rememberCoroutineScope()

    var currentBuffer by remember { mutableIntStateOf(bufferSeconds) }
    var resizeModeIdx by remember { mutableIntStateOf(0) }
    val resizeModes = listOf(
        "Fit" to AspectRatioFrameLayout.RESIZE_MODE_FIT,
        "Fill" to AspectRatioFrameLayout.RESIZE_MODE_FILL,
        "Zoom" to AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        "16:9" to AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
    )

    var isReconnecting by remember { mutableStateOf(false) }
    var reconnectAttempts by remember { mutableIntStateOf(0) }
    var showBottomOsd by remember { mutableStateOf(false) }
    var osdHideTick by remember { mutableLongStateOf(0L) }
    var focusedOsdControl by remember { mutableStateOf<String?>(null) }
    var focusedChannelId by remember { mutableStateOf<Int?>(null) }
    val rootFocusRequester = remember { FocusRequester() }

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

    val exoPlayer = remember(currentBuffer, activeUrl) {
        val bufMs = currentBuffer * 1000
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(500, bufMs.coerceAtLeast(30_000), 500, 500)
            .build()
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setTunnelingEnabled(true))
        }

        ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
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

                val dataSourceFactory = DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true)
                    .setUserAgent("GWStreams")
                val mediaItem = MediaItem.Builder()
                    .setUri(activeUrl)
                    .apply { if (isLivePlayback) setMimeType(MimeTypes.VIDEO_MP2T) }
                    .build()
                val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
                    .setConstantBitrateSeekingEnabled(true)
                val source = ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
                    .createMediaSource(mediaItem)
                setMediaSource(source)
                prepare()
                playWhenReady = true

                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        super.onPlayerError(error)
                        if (error.cause !is UnrecognizedInputFormatException) {
                            if (reconnectAttempts < 3) {
                                reconnectAttempts++
                                isReconnecting = true
                                coroutineScope.launch {
                                    delay(2000)
                                    prepare()
                                    playWhenReady = true
                                    isReconnecting = false
                                }
                            } else {
                                coroutineScope.launch(Dispatchers.IO) {
                                    val isOnline = try {
                                        Runtime.getRuntime().exec("ping -c 1 8.8.8.8").waitFor() == 0
                                    } catch (_: Exception) {
                                        false
                                    }

                                    withContext(Dispatchers.Main) {
                                        val message = if (isOnline) {
                                            "Provider Stream Offline (Server Error)"
                                        } else {
                                            "Check Internet Connection"
                                        }
                                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                                        onBack()
                                    }
                                }
                            }
                        }
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

    LaunchedEffect(showBottomOsd, osdHideTick) {
        if (showBottomOsd) {
            delay(5000)
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
                    resizeMode = resizeModes[resizeModeIdx].second
                    keepScreenOn = true
                }
            },
            update = {
                it.player = exoPlayer
                it.resizeMode = resizeModes[resizeModeIdx].second
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isReconnecting) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF00BCD4))
                    Spacer(Modifier.height(16.dp))
                    Text("Reconnecting...", color = Color.White)
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
                        onFocusChange = { focused ->
                            focusedOsdControl = if (focused) "guide" else focusedOsdControl.takeUnless { it == "guide" }
                        },
                        onClick = onBack
                    )
                    OsdIconButton(
                        key = "aspect",
                        icon = Icons.Filled.AspectRatio,
                        label = "Aspect Ratio",
                        onFocusChange = { focused ->
                            focusedOsdControl = if (focused) "aspect" else focusedOsdControl.takeUnless { it == "aspect" }
                        },
                        onClick = {
                            resizeModeIdx = (resizeModeIdx + 1) % resizeModes.size
                            bumpOsdTimer()
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
                            bumpOsdTimer()
                        }
                    )
                    if (startsLive && item.hasCatchup) {
                        OsdIconButton(
                            key = "catchup",
                            icon = Icons.Filled.Replay,
                            label = "Catch-up",
                            onFocusChange = { focused ->
                                focusedOsdControl = if (focused) "catchup" else focusedOsdControl.takeUnless { it == "catchup" }
                            },
                            onClick = {
                                val startUtc = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).apply {
                                    timeZone = TimeZone.getTimeZone("UTC")
                                }.format(Date(System.currentTimeMillis() - 3_600_000))
                                activeUrl = Session.archiveUrl(item.id, startUtc, 60)
                                isLivePlayback = false
                                revealOsd()
                            }
                        )
                    }
                    OsdIconButton(
                        key = "audio",
                        icon = Icons.Filled.Settings,
                        label = if (isLivePlayback) "Audio/CC" else "Seek ±10s",
                        onFocusChange = { focused ->
                            focusedOsdControl = if (focused) "audio" else focusedOsdControl.takeUnless { it == "audio" }
                        },
                        onClick = { bumpOsdTimer() }
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

@Composable
private fun OsdIconButton(
    key: String,
    icon: ImageVector,
    label: String,
    onFocusChange: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
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
            contentDescription = label,
            tint = if (isFocused) Color.White else Color.LightGray,
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (isFocused) Color.White else Color.LightGray, style = MaterialTheme.typography.labelSmall)
    }
}
