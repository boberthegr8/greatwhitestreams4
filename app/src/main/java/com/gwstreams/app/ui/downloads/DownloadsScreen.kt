package com.gwstreams.app.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.gwstreams.app.data.repo.DownloadEntry
import com.gwstreams.app.data.repo.DownloadState
import com.gwstreams.app.ui.components.InitialsTile
import com.gwstreams.app.ui.theme.*

@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onPlayLocal: (DownloadEntry) -> Unit,
    vm: DownloadsViewModel = viewModel()
) {
    val entries by vm.entries.collectAsState()
    val progress by vm.progress.collectAsState()

    Column(Modifier.fillMaxSize().background(Midnight)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextHi)
            }
            Text("Downloads", style = MaterialTheme.typography.headlineMedium, color = TextHi)
        }

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nothing downloaded yet.\nTap the download icon on a movie or episode.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextLow,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entries, key = { "${it.kind}-${it.id}" }) { e ->
                    DownloadRow(
                        entry = e,
                        progress = progress[e.downloadId] ?: 0f,
                        onPlay = { if (e.state == DownloadState.COMPLETE.name) onPlayLocal(e) },
                        onDelete = { vm.remove(e) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    entry: DownloadEntry,
    progress: Float,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    val complete = entry.state == DownloadState.COMPLETE.name
    val failed = entry.state == DownloadState.FAILED.name
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface1)
            .clickable(enabled = complete) { onPlay() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SubcomposeAsyncImage(
            model = entry.image,
            contentDescription = entry.title,
            contentScale = ContentScale.Crop,
            loading = { InitialsTile(entry.title) },
            error = { InitialsTile(entry.title) },
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextHi,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            when {
                complete -> Text("Ready to play offline", style = MaterialTheme.typography.labelSmall, color = AquaDim)
                failed -> Text("Download failed", style = MaterialTheme.typography.labelSmall, color = Coral)
                else -> {
                    Text(
                        if (progress > 0f) "Downloading ${(progress * 100).toInt()}%" else "Starting…",
                        style = MaterialTheme.typography.labelSmall, color = TextMid
                    )
                    Box(
                        Modifier.fillMaxWidth().height(3.dp).padding(top = 4.dp)
                            .clip(RoundedCornerShape(2.dp)).background(Surface2)
                    ) {
                        Box(
                            Modifier.fillMaxWidth(progress).fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp)).background(Aqua)
                        )
                    }
                }
            }
        }
        if (complete) {
            Icon(Icons.Filled.PlayArrow, "Play", tint = Aqua)
            Spacer(Modifier.width(4.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, "Delete", tint = TextLow)
        }
    }
}
