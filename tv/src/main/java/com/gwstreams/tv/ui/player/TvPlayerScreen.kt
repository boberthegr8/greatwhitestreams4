package com.gwstreams.tv.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.gwstreams.app.data.repo.Session
import com.gwstreams.tv.ui.TvContentItem
import com.gwstreams.tv.ui.TvSection

@UnstableApi
@Composable
fun TvPlayerScreen(
    item: TvContentItem,
    bufferSeconds: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val url = remember(item) {
        when (item.section) {
            TvSection.LIVE -> Session.liveUrl(item.id)
            TvSection.MOVIES -> Session.vodUrl(item.id, item.containerExt)
            TvSection.SERIES -> Session.seriesUrl(item.id, item.containerExt)
            TvSection.SETTINGS -> ""
        }
    }
    val isLive = item.section == TvSection.LIVE

    val player = remember {
        val bufMs = bufferSeconds * 1000
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                bufMs.coerceAtLeast(5000),
                (bufMs * 2).coerceAtLeast(15000),
                2500,
                5000
            )
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build().apply {
                val dataSourceFactory = DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true)
                    .setUserAgent("GWStreams")
                val mediaItem = MediaItem.Builder()
                    .setUri(url)
                    .apply { if (isLive) setMimeType(MimeTypes.VIDEO_MP2T) }
                    .build()
                val source = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem)
                setMediaSource(source)
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(Unit) { onDispose { player.release() } }
    BackHandler { onBack() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    useController = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
