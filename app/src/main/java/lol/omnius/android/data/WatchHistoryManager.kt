package lol.omnius.android.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class WatchEntry(
    val contentId: Int,
    val contentType: String,       // "movie" or "episode"
    val title: String,
    val image: String,
    val positionMs: Long,
    val durationMs: Long,
    val watchedAt: Long,
    val seriesId: Int? = null,
    val seriesTitle: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val torrentHash: String? = null,
    val fileIndex: Int? = null,
    val imdbCode: String? = null,
) {
    val isWatched: Boolean get() = durationMs > 0 && positionMs.toDouble() / durationMs > 0.9
    val isContinueWatching: Boolean get() = !isWatched && positionMs > 30_000
    val progressPercent: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

object WatchHistoryManager {
    private const val PREFS_NAME = "omnius_watch_history"
    private const val KEY_ENTRIES = "watch_entries"

    private lateinit var prefs: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true }

    private val _history = MutableStateFlow<List<WatchEntry>>(emptyList())
    val history: StateFlow<List<WatchEntry>> = _history.asStateFlow()

    private val _continueWatching = MutableStateFlow<List<WatchEntry>>(emptyList())
    val continueWatching: StateFlow<List<WatchEntry>> = _continueWatching.asStateFlow()

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _history.value = load()
        updateContinueWatching()
    }

    fun saveProgress(
        contentId: Int,
        contentType: String,
        title: String,
        image: String,
        positionMs: Long,
        durationMs: Long,
        seriesId: Int? = null,
        seriesTitle: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        torrentHash: String? = null,
        fileIndex: Int? = null,
        imdbCode: String? = null,
    ) {
        if (positionMs < 5_000) return // don't save if < 5s watched

        val entry = WatchEntry(
            contentId = contentId,
            contentType = contentType,
            title = title,
            image = image,
            positionMs = positionMs,
            durationMs = durationMs,
            watchedAt = System.currentTimeMillis(),
            seriesId = seriesId,
            seriesTitle = seriesTitle,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            torrentHash = torrentHash,
            fileIndex = fileIndex,
            imdbCode = imdbCode,
        )

        val current = _history.value.toMutableList()
        current.removeAll { it.contentId == contentId && it.contentType == contentType }
        current.add(0, entry)
        _history.value = current
        save(current)
        updateContinueWatching()
    }

    fun isWatched(contentId: Int, contentType: String): Boolean {
        return _history.value.any { it.contentId == contentId && it.contentType == contentType && it.isWatched }
    }

    fun getProgress(contentId: Int, contentType: String): WatchEntry? {
        return _history.value.find { it.contentId == contentId && it.contentType == contentType }
    }

    fun markWatched(contentId: Int, contentType: String) {
        val current = _history.value.toMutableList()
        val idx = current.indexOfFirst { it.contentId == contentId && it.contentType == contentType }
        if (idx >= 0) {
            val entry = current[idx]
            current[idx] = entry.copy(positionMs = entry.durationMs, watchedAt = System.currentTimeMillis())
            _history.value = current
            save(current)
            updateContinueWatching()
        }
    }

    fun clearEntry(contentId: Int, contentType: String) {
        val current = _history.value.toMutableList()
        current.removeAll { it.contentId == contentId && it.contentType == contentType }
        _history.value = current
        save(current)
        updateContinueWatching()
    }

    private fun updateContinueWatching() {
        _continueWatching.value = _history.value
            .filter { it.isContinueWatching }
            .sortedByDescending { it.watchedAt }
    }

    private fun load(): List<WatchEntry> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(list: List<WatchEntry>) {
        prefs.edit().putString(KEY_ENTRIES, json.encodeToString(list)).apply()
    }
}
