package com.gwstreams.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.gwstreams.app.data.repo.WatchItem
import com.gwstreams.app.ui.components.*
import com.gwstreams.app.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    onOpen: (ContentItem) -> Unit,
    onOpenWatch: (WatchItem) -> Unit,
    onOpenDownloads: () -> Unit,
    onLastChannel: (() -> Unit)? = null,
    vm: HomeViewModel = viewModel()
) {
    val state by vm.state.collectAsState()

    // A ticking clock so live progress bars advance without re-fetching (#3).
    var nowSec by remember { mutableStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) { delay(30_000); nowSec = System.currentTimeMillis() / 1000 }
    }

    Column(Modifier.fillMaxSize().background(Midnight)) {
        TopBar(
            expiry = vm.expiry,
            showLast = vm.lastChannelId() != null && state.tab == Tab.LIVE,
            onLast = { onLastChannel?.invoke() },
            onDownloads = onOpenDownloads
        )
        TabRow(state.tab) { vm.selectTab(it) }
        SearchBox(state.query, vm::onQuery, state.tab == Tab.LIVE)

        if (state.query.isBlank() && state.recent.isNotEmpty()) {
            ContinueRow(state.recent, onOpenWatch)
        }

        if (state.tab == Tab.LIVE) {
            LiveControls(
                state = state,
                genres = vm.availableGenres(),
                onToggleView = {
                    vm.setLiveView(if (state.liveView == LiveView.LIST) LiveView.GUIDE else LiveView.LIST)
                },
                onToggleFavs = vm::toggleFavoritesOnly,
                onGenre = vm::setGenreFilter
            )
        }

        if (state.categories.isNotEmpty()) {
            CategoryRail(state.categories, state.selectedCategory, vm::selectCategory)
        }

        Box(Modifier.fillMaxSize()) {
            when {
                state.loading -> SkeletonGrid(state.tab)
                state.error != null -> Text(
                    state.error!!, color = TextMid,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
                state.tab == Tab.LIVE -> {
                    val items = vm.visibleItems()
                    if (state.liveView == LiveView.GUIDE) {
                        GuideGrid(items, state.nowNext, nowSec, onOpen)
                    } else {
                        LiveList(
                            items = items,
                            state = state,
                            nowSec = nowSec,
                            hasMore = vm.hasMore(),
                            onOpen = onOpen,
                            onFavorite = { vm.toggleFavorite(it.id) },
                            onLoadMore = vm::loadMore
                        )
                    }
                }
                else -> ContentGrid(
                    items = vm.visibleItems(),
                    tab = state.tab,
                    hasMore = vm.hasMore(),
                    onOpen = onOpen,
                    onLoadMore = vm::loadMore
                )
            }
        }
    }
}

@Composable
private fun TopBar(expiry: String?, showLast: Boolean, onLast: () -> Unit, onDownloads: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GwLogo(size = 34)
        Spacer(Modifier.width(10.dp))
        Text("GWStreams", style = MaterialTheme.typography.titleLarge, color = TextHi)
        Spacer(Modifier.weight(1f))
        if (showLast) {
            IconButton(onClick = onLast) {
                Icon(Icons.Filled.Replay, contentDescription = "Last channel", tint = Aqua)
            }
        }
        IconButton(onClick = onDownloads) {
            Icon(Icons.Filled.Download, contentDescription = "Downloads", tint = TextHi)
        }
        if (expiry != null) {
            val date = runCatching {
                val ms = expiry.toLong() * 1000
                java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
                    .format(java.util.Date(ms))
            }.getOrNull()
            if (date != null) {
                Text("Expires $date", style = MaterialTheme.typography.labelSmall, color = TextLow)
            }
        }
    }
}

@Composable
private fun TabRow(current: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Tab.values().forEach { tab ->
            val active = tab == current
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) Aqua else Surface1)
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) Midnight else TextMid
                )
            }
        }
    }
}

@Composable
private fun SearchBox(query: String, onQuery: (String) -> Unit, isLive: Boolean) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextLow) },
        placeholder = {
            Text(if (isLive) "Search or type channel number" else "Search", color = TextLow)
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Aqua,
            unfocusedBorderColor = SurfaceHi,
            focusedContainerColor = Surface1,
            unfocusedContainerColor = Surface1,
            cursorColor = Aqua,
            focusedTextColor = TextHi,
            unfocusedTextColor = TextHi
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
    )
}

@Composable
private fun ContinueRow(recent: List<WatchItem>, onOpen: (WatchItem) -> Unit) {
    Column {
        Text(
            "Continue watching",
            style = MaterialTheme.typography.labelLarge,
            color = TextMid,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 6.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            items(recent, key = { "${it.kind}-${it.id}" }) { w ->
                Column(Modifier.width(130.dp).clickable { onOpen(w) }) {
                    Box(
                        Modifier.fillMaxWidth().height(78.dp)
                            .clip(RoundedCornerShape(10.dp)).background(Surface1)
                    ) {
                        AsyncImage(
                            model = w.image,
                            contentDescription = w.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (w.progress > 0f) {
                            Box(
                                Modifier.align(Alignment.BottomStart)
                                    .fillMaxWidth(w.progress.coerceIn(0f, 1f))
                                    .height(3.dp).background(Aqua)
                            )
                        }
                    }
                    Text(
                        w.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMid,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRail(
    cats: List<com.gwstreams.app.data.model.Category>,
    selected: String?,
    onSelect: (String) -> Unit
) {
    LazyRow(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(cats) { cat ->
            val active = cat.categoryId == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (active) SurfaceHi else Surface1)
                    .clickable { onSelect(cat.categoryId) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    cat.categoryName,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (active) Aqua else TextMid,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun LiveList(
    items: List<ContentItem>,
    state: HomeUiState,
    nowSec: Long,
    hasMore: Boolean,
    onOpen: (ContentItem) -> Unit,
    onFavorite: (ContentItem) -> Unit,
    onLoadMore: () -> Unit
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            hasMore && last >= items.size - 4
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) onLoadMore() }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items, key = { "live-${it.id}" }) { item ->
            ChannelRowNowNext(
                item = item,
                nowNext = state.nowNext[item.id],
                isFavorite = item.id in state.favorites,
                nowSec = nowSec,
                onOpen = onOpen,
                onFavorite = onFavorite
            )
        }
    }
}

@Composable
private fun SkeletonGrid(tab: Tab) {
    val cols = if (tab == Tab.LIVE) 1 else 3
    LazyVerticalGrid(
        columns = GridCells.Fixed(cols),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(if (tab == Tab.LIVE) 8 else 12) { SkeletonPoster() }
    }
}

@Composable
private fun ContentGrid(
    items: List<ContentItem>,
    tab: Tab,
    hasMore: Boolean,
    onOpen: (ContentItem) -> Unit,
    onLoadMore: () -> Unit
) {
    val cols = 3
    val gridState = rememberLazyGridState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            hasMore && last >= items.size - cols * 2
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) onLoadMore() }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(cols),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items, key = { "${it.kind}-${it.id}" }) { item ->
            PosterCard(item, onOpen)
        }
    }
}

@Composable
private fun PosterCard(item: ContentItem, onOpen: (ContentItem) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        Modifier
            .pressScale(interaction)
            .clip(RoundedCornerShape(14.dp))
            .background(Surface1)
            .clickable(interactionSource = interaction, indication = null) { onOpen(item) }
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.68f)) {
            SubcomposeAsyncImage(
                model = item.image,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                loading = { Box(Modifier.fillMaxSize().background(shimmerBrush())) },
                error = { InitialsTile(item.title, size = 120) },
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
            )
            if (item.rating?.isNotBlank() == true && item.rating != "0") {
                Box(
                    Modifier.align(Alignment.TopStart).padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Midnight.copy(alpha = 0.85f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("\u2605 ${item.rating}", style = MaterialTheme.typography.labelSmall, color = Aqua)
                }
            }
        }
        Text(
            item.title,
            style = MaterialTheme.typography.titleMedium,
            color = TextHi,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(8.dp)
        )
    }
}
