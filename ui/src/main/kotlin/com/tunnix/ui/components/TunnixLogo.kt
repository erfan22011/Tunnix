package com.tunnix.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tunnix.ui.theme.EmeraldGreen
import com.tunnix.ui.theme.EmeraldLight

@Composable
fun TunnixLogo(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    primaryColor: Color = EmeraldGreen,
    secondaryColor: Color = EmeraldLight
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height

            // Hexagon path
            val hexPath = Path().apply {
                moveTo(w * 0.5f, h * 0.05f)
                lineTo(w * 0.92f, h * 0.28f)
                lineTo(w * 0.92f, h * 0.72f)
                lineTo(w * 0.5f, h * 0.95f)
                lineTo(w * 0.08f, h * 0.72f)
                lineTo(w * 0.08f, h * 0.28f)
                close()
            }

            // Draw glowing outer border
            drawPath(
                path = hexPath,
                brush = Brush.linearGradient(
                    colors = listOf(primaryColor, secondaryColor, primaryColor)
                ),
                style = Stroke(
                    width = w * 0.06f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Inner stylized "S / T" ribbon path
            val ribbon1 = Path().apply {
                moveTo(w * 0.35f, h * 0.32f)
                lineTo(w * 0.65f, h * 0.32f)
                lineTo(w * 0.40f, h * 0.50f)
                lineTo(w * 0.65f, h * 0.50f)
                lineTo(w * 0.35f, h * 0.68f)
            }

            drawPath(
                path = ribbon1,
                brush = Brush.verticalGradient(
                    colors = listOf(secondaryColor, primaryColor)
                ),
                style = Stroke(
                    width = w * 0.10f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}
