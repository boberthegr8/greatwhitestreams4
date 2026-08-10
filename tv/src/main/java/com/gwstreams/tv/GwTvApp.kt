package com.gwstreams.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

class GwTvApp : Application(), ImageLoaderFactory {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        annihilateCacheIfBloated()
    }

    override fun onTerminate() {
        applicationScope.cancel()
        super.onTerminate()
    }

    private fun annihilateCacheIfBloated() {
        // Run in background to not block app startup
        applicationScope.launch {
            try {
                val coilCache = cacheDir.resolve("image_cache")
                if (coilCache.exists() && getFolderSize(coilCache) > 100 * 1024 * 1024) { // 100MB
                    coilCache.deleteRecursively()
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun getFolderSize(file: File): Long {
        var size: Long = 0
        if (file.isDirectory) {
            for (child in file.listFiles() ?: arrayOf()) {
                size += getFolderSize(child)
            }
        } else {
            size = file.length()
        }
        return size
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            .diskCache {
                val memClass = (getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager).memoryClass
                val cacheSize = if (memClass >= 256) 250L else 50L
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(cacheSize * 1024 * 1024)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .allowHardware(true)
            .bitmapConfig(android.graphics.Bitmap.Config.RGB_565) // RGB_565 half memory
            .build()
}
