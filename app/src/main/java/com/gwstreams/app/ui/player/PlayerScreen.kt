package com.gwstreams.app.ui.player

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.gwstreams.app.data.repo.EpgParser
import com.gwstreams.app.data.repo.Programme
import com.gwstreams.app.data.repo.Session
import com.gwstreams.app.data.repo.WatchItem
import com.gwstreams.app.reminder.ReminderScheduler
import com.gwstreams.app.ui.home.ContentItem
import com.gwstreams.app.ui.home.HomeViewModel
import com.gwstreams.app.ui.home.Tab
import com.gwstreams.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

data class PlayTarget(
    val title: String,
    val url: String,
    val streamId: Int,
    val isLive: Boolean,
    val hasArchive: Boolean = false
) {
    companion object {
        fun fromItem(item: ContentItem): PlayTarget {
            val url = when (item.kind) {
                Tab.LIVE -> Session.liveUrl(item.id)
                Tab.MOVIES -> Session.vodUrl(item.id, item.containerExt)
                Tab.SERIES -> Session.seriesUrl(item.id, item.containerExt)
            }
            return PlayTarget(item.title, url, item.id, item.kind == Tab.LIVE, item.hasArchive)
        }

        fun fromWatch(w: WatchItem): PlayTarget {
            val url = when (w.kind) {
                "LIVE" -> Session.liveUrl(w.id)
                "MOVIES" -> Session.vodUrl(w.id, w.containerExt)
                else -> Session.seriesUrl(w.id, w.containerExt)
            }
            return PlayTarget(w.title, url, w.id, w.kind == "LIVE")
        }

        fun fromLocal(title: String, localUri: String, streamId: Int): PlayTarget =
            PlayTarget(title, localUri, streamId, isLive = false)
    }
}

@UnstableApi
@Composable
fun PlayerScreen(
    target: PlayTarget,
    homeVm: HomeViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var currentUrl by remember { mutableStateOf(target.url) }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setUserAgent("GWStreams")
            val mediaItem = MediaItem.Builder()
                .setUri(currentUrl)
                .apply { if (target.isLive) setMimeType(MimeTypes.VIDEO_MP2T) }
                .build()
            val source = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
            setMediaSource(source)
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(currentUrl) {
        if (player.currentMediaItem?.localConfiguration?.uri.toString() != currentUrl) {
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setUserAgent("GWStreams")
            val mediaItem = MediaItem.Builder().setUri(currentUrl).build()
            val source = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
            player.setMediaSource(source)
            player.prepare()
            player.playWhenReady = true
        }
    }

    DisposableEffect(Unit) { onDispose { player.release() } }

    var programmes by remember { mutableStateOf<List<Programme>>(emptyList()) }
    LaunchedEffect(target) {
        if (target.isLive) programmes = EpgParser.toProgrammes(homeVm.epgFor(target.streamId))
    }

    val nowSec = System.currentTimeMillis() / 1000

    Column(Modifier.fillMaxSize().background(Midnight)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
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
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextHi)
            }
        }

        Text(
            target.title,
            style = MaterialTheme.typography.headlineMedium,
            color = TextHi,
            modifier = Modifier.padding(16.dp)
        )

        if (target.isLive) {
            Text(
                "Programme guide",
                style = MaterialTheme.typography.labelLarge,
                color = Aqua,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
            if (programmes.isEmpty()) {
                Text(
                    "No guide data for this channel.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextLow,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(programmes) { p ->
                        EpgRow(
                            p = p,
                            nowSec = nowSec,
                            canCatchUp = target.hasArchive && p.stop < nowSec,
                            onRemind = {
                                ReminderScheduler.schedule(context, target.title, p.title, p.start)
                                Toast.makeText(context, "Reminder set", Toast.LENGTH_SHORT).show()
                            },
                            onCatchUp = {
                                val start = archiveStart(p.start)
                                val dur = ((p.stop - p.start) / 60).toInt().coerceAtLeast(1)
                                currentUrl = Session.archiveUrl(target.streamId, start, dur)
                                Toast.makeText(context, "Starting from the beginning", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpgRow(
    p: Programme,
    nowSec: Long,
    canCatchUp: Boolean,
    onRemind: () -> Unit,
    onCatchUp: () -> Unit
) {
    val isNow = p.isLiveAt(nowSec)
    val isFuture = p.start > nowSec
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isNow) SurfaceHi else Surface1)
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            timeOf(p.start),
            style = MaterialTheme.typography.labelLarge,
            color = Aqua,
            modifier = Modifier.width(58.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(p.title, style = MaterialTheme.typography.titleMedium, color = TextHi)
            if (p.description.isNotBlank()) {
                Text(p.description, style = MaterialTheme.typography.bodyMedium, color = TextMid, maxLines = 2)
            }
            if (isNow) {
                Box(
                    Modifier.fillMaxWidth().height(3.dp).padding(top = 4.dp)
                        .clip(RoundedCornerShape(2.dp)).background(Surface2)
                ) {
                    Box(
                        Modifier.fillMaxWidth(p.progressAt(nowSec)).fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp)).background(Aqua)
                    )
                }
            }
        }
        if (isFuture) {
            IconButton(onClick = onRemind) {
                Icon(Icons.Filled.NotificationsActive, "Remind me", tint = TextLow)
            }
        }
        if (canCatchUp) {
            IconButton(onClick = onCatchUp) {
                Icon(Icons.Filled.Replay, "Watch from start", tint = Aqua)
            }
        }
    }
}

private fun timeOf(sec: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(sec * 1000))

private fun archiveStart(startSec: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US)
    fmt.timeZone = TimeZone.getTimeZone("UTC")
    return fmt.format(Date(startSec * 1000))
}
