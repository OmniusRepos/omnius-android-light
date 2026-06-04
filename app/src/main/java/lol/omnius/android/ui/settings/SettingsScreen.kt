package lol.omnius.android.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import lol.omnius.android.data.ServerManager
import lol.omnius.android.ui.theme.OmniusGreen
import lol.omnius.android.ui.theme.OmniusRed
import lol.omnius.android.ui.theme.OmniusSurface

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val servers by ServerManager.servers.collectAsState()
    val activeUrl by ServerManager.activeUrl.collectAsState()

    var newName by remember { mutableStateOf("") }
    var newUrl by remember { mutableStateOf("") }
    var showAddForm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(
            text = "Settings",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp),
        )

        // Servers section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Servers", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            FocusableSurface(
                onClick = { showAddForm = !showAddForm },
                color = if (showAddForm) Color(0xFF444444) else OmniusRed,
            ) {
                Text(
                    if (showAddForm) "Cancel" else "+ Add Server",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Add server form
        if (showAddForm) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1A1A), RoundedCornerShape(8.dp))
                    .padding(16.dp),
            ) {
                Text("Server Name", color = Color(0xFF888888), fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OmniusSurface, RoundedCornerShape(6.dp)),
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                        decorationBox = { inner ->
                            if (newName.isEmpty()) Text("My Server", color = Color(0xFF555555), fontSize = 14.sp)
                            inner()
                        },
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text("Server URL", color = Color(0xFF888888), fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OmniusSurface, RoundedCornerShape(6.dp)),
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = newUrl,
                        onValueChange = { newUrl = it },
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                        decorationBox = { inner ->
                            if (newUrl.isEmpty()) Text("https://my-server.com", color = Color(0xFF555555), fontSize = 14.sp)
                            inner()
                        },
                    )
                }

                Spacer(Modifier.height(12.dp))

                FocusableSurface(
                    onClick = {
                        if (newName.isNotBlank() && newUrl.isNotBlank()) {
                            ServerManager.addServer(newName.trim(), newUrl.trim())
                            newName = ""
                            newUrl = ""
                            showAddForm = false
                        }
                    },
                    color = OmniusGreen,
                ) {
                    Text(
                        "Add",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        // Server list
        servers.forEach { server ->
            val isActive = ServerManager.isActive(server.url)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(
                        if (isActive) Color(0xFF1A2A1A) else Color(0xFF1A1A1A),
                        RoundedCornerShape(8.dp),
                    )
                    .then(
                        if (isActive) Modifier.border(BorderStroke(1.dp, OmniusGreen), RoundedCornerShape(8.dp))
                        else Modifier
                    )
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(server.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        if (isActive) {
                            Text(
                                "ACTIVE",
                                color = OmniusGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .background(Color(0x332ECC71), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(server.url, color = Color(0xFF666666), fontSize = 12.sp)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isActive) {
                        FocusableSurface(
                            onClick = { ServerManager.setActive(server.url) },
                            color = Color(0xFF333333),
                        ) {
                            Text("Use", color = Color.White, fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                    }
                    if (servers.size > 1) {
                        FocusableSurface(
                            onClick = { ServerManager.removeServer(server.url) },
                            color = Color(0xFF331111),
                        ) {
                            Text("Remove", color = OmniusRed, fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // App info
        Text("About", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text("Omnius for Android TV", color = Color(0xFF888888), fontSize = 13.sp)
        Text("Version 1.0.0", color = Color(0xFF888888), fontSize = 13.sp)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FocusableSurface(
    onClick: () -> Unit,
    color: Color,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (focused) Modifier.border(BorderStroke(2.dp, Color.White), shape)
                else Modifier
            ),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(containerColor = color, focusedContainerColor = color),
    ) {
        content()
    }
}
