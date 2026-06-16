package com.gwstreams.tv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gwstreams.app.data.model.Category
import com.gwstreams.app.data.repo.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class TvSection { LIVE, MOVIES, SERIES, SETTINGS }

data class TvContentItem(
    val id: Int,
    val title: String,
    val image: String?,
    val rating: String? = null,
    val containerExt: String? = null,
    val num: Int? = null,
    val section: TvSection
)

data class TvUiState(
    val section: TvSection = TvSection.LIVE,
    val categories: List<Category> = emptyList(),
    val selectedCategory: String? = null,
    val items: List<TvContentItem> = emptyList(),
    val nowNext: Map<Int, NowNext> = emptyMap(),
    val loading: Boolean = false,
    val error: String? = null,
    val query: String = "",
    val settings: AppSettings = AppSettings(),
    val loggedIn: Boolean = false,
    val autoLoggingIn: Boolean = false,
    val savedHost: String = "",
    val savedUser: String = "",
    val savedPass: String = ""
)

class TvViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = XtreamRepository()
    private val settingsRepo = SettingsRepository(app)
    private val creds = com.gwstreams.tv.data.TvCredentialStore(app)

    private val _state = MutableStateFlow(TvUiState())
    val state: StateFlow<TvUiState> = _state

    private val catCache = mutableMapOf<TvSection, List<Category>>()

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(settings = settingsRepo.load())
            // Surface any saved credentials so the login screen can prefill,
            // and attempt a silent auto-login so the app stays signed in.
            creds.load()?.let { c ->
                _state.value = _state.value.copy(
                    savedHost = c.host, savedUser = c.user, savedPass = c.pass
                )
                attemptAutoLogin(c)
            }
        }
    }

    private fun attemptAutoLogin(c: com.gwstreams.tv.data.TvCredentialStore.Creds) {
        _state.value = _state.value.copy(autoLoggingIn = true)
        viewModelScope.launch {
            val result = repo.login(c.host, c.user, c.pass)
            result.fold(
                onSuccess = {
                    _state.value = _state.value.copy(autoLoggingIn = false, loggedIn = true)
                    selectSection(TvSection.LIVE)
                },
                onFailure = {
                    // Saved creds failed (expired/changed) — fall back to the login screen.
                    _state.value = _state.value.copy(autoLoggingIn = false)
                }
            )
        }
    }

    val expiry: String? get() = Session.userInfo?.expDate

    fun login(host: String, user: String, pass: String, remember: Boolean, onResult: (Boolean, String?) -> Unit) {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val result = repo.login(host, user, pass)
            result.fold(
                onSuccess = {
                    if (remember) creds.save(repo.normalizeHost(host), user, pass)
                    _state.value = _state.value.copy(loading = false, loggedIn = true)
                    selectSection(TvSection.LIVE)
                    onResult(true, null)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(loading = false, error = e.message)
                    onResult(false, e.message)
                }
            )
        }
    }

    fun selectSection(section: TvSection) {
        if (section == TvSection.SETTINGS) {
            _state.value = _state.value.copy(section = section)
            return
        }
        _state.value = _state.value.copy(section = section, loading = true, error = null, query = "")
        viewModelScope.launch {
            try {
                val cats = catCache.getOrPut(section) {
                    when (section) {
                        TvSection.LIVE -> repo.liveCategories()
                        TvSection.MOVIES -> repo.vodCategories()
                        TvSection.SERIES -> repo.seriesCategories()
                        TvSection.SETTINGS -> emptyList()
                    }
                }
                val visible = applyCategoryPrefs(cats)
                _state.value = _state.value.copy(categories = visible)
                selectCategory(visible.firstOrNull()?.categoryId)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = "Couldn't load. Check connection.")
            }
        }
    }

    /** Apply hidden + ordering settings to a category list. */
    private fun applyCategoryPrefs(cats: List<Category>): List<Category> {
        val s = _state.value.settings
        val visible = cats.filterNot { it.categoryId in s.hiddenCategories }
        if (s.categoryOrder.isEmpty()) return visible
        val orderIndex = s.categoryOrder.withIndex().associate { (i, id) -> id to i }
        return visible.sortedBy { orderIndex[it.categoryId] ?: Int.MAX_VALUE }
    }

    fun selectCategory(categoryId: String?) {
        _state.value = _state.value.copy(selectedCategory = categoryId, loading = true, nowNext = emptyMap())
        viewModelScope.launch {
            try {
                val section = _state.value.section
                val items = when (section) {
                    TvSection.LIVE -> repo.liveStreams(categoryId).map {
                        TvContentItem(it.streamId, it.name, it.streamIcon, num = it.num, section = TvSection.LIVE)
                    }
                    TvSection.MOVIES -> repo.vodStreams(categoryId).map {
                        TvContentItem(it.streamId, it.name, it.streamIcon, it.rating,
                            containerExt = it.containerExtension, section = TvSection.MOVIES)
                    }
                    TvSection.SERIES -> repo.series(categoryId).map {
                        TvContentItem(it.seriesId, it.name, it.cover, it.rating, section = TvSection.SERIES)
                    }
                    TvSection.SETTINGS -> emptyList()
                }
                _state.value = _state.value.copy(items = items, loading = false)
                if (section == TvSection.LIVE && _state.value.settings.autoFetchEpg) {
                    fetchEpg(items.map { it.id })
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = "Couldn't load content.")
            }
        }
    }

    private fun fetchEpg(ids: List<Int>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val map = repo.batchNowNext(ids)
            _state.value = _state.value.copy(nowNext = map)
        }
    }

    fun refreshEpg() {
        if (_state.value.section == TvSection.LIVE) fetchEpg(_state.value.items.map { it.id })
    }

    fun onQuery(q: String) { _state.value = _state.value.copy(query = q) }

    fun visibleItems(): List<TvContentItem> {
        val q = _state.value.query.trim()
        val all = _state.value.items
        return if (q.isEmpty()) all
        else all.filter { it.title.contains(q, ignoreCase = true) || it.num?.toString() == q }
    }

    fun nowNextFor(id: Int): NowNext? = _state.value.nowNext[id]

    suspend fun epgFor(id: Int) = repo.epgCached(id)

    // ---- Settings mutations ----
    fun setAutoFetchEpg(v: Boolean) = updateSettings { settingsRepo.setAutoFetchEpg(v); it.copy(autoFetchEpg = v) }
    fun setEpgRefreshMinutes(v: Int) = updateSettings { settingsRepo.setEpgRefreshMinutes(v); it.copy(epgRefreshMinutes = v) }
    fun setBufferSeconds(v: Int) = updateSettings { settingsRepo.setBufferSeconds(v); it.copy(bufferSeconds = v) }

    fun toggleCategoryHidden(categoryId: String) {
        updateSettings { s ->
            val hidden = s.hiddenCategories.toMutableSet()
            if (!hidden.add(categoryId)) hidden.remove(categoryId)
            settingsRepo.setHiddenCategories(hidden)
            s.copy(hiddenCategories = hidden)
        }
    }

    /** Categories for the settings manager (unfiltered, from cache). */
    fun allCategoriesForSection(section: TvSection): List<Category> =
        catCache[section] ?: emptyList()

    private fun updateSettings(block: suspend (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val updated = block(_state.value.settings)
            _state.value = _state.value.copy(settings = updated)
        }
    }

    fun logout() {
        Session.host = ""; Session.username = ""; Session.password = ""; Session.userInfo = null
        repo.clearCache()
        catCache.clear()
        viewModelScope.launch { creds.clear() }
        _state.value = TvUiState(settings = _state.value.settings)
    }
}
