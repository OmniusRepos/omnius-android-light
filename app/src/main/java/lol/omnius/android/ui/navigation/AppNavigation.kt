package lol.omnius.android.ui.navigation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import lol.omnius.android.ui.home.HomeScreen
import lol.omnius.android.ui.movies.MovieBrowseScreen
import lol.omnius.android.ui.movies.MovieDetailScreen
import lol.omnius.android.ui.series.SeriesBrowseScreen
import lol.omnius.android.ui.series.SeriesDetailScreen
import lol.omnius.android.ui.live.LiveBrowseScreen
import lol.omnius.android.ui.live.LiveCategoryScreen
import lol.omnius.android.ui.live.LiveCountryScreen
import lol.omnius.android.ui.favorites.FavoritesScreen
import lol.omnius.android.ui.settings.SettingsScreen
import lol.omnius.android.data.model.Channel
import lol.omnius.android.data.FavChannel
import lol.omnius.android.ui.player.LivePlayerActivity
import lol.omnius.android.ui.player.PlayerActivity

@Composable
fun AppNavGraph(navController: NavHostController) {
    val context = LocalContext.current

    fun launchPlayer(
        title: String,
        streamUrl: String,
        imdbCode: String = "",
        isTorrent: Boolean = false,
        contentId: Int = 0,
        contentType: String = "",
        contentImage: String = "",
        seriesId: Int = 0,
        seriesTitle: String = "",
        seasonNumber: Int = 0,
        episodeNumber: Int = 0,
        torrentHash: String = "",
        fileIndex: Int = -1,
    ) {
        val intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra("title", title)
            putExtra("stream_url", streamUrl)
            putExtra("imdb_code", imdbCode)
            putExtra("is_torrent", isTorrent)
            putExtra("content_id", contentId)
            putExtra("content_type", contentType)
            putExtra("content_image", contentImage)
            putExtra("series_id", seriesId)
            putExtra("series_title", seriesTitle)
            putExtra("season_number", seasonNumber)
            putExtra("episode_number", episodeNumber)
            putExtra("torrent_hash", torrentHash)
            putExtra("file_index", fileIndex)
        }
        context.startActivity(intent)
    }

    fun launchLivePlayer(channels: List<Channel>, index: Int) {
        val names = channels.map { it.name }.toTypedArray()
        val urls = channels.map { it.streamUrl ?: "" }.toTypedArray()
        val intent = Intent(context, LivePlayerActivity::class.java).apply {
            putExtra("channel_names", names)
            putExtra("channel_urls", urls)
            putExtra("channel_index", index)
        }
        context.startActivity(intent)
    }

    NavHost(
        navController = navController,
        startDestination = NavRoutes.HOME,
    ) {
        composable(NavRoutes.HOME) {
            HomeScreen(
                onMovieClick = { navController.navigate(NavRoutes.movieDetail(it)) },
                onSeriesClick = { navController.navigate(NavRoutes.seriesDetail(it)) },
            )
        }

        composable(NavRoutes.MOVIES) {
            MovieBrowseScreen(
                onMovieClick = { navController.navigate(NavRoutes.movieDetail(it)) },
            )
        }

        composable(
            route = NavRoutes.MOVIE_DETAIL,
            arguments = listOf(navArgument("movieId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val movieId = backStackEntry.arguments?.getInt("movieId") ?: return@composable
            MovieDetailScreen(
                movieId = movieId,
                onBack = { navController.popBackStack() },
                onPlay = { title, streamUrl, imdbCode, isTorrent, contentId, contentImage ->
                    launchPlayer(title, streamUrl, imdbCode, isTorrent, contentId = contentId, contentType = "movie", contentImage = contentImage)
                },
                onMovieClick = { navController.navigate(NavRoutes.movieDetail(it)) },
            )
        }

        composable(NavRoutes.SERIES) {
            SeriesBrowseScreen(
                onSeriesClick = { navController.navigate(NavRoutes.seriesDetail(it)) },
            )
        }

        composable(
            route = NavRoutes.SERIES_DETAIL,
            arguments = listOf(navArgument("seriesId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val seriesId = backStackEntry.arguments?.getInt("seriesId") ?: return@composable
            SeriesDetailScreen(
                seriesId = seriesId,
                onBack = { navController.popBackStack() },
                onPlay = { title, streamUrl, imdbCode, isTorrent, contentId, contentImage, seriesId2, seriesTitle, seasonNum, episodeNum, hash, fIdx ->
                    launchPlayer(title, streamUrl, imdbCode, isTorrent, contentId = contentId, contentType = "episode", contentImage = contentImage, seriesId = seriesId2, seriesTitle = seriesTitle, seasonNumber = seasonNum, episodeNumber = episodeNum, torrentHash = hash, fileIndex = fIdx)
                },
            )
        }

        composable(NavRoutes.LIVE) {
            LiveBrowseScreen(
                onCountryClick = { navController.navigate(NavRoutes.liveCountry(it)) },
                onCategoryClick = { navController.navigate(NavRoutes.liveCategory(it)) },
                onChannelPlay = { channels, index -> launchLivePlayer(channels, index) },
            )
        }

        composable(
            route = NavRoutes.LIVE_COUNTRY,
            arguments = listOf(navArgument("countryCode") { type = NavType.StringType }),
        ) { backStackEntry ->
            val code = backStackEntry.arguments?.getString("countryCode") ?: return@composable
            LiveCountryScreen(
                countryCode = code,
                onBack = { navController.popBackStack() },
                onChannelPlay = { channels, index -> launchLivePlayer(channels, index) },
            )
        }

        composable(
            route = NavRoutes.LIVE_CATEGORY,
            arguments = listOf(navArgument("categoryId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("categoryId") ?: return@composable
            LiveCategoryScreen(
                categoryId = id,
                onBack = { navController.popBackStack() },
                onChannelPlay = { channels, index -> launchLivePlayer(channels, index) },
            )
        }

        composable(NavRoutes.FAVORITES) {
            FavoritesScreen(
                onMovieClick = { navController.navigate(NavRoutes.movieDetail(it)) },
                onSeriesClick = { navController.navigate(NavRoutes.seriesDetail(it)) },
                onCountryClick = { navController.navigate(NavRoutes.liveCountry(it)) },
                onChannelPlay = { favChannel ->
                    if (favChannel.streamUrl != null) {
                        val names = arrayOf(favChannel.name)
                        val urls = arrayOf(favChannel.streamUrl)
                        val intent = Intent(context, LivePlayerActivity::class.java).apply {
                            putExtra("channel_names", names)
                            putExtra("channel_urls", urls)
                            putExtra("channel_index", 0)
                        }
                        context.startActivity(intent)
                    }
                },
            )
        }

        composable(NavRoutes.SETTINGS) {
            SettingsScreen()
        }
    }
}
