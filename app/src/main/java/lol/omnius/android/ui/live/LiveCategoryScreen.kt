package lol.omnius.android.ui.live

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lol.omnius.android.api.ApiClient
import lol.omnius.android.data.FavChannel
import lol.omnius.android.data.FavoritesManager
import lol.omnius.android.data.model.Channel

class LiveCategoryViewModel : ViewModel() {
    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels = _channels.asStateFlow()
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()
    private val _categoryName = MutableStateFlow("")
    val categoryName = _categoryName.asStateFlow()

    fun load(categoryId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                try {
                    val catResp = ApiClient.getApi().getChannelCategories()
                    val match = catResp.data?.categories?.find { it.id.equals(categoryId, ignoreCase = true) }
                    _categoryName.value = match?.name ?: categoryId
                } catch (_: Exception) {
                    _categoryName.value = categoryId
                }

                val resp = ApiClient.getApi().listChannels(limit = 5000, category = categoryId)
                _channels.value = resp.data?.channels ?: emptyList()
            } catch (_: Exception) {}
            _isLoading.value = false
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LiveCategoryScreen(
    categoryId: String,
    onBack: () -> Unit,
    onChannelPlay: (channels: List<Channel>, index: Int) -> Unit,
    viewModel: LiveCategoryViewModel = viewModel(),
) {
    val channels by viewModel.channels.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val categoryName by viewModel.categoryName.collectAsState()
    var favDialogChannel by remember { mutableStateOf<FavChannel?>(null) }

    favDialogChannel?.let { ch ->
        FavoriteDialogFav(ch = ch, onDismiss = { favDialogChannel = null })
    }

    LaunchedEffect(categoryId) { viewModel.load(categoryId) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = categoryName.ifEmpty { categoryId },
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            if (channels.isNotEmpty()) {
                Spacer(Modifier.width(12.dp))
                Text("${channels.size} channels", color = Color(0xFF888888), fontSize = 14.sp)
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading channels...", color = Color.White)
            }
        } else if (channels.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No channels found", color = Color(0xFF888888))
            }
        } else {
            TvLazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(channels, key = { it.id }) { channel ->
                    ChannelRow(
                        channel = channel,
                        onClick = {
                            if (channel.streamUrl != null) {
                                val playable = channels.filter { it.streamUrl != null }
                                val idx = playable.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
                                onChannelPlay(playable, idx)
                            }
                        },
                        onLongClick = {
                            favDialogChannel = FavChannel(channel.id, channel.name, channel.logo, channel.streamUrl, channel.country)
                        },
                        showCountry = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteDialogFav(ch: FavChannel, onDismiss: () -> Unit) {
    lol.omnius.android.ui.components.FavoriteDialog(
        title = ch.name,
        isFavorite = FavoritesManager.isChannelFav(ch.id),
        onToggle = { FavoritesManager.toggleChannel(ch) },
        onDismiss = onDismiss,
    )
}
