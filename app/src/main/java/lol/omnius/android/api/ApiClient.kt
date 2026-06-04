package lol.omnius.android.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object ApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private var baseUrl: String = "https://api.omnius.lol/"
    private var retrofit: Retrofit? = null
    private var api: OmniusApi? = null

    private fun buildRetrofit(): Retrofit {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    fun getApi(): OmniusApi {
        if (api == null) {
            retrofit = buildRetrofit()
            api = retrofit!!.create(OmniusApi::class.java)
        }
        return api!!
    }

    fun setBaseUrl(url: String) {
        val normalizedUrl = if (url.endsWith("/")) url else "$url/"
        if (normalizedUrl != baseUrl) {
            baseUrl = normalizedUrl
            retrofit = null
            api = null
        }
    }

    fun getBaseUrl(): String = baseUrl

    fun subtitleDownloadUrl(downloadUrl: String): String {
        return "${baseUrl}api/v2/subtitles/download?url=${java.net.URLEncoder.encode(downloadUrl, "UTF-8")}"
    }
}
