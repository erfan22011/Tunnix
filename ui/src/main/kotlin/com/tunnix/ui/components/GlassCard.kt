package com.tunnix.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tunnix.ui.theme.TunnixStyle

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    borderWidth: Dp = 1.dp,
    borderColor: Color = TunnixStyle.glass.glassBorder,
    backgroundColor: Color = TunnixStyle.glass.cardGlass,
    content: @Composable BoxScope.() -> Unit
) {
    val glassBrush = Brush.linearGradient(
        colors = listOf(
            borderColor,
            borderColor.copy(alpha = 0.05f),
            borderColor.copy(alpha = 0.35f)
        )
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor, shape)
            .border(BorderStroke(borderWidth, glassBrush), shape),
        content = content
    )
}
