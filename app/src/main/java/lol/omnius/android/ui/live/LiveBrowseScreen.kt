package lol.omnius.android.ui.live

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyListState
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.*
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lol.omnius.android.api.ApiClient
import lol.omnius.android.data.FavChannel
import lol.omnius.android.data.FavCountry
import lol.omnius.android.data.FavoritesManager
import lol.omnius.android.data.model.Channel
import lol.omnius.android.data.model.ChannelCategory
import lol.omnius.android.data.model.ChannelCountry
import lol.omnius.android.ui.components.FavoriteDialog
import lol.omnius.android.ui.theme.OmniusCard
import lol.omnius.android.ui.theme.OmniusRed
import lol.omnius.android.ui.theme.OmniusSurface

class LiveAllViewModel : ViewModel() {
    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels = _channels.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _hasMore = MutableStateFlow(true)
    val hasMore = _hasMore.asStateFlow()

    private var page = 1
    private val pageSize = 100
    private var loading = false

    init { loadNext() }

    fun loadNext() {
        if (loading || !_hasMore.value) return
        loading = true
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val resp = ApiClient.getApi().listChannels(limit = pageSize, page = page)
                val data = resp.data
                val newItems = data?.channels ?: emptyList()
                val total = data?.channelCount ?: 0
                _channels.value = _channels.value + newItems
                page += 1
                if (newItems.isEmpty() || _channels.value.size >= total) _hasMore.value = false
            } catch (_: Exception) {}
            _isLoading.value = false
            loading = false
        }
    }
}

class LiveCategoriesViewModel : ViewModel() {
    private val _categories = MutableStateFlow<List<ChannelCategory>>(emptyList())
    val categories = _categories.asStateFlow()
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val resp = ApiClient.getApi().getChannelCategories()
                _categories.value = (resp.data?.categories ?: emptyList())
                    .filter { (it.channelCount ?: 0) > 0 }
            } catch (_: Exception) {}
            _isLoading.value = false
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LiveBrowseScreen(
    onCountryClick: (String) -> Unit,
    onCategoryClick: (String) -> Unit,
    onChannelPlay: (channels: List<Channel>, index: Int) -> Unit,
) {
    val tabs = listOf("All", "Countries", "Categories", "Favorites")
    var selectedTab by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Live TV",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
        )
        LiveTabBar(tabs = tabs, selected = selectedTab, onSelect = { selectedTab = it })
        Spacer(Modifier.height(8.dp))

        when (selectedTab) {
            0 -> AllChannelsTab(onChannelPlay = onChannelPlay)
            1 -> CountriesTab(onCountryClick = onCountryClick)
            2 -> CategoriesTab(onCategoryClick = onCategoryClick)
            3 -> FavoritesTab(onChannelPlay = onChannelPlay)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LiveTabBar(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEachIndexed { idx, label ->
            var focused by remember { mutableStateOf(false) }
            val shape = RoundedCornerShape(8.dp)
            val isSelected = selected == idx
            Surface(
                onClick = { onSelect(idx) },
                modifier = Modifier
                    .onFocusChanged {
                        focused = it.isFocused
                        if (it.isFocused) onSelect(idx)
                    }
                    .then(
                        if (focused) Modifier.border(BorderStroke(2.dp, OmniusRed), shape)
                        else Modifier
                    ),
                shape = ClickableSurfaceDefaults.shape(shape = shape),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isSelected) OmniusRed.copy(alpha = 0.3f) else OmniusCard,
                    focusedContainerColor = OmniusRed.copy(alpha = 0.3f),
                ),
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AllChannelsTab(
    onChannelPlay: (channels: List<Channel>, index: Int) -> Unit,
    viewModel: LiveAllViewModel = viewModel(),
) {
    val channels by viewModel.channels.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val listState = rememberTvLazyListState()
    var favDialogChannel by remember { mutableStateOf<FavChannel?>(null) }

    favDialogChannel?.let { ch ->
        FavoriteDialog(
            title = ch.name,
            isFavorite = FavoritesManager.isChannelFav(ch.id),
            onToggle = { FavoritesManager.toggleChannel(ch) },
            onDismiss = { favDialogChannel = null },
        )
    }

    LaunchedEffect(listState, channels.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { lastVisible ->
                if (hasMore && channels.isNotEmpty() && lastVisible >= channels.size - 15) {
                    viewModel.loadNext()
                }
            }
    }

    if (channels.isEmpty() && isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading channels...", color = Color.White)
        }
    } else if (channels.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No channels found", color = Color(0xFF888888))
        }
    } else {
        TvLazyColumn(
            state = listState,
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
            if (hasMore) {
                item {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        Text("Loading more...", color = Color(0xFF888888), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CountriesTab(
    onCountryClick: (String) -> Unit,
    viewModel: LiveBrowseViewModel = viewModel(),
) {
    val countries by viewModel.countries.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var favDialogCountry by remember { mutableStateOf<FavCountry?>(null) }

    favDialogCountry?.let { c ->
        FavoriteDialog(
            title = c.name,
            isFavorite = FavoritesManager.isCountryFav(c.code),
            onToggle = { FavoritesManager.toggleCountry(c) },
            onDismiss = { favDialogCountry = null },
        )
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading countries...", color = Color.White)
        }
    } else {
        TvLazyVerticalGrid(
            columns = TvGridCells.Adaptive(180.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(countries, key = { it.code }) { country ->
                var focused by remember { mutableStateOf(false) }
                val shape = RoundedCornerShape(12.dp)
                Surface(
                    onClick = { onCountryClick(country.code) },
                    onLongClick = {
                        favDialogCountry = FavCountry(country.code, country.name, country.flag)
                    },
                    modifier = Modifier
                        .onFocusChanged { focused = it.isFocused }
                        .then(
                            if (focused) Modifier.border(BorderStroke(2.dp, OmniusRed), shape)
                            else Modifier
                        ),
                    shape = ClickableSurfaceDefaults.shape(shape = shape),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = OmniusCard,
                        focusedContainerColor = OmniusRed.copy(alpha = 0.3f),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = country.flag ?: "", fontSize = 28.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(country.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            country.channelCount?.let {
                                Text("$it channels", color = Color(0xFF888888), fontSize = 12.sp)
                            }
                        }
                        if (FavoritesManager.isCountryFav(country.code)) {
                            Text("\u2665", color = OmniusRed, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoriesTab(
    onCategoryClick: (String) -> Unit,
    viewModel: LiveCategoriesViewModel = viewModel(),
) {
    val categories by viewModel.categories.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading categories...", color = Color.White)
        }
    } else if (categories.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No categories", color = Color(0xFF888888))
        }
    } else {
        TvLazyVerticalGrid(
            columns = TvGridCells.Adaptive(180.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(categories, key = { it.id }) { cat ->
                var focused by remember { mutableStateOf(false) }
                val shape = RoundedCornerShape(12.dp)
                Surface(
                    onClick = { onCategoryClick(cat.id) },
                    modifier = Modifier
                        .onFocusChanged { focused = it.isFocused }
                        .then(
                            if (focused) Modifier.border(BorderStroke(2.dp, OmniusRed), shape)
                            else Modifier
                        ),
                    shape = ClickableSurfaceDefaults.shape(shape = shape),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = OmniusCard,
                        focusedContainerColor = OmniusRed.copy(alpha = 0.3f),
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(cat.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        cat.channelCount?.let {
                            Text("$it channels", color = Color(0xFF888888), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FavoritesTab(
    onChannelPlay: (channels: List<Channel>, index: Int) -> Unit,
) {
    val favs by FavoritesManager.channels.collectAsState()
    var favDialogChannel by remember { mutableStateOf<FavChannel?>(null) }

    favDialogChannel?.let { ch ->
        FavoriteDialog(
            title = ch.name,
            isFavorite = FavoritesManager.isChannelFav(ch.id),
            onToggle = { FavoritesManager.toggleChannel(ch) },
            onDismiss = { favDialogChannel = null },
        )
    }

    if (favs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No favorite channels yet. Long-press a channel to add.", color = Color(0xFF888888))
        }
        return
    }

    val playableChannels = favs
        .filter { it.streamUrl != null }
        .map { Channel(id = it.id, name = it.name, country = it.country, logo = it.logo, streamUrl = it.streamUrl) }

    TvLazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(favs, key = { it.id }) { fav ->
            val channel = Channel(id = fav.id, name = fav.name, country = fav.country, logo = fav.logo, streamUrl = fav.streamUrl)
            ChannelRow(
                channel = channel,
                onClick = {
                    if (fav.streamUrl != null) {
                        val idx = playableChannels.indexOfFirst { it.id == fav.id }.coerceAtLeast(0)
                        onChannelPlay(playableChannels, idx)
                    }
                },
                onLongClick = { favDialogChannel = fav },
                showCountry = true,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ChannelRow(
    channel: Channel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showCountry: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (focused) Modifier.border(BorderStroke(2.dp, OmniusRed), shape)
                else Modifier
            ),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = OmniusSurface,
            focusedContainerColor = OmniusCard,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (channel.logo != null) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                )
            } else {
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Text(channel.name.take(2).uppercase(), color = Color(0xFF666666), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(channel.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                val subtitle = buildList {
                    if (showCountry && !channel.country.isNullOrBlank()) add(channel.country!!)
                    channel.categories?.takeIf { it.isNotEmpty() }?.let { add(it.joinToString(", ")) }
                }.joinToString(" \u2022 ")
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, color = Color(0xFF888888), fontSize = 11.sp, maxLines = 1)
                }
            }
            if (FavoritesManager.isChannelFav(channel.id)) {
                Text("\u2665", color = OmniusRed, fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
            }
            if (channel.streamUrl != null) {
                Text("LIVE", color = OmniusRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            } else {
                Text("OFFLINE", color = Color(0xFF555555), fontSize = 11.sp)
            }
        }
    }
}

class LiveBrowseViewModel : ViewModel() {
    private val _countries = MutableStateFlow<List<ChannelCountry>>(emptyList())
    val countries = _countries.asStateFlow()
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val response = ApiClient.getApi().getChannelCountries()
                _countries.value = response.data?.countries ?: emptyList()
            } catch (_: Exception) {}
            _isLoading.value = false
        }
    }
}
