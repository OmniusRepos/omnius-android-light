package lol.omnius.android.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class FavMovie(
    val id: Int,
    val title: String,
    val image: String,
    val year: Int,
    val rating: Double,
)

@Serializable
data class FavSeries(
    val id: Int,
    val title: String,
    val image: String,
    val year: Int,
    val rating: Double,
)

@Serializable
data class FavChannel(
    val id: String,
    val name: String,
    val logo: String? = null,
    val streamUrl: String? = null,
    val country: String? = null,
)

@Serializable
data class FavCountry(
    val code: String,
    val name: String,
    val flag: String? = null,
)

object FavoritesManager {
    private const val PREFS_NAME = "omnius_favorites"
    private const val KEY_MOVIES = "fav_movies"
    private const val KEY_SERIES = "fav_series"
    private const val KEY_CHANNELS = "fav_channels"
    private const val KEY_COUNTRIES = "fav_countries"

    private lateinit var prefs: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true }

    private val _movies = MutableStateFlow<List<FavMovie>>(emptyList())
    val movies: StateFlow<List<FavMovie>> = _movies.asStateFlow()

    private val _series = MutableStateFlow<List<FavSeries>>(emptyList())
    val series: StateFlow<List<FavSeries>> = _series.asStateFlow()

    private val _channels = MutableStateFlow<List<FavChannel>>(emptyList())
    val channels: StateFlow<List<FavChannel>> = _channels.asStateFlow()

    private val _countries = MutableStateFlow<List<FavCountry>>(emptyList())
    val countries: StateFlow<List<FavCountry>> = _countries.asStateFlow()

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _movies.value = load(KEY_MOVIES)
        _series.value = load(KEY_SERIES)
        _channels.value = load(KEY_CHANNELS)
        _countries.value = load(KEY_COUNTRIES)
    }

    // --- Movies ---

    fun toggleMovie(movie: FavMovie) {
        val current = _movies.value.toMutableList()
        val idx = current.indexOfFirst { it.id == movie.id }
        if (idx >= 0) current.removeAt(idx) else current.add(0, movie)
        _movies.value = current
        save(KEY_MOVIES, current)
    }

    fun isMovieFav(id: Int): Boolean = _movies.value.any { it.id == id }

    // --- Series ---

    fun toggleSeries(series: FavSeries) {
        val current = _series.value.toMutableList()
        val idx = current.indexOfFirst { it.id == series.id }
        if (idx >= 0) current.removeAt(idx) else current.add(0, series)
        _series.value = current
        save(KEY_SERIES, current)
    }

    fun isSeriesFav(id: Int): Boolean = _series.value.any { it.id == id }

    // --- Channels ---

    fun toggleChannel(channel: FavChannel) {
        val current = _channels.value.toMutableList()
        val idx = current.indexOfFirst { it.id == channel.id }
        if (idx >= 0) current.removeAt(idx) else current.add(0, channel)
        _channels.value = current
        save(KEY_CHANNELS, current)
    }

    fun isChannelFav(id: String): Boolean = _channels.value.any { it.id == id }

    // --- Countries ---

    fun toggleCountry(country: FavCountry) {
        val current = _countries.value.toMutableList()
        val idx = current.indexOfFirst { it.code == country.code }
        if (idx >= 0) current.removeAt(idx) else current.add(0, country)
        _countries.value = current
        save(KEY_COUNTRIES, current)
    }

    fun isCountryFav(code: String): Boolean = _countries.value.any { it.code == code }

    // --- Persistence ---

    private inline fun <reified T> load(key: String): List<T> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private inline fun <reified T> save(key: String, list: List<T>) {
        prefs.edit().putString(key, json.encodeToString(list)).apply()
    }
}
