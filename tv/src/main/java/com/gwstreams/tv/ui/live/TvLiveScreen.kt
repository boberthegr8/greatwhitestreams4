@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.gwstreams.tv.ui.live


import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.platform.LocalContext
import com.gwstreams.app.data.repo.Session

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
private val BrowseSections = listOf(
    TvSection.LIVE,
    TvSection.MOVIES,
    TvSection.SERIES,
    TvSection.SEARCH,
    TvSection.SETTINGS
)

@Composable
@UnstableApi
fun TvLiveScreen(
    vm: TvViewModel,
    nowSec: Long,
    onPlay: (TvContentItem) -> Unit
) {
    val state by vm.state.collectAsState()
    var bgStreamUrl by remember { mutableStateOf("") }
    var bgIsLive by remember { mutableStateOf(true) }
    var previousBrowseSection by rememberSaveable { mutableStateOf(TvSection.LIVE) }
    var showSearchDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.section) {
        when (state.section) {
            TvSection.LIVE, TvSection.MOVIES, TvSection.SERIES -> previousBrowseSection = state.section
            TvSection.SEARCH -> showSearchDialog = true
            else -> Unit
        }
        if (state.section != TvSection.SEARCH) {
            showSearchDialog = false
        }
    }

    BackHandler(enabled = state.section == TvSection.SEARCH) {
        vm.selectSection(previousBrowseSection)
    }

    Row(Modifier.fillMaxSize().background(Color(0xFF0F1115))) {
        // StartupShow Collapsible Left Sidebar
        SectionNavPane(
            currentSection = state.section,
            onSection = vm::selectSection,
            momMode = state.settings.momMode
        )

        Box(modifier = Modifier.fillMaxSize()) {

            // Content Layer (Categories + Guide)
            Row(Modifier.fillMaxSize().padding(top = 16.dp, end = 16.dp)) {
                if (state.section != TvSection.SEARCH && state.section != TvSection.SETTINGS) {
                    CategoryPane(
                        categories = state.categories,
                        selectedId = state.selectedCategory,
                        onCategory = vm::selectCategory
                    )
                }

                Box(Modifier.weight(1f)) {
                    if (state.items.isEmpty() && !state.loading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.Tv, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.DarkGray)
                                Spacer(Modifier.height(16.dp))
                                Text("No Content Available", color = Color.Gray, style = TvType.titleMedium)
                            }
                        }
                    } else {
                        // Guide / VOD Grid
                        Column(Modifier.fillMaxSize()) {
                            // Top PiP area (Only for Live TV)
                            if (state.section == TvSection.LIVE) {
                                Row(Modifier.fillMaxWidth().height(110.dp).padding(bottom = 16.dp)) {
                                    Box(Modifier.weight(1f)) {
                                        // We can put current program details here if we want, or just leave blank
                                    }
                                    if (bgStreamUrl.isNotBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .width(180.dp)
                                                .aspectRatio(16f / 9f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(2.dp, Color(0xFF222222), RoundedCornerShape(8.dp))
                                                .background(Color.Black)
                                        ) {
                                            BackgroundVideoPlayer(url = bgStreamUrl, isLive = true)
                                        }
                                    }
                                }
                            }

                            Box(Modifier.weight(1f)) {
                                GuideGrid(
                                    items = state.items,
                                    state = state,
                                    nowSec = nowSec,
                                    onPlay = { item ->
                                        if (state.section == TvSection.SEARCH) {
                                            vm.selectSection(previousBrowseSection)
                                        }
                                        onPlay(item)
                                    },
                                    onQuery = vm::onQuery,
                                    onOpenSearch = { showSearchDialog = true },
                                    onFocus = {
                                        bgIsLive = it.section == TvSection.LIVE
                                        if (it.section == TvSection.LIVE) {
                                            bgStreamUrl = Session.liveUrl(it.id)
                                        }
                                        // We remove background video for VOD to focus purely on PiP for Live.
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (state.section == TvSection.SEARCH && showSearchDialog) {
                SearchQueryDialog(
                    query = state.query,
                    onQuery = vm::onQuery,
                    onDismiss = { vm.selectSection(previousBrowseSection) }
                )
            }
        }
    }
}

@Composable
private fun SectionNavPane(
    currentSection: TvSection,
    onSection: (TvSection) -> Unit,
    momMode: Boolean
) {
    var hasFocus by remember { mutableStateOf(false) }
    val width by androidx.compose.animation.core.animateDpAsState(if (hasFocus) 220.dp else 72.dp, androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 300f))

    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(Color(0xFF18181C))
            .onFocusChanged { hasFocus = it.hasFocus }
            .padding(vertical = 16.dp)
    ) {
        BrowseSections.forEach { section ->
            if (momMode && (section == TvSection.MOVIES || section == TvSection.SERIES)) return@forEach
            SectionRow(
                section = section,
                selected = currentSection == section,
                expanded = hasFocus,
                onClick = { onSection(section) }
            )
        }
    }
}

@Composable
private fun SectionRow(section: TvSection, selected: Boolean, expanded: Boolean, onClick: () -> Unit) {
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
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .then(if (focused) Modifier.border(2.dp, Aqua, RoundedCornerShape(10.dp)) else Modifier)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(section.icon(), contentDescription = section.label(), tint = if (selected || focused) Aqua else TextMid)
            if (expanded) {
                Spacer(Modifier.width(16.dp))
                Text(section.label(), color = if (selected || focused) Aqua else TextMid, style = TvType.bodyMedium, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CategoryPane(
    categories: List<com.gwstreams.app.data.model.Category>,
    selectedId: String?,
    onCategory: (String) -> Unit
) {
    LazyColumn(
        Modifier.width(240.dp).fillMaxHeight().background(Color.Transparent).padding(top = 16.dp, bottom = 16.dp, start = 8.dp, end = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(categories, key = { it.categoryId }) { cat ->
            NavRow(
                label = cat.categoryName,
                selected = cat.categoryId == selectedId,
                onClick = { onCategory(cat.categoryId) },
                onFocus = {
                    if (cat.categoryId != selectedId) onCategory(cat.categoryId)
                }
            )
        }
    }
}

@Composable
private fun NavRow(label: String, selected: Boolean, onClick: () -> Unit, onFocus: () -> Unit = {}) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) {
        if (focused) {
            kotlinx.coroutines.delay(200) // Debounce
            onFocus()
        }
    }
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
    onPlay: (TvContentItem) -> Unit,
    onQuery: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onFocus: (TvContentItem) -> Unit = {}
) {
    if (state.section == TvSection.SEARCH) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Surface(
                onClick = onOpenSearch,
                color = Surface2,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Search, null, tint = TextLow)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (state.query.isBlank()) "Open search keyboard"
                        else "Search: ${state.query}",
                        color = TextHi,
                        style = TvType.bodyMedium
                    )
                }
            }
            
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (state.loading) {
                    CircularProgressIndicator(color = Aqua, modifier = Modifier.align(Alignment.Center))
                } else if (items.isEmpty() && state.query.trim().length >= 3) {
                    Text("No results found for \"${state.query}\"", color = TextMid, modifier = Modifier.align(Alignment.Center))
                } else if (items.isEmpty()) {
                    Text("Type at least 3 characters to search", color = TextMid, modifier = Modifier.align(Alignment.Center))
                } else {
                    com.gwstreams.tv.ui.vod.TvVodGrid(items, onPlay, onFocus)
                }
            }
        }
        return
    }

    if (state.section != TvSection.LIVE) {
        // Movies/Series render as a focusable card grid instead of a guide.
        com.gwstreams.tv.ui.vod.TvVodGrid(items, onPlay, onFocus)
        return
    }
    Column(Modifier.fillMaxSize()) {
        // TiviMate-style Timeline Header
        Row(
            Modifier.fillMaxWidth().height(36.dp).padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(220.dp))
            Row(
                Modifier.weight(1f).padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Approximate alignment with the 1.4f and 1.0f weights used below
                Box(Modifier.weight(1.4f)) {
                    Text(timeLabel(nowSec - (nowSec % 1800)), style = TvType.labelMedium, color = TextMid)
                }
                Box(Modifier.weight(1f).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color(0x33000000))))) { // Idea 24: Gradient EPG
                    Text(timeLabel(nowSec - (nowSec % 1800) + 3600), style = TvType.labelMedium, color = TextMid)
                }
            }
        }
        val listState = androidx.compose.runtime.saveable.rememberSaveable(saver = androidx.compose.foundation.lazy.LazyListState.Saver) { androidx.compose.foundation.lazy.LazyListState() }
        val coroutineScope = rememberCoroutineScope()
        var lastScrollTime by remember { mutableLongStateOf(0L) }
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 120.dp), // Focus Re-centering: Keeps active item near center
            modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                val now = System.currentTimeMillis()
                if (now - lastScrollTime < 50) return@onPreviewKeyEvent true
                lastScrollTime = now
                val it = event // map event for inner code
            if (it.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                when (it.nativeKeyEvent.keyCode) {
                    android.view.KeyEvent.KEYCODE_CHANNEL_UP, android.view.KeyEvent.KEYCODE_MEDIA_REWIND, android.view.KeyEvent.KEYCODE_PAGE_UP -> {
                        coroutineScope.launch {
                            val next = maxOf(0, listState.firstVisibleItemIndex - 7)
                            listState.animateScrollToItem(next)
                        }
                        return@onPreviewKeyEvent true
                    }
                    android.view.KeyEvent.KEYCODE_CHANNEL_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, android.view.KeyEvent.KEYCODE_PAGE_DOWN -> {
                        coroutineScope.launch {
                            val next = minOf(items.size - 1, listState.firstVisibleItemIndex + 7)
                            listState.animateScrollToItem(next)
                        }
                        return@onPreviewKeyEvent true
                    }
                }
            }
            false
        }
    ) {
        items(items, key = { "g-${it.id}" }) { item ->
            GuideRow(
                item = item,
                nowNext = state.nowNext[item.id],
                nowSec = nowSec,
                isPlaying = state.lastPlayedChannelId == item.id,
                isFavorite = item.id in state.favorites,
                onPlay = onPlay,
                onFocus = onFocus
            )
        }
    }
    }
}

@Composable
private fun SearchQueryDialog(
    query: String,
    onQuery: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val textRequester = remember { FocusRequester() }
    val closeRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(75)
        textRequester.requestFocus()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceHi,
            modifier = Modifier.width(640.dp)
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("Search", style = TvType.headlineMedium, color = TextHi)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = onQuery,
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextLow) },
                    placeholder = { Text("Search channels, movies, series...", color = TextLow) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { closeRequester.requestFocus() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Aqua,
                        unfocusedBorderColor = SurfaceHi,
                        focusedContainerColor = Surface2,
                        unfocusedContainerColor = Surface2,
                        cursorColor = Aqua,
                        focusedTextColor = TextHi,
                        unfocusedTextColor = TextHi
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(textRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN && event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                                closeRequester.requestFocus()
                                true
                            } else {
                                false
                            }
                        }
                )
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Surface1, contentColor = TextHi),
                        modifier = Modifier.focusRequester(closeRequester)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideRow(
    item: TvContentItem,
    nowNext: NowNext?,
    nowSec: Long,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onPlay: (TvContentItem) -> Unit,
    onFocus: (TvContentItem) -> Unit = {}
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    LaunchedEffect(focused) {
        if (focused) {
            kotlinx.coroutines.delay(800) // Wait a bit before auto-playing background
            onFocus(item)
        }
    }

    Surface(
        onClick = { onPlay(item) },
        interactionSource = interaction,
        color = if (focused) SurfaceHi else Surface1,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .then(if (focused) Modifier.border(2.dp, Aqua, RoundedCornerShape(10.dp)) else Modifier)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Channel cell
            Row(
                Modifier.width(220.dp).fillMaxHeight().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isPlaying) {
                    val infiniteTransition = rememberInfiniteTransition()
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f, targetValue = 1f,
                        animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse)
                    )
                    Text("▶", color = Coral, style = TvType.labelSmall, modifier = Modifier.width(36.dp).alpha(alpha))
                } else if (item.num != null) {
                    Text(
                        "${item.num}",
                        style = TvType.labelSmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), // Monospace alignment
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.title,
                        style = TvType.bodyMedium,
                        color = TextHi,
                        maxLines = 1,
                        modifier = if (focused) Modifier.weight(1f, fill = false).basicMarquee() else Modifier
                    )
                    if (isFavorite) {
                        Spacer(Modifier.width(6.dp))
                        Text("★", color = Color(0xFFFFD54F), style = TvType.labelSmall)
                    }
                }
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
            .alpha(if (isNow) 1f else 0.65f) // Idea 16: Dim future/past shows
            .clip(RoundedCornerShape(8.dp))
            .background(if (isNow) Surface2 else Surface1)
            .padding(8.dp)
    ) {
        Text(
            p.title,
            style = TvType.bodyMedium,
            color = if (isNow) TextHi else TextMid,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (p.description.isNotBlank()) {
            Text(
                p.description,
                style = TvType.labelSmall,
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
    TvSection.SEARCH -> "Search"
    TvSection.LIVE -> "Live TV"
    TvSection.MOVIES -> "Movies"
    TvSection.SERIES -> "Series"
    TvSection.SETTINGS -> "Settings"
}


fun TvSection.icon() = when (this) {
    TvSection.SEARCH -> Icons.Filled.Search
    TvSection.LIVE -> Icons.Filled.Tv
    TvSection.MOVIES -> Icons.Filled.Movie
    TvSection.SERIES -> Icons.Filled.VideoLibrary
    TvSection.SETTINGS -> Icons.Filled.Settings
}




@Composable
@UnstableApi
fun BackgroundVideoPlayer(url: String, isLive: Boolean) {
    if (url.isBlank()) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F1115)))
        return
    }
    
    val context = LocalContext.current
    val player = remember(url) {
        val exoPlayer = ExoPlayer.Builder(context)
            .setRenderersFactory(androidx.media3.exoplayer.DefaultRenderersFactory(context).setEnableDecoderFallback(true))
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .apply { if (isLive) setMimeType(MimeTypes.VIDEO_MP2T) }
            .build()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.volume = 0f // Mute background video by default so it doesn't annoy
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        exoPlayer
    }
    
    DisposableEffect(url) {
        onDispose { player.release() }
    }
    
    AndroidView(
        factory = {
            PlayerView(it).apply {
                this.player = player
                useController = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
