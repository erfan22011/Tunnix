package com.tunnix.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tunnix.core.models.VpnState
import com.tunnix.ui.theme.EmeraldGreen
import com.tunnix.ui.theme.EmeraldGlow
import com.tunnix.ui.theme.StatusConnecting
import com.tunnix.ui.theme.TunnixStyle

@Composable
fun LiquidPowerButton(
    vpnState: VpnState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 190.dp,
    connectText: String = "Connect",
    cancelText: String = "Cancel",
    disconnectText: String = "Disconnect"
) {
    val infiniteTransition = rememberInfiniteTransition(label = "power_pulse")

    // Rotation animation for Connecting state
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Pulse animation for Connected state
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val isConnected = vpnState is VpnState.Connected
    val isConnecting = vpnState is VpnState.Connecting
    val isError = vpnState is VpnState.Error

    val targetActiveColor = when {
        isConnected -> EmeraldGreen
        isConnecting -> StatusConnecting
        isError -> Color(0xFFFF5252)
        else -> EmeraldGreen
    }

    val activeColor by animateColorAsState(
        targetValue = targetActiveColor,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "powerButtonActiveColor"
    )

    val buttonLabel = when {
        isConnected -> disconnectText
        isConnecting -> cancelText
        else -> connectText
    }

    Box(
        modifier = modifier
            .size(size)
            .scale(if (isConnected) pulseScale else 1f),
        contentAlignment = Alignment.Center
    ) {
        // Outer concentric rings and animation
        Canvas(modifier = Modifier.size(size)) {
            val canvasWidth = this.size.width
            val canvasHeight = this.size.height
            val centerOffset = androidx.compose.ui.geometry.Offset(canvasWidth / 2, canvasHeight / 2)
            val outerRadius = (canvasWidth / 2) - 8.dp.toPx()
            val middleRadius = outerRadius - 12.dp.toPx()

            // Subtle base ring
            drawCircle(
                color = activeColor.copy(alpha = 0.12f),
                radius = outerRadius,
                style = Stroke(width = 2.dp.toPx())
            )

            drawCircle(
                color = activeColor.copy(alpha = 0.08f),
                radius = middleRadius,
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Animated arc for connecting
            if (isConnecting) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            Color.Transparent,
                            activeColor.copy(alpha = 0.2f),
                            activeColor
                        )
                    ),
                    startAngle = rotationAngle,
                    sweepAngle = 140f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Glowing solid ring for connected
            if (isConnected) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(activeColor, activeColor.copy(alpha = 0.6f)),
                        center = centerOffset,
                        radius = outerRadius
                    ),
                    radius = outerRadius,
                    style = Stroke(width = 3.5.dp.toPx())
                )
            }
        }

        // Inner Liquid Glass Circle Button
        val innerSize = size - 42.dp
        Box(
            modifier = Modifier
                .size(innerSize)
                .clip(CircleShape)
                .background(
                    if (TunnixStyle.glass.isDark) {
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF1E2837),
                                Color(0xFF111722),
                                Color(0xFF0C1018)
                            )
                        )
                    } else {
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFFFFF),
                                Color(0xFFF1F5F9),
                                Color(0xFFE2E8F0)
                            )
                        )
                    }
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = activeColor),
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Rounded.PowerSettingsNew,
                    contentDescription = buttonLabel,
                    tint = activeColor,
                    modifier = Modifier.size(46.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = buttonLabel,
                    color = TunnixStyle.glass.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
