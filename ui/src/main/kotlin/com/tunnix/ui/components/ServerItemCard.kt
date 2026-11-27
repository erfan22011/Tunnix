package com.tunnix.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tunnix.core.models.ServerConfig
import com.tunnix.core.models.VpnState
import com.tunnix.ui.theme.EmeraldGreen
import com.tunnix.ui.theme.StatusConnecting
import com.tunnix.ui.theme.TunnixStyle

@Composable
fun ServerItemCard(
    server: ServerConfig,
    isSelected: Boolean,
    vpnState: VpnState,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    tapToSelectLabel: String = "Tap to select",
    selectedLabel: String = "Selected",
    connectingLabel: String = "Connecting...",
    connectedLabel: String = "Connected"
) {
    val isConnected = isSelected && vpnState is VpnState.Connected
    val isConnecting = isSelected && vpnState is VpnState.Connecting

    val statusText = when {
        isConnected -> connectedLabel
        isConnecting -> connectingLabel
        isSelected -> selectedLabel
        else -> tapToSelectLabel
    }

    val statusColor = when {
        isConnected -> EmeraldGreen
        isConnecting -> StatusConnecting
        isSelected -> TunnixStyle.glass.textPrimary
        else -> TunnixStyle.glass.textMuted
    }

    val flagEmoji = getFlagEmoji(server.countryCode, server.displayName)

    val animatedBorderColor by animateColorAsState(
        targetValue = if (isSelected) EmeraldGreen.copy(alpha = 0.6f) else TunnixStyle.glass.glassBorder,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "serverCardBorderColor"
    )

    val animatedBorderWidth by animateDpAsState(
        targetValue = if (isSelected) 1.5.dp else 1.dp,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "serverCardBorderWidth"
    )

    val iconBgColor by animateColorAsState(
        targetValue = if (isSelected) {
            if (TunnixStyle.glass.isDark) Color(0x4D00E676) else Color(0x3300E676)
        } else {
            if (TunnixStyle.glass.isDark) Color(0x331E293B) else Color(0x1F00E676)
        },
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "iconBgColor"
    )

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = EmeraldGreen),
                onClick = onSelect
            ),
        shape = RoundedCornerShape(16.dp),
        borderWidth = animatedBorderWidth,
        borderColor = animatedBorderColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Server Rack Icon Container
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Dns,
                    contentDescription = null,
                    tint = if (isSelected) EmeraldGreen else TunnixStyle.glass.textSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Server Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$flagEmoji  ${server.displayName}",
                        color = TunnixStyle.glass.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (server.isXhttpModified || server.downloadSettings != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(EmeraldGreen.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "⚡ XHTTP",
                                color = EmeraldGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (server.isTransportUnencrypted) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFFFB300).copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "⚠️ No TLS",
                                color = Color(0xFFFFB300),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                )
            }

            // Selection Checkmark with smooth animated scale & fade
            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn(tween(200, easing = FastOutSlowInEasing)) + scaleIn(tween(200, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(150, easing = FastOutSlowInEasing)) + scaleOut(tween(150, easing = FastOutSlowInEasing))
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Selected",
                    tint = if (isConnected) EmeraldGreen else TunnixStyle.glass.textPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

fun getFlagEmoji(countryCode: String, serverName: String): String {
    val codeLower = countryCode.trim().lowercase()
    if (codeLower.length == 2) {
        when (codeLower) {
            "nl" -> return "🇳🇱"
            "ch" -> return "🇨🇭"
            "de" -> return "🇩🇪"
            "fr" -> return "🇫🇷"
            "gb", "uk" -> return "🇬🇧"
            "us" -> return "🇺🇸"
            "ca" -> return "🇨🇦"
            "fi" -> return "🇫🇮"
            "tr" -> return "🇹🇷"
            "se" -> return "🇸🇪"
            "ru" -> return "🇷🇺"
        }
    }

    val tokens = serverName.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
    val nameLower = serverName.lowercase()
    return when {
        tokens.contains("nl") || nameLower.contains("netherland") -> "🇳🇱"
        tokens.contains("ch") || nameLower.contains("swiss") || nameLower.contains("switzerland") -> "🇨🇭"
        tokens.contains("de") || nameLower.contains("germany") -> "🇩🇪"
        tokens.contains("fr") || nameLower.contains("france") -> "🇫🇷"
        tokens.contains("uk") || tokens.contains("gb") || nameLower.contains("united kingdom") || nameLower.contains("london") -> "🇬🇧"
        tokens.contains("us") || tokens.contains("usa") || nameLower.contains("united states") -> "🇺🇸"
        tokens.contains("ca") || nameLower.contains("canada") -> "🇨🇦"
        tokens.contains("fi") || nameLower.contains("finland") -> "🇫🇮"
        tokens.contains("tr") || nameLower.contains("turkey") -> "🇹🇷"
        tokens.contains("se") || nameLower.contains("sweden") -> "🇸🇪"
        tokens.contains("ru") || nameLower.contains("russia") -> "🇷🇺"
        else -> "🌐"
    }
}
