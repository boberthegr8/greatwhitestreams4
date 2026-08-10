package com.gwstreams.tv.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gwstreams.app.data.model.SeriesInfoResponse
import com.gwstreams.app.data.model.Category
import com.gwstreams.app.data.model.LiveStream
import com.gwstreams.tv.BuildConfig
import com.gwstreams.tv.data.CrashReporter
import com.gwstreams.tv.data.DnsBootstrapper
import com.gwstreams.tv.data.Updater
import com.gwstreams.tv.data.UserStateRepository
import com.gwstreams.app.data.repo.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

enum class TvSection { SEARCH, LIVE, MOVIES, SERIES, SETTINGS }

@androidx.compose.runtime.Immutable
data class TvContentItem(
    val id: Int,
    val title: String,
    val image: String?,
    val rating: String? = null,
    val containerExt: String? = null,
    val num: Int? = null,
    var hasCatchup: Boolean = false,
    val section: TvSection
)

@androidx.compose.runtime.Immutable
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
    val savedPass: String = "",
    val savedProvider: String? = null,
    val seriesInfo: SeriesInfoResponse? = null,
    val appUpdate: AppUpdateState = AppUpdateState(),
    val lastPlayedChannelId: Int? = null,
    val favorites: Set<Int> = emptySet(),
    val recentChannelIds: List<Int> = emptyList(),
    val lastCrashSummary: CrashReporter.CrashSummary? = null,
    val lastCrashDetails: String? = null
)

@androidx.compose.runtime.Immutable
data class AppUpdateState(
    val checking: Boolean = false,
    val availableUpdate: Updater.UpdateInfo? = null,
    val dismissedVersionCode: Int? = null,
    val inProgress: Boolean = false,
    val installPermissionRequired: Boolean = false,
    val downloadPercent: Int? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null
) {
    val isDialogVisible: Boolean
        get() = availableUpdate != null && dismissedVersionCode != availableUpdate.versionCode
}

class TvViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = XtreamRepository(app)
    private val settingsRepo = SettingsRepository(app)
    private val creds = com.gwstreams.tv.data.TvCredentialStore(app)
    private val userStateRepo = UserStateRepository(app)

    private val _state = MutableStateFlow(TvUiState())
    val state: StateFlow<TvUiState> = _state

    private val catCache = mutableMapOf<TvSection, List<Category>>()
    private val liveItemsCache = mutableMapOf<String?, List<TvContentItem>>()
    private val liveNowNextCache = mutableMapOf<Int, NowNext>()
    private val livePrefetchMutex = Mutex()
    private var livePrefetchJob: Job? = null
    private var hasCheckedForUpdate = false

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                settings = loadSettingsSafely(),
                lastPlayedChannelId = loadLastPlayedChannelIdSafely(),
                lastCrashSummary = readLastCrashSummarySafely(app)
            )
            refreshUserState()

            // Surface any saved credentials so the login screen can prefill,
            // and attempt a silent auto-login so the app stays signed in.
            runCatching { creds.load() }
                .onFailure { error ->
                    Log.w(TAG, "Unable to load saved TV credentials for auto-login; continuing without them", error)
                }
                .getOrNull()?.let { c ->
                _state.value = _state.value.copy(
                    savedHost = c.host,
                    savedUser = c.user,
                    savedPass = c.pass,
                    savedProvider = c.provider
                )
                attemptAutoLogin(c)
            }
        }
    }

    private fun attemptAutoLogin(c: com.gwstreams.tv.data.TvCredentialStore.Creds) {
        _state.value = _state.value.copy(autoLoggingIn = true)
        viewModelScope.launch {
            // Provider-aware bootstrap overrides only; otherwise keep the saved host.
            val dnsHost = DnsBootstrapper.fetchLatestPortalHost(c.provider)
            if (dnsHost == "MAINTENANCE_MODE_ACTIVE") {
                _state.value = _state.value.copy(
                    autoLoggingIn = false,
                    error = "Provider Maintenance. Service will return shortly."
                )
                return@launch
            }
            val finalHost = dnsHost ?: c.host

            val result = repo.login(finalHost, c.user, c.pass)
            result.fold(
                onSuccess = {
                    if (finalHost != c.host) creds.save(repo.normalizeHost(finalHost), c.user, c.pass, c.provider)
                    _state.value = _state.value.copy(autoLoggingIn = false, loggedIn = true)
                    selectSection(TvSection.LIVE)
                    if (_state.value.settings.autoFetchEpg) {
                        prefetchAllLiveContent()
                    }
                },
                onFailure = {
                    // Saved creds failed (expired/changed) — fall back to the login screen.
                    _state.value = _state.value.copy(autoLoggingIn = false)
                }
            )
        }
    }

    val expiry: String? get() = Session.userInfo?.expDate

    fun checkForAppUpdate(force: Boolean = false) {
        if (!force && (hasCheckedForUpdate || _state.value.appUpdate.checking)) return
        hasCheckedForUpdate = true
        _state.value = _state.value.copy(
            appUpdate = _state.value.appUpdate.copy(
                checking = true,
                errorMessage = null,
                statusMessage = null
            )
        )
        viewModelScope.launch {
            Updater.checkForUpdateResult(BuildConfig.VERSION_CODE)
                .onSuccess { update ->
                    _state.value = _state.value.copy(
                        appUpdate = _state.value.appUpdate.copy(
                            checking = false,
                            availableUpdate = update,
                            dismissedVersionCode = if (force) null else _state.value.appUpdate.dismissedVersionCode,
                            installPermissionRequired = false,
                            downloadPercent = null,
                            statusMessage = when {
                                update != null -> null
                                force -> "You're up to date."
                                else -> null
                            },
                            errorMessage = null
                        )
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        appUpdate = _state.value.appUpdate.copy(
                            checking = false,
                            availableUpdate = null,
                            installPermissionRequired = false,
                            downloadPercent = null,
                            statusMessage = null,
                            errorMessage = error.message ?: "Couldn't check for updates."
                        )
                    )
                }
        }
    }

    fun dismissUpdatePrompt() {
        val available = _state.value.appUpdate.availableUpdate ?: return
        _state.value = _state.value.copy(
            appUpdate = _state.value.appUpdate.copy(
                dismissedVersionCode = available.versionCode,
                statusMessage = null,
                errorMessage = null,
                installPermissionRequired = false
            )
        )
    }

    fun beginAppUpdate(context: android.content.Context) {
        val update = _state.value.appUpdate.availableUpdate ?: return
        if (!Updater.canInstallPackages(context)) {
            _state.value = _state.value.copy(
                appUpdate = _state.value.appUpdate.copy(
                    installPermissionRequired = true,
                    errorMessage = null,
                    statusMessage = "Allow installs from this app, then return and press Update again.",
                    inProgress = false
                )
            )
            Updater.openUnknownAppSettings(context)
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(
                appUpdate = _state.value.appUpdate.copy(
                    inProgress = true,
                    installPermissionRequired = false,
                    downloadPercent = 0,
                    errorMessage = null,
                    statusMessage = "Downloading ${update.versionName}…"
                )
            )
            Updater.downloadAndInstall(context, update) { percent ->
                _state.value = _state.value.copy(
                    appUpdate = _state.value.appUpdate.copy(
                        inProgress = true,
                        downloadPercent = percent,
                        statusMessage = "Downloading ${update.versionName}… $percent%"
                    )
                )
            }.onSuccess {
                _state.value = _state.value.copy(
                    appUpdate = _state.value.appUpdate.copy(
                        inProgress = false,
                        statusMessage = "Installer opened. Install over the current app to keep your data.",
                        errorMessage = null
                    )
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    appUpdate = _state.value.appUpdate.copy(
                        inProgress = false,
                        downloadPercent = null,
                        statusMessage = null,
                        errorMessage = error.message ?: "Update failed."
                    )
                )
            }
        }
    }

    fun login(
        host: String,
        user: String,
        pass: String,
        remember: Boolean,
        provider: String? = null,
        onResult: (Boolean, String?) -> Unit
    ) {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            // Manual/preset host entry stays authoritative unless a provider-aware feed exists.
            val dnsHost = DnsBootstrapper.fetchLatestPortalHost(provider)
            if (dnsHost == "MAINTENANCE_MODE_ACTIVE") {
                _state.value = _state.value.copy(loading = false, error = "Provider Maintenance. Service will return shortly.")
                onResult(false, "Maintenance Mode")
                return@launch
            }
            val finalHost = if (dnsHost.isNullOrEmpty()) host else dnsHost
            
            val result = repo.login(finalHost, user, pass)
            result.fold(
                onSuccess = {
                    if (remember) creds.save(repo.normalizeHost(finalHost), user, pass, provider)
                    _state.value = _state.value.copy(loading = false, loggedIn = true)
                    selectSection(TvSection.LIVE)
                    if (_state.value.settings.autoFetchEpg) {
                        prefetchAllLiveContent()
                    }
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
        if (section != TvSection.LIVE) {
            livePrefetchJob?.cancel()
        }
        if (section == TvSection.SETTINGS) {
            _state.value = _state.value.copy(section = section)
            return
        }
        if (section == TvSection.SEARCH) {
            _state.value = _state.value.copy(section = section, categories = emptyList(), selectedCategory = null, items = emptyList(), query = "", error = null)
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
                        else -> emptyList()
                    }
                }
                val visible = applyCategoryPrefs(cats)
                val liveCategories = if (section == TvSection.LIVE) withUserStateCategories(visible) else visible
                _state.value = _state.value.copy(categories = liveCategories)
                if (section == TvSection.LIVE) {
                    prefetchAllLiveContent(visible)
                }
                selectCategory(
                    when {
                        section != TvSection.LIVE -> liveCategories.firstOrNull()?.categoryId
                        visible.isNotEmpty() -> visible.first().categoryId
                        else -> liveCategories.firstOrNull()?.categoryId
                    }
                )
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
        val section = _state.value.section
        if (section == TvSection.LIVE) {
            val cachedItems = cachedDisplayItemsFor(categoryId)
            _state.value = _state.value.copy(
                selectedCategory = categoryId,
                loading = cachedItems == null,
                items = cachedItems ?: emptyList(),
                nowNext = cachedItems?.associateNotNull { item ->
                    liveNowNextCache[item.id]?.let { item.id to it }
                } ?: emptyMap(),
                error = null
            )
        } else {
            _state.value = _state.value.copy(selectedCategory = categoryId, loading = true, nowNext = emptyMap())
        }
        viewModelScope.launch {
            try {
                val items = when (section) {
                    TvSection.LIVE -> getOrLoadDisplayedLiveItems(categoryId)
                    TvSection.MOVIES -> repo.vodStreams(categoryId).map {
                        TvContentItem(it.streamId, it.name, it.streamIcon, it.rating,
                            containerExt = it.containerExtension, section = TvSection.MOVIES)
                    }
                    TvSection.SERIES -> repo.series(categoryId).map {
                        TvContentItem(it.seriesId, it.name, it.cover, it.rating, section = TvSection.SERIES)
                    }
                    else -> emptyList()
                }
                if (_state.value.selectedCategory == categoryId && _state.value.section == section) {
                    _state.value = _state.value.copy(items = items, loading = false)
                }
                if (section == TvSection.LIVE) {
                    if (_state.value.settings.autoFetchEpg) {
                        refreshCachedNowNext(categoryId, items)
                    } else if (_state.value.selectedCategory == categoryId && _state.value.section == section) {
                        _state.value = _state.value.copy(nowNext = emptyMap())
                    }
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
        if (_state.value.section != TvSection.LIVE) return
        viewModelScope.launch {
            refreshCachedNowNext(_state.value.selectedCategory, _state.value.items, force = true)
        }
    }

    fun onQuery(q: String) { 
        _state.value = _state.value.copy(query = q) 
        if (_state.value.section == TvSection.SEARCH) {
            if (q.trim().length >= 3) {
                 performGlobalSearch(q)
            } else {
                 _state.value = _state.value.copy(items = emptyList())
            }
        }
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    private fun performGlobalSearch(query: String) {
        val q = query.trim()
        if (q.length < 3) return
        
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val liveDef = async { repo.liveStreams(null) }
                val vodDef = async { repo.vodStreams(null) }
                val seriesDef = async { repo.series(null) }

                val live = liveDef.await().filter { it.name.contains(q, ignoreCase = true) }.map {
                    TvContentItem(it.streamId, it.name, it.streamIcon, num = it.num, hasCatchup = it.tvArchive == 1, section = TvSection.LIVE)
                }
                val vod = vodDef.await().filter { it.name.contains(q, ignoreCase = true) }.map {
                    TvContentItem(it.streamId, it.name, it.streamIcon, it.rating, containerExt = it.containerExtension, section = TvSection.MOVIES)
                }
                val series = seriesDef.await().filter { it.name.contains(q, ignoreCase = true) }.map {
                    TvContentItem(it.seriesId, it.name, it.cover, it.rating, section = TvSection.SERIES)
                }

                _state.value = _state.value.copy(
                    items = live + vod + series,
                    loading = false
                )
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _state.value = _state.value.copy(loading = false, error = "Search failed")
                }
            }
        }
    }

    fun loadSeriesDetails(seriesId: Int) {
        _state.value = _state.value.copy(loading = true, error = null, seriesInfo = null)
        viewModelScope.launch {
            try {
                val info = repo.seriesInfo(seriesId)
                _state.value = _state.value.copy(seriesInfo = info, loading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = "Failed to load series")
            }
        }
    }

    fun visibleItems(): List<TvContentItem> {
        val q = _state.value.query.trim()
        val all = _state.value.items
        return if (q.isEmpty() || _state.value.section == TvSection.SEARCH) all
        else all.filter { it.title.contains(q, ignoreCase = true) || it.num?.toString() == q }
    }

    fun nowNextFor(id: Int): NowNext? = _state.value.nowNext[id]

    suspend fun epgFor(id: Int) = repo.epgCached(id)

    // ---- Settings mutations ----
    fun setAutoFetchEpg(v: Boolean) = updateSettings { settingsRepo.setAutoFetchEpg(v); it.copy(autoFetchEpg = v) }
    fun setEpgRefreshMinutes(v: Int) = updateSettings { settingsRepo.setEpgRefreshMinutes(v); it.copy(epgRefreshMinutes = v) }
    fun setBufferSeconds(v: Int) = updateSettings { settingsRepo.setBufferSeconds(v); it.copy(bufferSeconds = v) }
    fun setCableBoxMode(v: Boolean) = updateSettings { settingsRepo.setCableBoxMode(v); it.copy(cableBoxMode = v) }
    fun setMomMode(v: Boolean) = updateSettings { settingsRepo.setMomMode(v); it.copy(momMode = v) }
    fun openUnknownSourcesSettings(context: android.content.Context) {
        _state.value = _state.value.copy(
            appUpdate = _state.value.appUpdate.copy(
                installPermissionRequired = true,
                errorMessage = null,
                statusMessage = "Open the installer permission screen, enable this app, then return and retry."
            )
        )
        Updater.openUnknownAppSettings(context)
    }

    fun nukeAndRestart(context: android.content.Context) {
        viewModelScope.launch {
            try {
                // Clear ExoPlayer cache
                val coilCache = context.cacheDir.resolve("image_cache")
                if (coilCache.exists()) coilCache.deleteRecursively()
                
                // Clear App Data
                logout()
                settingsRepo.setAutoFetchEpg(true)
                settingsRepo.setCableBoxMode(false)
                settingsRepo.setMomMode(false)
                
                // Trigger hard restart using AlarmManager
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                val componentName = intent?.component
                val mainIntent = android.content.Intent.makeRestartActivityTask(componentName)
                context.startActivity(mainIntent)
                Runtime.getRuntime().exit(0)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Failed to clear cache")
            }
        }
    }

    fun loadCrashDetails() {
        val app = getApplication<Application>()
        _state.value = _state.value.copy(
            lastCrashSummary = readLastCrashSummarySafely(app),
            lastCrashDetails = readLastCrashDetailsSafely(app)
        )
    }

    fun clearCrashReport() {
        CrashReporter.clearLastCrash(getApplication())
        _state.value = _state.value.copy(lastCrashSummary = null, lastCrashDetails = null)
    }

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
            publishCurrentLiveSelection(forceLoading = false)
        }
    }

    fun logout() {
        Session.host = ""; Session.username = ""; Session.password = ""; Session.userInfo = null
        repo.clearCache()
        catCache.clear()
        liveItemsCache.clear()
        liveNowNextCache.clear()
        livePrefetchJob?.cancel()
        viewModelScope.launch { creds.clear() }
        _state.value = TvUiState(
            settings = _state.value.settings,
            lastCrashSummary = _state.value.lastCrashSummary,
            lastCrashDetails = _state.value.lastCrashDetails
        )
    }

    fun onPlayItem(item: TvContentItem) {
        if (item.section == TvSection.LIVE) {
            _state.value = _state.value.copy(lastPlayedChannelId = item.id)
            viewModelScope.launch {
                try {
                    userStateRepo.pushRecent(item.id)
                    userStateRepo.recordLivePlaybackStart(
                        channelId = item.id,
                        title = item.title,
                        artworkUrl = item.image
                    )
                    refreshUserState()
                    publishCurrentLiveSelection(forceLoading = false)
                } catch (error: Exception) {
                    Log.w(TAG, "Ignoring live playback state persistence failure for channelId=${item.id}", error)
                }
            }
        }
    }

    fun toggleFavorite(channelId: Int) {
        viewModelScope.launch {
            try {
                userStateRepo.toggleFavorite(channelId)
                refreshUserState()
                publishCurrentLiveSelection(forceLoading = false)
            } catch (error: Exception) {
                Log.w(TAG, "Ignoring favorite toggle persistence failure for channelId=$channelId", error)
            }
        }
    }

    private suspend fun getOrLoadDisplayedLiveItems(categoryId: String?): List<TvContentItem> {
        if (isUserStateCategory(categoryId)) {
            ensureUserStateCategorySeeded()
            return displayedItemsForCategory(categoryId)
        }
        val baseItems = getOrLoadLiveItems(categoryId)
        return applyLiveFilters(baseItems)
    }

    private suspend fun getOrLoadLiveItems(categoryId: String?): List<TvContentItem> {
        liveItemsCache[categoryId]?.let { return it }
        val streams = repo.liveStreams(categoryId)
        val items = mapLiveStreams(streams)
        liveItemsCache[categoryId] = items
        return items
    }

    private suspend fun refreshCachedNowNext(
        categoryId: String?,
        items: List<TvContentItem>,
        force: Boolean = false
    ) {
        if (items.isEmpty()) {
            if (_state.value.selectedCategory == categoryId && _state.value.section == TvSection.LIVE) {
                _state.value = _state.value.copy(nowNext = emptyMap(), loading = false)
            }
            return
        }
        val map = if (!force) {
            items.associateNotNull { item -> liveNowNextCache[item.id]?.let { item.id to it } }
        } else {
            emptyMap()
        }
        if (map.size == items.size && _state.value.selectedCategory == categoryId && _state.value.section == TvSection.LIVE) {
            _state.value = _state.value.copy(nowNext = map, loading = false)
            return
        }

        val fetched = repo.batchNowNext(items.map { it.id })
        livePrefetchMutex.withLock {
            liveNowNextCache.putAll(fetched)
        }
        if (_state.value.selectedCategory == categoryId && _state.value.section == TvSection.LIVE) {
            _state.value = _state.value.copy(
                nowNext = items.associateNotNull { item -> liveNowNextCache[item.id]?.let { item.id to it } },
                loading = false
            )
        }
    }

    private fun prefetchAllLiveContent(categoriesOverride: List<Category>? = null) {
        if (!_state.value.loggedIn) return
        livePrefetchJob?.cancel()
        livePrefetchJob = viewModelScope.launch(Dispatchers.IO + SupervisorJob()) {
            val categories = categoriesOverride ?: catCache[TvSection.LIVE] ?: runCatching { repo.liveCategories() }.getOrDefault(emptyList())
            if (categories.isEmpty()) return@launch
            val visible = applyCategoryPrefs(categories)
            val semaphore = Semaphore(3)
            coroutineScope {
                visible.map { category ->
                    async {
                        semaphore.withPermit {
                            if (!isActive) return@withPermit
                            val streams = runCatching { repo.liveStreams(category.categoryId) }.getOrElse { return@withPermit }
                            val items = mapLiveStreams(streams)
                            livePrefetchMutex.withLock {
                                liveItemsCache[category.categoryId] = items
                            }
                            if (_state.value.settings.autoFetchEpg && items.isNotEmpty()) {
                                val nowNext = runCatching { repo.batchNowNext(items.map { it.id }) }.getOrElse { emptyMap() }
                                livePrefetchMutex.withLock {
                                    liveNowNextCache.putAll(nowNext)
                                }
                            }
                            if (_state.value.section == TvSection.LIVE && _state.value.selectedCategory == category.categoryId) {
                                withContext(Dispatchers.Main) {
                                    val visibleItems = applyLiveFilters(items)
                                    _state.value = _state.value.copy(
                                        items = visibleItems,
                                        nowNext = visibleItems.associateNotNull { item -> liveNowNextCache[item.id]?.let { item.id to it } },
                                        loading = false
                                    )
                                }
                            } else if (_state.value.section == TvSection.LIVE && isUserStateCategory(_state.value.selectedCategory)) {
                                withContext(Dispatchers.Main) {
                                    publishCurrentLiveSelection(forceLoading = false)
                                }
                            }
                        }
                    }
                }.awaitAll()
            }
        }
    }

    private fun mapLiveStreams(streams: List<LiveStream>): List<TvContentItem> {
        return streams.map {
            TvContentItem(
                id = it.streamId,
                title = it.name,
                image = it.streamIcon,
                num = it.num,
                hasCatchup = it.tvArchive == 1,
                section = TvSection.LIVE
            )
        }
    }

    private fun withUserStateCategories(categories: List<Category>): List<Category> = buildList {
        add(Category(CATEGORY_ID_FAVORITES, "★ Favorites", null))
        add(Category(CATEGORY_ID_RECENT, "↺ Recently Watched", null))
        addAll(categories)
    }

    private fun isUserStateCategory(categoryId: String?): Boolean =
        categoryId == CATEGORY_ID_FAVORITES || categoryId == CATEGORY_ID_RECENT

    private fun cachedDisplayItemsFor(categoryId: String?): List<TvContentItem>? = when {
        categoryId == null -> null
        categoryId == CATEGORY_ID_FAVORITES -> displayedItemsForFavorites().takeIf { it.isNotEmpty() }
        categoryId == CATEGORY_ID_RECENT -> displayedItemsForRecents().takeIf { it.isNotEmpty() }
        else -> liveItemsCache[categoryId]?.let(::applyLiveFilters)
    }

    private fun displayedItemsForCategory(categoryId: String?): List<TvContentItem> = when (categoryId) {
        CATEGORY_ID_FAVORITES -> displayedItemsForFavorites()
        CATEGORY_ID_RECENT -> displayedItemsForRecents()
        else -> applyLiveFilters(liveItemsCache[categoryId].orEmpty())
    }

    private fun displayedItemsForFavorites(): List<TvContentItem> {
        val favorites = _state.value.favorites.toList()
        if (favorites.isEmpty()) return emptyList()
        return dedupeLiveItems()
            .filter { it.id in favorites }
            .sortedBy { favoritesOrder(it.id) }
    }

    private fun displayedItemsForRecents(): List<TvContentItem> {
        val recents = _state.value.recentChannelIds
        if (recents.isEmpty()) return emptyList()
        val byId = dedupeLiveItems().associateBy { it.id }
        return recents.mapNotNull(byId::get)
    }

    private fun dedupeLiveItems(): List<TvContentItem> =
        liveItemsCache.values.asSequence().flatten().associateBy { it.id }.values.toList()

    private suspend fun ensureUserStateCategorySeeded() {
        if (dedupeLiveItems().isNotEmpty()) return
        val allItems = mapLiveStreams(repo.liveStreams(null))
        liveItemsCache[null] = allItems
    }

    private fun favoritesOrder(channelId: Int): Int {
        val index = _state.value.favorites.toList().indexOf(channelId)
        return if (index >= 0) index else Int.MAX_VALUE
    }

    private suspend fun refreshUserState() {
        val favorites = runCatching { userStateRepo.favorites() }
            .onFailure { error ->
                Log.w(TAG, "Unable to load favorites from local user state; using empty set", error)
            }
            .getOrElse { emptySet() }
        val recentChannelIds = runCatching { userStateRepo.recentChannels() }
            .onFailure { error ->
                Log.w(TAG, "Unable to load recent channels from local user state; using empty list", error)
            }
            .getOrElse { emptyList() }
        _state.value = _state.value.copy(
            favorites = favorites,
            recentChannelIds = recentChannelIds
        )
    }

    private suspend fun loadSettingsSafely(): AppSettings =
        try {
            settingsRepo.load()
        } catch (error: Throwable) {
            Log.w(TAG, "Unable to load app settings; using defaults", error)
            AppSettings()
        }

    private suspend fun loadLastPlayedChannelIdSafely(): Int? =
        try {
            userStateRepo.lastPlayedChannelId()
        } catch (error: Throwable) {
            Log.w(TAG, "Unable to load last played channel id; continuing without it", error)
            null
        }

    private fun readLastCrashSummarySafely(app: Application): CrashReporter.CrashSummary? =
        runCatching { CrashReporter.readLastCrashSummary(app) }
            .onFailure { error ->
                Log.w(TAG, "Unable to read last crash summary; continuing without it", error)
            }
            .getOrNull()

    private fun readLastCrashDetailsSafely(app: Application): String? =
        runCatching { CrashReporter.readLastCrashDetails(app) }
            .onFailure { error ->
                Log.w(TAG, "Unable to read last crash details; continuing without them", error)
            }
            .getOrNull()

    private fun applyLiveFilters(items: List<TvContentItem>): List<TvContentItem> {
        if (!_state.value.settings.momMode) return items
        val favorites = _state.value.favorites
        return items.filter { it.id in favorites }
    }

    private fun publishCurrentLiveSelection(forceLoading: Boolean) {
        val current = _state.value
        if (current.section != TvSection.LIVE) return
        val selectedCategory = current.selectedCategory ?: return
        val items = displayedItemsForCategory(selectedCategory)
        _state.value = current.copy(
            items = items,
            nowNext = items.associateNotNull { item -> liveNowNextCache[item.id]?.let { item.id to it } },
            loading = forceLoading && items.isEmpty(),
            error = null
        )
    }

    private inline fun <T, K, V> Iterable<T>.associateNotNull(transform: (T) -> Pair<K, V>?): Map<K, V> =
        buildMap {
            for (item in this@associateNotNull) {
                transform(item)?.let { (key, value) -> put(key, value) }
            }
        }

    companion object {
        private const val TAG = "TvViewModel"
        private const val CATEGORY_ID_FAVORITES = "__favorites__"
        private const val CATEGORY_ID_RECENT = "__recent__"
    }

}
