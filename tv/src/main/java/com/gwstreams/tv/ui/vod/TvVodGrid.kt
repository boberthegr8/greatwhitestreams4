@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.gwstreams.tv.ui.vod


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.foundation.basicMarquee
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
fun TvVodGrid(items: List<TvContentItem>, onPlay: (TvContentItem) -> Unit, onFocus: (TvContentItem) -> Unit = {}) {
    Column(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(items, key = { "v-${it.id}" }) { item ->
                PosterCard(item, onPlay, onFocus)
            }
        }
    }
}

@Composable
private fun PosterCard(item: TvContentItem, onPlay: (TvContentItem) -> Unit, onFocus: (TvContentItem) -> Unit = {}) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 400f))

    LaunchedEffect(focused) {
        if (focused) {
            kotlinx.coroutines.delay(1000)
            onFocus(item)
        }
    }

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
                    placeholder = androidx.compose.ui.graphics.painter.ColorPainter(Color(0x33000000)),
                    error = androidx.compose.ui.graphics.painter.ColorPainter(Color(0x33000000)),
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
            val view = androidx.compose.ui.platform.LocalView.current
            LaunchedEffect(focused) {
                if (focused) view.playSoundEffect(android.view.SoundEffectConstants.NAVIGATION_DOWN) // Idea 15
            }
            Text(
                item.title,
                style = TvType.titleMedium,
                color = if (focused) Aqua else TextHi,
                maxLines = 1,
                modifier = Modifier.padding(8.dp).then(if (focused) Modifier.basicMarquee() else Modifier) // Idea 9
            )
        }
    }
}
