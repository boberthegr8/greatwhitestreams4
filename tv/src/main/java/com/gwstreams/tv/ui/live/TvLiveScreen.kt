@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.gwstreams.tv.ui.live


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gwstreams.app.data.repo.NowNext
import com.gwstreams.app.data.repo.Programme
import com.gwstreams.tv.ui.TvContentItem
import com.gwstreams.tv.ui.TvSection
import com.gwstreams.tv.ui.TvUiState
import com.gwstreams.tv.ui.TvViewModel
import com.gwstreams.tv.ui.theme.*

/**
 * TiviMate-style live screen:
 *   Left panel: section switch, search, category list (vertical)
 *   Right panel: channel rows with a horizontally-scrolling Now/Next/Later guide
 */
@Composable
fun TvLiveScreen(
    vm: TvViewModel,
    nowSec: Long,
    onPlay: (TvContentItem) -> Unit
) {
    val state by vm.state.collectAsState()

    Row(Modifier.fillMaxSize().background(Midnight)) {
        LeftPanel(
            state = state,
            onSection = vm::selectSection,
            onCategory = vm::selectCategory,
            onQuery = vm::onQuery
        )
        Box(Modifier.weight(1f).fillMaxHeight().padding(8.dp)) {
            when {
                state.loading -> CircularProgressIndicator(
                    color = Aqua, modifier = Modifier.align(Alignment.Center)
                )
                state.error != null -> Text(
                    state.error!!, color = TextMid,
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> GuideGrid(vm.visibleItems(), state, nowSec, onPlay)
            }
        }
    }
}

@Composable
private fun LeftPanel(
    state: TvUiState,
    onSection: (TvSection) -> Unit,
    onCategory: (String) -> Unit,
    onQuery: (String) -> Unit
) {
    Column(
        Modifier
            .width(300.dp)
            .fillMaxHeight()
            .background(Surface1)
            .padding(16.dp)
    ) {
        Text("Great White", style = TvType.titleLarge, color = TextHi)
        Text("Streams", style = TvType.titleLarge, color = Aqua)
        Spacer(Modifier.height(20.dp))

        // Section tabs
        TvSection.values().forEach { section ->
            NavRow(
                label = section.label(),
                selected = state.section == section,
                onClick = { onSection(section) }
            )
        }

        Spacer(Modifier.height(16.dp))
        Divider(color = Surface2)
        Spacer(Modifier.height(16.dp))

        // Search lives in the left panel so it doesn't eat vertical space on the right.
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextLow) },
            placeholder = { Text("Search", color = TextLow) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Aqua,
                unfocusedBorderColor = SurfaceHi,
                focusedContainerColor = Surface2,
                unfocusedContainerColor = Surface2,
                cursorColor = Aqua,
                focusedTextColor = TextHi,
                unfocusedTextColor = TextHi
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))
        Text("CATEGORIES", style = TvType.labelSmall, color = TextLow)
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(state.categories, key = { it.categoryId }) { cat ->
                NavRow(
                    label = cat.categoryName,
                    selected = cat.categoryId == state.selectedCategory,
                    onClick = { onCategory(cat.categoryId) }
                )
            }
        }
    }
}

@Composable
private fun NavRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val bg = when {
        focused -> SurfaceHi
        selected -> Surface2
        else -> Color.Transparent
    }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        color = bg,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focused) Modifier.border(2.dp, Aqua, RoundedCornerShape(10.dp)) else Modifier)
    ) {
        Text(
            label,
            style = TvType.titleMedium,
            color = if (selected || focused) Aqua else TextMid,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun GuideGrid(
    items: List<TvContentItem>,
    state: TvUiState,
    nowSec: Long,
    onPlay: (TvContentItem) -> Unit
) {
    if (state.section != TvSection.LIVE) {
        // Movies/Series render as a focusable card grid instead of a guide.
        com.gwstreams.tv.ui.vod.TvVodGrid(items, onPlay)
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items, key = { "g-${it.id}" }) { item ->
            GuideRow(item, state.nowNext[item.id], nowSec, onPlay)
        }
    }
}

@Composable
private fun GuideRow(
    item: TvContentItem,
    nowNext: NowNext?,
    nowSec: Long,
    onPlay: (TvContentItem) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Surface(
        onClick = { onPlay(item) },
        interactionSource = interaction,
        color = if (focused) SurfaceHi else Surface1,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .then(if (focused) Modifier.border(2.dp, Aqua, RoundedCornerShape(10.dp)) else Modifier)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Channel cell
            Row(
                Modifier.width(220.dp).fillMaxHeight().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.num != null) {
                    Text(
                        "${item.num}",
                        style = TvType.labelSmall,
                        color = TextLow,
                        modifier = Modifier.width(36.dp)
                    )
                }
                AsyncImage(
                    model = item.image,
                    contentDescription = item.title,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    item.title,
                    style = TvType.titleMedium,
                    color = TextHi,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Programme blocks
            Row(
                Modifier.weight(1f).fillMaxHeight().padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val blocks = listOfNotNull(nowNext?.now, nowNext?.next)
                if (blocks.isEmpty()) {
                    Text(
                        "No guide data",
                        style = TvType.bodyMedium,
                        color = TextLow,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                } else {
                    blocks.forEachIndexed { i, p ->
                        ProgrammeBlock(p, isNow = i == 0, nowSec = nowSec, weight = if (i == 0) 1.4f else 1f)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.ProgrammeBlock(p: Programme, isNow: Boolean, nowSec: Long, weight: Float) {
    Column(
        Modifier
            .weight(weight)
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isNow) Surface2 else Surface1)
            .padding(8.dp)
    ) {
        Text(
            "${timeLabel(p.start)}  ${p.title}",
            style = TvType.titleMedium,
            color = if (isNow) TextHi else TextMid,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (p.description.isNotBlank()) {
            Text(
                p.description,
                style = TvType.bodyMedium,
                color = TextLow,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isNow) {
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier.fillMaxWidth().height(3.dp)
                    .clip(RoundedCornerShape(2.dp)).background(SurfaceHi)
            ) {
                Box(
                    Modifier.fillMaxWidth(p.progressAt(nowSec)).fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp)).background(Aqua)
                )
            }
        }
    }
}

private fun timeLabel(sec: Long): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(sec * 1000))

fun TvSection.label(): String = when (this) {
    TvSection.LIVE -> "Live TV"
    TvSection.MOVIES -> "Movies"
    TvSection.SERIES -> "Series"
    TvSection.SETTINGS -> "Settings"
}
