package com.example.visualassistant.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun GlassesAnimation(
    modifier: Modifier = Modifier,
    isOnline: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glasses_anim")
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (isOnline) 1f else 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val color = if (isOnline) Color(0xFF4CAF50) else Color.Gray

    Box(
        modifier = modifier.size(100.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 4.dp.toPx()
            
            // Draw Glasses Frame
            val leftLensCenter = center.copy(x = size.width * 0.3f)
            val rightLensCenter = center.copy(x = size.width * 0.7f)
            val lensRadius = size.width * 0.18f
            
            // Lenses
            drawCircle(
                color = color.copy(alpha = alpha * 0.2f),
                radius = lensRadius,
                center = leftLensCenter
            )
            drawCircle(
                color = color.copy(alpha = alpha * 0.2f),
                radius = lensRadius,
                center = rightLensCenter
            )
            
            // Lens Outlines
            drawCircle(
                color = color,
                radius = lensRadius,
                center = leftLensCenter,
                style = Stroke(width = strokeWidth)
            )
            drawCircle(
                color = color,
                radius = lensRadius,
                center = rightLensCenter,
                style = Stroke(width = strokeWidth)
            )
            
            // Bridge
            val bridgePath = Path().apply {
                moveTo(leftLensCenter.x + lensRadius, leftLensCenter.y)
                quadraticTo(
                    center.x, center.y - lensRadius * 0.2f,
                    rightLensCenter.x - lensRadius, rightLensCenter.y
                )
            }
            drawPath(
                path = bridgePath,
                color = color,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            
            // Temples (Sides)
            drawLine(
                color = color,
                start = leftLensCenter.copy(x = leftLensCenter.x - lensRadius),
                end = leftLensCenter.copy(x = 0f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = rightLensCenter.copy(x = rightLensCenter.x + lensRadius),
                end = rightLensCenter.copy(x = size.width),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}
