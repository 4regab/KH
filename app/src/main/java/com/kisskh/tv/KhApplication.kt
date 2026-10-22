package com.kisskh.tv

import android.app.ActivityManager
import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * Sets up the one image loader every poster and backdrop goes through: a memory cache of a quarter
 * of the app's memory, and 250 MB on disk, so a poster seen once does not download again.
 *
 * ponytail: Coil keeps its own OkHttp client. The app has no single shared client (KissKH, the other
 * sources, TMDB and Febbox each build theirs), and sharing a request queue would let a burst of
 * posters hold up API calls. Revisit if image hosts need the same headers as an API.
 */
class KhApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        val lowRam = getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
        return ImageLoader.Builder(this)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("images"))
                    .maxSizeBytes(250L * 1024 * 1024)
                    .build()
            }
            // Half the memory per picture on a weak device, in exchange for slightly coarser colour.
            .allowRgb565(lowRam)
            .build()
    }
}