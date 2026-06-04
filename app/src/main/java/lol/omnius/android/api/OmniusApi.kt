package lol.omnius.android.api

import lol.omnius.android.data.model.*
import retrofit2.http.*

interface OmniusApi {

    // --- Movies ---

    @GET("api/v2/list_movies.json")
    suspend fun listMovies(
        @Query("limit") limit: Int = 20,
        @Query("page") page: Int = 1,
        @Query("genre") genre: String? = null,
        @Query("sort_by") sortBy: String? = null,
        @Query("order_by") orderBy: String? = null,
        @Query("minimum_rating") minimumRating: Int? = null,
        @Query("query_term") queryTerm: String? = null,
        @Query("year") year: Int? = null,
    ): MovieListResponse

    @GET("api/v2/search_online.json")
    suspend fun searchMoviesOnline(
        @Query("query_term") queryTerm: String,
        @Query("limit") limit: Int = 20,
    ): MovieListResponse

    @GET("api/v2/movie_details.json")
    suspend fun getMovieDetails(
        @Query("movie_id") movieId: Int,
        @Query("with_images") withImages: Boolean = true,
        @Query("with_cast") withCast: Boolean = true,
    ): MovieDetailResponse

    @GET("api/v2/movie_suggestions.json")
    suspend fun getMovieSuggestions(
        @Query("movie_id") movieId: Int,
    ): MovieListResponse

    @GET("api/v2/franchise_movies.json")
    suspend fun getFranchiseMovies(
        @Query("movie_id") movieId: Int,
    ): MovieListResponse

    // --- Series ---

    @GET("api/v2/list_series.json")
    suspend fun listSeries(
        @Query("limit") limit: Int = 20,
        @Query("page") page: Int = 1,
        @Query("genre") genre: String? = null,
        @Query("sort_by") sortBy: String? = null,
        @Query("order_by") orderBy: String? = null,
        @Query("minimum_rating") minimumRating: Int? = null,
        @Query("query_term") queryTerm: String? = null,
        @Query("status") status: String? = null,
        @Query("network") network: String? = null,
    ): SeriesListResponse

    @GET("api/v2/search_series_online.json")
    suspend fun searchSeriesOnline(
        @Query("query_term") queryTerm: String,
        @Query("limit") limit: Int = 20,
    ): SeriesListResponse

    @GET("api/v2/series_details.json")
    suspend fun getSeriesDetails(
        @Query("series_id") seriesId: Int,
    ): SeriesDetailResponse

    @GET("api/v2/season_episodes.json")
    suspend fun getSeasonEpisodes(
        @Query("series_id") seriesId: Int,
        @Query("season") season: Int,
    ): SeasonEpisodesResponse

    // --- Channels ---

    @GET("api/v2/list_channels.json")
    suspend fun listChannels(
        @Query("limit") limit: Int = 50,
        @Query("page") page: Int = 1,
        @Query("country") country: String? = null,
        @Query("category") category: String? = null,
        @Query("query_term") queryTerm: String? = null,
    ): ChannelListResponse

    @GET("api/v2/channel_countries.json")
    suspend fun getChannelCountries(): ChannelCountriesResponse

    @GET("api/v2/channel_categories.json")
    suspend fun getChannelCategories(): ChannelCategoriesResponse

    // --- Subtitles ---

    @GET("api/v2/subtitles/search")
    suspend fun searchSubtitles(
        @Query("imdb_id") imdbId: String,
        @Query("languages") languages: String? = null,
    ): SubtitleSearchResponse

    // --- Home ---

    @GET("api/v2/home.json")
    suspend fun getHomeData(): HomeResponse

    // --- Refresh (used by detail screens to pull fresh torrents) ---

    @POST("api/v2/refresh_movie")
    suspend fun refreshMovie(
        @Body body: Map<String, Int>,
    ): RefreshResponse

    @POST("api/v2/refresh_series")
    suspend fun refreshSeries(
        @Body body: Map<String, Int>,
    ): RefreshResponse

    // --- Config ---

    @GET("api/v2/config.json")
    suspend fun getConfig(): ConfigResponse
}
