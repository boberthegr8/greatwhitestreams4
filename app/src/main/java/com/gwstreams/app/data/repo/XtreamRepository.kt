package com.gwstreams.app.data.repo

import android.content.Context
import com.gwstreams.app.data.api.XtreamApi
import com.gwstreams.app.data.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

class XtreamRepository(private val context: Context? = null) {

    private var cachedApi: XtreamApi? = null
    private var cachedHost: String? = null

    private fun apiFor(host: String): XtreamApi {
        if (cachedApi != null && cachedHost == host) return cachedApi!!
        val base = if (host.endsWith("/")) host else "$host/"
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        
        // Ideas 10-17: Network Carpet Bombing
        val cacheSize = 15L * 1024 * 1024 // 15MB
        val dispatcher = okhttp3.Dispatcher().apply { maxRequestsPerHost = 15 }
        val pool = okhttp3.ConnectionPool(10, 5, TimeUnit.MINUTES)
        
        val builder = OkHttpClient.Builder()
            .cache(okhttp3.Cache(cacheDirectory(), cacheSize))
            .dispatcher(dispatcher)
            .connectionPool(pool)
            .retryOnConnectionFailure(false) // Let ExoPlayer handle retries, don't block threads
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(logging)
            
        val client = builder.build()
        val gson = com.google.gson.GsonBuilder().setLenient().create() // Accept dirty JSON from IPTV providers
        val api = Retrofit.Builder()
            .baseUrl(base)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(XtreamApi::class.java)
        cachedApi = api
        cachedHost = host
        return api
    }

    private fun cacheDirectory(): File {
        val baseDir = context?.cacheDir
            ?: System.getProperty("java.io.tmpdir")?.let(::File)
            ?: File(".")
        return File(baseDir, "okhttp_cache").apply { mkdirs() }
    }

    fun normalizeHost(raw: String): String {
        var h = raw.trim().removeSuffix("/")
        val lower = h.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            val idx = h.indexOf("://")
            h = h.substring(0, idx).lowercase() + h.substring(idx)
        } else {
            h = "http://$h"
        }
        return h
    }

    suspend fun login(rawHost: String, user: String, pass: String): Result<AuthResponse> = try {
        val host = normalizeHost(rawHost)
        val resp = apiFor(host).authenticate(user, pass)
        if (resp.userInfo?.status.equals("Active", ignoreCase = true) ||
            resp.userInfo?.auth() == true
        ) {
            Session.host = host
            Session.username = user
            Session.password = pass
            Session.userInfo = resp.userInfo
            Result.success(resp)
        } else {
            Result.failure(Exception("Login failed — check your details and try again."))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun UserInfo.auth(): Boolean = !username.isNullOrBlank()

    private data class Cached<T>(val data: T, val at: Long)
    private val listCache = mutableMapOf<String, Cached<List<*>>>()
    private val ttlMs = 5 * 60 * 1000L

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> cachedList(key: String, loader: suspend () -> List<T>): List<T> {
        val now = System.currentTimeMillis()
        val hit = listCache[key]
        if (hit != null && now - hit.at < ttlMs) return hit.data as List<T>
        val fresh = loader()
        listCache[key] = Cached(fresh, now)
        return fresh
    }

    fun clearCache() { listCache.clear(); epgCache.clear() }

    suspend fun liveCategories() = cachedList("live_cats") {
        apiFor(Session.host).getLiveCategories(Session.username, Session.password)
    }

    suspend fun liveStreams(categoryId: String?) = cachedList("live_$categoryId") {
        apiFor(Session.host).getLiveStreams(Session.username, Session.password, categoryId = categoryId)
    }

    suspend fun vodCategories() = cachedList("vod_cats") {
        apiFor(Session.host).getVodCategories(Session.username, Session.password)
    }

    suspend fun vodStreams(categoryId: String?) = cachedList("vod_$categoryId") {
        apiFor(Session.host).getVodStreams(Session.username, Session.password, categoryId = categoryId)
    }

    suspend fun seriesCategories() = cachedList("series_cats") {
        apiFor(Session.host).getSeriesCategories(Session.username, Session.password)
    }

    suspend fun series(categoryId: String?) = cachedList("series_$categoryId") {
        apiFor(Session.host).getSeries(Session.username, Session.password, categoryId = categoryId)
    }

    suspend fun shortEpg(streamId: Int): List<EpgListing> =
        apiFor(Session.host).getShortEpg(
            Session.username, Session.password, streamId = streamId
        ).listings ?: emptyList()

    private data class EpgCached(val listings: List<EpgListing>, val at: Long)
    private val epgCache = mutableMapOf<Int, EpgCached>()
    private val epgTtlMs = 10 * 60 * 1000L

    suspend fun epgCached(streamId: Int): List<EpgListing> {
        val now = System.currentTimeMillis()
        epgCache[streamId]?.let { if (now - it.at < epgTtlMs) return it.listings }
        val fresh = runCatching { shortEpg(streamId) }.getOrDefault(emptyList())
        epgCache[streamId] = EpgCached(fresh, now)
        return fresh
    }

    suspend fun batchNowNext(streamIds: List<Int>): Map<Int, NowNext> = coroutineScope {
        val sem = Semaphore(6)
        streamIds.map { id ->
            async {
                sem.withPermit { id to EpgParser.nowNext(epgCached(id)) }
            }
        }.awaitAll().toMap()
    }

    fun inferLiveMimeType(url: String): String? {
        val normalized = url.substringBefore('?').lowercase(Locale.US)
        return when {
            normalized.endsWith(".m3u8") -> androidx.media3.common.MimeTypes.APPLICATION_M3U8
            normalized.endsWith(".ts") || normalized.contains("/live/") -> androidx.media3.common.MimeTypes.VIDEO_MP2T
            else -> null
        }
    }

    suspend fun vodInfo(vodId: Int): VodInfoResponse =
        apiFor(Session.host).getVodInfo(Session.username, Session.password, vodId = vodId)

    suspend fun seriesInfo(seriesId: Int): SeriesInfoResponse =
        apiFor(Session.host).getSeriesInfo(Session.username, Session.password, seriesId = seriesId)
}
