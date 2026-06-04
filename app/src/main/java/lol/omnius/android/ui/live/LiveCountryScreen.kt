package lol.omnius.android.ui.live

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.*
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lol.omnius.android.api.ApiClient
import lol.omnius.android.data.FavChannel
import lol.omnius.android.data.FavoritesManager
import lol.omnius.android.data.model.Channel
import lol.omnius.android.data.model.ChannelCountry
import lol.omnius.android.ui.components.FavoriteDialog
import lol.omnius.android.ui.theme.OmniusCard
import lol.omnius.android.ui.theme.OmniusRed
import lol.omnius.android.ui.theme.OmniusSurface

class LiveCountryViewModel : ViewModel() {
    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels = _channels.asStateFlow()
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()
    private val _countryName = MutableStateFlow("")
    val countryName = _countryName.asStateFlow()
    private val _totalChannels = MutableStateFlow(0)
    val totalChannels = _totalChannels.asStateFlow()

    fun load(countryCode: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                // Get country name from countries list
                try {
                    val response = ApiClient.getApi().getChannelCountries()
                    val countries = response.data?.countries ?: emptyList()
                    val country = countries.find { it.code.equals(countryCode, ignoreCase = true) }
                    _countryName.value = country?.name ?: countryCode.uppercase()
                } catch (_: Exception) {
                    _countryName.value = countryCode.uppercase()
                }

                // Load all channels for this country
                val response = ApiClient.getApi().listChannels(limit = 5000, country = countryCode)
                val allChannels = response.data?.channels ?: emptyList()
                _channels.value = allChannels
                _totalChannels.value = allChannels.size
            } catch (_: Exception) {}
            _isLoading.value = false
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LiveCountryScreen(
    countryCode: String,
    onBack: () -> Unit,
    onChannelPlay: (channels: List<Channel>, index: Int) -> Unit,
    viewModel: LiveCountryViewModel = viewModel(),
) {
    val channels by viewModel.channels.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val countryName by viewModel.countryName.collectAsState()
    val totalChannels by viewModel.totalChannels.collectAsState()
    val favChannels by FavoritesManager.channels.collectAsState()
    var favDialogChannel by remember { mutableStateOf<FavChannel?>(null) }

    favDialogChannel?.let { ch ->
        FavoriteDialog(
            title = ch.name,
            isFavorite = FavoritesManager.isChannelFav(ch.id),
            onToggle = { FavoritesManager.toggleChannel(ch) },
            onDismiss = { favDialogChannel = null },
        )
    }

    LaunchedEffect(countryCode) { viewModel.load(countryCode) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header with country name and channel count
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = countryName.ifEmpty { countryCode.uppercase() },
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            if (totalChannels > 0) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "$totalChannels channels",
                    color = Color(0xFF888888),
                    fontSize = 14.sp,
                )
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading channels...", color = Color.White)
            }
        } else if (channels.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No channels found", color = Color(0xFF888888), fontSize = 14.sp)
            }
        } else {
            TvLazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(channels, key = { it.id }) { channel ->
                    var channelFocused by remember { mutableStateOf(false) }
                    val channelShape = RoundedCornerShape(8.dp)
                    Surface(
                        onClick = {
                            if (channel.streamUrl != null) {
                                val playableChannels = channels.filter { it.streamUrl != null }
                                val idx = playableChannels.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
                                onChannelPlay(playableChannels, idx)
                            }
                        },
                        onLongClick = {
                            favDialogChannel = FavChannel(channel.id, channel.name, channel.logo, channel.streamUrl, channel.country)
                        },
                        modifier = Modifier
                            .onFocusChanged { channelFocused = it.isFocused }
                            .then(
                                if (channelFocused) Modifier.border(BorderStroke(2.dp, OmniusRed), channelShape)
                                else Modifier
                            ),
                        shape = ClickableSurfaceDefaults.shape(shape = channelShape),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = OmniusSurface,
                            focusedContainerColor = OmniusCard,
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Channel logo
                            if (channel.logo != null) {
                                AsyncImage(
                                    model = channel.logo,
                                    contentDescription = channel.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape),
                                )
                            } else {
                                // Placeholder
                                Box(
                                    modifier = Modifier.size(40.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = channel.name.take(2).uppercase(),
                                        color = Color(0xFF666666),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = channel.name,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                )
                                channel.categories?.takeIf { it.isNotEmpty() }?.let { cats ->
                                    Text(
                                        text = cats.joinToString(", "),
                                        color = Color(0xFF888888),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                    )
                                }
                            }

                            // Favorite indicator
                            if (FavoritesManager.isChannelFav(channel.id)) {
                                Text("\u2665", color = OmniusRed, fontSize = 14.sp)
                                Spacer(Modifier.width(8.dp))
                            }

                            // Live indicator
                            if (channel.streamUrl != null) {
                                Text(
                                    text = "LIVE",
                                    color = OmniusRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            } else {
                                Text(
                                    text = "OFFLINE",
                                    color = Color(0xFF555555),
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
