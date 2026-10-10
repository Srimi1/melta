package com.melta.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

class MeltaApp : Application(), ImageLoaderFactory {

    /** Thumbnails in the picker are tiny, so keep Coil's caches small too. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.10).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("thumbs"))
                .maxSizeBytes(32L * 1024 * 1024)
                .build()
        }
        .crossfade(false)
        .respectCacheHeaders(false)
        .build()
}
