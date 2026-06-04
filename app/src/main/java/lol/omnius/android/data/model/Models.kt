package lol.omnius.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- Movies ---

@Serializable
data class Movie(
    val id: Int,
    val url: String = "",
    @SerialName("imdb_code") val imdbCode: String = "",
    val title: String,
    @SerialName("title_english") val titleEnglish: String? = null,
    @SerialName("title_long") val titleLong: String = "",
    val slug: String = "",
    val year: Int = 0,
    val rating: Double = 0.0,
    val runtime: Int = 0,
    val genres: List<String> = emptyList(),
    val summary: String = "",
    @SerialName("description_full") val descriptionFull: String = "",
    val synopsis: String = "",
    @SerialName("yt_trailer_code") val ytTrailerCode: String = "",
    val language: String = "",
    @SerialName("background_image") val backgroundImage: String = "",
    @SerialName("background_image_original") val backgroundImageOriginal: String = "",
    @SerialName("small_cover_image") val smallCoverImage: String = "",
    @SerialName("medium_cover_image") val mediumCoverImage: String = "",
    @SerialName("large_cover_image") val largeCoverImage: String = "",
    val torrents: List<Torrent> = emptyList(),
    @SerialName("imdb_rating") val imdbRating: Double? = null,
    val status: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val franchise: String? = null,
)

@Serializable
data class Torrent(
    val url: String = "",
    val hash: String,
    val quality: String,
    val type: String? = null,
    @SerialName("torrent_type") val torrentType: String? = null,
    @SerialName("video_codec") val videoCodec: String? = null,
    @SerialName("audio_channels") val audioChannels: String? = null,
    val seeds: Int = 0,
    val peers: Int = 0,
    val size: String = "",
    @SerialName("size_bytes") val sizeBytes: Long = 0,
    @SerialName("date_uploaded") val dateUploaded: String = "",
)

@Serializable
data class MovieListResponse(
    val status: String = "",
    val data: MovieListData? = null,
)

@Serializable
data class MovieListData(
    @SerialName("movie_count") val movieCount: Int = 0,
    val limit: Int = 20,
    @SerialName("page_number") val pageNumber: Int = 1,
    val movies: List<Movie> = emptyList(),
)

@Serializable
data class MovieDetailResponse(
    val status: String = "",
    val data: MovieDetailData? = null,
)

@Serializable
data class MovieDetailData(
    val movie: MovieDetails? = null,
)

@Serializable
data class MovieDetails(
    val id: Int,
    val url: String = "",
    @SerialName("imdb_code") val imdbCode: String = "",
    val title: String,
    @SerialName("title_long") val titleLong: String = "",
    val year: Int = 0,
    val rating: Double = 0.0,
    val runtime: Int = 0,
    val genres: List<String> = emptyList(),
    val summary: String = "",
    @SerialName("description_full") val descriptionFull: String = "",
    val synopsis: String = "",
    @SerialName("yt_trailer_code") val ytTrailerCode: String = "",
    val language: String = "",
    @SerialName("mpa_rating") val mpaRating: String? = null,
    @SerialName("background_image") val backgroundImage: String = "",
    @SerialName("background_image_original") val backgroundImageOriginal: String = "",
    @SerialName("small_cover_image") val smallCoverImage: String = "",
    @SerialName("medium_cover_image") val mediumCoverImage: String = "",
    @SerialName("large_cover_image") val largeCoverImage: String = "",
    val torrents: List<Torrent> = emptyList(),
    val cast: List<Cast> = emptyList(),
    val director: String? = null,
    @SerialName("imdb_rating") val imdbRating: Double? = null,
    @SerialName("rotten_tomatoes") val rottenTomatoes: Int? = null,
    @SerialName("all_images") val allImages: List<String>? = null,
    val franchise: String? = null,
)

@Serializable
data class Cast(
    val name: String,
    @SerialName("character_name") val characterName: String = "",
    @SerialName("url_small_image") val urlSmallImage: String? = null,
    @SerialName("imdb_code") val imdbCode: String = "",
)

// --- Series ---

@Serializable
data class Series(
    val id: Int,
    @SerialName("imdb_code") val imdbCode: String = "",
    @SerialName("tvdb_id") val tvdbId: Int? = null,
    val title: String,
    @SerialName("title_slug") val titleSlug: String = "",
    val year: Int = 0,
    @SerialName("end_year") val endYear: Int? = null,
    val rating: Double = 0.0,
    val runtime: Int = 0,
    val genres: List<String> = emptyList(),
    val summary: String = "",
    val status: String = "",
    val network: String? = null,
    @SerialName("poster_image") val posterImage: String = "",
    @SerialName("background_image") val backgroundImage: String = "",
    @SerialName("total_seasons") val totalSeasons: Int = 0,
    @SerialName("total_episodes") val totalEpisodes: Int = 0,
    val seasons: List<Season>? = null,
)

@Serializable
data class Season(
    val id: Int = 0,
    @SerialName("series_id") val seriesId: Int = 0,
    @SerialName("season_number") val seasonNumber: Int = 0,
    @SerialName("episode_count") val episodeCount: Int = 0,
    @SerialName("air_date") val airDate: String? = null,
    val episodes: List<Episode>? = null,
)

@Serializable
data class Episode(
    val id: Int,
    @SerialName("series_id") val seriesId: Int = 0,
    @SerialName("season_number") val seasonNumber: Int = 0,
    @SerialName("episode_number") val episodeNumber: Int = 0,
    val title: String,
    val summary: String? = null,
    @SerialName("air_date") val airDate: String? = null,
    val runtime: Int? = null,
    @SerialName("still_image") val stillImage: String? = null,
    val torrents: List<EpisodeTorrent>? = null,
)

@Serializable
data class EpisodeTorrent(
    val id: Int = 0,
    @SerialName("episode_id") val episodeId: Int = 0,
    val hash: String,
    val quality: String,
    @SerialName("video_codec") val videoCodec: String? = null,
    val seeds: Int = 0,
    val peers: Int = 0,
    val size: String = "",
    @SerialName("size_bytes") val sizeBytes: Long = 0,
    @SerialName("file_index") val fileIndex: Int? = null,
)

@Serializable
data class SeriesListResponse(
    val status: String = "",
    val data: SeriesListData? = null,
)

@Serializable
data class SeriesListData(
    @SerialName("series_count") val seriesCount: Int = 0,
    val limit: Int = 20,
    @SerialName("page_number") val pageNumber: Int = 1,
    val series: List<Series> = emptyList(),
)

// Series detail: { status, data: { series, episodes, season_packs } }
@Serializable
data class SeriesDetailResponse(
    val status: String = "",
    val data: SeriesDetailData? = null,
)

@Serializable
data class SeriesDetailData(
    val series: Series? = null,
    val episodes: List<Episode>? = null,
)

// Season episodes: { status, data: [...episodes] }
@Serializable
data class SeasonEpisodesResponse(
    val status: String = "",
    val data: List<Episode>? = null,
)

// --- Channels / IPTV ---

@Serializable
data class Channel(
    val id: String,
    val name: String,
    val country: String? = null,
    val languages: List<String>? = null,
    val categories: List<String>? = null,
    val logo: String? = null,
    @SerialName("stream_url") val streamUrl: String? = null,
    @SerialName("is_nsfw") val isNsfw: Boolean = false,
    val website: String? = null,
)

@Serializable
data class ChannelCountry(
    val code: String,
    val name: String,
    val flag: String? = null,
    @SerialName("channel_count") val channelCount: Int? = null,
)

@Serializable
data class ChannelCategory(
    val id: String,
    val name: String,
    @SerialName("channel_count") val channelCount: Int? = null,
)

// Channel list: { status, data: { channel_count, limit, page_number, channels } }
@Serializable
data class ChannelListResponse(
    val status: String = "",
    val data: ChannelListData? = null,
)

@Serializable
data class ChannelListData(
    @SerialName("channel_count") val channelCount: Int = 0,
    val limit: Int = 50,
    @SerialName("page_number") val pageNumber: Int = 1,
    val channels: List<Channel> = emptyList(),
)

// Channel countries: { status, data: { countries: [...] } }
@Serializable
data class ChannelCountriesResponse(
    val status: String = "",
    val data: ChannelCountriesData? = null,
)

@Serializable
data class ChannelCountriesData(
    val countries: List<ChannelCountry> = emptyList(),
)

// Channel categories: { status, data: { categories: [...] } }
@Serializable
data class ChannelCategoriesResponse(
    val status: String = "",
    val data: ChannelCategoriesData? = null,
)

@Serializable
data class ChannelCategoriesData(
    val categories: List<ChannelCategory> = emptyList(),
)

// --- Subtitles ---

@Serializable
data class Subtitle(
    val id: String? = null,
    val language: String = "",
    @SerialName("language_name") val languageName: String = "",
    @SerialName("download_url") val downloadUrl: String = "",
    @SerialName("release_name") val releaseName: String? = null,
    @SerialName("hearing_impaired") val hearingImpaired: Boolean = false,
)

@Serializable
data class SubtitleSearchResponse(
    val subtitles: List<Subtitle> = emptyList(),
    @SerialName("total_count") val totalCount: Int = 0,
)

// --- Home ---

@Serializable
data class HomeResponse(
    val status: String = "",
    val data: HomeData? = null,
)

@Serializable
data class HomeData(
    @SerialName("hero_slider") val heroSlider: List<HomeMovieHero>? = null,
    val sections: List<HomeSection>? = null,
)

// Slim movie for hero slider (has bg images, genres, summary for display)
@Serializable
data class HomeMovieHero(
    val id: Int,
    val title: String = "",
    val year: Int = 0,
    val rating: Double = 0.0,
    val runtime: Int = 0,
    val genres: List<String> = emptyList(),
    val summary: String = "",
    @SerialName("background_image") val backgroundImage: String = "",
    @SerialName("background_image_original") val backgroundImageOriginal: String = "",
    @SerialName("medium_cover_image") val mediumCoverImage: String = "",
    @SerialName("large_cover_image") val largeCoverImage: String = "",
)

// Slim movie for section cards (minimal: just enough for a thumbnail card)
@Serializable
data class HomeMovieSlim(
    val id: Int,
    val title: String = "",
    val year: Int = 0,
    val rating: Double = 0.0,
    @SerialName("medium_cover_image") val mediumCoverImage: String = "",
)

@Serializable
data class HomeSection(
    val id: String? = null,
    val title: String = "",
    val type: String = "",
    @SerialName("display_type") val displayType: String? = null,
    val movies: List<HomeMovieSlim>? = null,
    val series: List<Series>? = null,
)

// --- Refresh ---

@Serializable
data class RefreshResponse(
    val status: String = "",
    @SerialName("status_message") val statusMessage: String = "",
    val data: RefreshData? = null,
)

@Serializable
data class RefreshData(
    val refreshed: Boolean = false,
    val movie: Movie? = null,
    val series: Series? = null,
)

// --- Config ---

@Serializable
data class ConfigResponse(
    val status: String = "",
    val data: ConfigData? = null,
)

@Serializable
data class ConfigData(
    @SerialName("server_name") val serverName: String? = null,
)
