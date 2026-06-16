package com.gwstreams.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.gwstreams.app.data.repo.NowNext
import com.gwstreams.app.data.repo.Programme
import com.gwstreams.app.ui.components.InitialsTile
import com.gwstreams.app.ui.components.pressScale
import com.gwstreams.app.ui.theme.*

/** Controls row: list/guide toggle, favorites-only, genre chips (#4,#5,#7). */
@Composable
fun LiveControls(
    state: HomeUiState,
    genres: List<String>,
    onToggleView: () -> Unit,
    onToggleFavs: () -> Unit,
    onGenre: (String?) -> Unit
) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // List / Guide toggle
            Box(
                Modifier.clip(RoundedCornerShape(10.dp)).background(Surface1)
                    .clickable { onToggleView() }.padding(8.dp)
            ) {
                Icon(
                    if (state.liveView == LiveView.LIST) Icons.Filled.GridView
                    else Icons.AutoMirrored.Filled.ViewList,
                    contentDescription = "Toggle guide view",
                    tint = Aqua
                )
            }
            Spacer(Modifier.width(8.dp))
            // Favorites-only
            Box(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (state.showFavoritesOnly) Aqua else Surface1)
                    .clickable { onToggleFavs() }.padding(8.dp)
            ) {
                Icon(
                    Icons.Filled.Star, contentDescription = "Favorites only",
                    tint = if (state.showFavoritesOnly) Midnight else TextMid
                )
            }
            if (state.epgLoading) {
                Spacer(Modifier.width(12.dp))
                Text("Updating guide…", style = MaterialTheme.typography.labelSmall, color = TextLow)
            }
        }

        if (genres.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                item {
                    GenreChip("All", state.genreFilter == null) { onGenre(null) }
                }
                items(genres) { g ->
                    GenreChip(g, state.genreFilter == g) { onGenre(g) }
                }
            }
        }
    }
}

@Composable
private fun GenreChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (active) Aqua else Surface1)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            label, style = MaterialTheme.typography.labelLarge,
            color = if (active) Midnight else TextMid
        )
    }
}

/** A Now/Next channel row with progress bar, number, archive + favorite (#2,#3,#7,#9,#10). */
@Composable
fun ChannelRowNowNext(
    item: ContentItem,
    nowNext: NowNext?,
    isFavorite: Boolean,
    nowSec: Long,
    onOpen: (ContentItem) -> Unit,
    onFavorite: (ContentItem) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface1)
            .clickable(interactionSource = interaction, indication = null) { onOpen(item) }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.num != null) {
            Text(
                "${item.num}",
                style = MaterialTheme.typography.labelSmall,
                color = TextLow,
                modifier = Modifier.width(28.dp)
            )
        }
        SubcomposeAsyncImage(
            model = item.image,
            contentDescription = item.title,
            contentScale = ContentScale.Fit,
            loading = { InitialsTile(item.title) },
            error = { InitialsTile(item.title) },
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextHi,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (item.hasArchive) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(4.dp)).background(Surface2)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text("CATCH-UP", style = MaterialTheme.typography.labelSmall, color = AquaDim)
                    }
                }
            }
            val now = nowNext?.now
            if (now != null) {
                Text(
                    now.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMid,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val progress = now.progressAt(nowSec)
                Box(
                    Modifier.fillMaxWidth().height(3.dp).padding(top = 3.dp)
                        .clip(RoundedCornerShape(2.dp)).background(Surface2)
                ) {
                    Box(
                        Modifier.fillMaxWidth(progress).fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp)).background(Aqua)
                    )
                }
                nowNext.next?.let { nx ->
                    Text(
                        "Next: ${nx.title}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextLow,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            } else {
                Text(
                    "No guide data",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextLow
                )
            }
        }
        IconButton(onClick = { onFavorite(item) }) {
            Icon(
                if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) Aqua else TextLow
            )
        }
    }
}

/** Timeline guide grid: channels down the left, programmes scrolling right (#4). */
@Composable
fun GuideGrid(
    items: List<ContentItem>,
    nowNext: Map<Int, NowNext>,
    nowSec: Long,
    onOpen: (ContentItem) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(items, key = { "guide-${it.id}" }) { item ->
            val nn = nowNext[item.id]
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Surface1)
                    .clickable { onOpen(item) }
                    .height(IntrinsicSize.Min)
            ) {
                // Channel label column
                Column(
                    Modifier.width(96.dp).background(Surface2).padding(8.dp)
                ) {
                    if (item.num != null) {
                        Text("${item.num}", style = MaterialTheme.typography.labelSmall, color = TextLow)
                    }
                    Text(
                        item.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = TextHi,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Programme blocks
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val blocks = listOfNotNull(nn?.now, nn?.next)
                    if (blocks.isEmpty()) {
                        Text(
                            "No guide data",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextLow,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    } else {
                        blocks.forEachIndexed { i, p ->
                            GuideBlock(p, isNow = i == 0, nowSec = nowSec)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideBlock(p: Programme, isNow: Boolean, nowSec: Long) {
    Column(
        Modifier
            .width(160.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isNow) SurfaceHi else Surface2)
            .padding(8.dp)
    ) {
        Text(
            p.title,
            style = MaterialTheme.typography.titleMedium,
            color = if (isNow) TextHi else TextMid,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (isNow) {
            val progress = p.progressAt(nowSec)
            Box(
                Modifier.fillMaxWidth().height(3.dp).padding(top = 4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(Surface1)
            ) {
                Box(
                    Modifier.fillMaxWidth(progress).fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp)).background(Aqua)
                )
            }
        }
    }
}
