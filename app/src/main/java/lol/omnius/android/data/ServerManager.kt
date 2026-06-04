package lol.omnius.android.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import lol.omnius.android.api.ApiClient

@Serializable
data class Server(
    val name: String,
    val url: String,
)

object ServerManager {
    private const val PREFS_NAME = "omnius_servers"
    private const val KEY_SERVERS = "servers"
    private const val KEY_ACTIVE = "active_url"

    private lateinit var prefs: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true }

    private val _servers = MutableStateFlow<List<Server>>(emptyList())
    val servers: StateFlow<List<Server>> = _servers.asStateFlow()

    private val _activeUrl = MutableStateFlow("")
    val activeUrl: StateFlow<String> = _activeUrl.asStateFlow()

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _servers.value = loadServers()
        _activeUrl.value = prefs.getString(KEY_ACTIVE, "") ?: ""

        // If no servers, add default
        if (_servers.value.isEmpty()) {
            val defaultServer = Server("Omnius", "https://api.omnius.lol/")
            _servers.value = listOf(defaultServer)
            saveServers()
        }

        // If no active, use first
        if (_activeUrl.value.isBlank() && _servers.value.isNotEmpty()) {
            setActive(_servers.value.first().url)
        } else if (_activeUrl.value.isNotBlank()) {
            ApiClient.setBaseUrl(_activeUrl.value)
        }
    }

    fun addServer(name: String, url: String) {
        val normalized = if (url.endsWith("/")) url else "$url/"
        // Don't add duplicates
        if (_servers.value.any { it.url == normalized }) return
        _servers.value = _servers.value + Server(name, normalized)
        saveServers()
    }

    fun removeServer(url: String) {
        _servers.value = _servers.value.filter { it.url != url }
        saveServers()
        // If we removed the active one, switch to first
        if (_activeUrl.value == url && _servers.value.isNotEmpty()) {
            setActive(_servers.value.first().url)
        }
    }

    fun setActive(url: String) {
        _activeUrl.value = url
        prefs.edit().putString(KEY_ACTIVE, url).apply()
        ApiClient.setBaseUrl(url)
    }

    fun isActive(url: String): Boolean = _activeUrl.value == url

    private fun loadServers(): List<Server> {
        val raw = prefs.getString(KEY_SERVERS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<Server>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveServers() {
        prefs.edit().putString(KEY_SERVERS, json.encodeToString(_servers.value)).apply()
    }
}
