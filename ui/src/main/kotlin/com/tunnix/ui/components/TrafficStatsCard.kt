package com.tunnix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tunnix.core.models.TrafficStats
import com.tunnix.core.models.VpnState
import com.tunnix.ui.theme.EmeraldGreen
import com.tunnix.ui.theme.TunnixStyle

@Composable
fun TrafficStatsCard(
    vpnState: VpnState,
    trafficStats: TrafficStats,
    modifier: Modifier = Modifier,
    downloadLabel: String = "Download",
    uploadLabel: String = "Upload"
) {
    val isConnected = vpnState is VpnState.Connected
    val isConnecting = vpnState is VpnState.Connecting

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Download Column
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = downloadLabel,
                    color = TunnixStyle.glass.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                )

                Spacer(modifier = Modifier.height(6.dp))

                val downloadSpeedText = when {
                    isConnected -> trafficStats.formattedDownloadSpeed
                    isConnecting -> "0 B"
                    else -> "-"
                }

                Text(
                    text = downloadSpeedText,
                    color = if (isConnected) EmeraldGreen else TunnixStyle.glass.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                if (isConnected) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = trafficStats.formattedTotalDownload,
                        color = TunnixStyle.glass.textMuted,
                        fontSize = 11.sp
                    )
                }
            }

            // Vertical Glass Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight(0.65f)
                    .background(TunnixStyle.glass.glassBorder.copy(alpha = 0.5f))
            )

            // Upload Column
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = uploadLabel,
                    color = TunnixStyle.glass.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                )

                Spacer(modifier = Modifier.height(6.dp))

                val uploadSpeedText = when {
                    isConnected -> trafficStats.formattedUploadSpeed
                    isConnecting -> "${trafficStats.totalUploadBytes} B"
                    else -> "-"
                }

                Text(
                    text = uploadSpeedText,
                    color = TunnixStyle.glass.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                if (isConnected) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = trafficStats.formattedTotalUpload,
                        color = TunnixStyle.glass.textMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
