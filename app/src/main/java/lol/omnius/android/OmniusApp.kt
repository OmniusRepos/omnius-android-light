package lol.omnius.android

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import lol.omnius.android.data.FavoritesManager
import lol.omnius.android.data.ServerManager
import lol.omnius.android.data.WatchHistoryManager
import lol.omnius.android.torrent.TorrentStreamManager

class OmniusApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        ServerManager.initialize(this)
        TorrentStreamManager.initialize(this)
        FavoritesManager.initialize(this)
        WatchHistoryManager.initialize(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                // Low-RAM (Kali) build: keep the in-memory poster cache small.
                MemoryCache.Builder(this)
                    .maxSizePercent(0.10)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .crossfade(false)
            .build()
    }
}
