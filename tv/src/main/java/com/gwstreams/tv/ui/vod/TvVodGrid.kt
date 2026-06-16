@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gwstreams.tv.ui.vod


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gwstreams.tv.ui.TvContentItem
import com.gwstreams.tv.ui.theme.*

/** Poster-card grid for Movies/Series, focusable with the D-pad. */
@Composable
fun TvVodGrid(items: List<TvContentItem>, onPlay: (TvContentItem) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items, key = { "v-${it.id}" }) { item ->
            PosterCard(item, onPlay)
        }
    }
}

@Composable
private fun PosterCard(item: TvContentItem, onPlay: (TvContentItem) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale = if (focused) 1.08f else 1f

    Surface(
        onClick = { onPlay(item) },
        interactionSource = interaction,
        color = Surface1,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .scale(scale)
            .then(if (focused) Modifier.border(3.dp, Aqua, RoundedCornerShape(12.dp)) else Modifier)
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(0.68f)) {
                AsyncImage(
                    model = item.image,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                )
                if (item.rating?.isNotBlank() == true && item.rating != "0") {
                    Box(
                        Modifier.align(Alignment.TopStart).padding(6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Midnight.copy(alpha = 0.85f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("\u2605 ${item.rating}", style = TvType.labelSmall, color = Aqua)
                    }
                }
            }
            Text(
                item.title,
                style = TvType.titleMedium,
                color = if (focused) Aqua else TextHi,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}
