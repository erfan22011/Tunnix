package com.tunnix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tunnix.core.models.AppLanguage
import com.tunnix.core.models.ServerConfig
import com.tunnix.core.models.TrafficStats
import com.tunnix.core.models.VpnState
import com.tunnix.ui.components.LiquidPowerButton
import com.tunnix.ui.components.ServerItemCard
import com.tunnix.ui.components.TrafficStatsCard
import com.tunnix.ui.components.TunnixLogo
import com.tunnix.ui.components.getFlagEmoji
import com.tunnix.ui.theme.EmeraldGreen
import com.tunnix.ui.theme.StatusConnecting
import com.tunnix.ui.theme.StatusDisconnected
import com.tunnix.ui.theme.TunnixStyle

@Composable
fun MainScreen(
    vpnState: VpnState,
    trafficStats: TrafficStats,
    servers: List<ServerConfig>,
    selectedServer: ServerConfig?,
    onSelectServer: (ServerConfig) -> Unit,
    onToggleConnection: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onChangeSubscription: () -> Unit,
    currentLanguage: AppLanguage
) {
    val isFa = currentLanguage == AppLanguage.PERSIAN

    val isConnected = vpnState is VpnState.Connected
    val isConnecting = vpnState is VpnState.Connecting

    val statusDotColor = when {
        isConnected -> EmeraldGreen
        isConnecting -> StatusConnecting
        vpnState is VpnState.Error -> Color(0xFFFF5252)
        else -> StatusDisconnected
    }

    val statusTitle = when (vpnState) {
        is VpnState.Connected -> if (isFa) "متصل شد" else "Connected"
        is VpnState.Connecting -> if (isFa) "در حال اتصال..." else "Connecting..."
        is VpnState.Reconnecting -> if (isFa) "در حال اتصال مجدد..." else "Reconnecting..."
        is VpnState.Error -> if (isFa) "خطای اتصال" else "Connection Error"
        is VpnState.Disconnected -> if (isFa) "آماده اتصال" else "Ready to connect"
    }

    val statusSubtitle = when (vpnState) {
        is VpnState.Connecting -> if (isFa) "در انتظار پاسخ. می‌توانید لغو کنید." else "Waiting for a response. You can cancel."
        is VpnState.Error -> vpnState.message
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TunnixStyle.glass.background)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Top App Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Logo + App Name
            Row(verticalAlignment = Alignment.CenterVertically) {
                TunnixLogo(size = 36.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Tunnix",
                    color = TunnixStyle.glass.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Right: Settings & Change Sub Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = TunnixStyle.glass.textSecondary
                    )
                }

                IconButton(onClick = onChangeSubscription) {
                    Icon(
                        imageVector = Icons.Rounded.ExitToApp,
                        contentDescription = "Change Subscription",
                        tint = TunnixStyle.glass.textSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Status Header (Dot + Text)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusDotColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusTitle,
                    color = if (isConnected) EmeraldGreen else TunnixStyle.glass.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (!statusSubtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = statusSubtitle,
                    color = TunnixStyle.glass.textSecondary,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Center Hero Power Button
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            LiquidPowerButton(
                vpnState = vpnState,
                onClick = onToggleConnection,
                connectText = if (isFa) "اتصال" else "Connect",
                cancelText = if (isFa) "لغو" else "Cancel",
                disconnectText = if (isFa) "قطع اتصال" else "Disconnect"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Real-time Traffic Stats Card
        TrafficStatsCard(
            vpnState = vpnState,
            trafficStats = trafficStats,
            downloadLabel = if (isFa) "دانلود" else "Download",
            uploadLabel = if (isFa) "آپلود" else "Upload"
        )

        Spacer(modifier = Modifier.height(22.dp))

        // Servers Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isFa) "سرورها (${servers.size})" else "Servers (${servers.size})",
                color = TunnixStyle.glass.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (selectedServer != null) {
                val flag = getFlagEmoji(selectedServer.countryCode, selectedServer.name)
                Text(
                    text = if (isFa) "فعال: $flag ${selectedServer.name}" else "Using: $flag ${selectedServer.name}",
                    color = TunnixStyle.glass.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).padding(start = 12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Server List
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            items(servers, key = { it.id }) { server ->
                ServerItemCard(
                    server = server,
                    isSelected = server.id == selectedServer?.id,
                    vpnState = vpnState,
                    onSelect = { onSelectServer(server) },
                    tapToSelectLabel = if (isFa) "برای انتخاب لمس کنید" else "Tap to select",
                    selectedLabel = if (isFa) "انتخاب شده" else "Selected",
                    connectingLabel = if (isFa) "در حال اتصال..." else "Connecting...",
                    connectedLabel = if (isFa) "متصل" else "Connected"
                )
            }
        }
    }
}
