package com.gwstreams.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gwstreams.app.data.model.*
import com.gwstreams.app.data.repo.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class Tab(val label: String) { LIVE("Live TV"), MOVIES("Movies"), SERIES("Series") }

/** Live can render as a Now/Next list or a timeline guide grid (#4). */
enum class LiveView { LIST, GUIDE }

/** A unified content item so the grid can render any tab. */
data class ContentItem(
    val id: Int,
    val title: String,
    val image: String?,
    val rating: String? = null,
    val epgChannelId: String? = null,
    val containerExt: String? = null,
    val kind: Tab,
    val num: Int? = null,
    val hasArchive: Boolean = false,
    val archiveDuration: Int = 0
)

data class HomeUiState(
    val tab: Tab = Tab.LIVE,
    val categories: List<Category> = emptyList(),
    val selectedCategory: String? = null,
    val items: List<ContentItem> = emptyList(),
    val visibleCount: Int = PAGE,
    val loading: Boolean = false,
    val error: String? = null,
    val query: String = "",
    val recent: List<WatchItem> = emptyList(),
    // live-specific
    val liveView: LiveView = LiveView.LIST,
    val nowNext: Map<Int, NowNext> = emptyMap(),
    val epgLoading: Boolean = false,
    val favorites: Set<Int> = emptySet(),
    val recentChannelIds: List<Int> = emptyList(),
    val genreFilter: String? = null,           // #5
    val showFavoritesOnly: Boolean = false      // #7
) {
    companion object { const val PAGE = 30 }
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = XtreamRepository()
    private val watch = WatchRepository(app)
    private val livePrefs = LivePrefsRepository(app)
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    private val catCache = mutableMapOf<Tab, List<Category>>()

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                favorites = livePrefs.favorites(),
                recentChannelIds = livePrefs.recentChannels()
            )
        }
        selectTab(Tab.LIVE)
        refreshRecent()
    }

    val expiry: String? get() = Session.userInfo?.expDate

    fun refreshRecent() {
        viewModelScope.launch { _state.value = _state.value.copy(recent = watch.recent()) }
    }

    fun recordWatch(item: ContentItem) {
        viewModelScope.launch {
            watch.record(
                WatchItem(
                    id = item.id, title = item.title, image = item.image,
                    kind = item.kind.name, containerExt = item.containerExt
                )
            )
            if (item.kind == Tab.LIVE) {
                val recents = livePrefs.pushRecent(item.id)
                _state.value = _state.value.copy(recentChannelIds = recents)
            }
            refreshRecent()
        }
    }

    fun toggleFavorite(streamId: Int) {
        viewModelScope.launch {
            val favs = livePrefs.toggleFavorite(streamId)
            _state.value = _state.value.copy(favorites = favs)
        }
    }

    fun setLiveView(v: LiveView) { _state.value = _state.value.copy(liveView = v) }
    fun setGenreFilter(g: String?) { _state.value = _state.value.copy(genreFilter = g) }
    fun toggleFavoritesOnly() {
        _state.value = _state.value.copy(showFavoritesOnly = !_state.value.showFavoritesOnly)
    }

    /** The previous channel for quick flip-back (#8); null if none. */
    fun lastChannelId(): Int? = _state.value.recentChannelIds.getOrNull(1)

    fun selectTab(tab: Tab) {
        _state.value = _state.value.copy(
            tab = tab, loading = true, error = null, query = "",
            visibleCount = HomeUiState.PAGE, genreFilter = null
        )
        viewModelScope.launch {
            try {
                val cats = catCache.getOrPut(tab) {
                    when (tab) {
                        Tab.LIVE -> repo.liveCategories()
                        Tab.MOVIES -> repo.vodCategories()
                        Tab.SERIES -> repo.seriesCategories()
                    }
                }
                _state.value = _state.value.copy(categories = cats)
                selectCategory(cats.firstOrNull()?.categoryId)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = friendly(e))
            }
        }
    }

    fun selectCategory(categoryId: String?) {
        _state.value = _state.value.copy(
            selectedCategory = categoryId, loading = true, error = null,
            visibleCount = HomeUiState.PAGE, nowNext = emptyMap()
        )
        viewModelScope.launch {
            try {
                val items = when (_state.value.tab) {
                    Tab.LIVE -> repo.liveStreams(categoryId).map {
                        ContentItem(
                            id = it.streamId, title = it.name, image = it.streamIcon,
                            kind = Tab.LIVE, epgChannelId = it.epgChannelId, num = it.num,
                            hasArchive = (it.tvArchive ?: 0) > 0,
                            archiveDuration = it.tvArchiveDuration ?: 0
                        )
                    }
                    Tab.MOVIES -> repo.vodStreams(categoryId).map {
                        ContentItem(it.streamId, it.name, it.streamIcon, it.rating,
                            containerExt = it.containerExtension, kind = Tab.MOVIES)
                    }
                    Tab.SERIES -> repo.series(categoryId).map {
                        ContentItem(it.seriesId, it.name, it.cover, it.rating, kind = Tab.SERIES)
                    }
                }
                _state.value = _state.value.copy(items = items, loading = false)
                // Auto-fetch EPG whenever the Live tab shows channels (#1)
                if (_state.value.tab == Tab.LIVE) autoFetchEpg(items)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = friendly(e))
            }
        }
    }

    /** Batch-load Now/Next for the channels currently shown (#1, #2). */
    private fun autoFetchEpg(items: List<ContentItem>) {
        if (items.isEmpty()) return
        _state.value = _state.value.copy(epgLoading = true)
        viewModelScope.launch {
            val ids = items.map { it.id }
            val map = repo.batchNowNext(ids)
            _state.value = _state.value.copy(nowNext = map, epgLoading = false)
        }
    }

    /** Manual refresh hook (e.g. pull-to-refresh) re-pulls EPG for current channels. */
    fun refreshEpg() {
        if (_state.value.tab == Tab.LIVE) autoFetchEpg(_state.value.items)
    }

    fun loadMore() {
        val s = _state.value
        if (s.visibleCount < filteredAll().size) {
            _state.value = s.copy(visibleCount = s.visibleCount + HomeUiState.PAGE)
        }
    }

    fun onQuery(q: String) {
        _state.value = _state.value.copy(query = q, visibleCount = HomeUiState.PAGE)
    }

    /** Genres present in the current Now programmes, for the filter chips (#5). */
    fun availableGenres(): List<String> {
        if (_state.value.tab != Tab.LIVE) return emptyList()
        return _state.value.nowNext.values
            .mapNotNull { it.now?.title }
            .flatMap { guessGenres(it) }
            .distinct()
            .sorted()
    }

    private fun guessGenres(title: String): List<String> {
        val t = title.lowercase()
        val out = mutableListOf<String>()
        if (listOf("news", "tonight", "report", "headlines").any { t.contains(it) }) out += "News"
        if (listOf("football", "soccer", "nba", "nhl", "nfl", "match", "live:", "vs ", "game").any { t.contains(it) }) out += "Sports"
        if (listOf("movie", "film", "cinema").any { t.contains(it) }) out += "Movies"
        if (listOf("kids", "cartoon", "junior").any { t.contains(it) }) out += "Kids"
        return out
    }

    private fun filteredAll(): List<ContentItem> {
        val s = _state.value
        var all = s.items
        if (s.tab == Tab.LIVE && s.showFavoritesOnly) {
            all = all.filter { it.id in s.favorites }
        }
        if (s.tab == Tab.LIVE && s.genreFilter != null) {
            all = all.filter { item ->
                val nowTitle = s.nowNext[item.id]?.now?.title ?: return@filter false
                s.genreFilter in guessGenres(nowTitle)
            }
        }
        val q = s.query.trim()
        if (q.isNotEmpty()) {
            all = all.filter {
                it.title.contains(q, ignoreCase = true) ||
                    (it.num?.toString() == q)   // jump by channel number (#9)
            }
        }
        return all
    }

    fun visibleItems(): List<ContentItem> = filteredAll().take(_state.value.visibleCount)
    fun hasMore(): Boolean = _state.value.visibleCount < filteredAll().size
    fun nowNextFor(id: Int): NowNext? = _state.value.nowNext[id]

    suspend fun epgFor(streamId: Int): List<EpgListing> = repo.epgCached(streamId)

    fun prefetchAround(item: ContentItem) {
        if (item.kind != Tab.LIVE) return
        val list = _state.value.items
        val idx = list.indexOfFirst { it.id == item.id }
        if (idx < 0) return
        viewModelScope.launch {
            for (offset in 1..2) {
                list.getOrNull(idx + offset)?.let { runCatching { repo.epgCached(it.id) } }
            }
        }
    }

    private fun friendly(e: Exception): String =
        "Couldn't load content. Pull to retry or check your connection."
}
