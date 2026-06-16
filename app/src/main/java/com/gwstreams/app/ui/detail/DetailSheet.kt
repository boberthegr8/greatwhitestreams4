package com.gwstreams.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.gwstreams.app.data.model.Episode
import com.gwstreams.app.ui.home.ContentItem
import com.gwstreams.app.ui.home.Tab
import com.gwstreams.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailSheet(
    item: ContentItem,
    onPlayMovie: (ContentItem) -> Unit,
    onPlayEpisode: (Episode) -> Unit,
    onDownloadMovie: (ContentItem) -> Unit,
    onDownloadEpisode: (Episode) -> Unit,
    onDismiss: () -> Unit,
    vm: DetailViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(item) { vm.load(item) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextLow) }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            // Backdrop hero (#1)
            Box(Modifier.fillMaxWidth().height(200.dp)) {
                AsyncImage(
                    model = state.backdrop ?: state.poster,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to androidx.compose.ui.graphics.Color.Transparent,
                                1f to Surface1
                            )
                        )
                )
                Text(
                    item.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextHi,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                )
            }

            if (state.loading) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Aqua)
                }
            } else {
                // Meta row
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    state.year?.takeIf { it.isNotBlank() }?.let { Meta(it) }
                    state.genre?.takeIf { it.isNotBlank() }?.let { Meta(it) }
                    state.rating?.takeIf { it.isNotBlank() && it != "0" }?.let { Meta("★ $it") }
                    state.duration?.takeIf { it.isNotBlank() }?.let { Meta(it) }
                }

                state.plot?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMid,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
                state.cast?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        "Cast: $it",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextLow,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (item.kind == Tab.MOVIES) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onPlayMovie(item) },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Aqua, contentColor = Midnight),
                            modifier = Modifier.weight(1f).height(52.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Play", style = MaterialTheme.typography.labelLarge)
                        }
                        OutlinedButton(
                            onClick = { onDownloadMovie(item) },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Aqua),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Icon(Icons.Filled.Download, "Download")
                        }
                    }
                } else if (item.kind == Tab.SERIES) {
                    SeasonEpisodes(state, onPlayEpisode, onDownloadEpisode)
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun Meta(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Surface2)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = TextMid)
    }
}

@Composable
private fun SeasonEpisodes(
    state: DetailUiState,
    onPlayEpisode: (Episode) -> Unit,
    onDownloadEpisode: (Episode) -> Unit
) {
    if (state.seasons.isEmpty()) return
    var selected by remember(state.seasons) { mutableStateOf(state.seasons.first()) }

    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        state.seasons.forEach { s ->
            val active = s == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) Aqua else Surface2)
                    .clickable { selected = s }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    "Season $s",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) Midnight else TextMid
                )
            }
        }
    }

    state.episodesBySeason[selected]?.forEach { ep ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Surface2)
                    .clickable { onPlayEpisode(ep) },
                contentAlignment = Alignment.Center
            ) {
                Text("${ep.episodeNum ?: ""}", color = Aqua, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                ep.title ?: "Episode ${ep.episodeNum}",
                style = MaterialTheme.typography.titleMedium,
                color = TextHi,
                modifier = Modifier.weight(1f).clickable { onPlayEpisode(ep) }
            )
            IconButton(onClick = { onDownloadEpisode(ep) }) {
                Icon(Icons.Filled.Download, "Download", tint = TextLow)
            }
            IconButton(onClick = { onPlayEpisode(ep) }) {
                Icon(Icons.Filled.PlayArrow, "Play", tint = Aqua)
            }
        }
    }
}
