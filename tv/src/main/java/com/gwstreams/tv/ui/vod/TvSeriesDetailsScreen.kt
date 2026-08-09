package com.gwstreams.tv.ui.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gwstreams.app.data.model.Episode
import com.gwstreams.app.data.model.SeriesInfoResponse
import com.gwstreams.tv.ui.TvContentItem
import com.gwstreams.tv.ui.TvSection
import com.gwstreams.tv.ui.TvViewModel
import com.gwstreams.tv.ui.theme.*

@Composable
fun TvSeriesDetailsScreen(
    vm: TvViewModel,
    seriesId: Int,
    seriesName: String,
    onPlay: (TvContentItem) -> Unit,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()

    BackHandler(onBack = onBack)

    LaunchedEffect(seriesId) {
        vm.loadSeriesDetails(seriesId)
    }

    val infoResp = state.seriesInfo
    
    Box(Modifier.fillMaxSize().background(Color(0xFF0D1218))) {
        if (state.loading && infoResp == null) {
            CircularProgressIndicator(color = Color(0xFF00BCD4), modifier = Modifier.align(Alignment.Center))
        } else if (state.error != null && infoResp == null) {
            Text(state.error!!, color = Color.Red, modifier = Modifier.align(Alignment.Center))
        } else if (infoResp != null) {
            Row(Modifier.fillMaxSize().padding(32.dp)) {
                // Left Panel: Cover and Info
                Column(Modifier.width(300.dp).fillMaxHeight()) {
                    AsyncImage(
                        model = infoResp.info?.cover,
                        contentDescription = infoResp.info?.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.66f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.DarkGray)
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(infoResp.info?.name ?: seriesName, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(infoResp.info?.genre ?: "Unknown Genre", color = Color.Gray, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Rating: ${infoResp.info?.rating ?: "N/A"}", color = Color.Yellow, fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        infoResp.info?.plot ?: "No plot available.",
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Spacer(Modifier.width(48.dp))
                
                // Right Panel: Seasons and Episodes
                var selectedSeason by remember { mutableStateOf<String?>(null) }
                
                // Default to first season
                LaunchedEffect(infoResp) {
                    if (selectedSeason == null && !infoResp.episodes.isNullOrEmpty()) {
                        selectedSeason = infoResp.episodes.keys.minByOrNull { it.toIntOrNull() ?: 0 }
                    }
                }
                
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    // Season Tabs
                    val seasons = infoResp.episodes?.keys?.sortedBy { it.toIntOrNull() ?: 0 } ?: emptyList()
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(seasons) { sNum ->
                            val isFocused = remember { MutableInteractionSource() }.collectIsFocusedAsState().value
                            val isSelected = selectedSeason == sNum
                            Button(
                                onClick = { selectedSeason = sNum },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) Color(0xFF00BCD4) else Color(0xFF1A222C),
                                    contentColor = if (isSelected) Color(0xFF0D1218) else Color.White
                                )
                            ) {
                                Text("Season $sNum")
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(24.dp))
                    
                    // Episodes List
                    val episodes = infoResp.episodes?.get(selectedSeason) ?: emptyList()
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(episodes, key = { it.id }) { ep ->
                            EpisodeCard(ep, infoResp.info?.cover) {
                                val item = TvContentItem(
                                    id = ep.id.toIntOrNull() ?: 0,
                                    title = "${ep.title} (S${ep.season} E${ep.episodeNum})",
                                    image = infoResp.info?.cover,
                                    containerExt = ep.containerExtension,
                                    section = TvSection.SERIES
                                )
                                onPlay(item)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EpisodeCard(ep: Episode, cover: String?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        color = if (focused) Color(0xFF26323F) else Color(0xFF1A222C),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().then(if (focused) Modifier.border(2.dp, Color(0xFF00BCD4), RoundedCornerShape(8.dp)) else Modifier)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color.DarkGray), contentAlignment = Alignment.Center) {
                Text("E${ep.episodeNum ?: "?"}", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(16.dp))
            Text(ep.title ?: "Episode ${ep.episodeNum}", color = Color.White, fontSize = 18.sp, maxLines = 1)
        }
    }
}
